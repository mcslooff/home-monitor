# Home Monitor

Home Monitor is een Java-project voor het verzamelen, bewaren en bekijken van meetgegevens in en rond huis. Het groeit uit tot een platform voor thuismonitoring, geïnspireerd door Domoticz. Het doel is om op één plek live te zien wat er gebeurt, terug te kijken in de tijd en metingen uit verschillende bronnen naast elkaar te leggen.

## Projectstatus

Home Monitor bevindt zich in de proof-of-conceptfase (POC). De huidige Java-applicatie gebruikt Spring Boot, Apache Camel en Eclipse Paho om MQTT-berichten te publiceren en te ontvangen. Het project bevat Moquette als ingebouwde MQTT-broker en een MariaDB-opslagbasis voor gebruikers, MQTT-identiteiten en topicrechten. De ESP8266/BME680-node is operationeel en levert via MQTT meetgegevens aan Home Monitor. De overige integraties, meetgegevensopslag en het dashboard staan op de roadmap.

## Gegevensbronnen

| Bron | Koppeling | Voorbeelden van gegevens |
| --- | --- | --- |
| [ESP8266/BME680-sensornodes](https://github.com/mcslooff/esp8266_bme680_display) | MQTT (operationeel) | Temperatuur, relatieve luchtvochtigheid, luchtdruk en gasweerstand |
| Remeha-cv-installatie | Uitleesinterface nog te bepalen | Bedrijfsstatus en aanvoer- en retourtemperatuur, afhankelijk van model en interface |
| Weer | Gratis weer-API, aanbieder nog te kiezen | Buitentemperatuur, neerslag en wind |
| Slimme meter | Uitleesinterface nog te bepalen | Actueel elektriciteitsvermogen, meterstanden, teruglevering en gasverbruik waar beschikbaar |

## Van meting naar inzicht

De Java-backend brengt metingen uit de verschillende bronnen samen en slaat ze op in MariaDB. Bij elke meting horen een bron, een meettijdstip en, waar van toepassing, een eenheid. Zo kunnen gegevens met verschillende meetintervallen op dezelfde tijdlijn worden weergegeven.

- **Live dashboard** toont actuele waarden en wanneer elke bron voor het laatst een meting heeft geleverd.
- **Historische overzichten** tonen grafieken voor een gekozen periode en samenvattingen per dag, week of maand, bijvoorbeeld energieverbruik per dag.
- **Correlaties** helpen verbanden te onderzoeken. Leg bijvoorbeeld buitentemperatuur, cv-activiteit en gasverbruik over dezelfde periode naast elkaar om te zien hoe die samen veranderen.

## Werken met de POC

De `pom.xml` specificeert Java 21. Voor lokaal ontwikkelen heb je een JDK, Maven en MariaDB 11.8 LTS nodig. Voor de integratietests is standaard een Docker-compatibele containeromgeving nodig.

| Onderdeel | Standaardinstelling |
| --- | --- |
| HTTP-poort van de applicatie | `8180` |
| MQTT-poort | `1883` |
| Broker-URL voor Camel | `tcp://127.0.0.1:1883` |
| MQTT-demotopic | `sensors/demo/measur` |

De instellingen staan in [application.yml](src/main/resources/application.yml). Met `MQTT_BROKER_URL` kun je Camel naar een andere broker laten verbinden.

Bouw en verifieer de applicatie met Java 21 en Maven:

```sh
mvn -B verify
```

`verify` start met Testcontainers tijdelijke MariaDB 11.8-databases en test migraties, databaseconstraints en repositories. Ook wordt de gebouwde JAR gestart in een tijdelijke werkmap met lokale, geïsoleerde HTTP- en MQTT-poorten. Deze test controleert MQTT-verwerking en afsluiten, en verifieert dat een databasefout het opstarten blokkeert voordat HTTP of MQTT verbindingen accepteert. Containers en processen worden na afloop gestopt. `mvn test` voert alleen de unittests uit.

Zonder containeromgeving kun je een **aparte, lege testdatabase** op MariaDB 11.8 beschikbaar stellen via `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME` en `TEST_DATABASE_PASSWORD`. De tests passen echte Flyway-migraties toe; gebruik nooit een productie- of ontwikkeldatabase met waardevolle gegevens. Er is geen H2-vervanging en een ontbrekende database/containeromgeving leidt tot een testfout, niet tot overgeslagen tests.

### Databaseconfiguratie en migraties

Maak vooraf een lege database en een eigen databaseaccount aan; de applicatie maakt geen database of databasegebruikers aan. Lever de volgende variabelen aan via je shell, container- of secretconfiguratie:

| Variabele | Betekenis |
| --- | --- |
| `DB_URL` | MariaDB JDBC-URL, bijvoorbeeld `jdbc:mariadb://127.0.0.1:3306/home_monitor` |
| `DB_USERNAME` | Eigen databaseaccount van de applicatie |
| `DB_PASSWORD` | Wachtwoord van dat account; geen standaardwaarde |
| `BOOTSTRAP_ADMIN_USERNAME` | Zelf gekozen beheerdernaam, alleen nodig bij een volledig lege gebruikerstabel |
| `BOOTSTRAP_ADMIN_PASSWORD` | Uniek beheerderswachtwoord, alleen nodig bij de eerste initialisatie |

Start daarna met:

```sh
java -jar target/bme680-0.0.1-SNAPSHOT.jar
```

Flyway voert bij het opstarten de SQL-migraties uit [db/migration](src/main/resources/db/migration) uit en valideert eerder toegepaste migraties. Bij een onbereikbare database, ongeldige inloggegevens of migratiefout start de MQTT-broker niet en wordt de applicatie niet beschikbaar. Schakel Flyway niet uit om een fout te omzeilen. Er is geen automatische tabelgeneratie, `clean` is uitgeschakeld en een bestaande database wordt niet automatisch gebaselined.

Voor de eerste migratie heeft het migratieaccount binnen de eigen database `CREATE`, `SELECT`, `INSERT`, `UPDATE` en `DELETE` nodig, inclusief toegang tot `flyway_schema_history`. Latere migraties kunnen aanvullende DDL-rechten zoals `ALTER`, `INDEX` of `DROP` vereisen; beoordeel dat per migratie. Geef geen globale beheerdersrechten. Standaard gebruikt Flyway het applicatieaccount; voor een afzonderlijk migratieaccount kun je `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER` en `SPRING_FLYWAY_PASSWORD` instellen. Het runtime-account heeft dan alleen de benodigde `SELECT`, `INSERT`, `UPDATE` en `DELETE`-rechten op de identiteitstabellen nodig.

De eerste migratie voegt alleen identiteitstabellen toe:

- `http_user` en `http_user_role`: unieke, hoofdlettergevoelige gebruikersnamen, wachtwoordhashes, actieve status en de rollen `USER`, `ADMIN` en `SENSOR`.
- `mqtt_identity`: aparte MQTT-gebruikersnamen, hashes en actieve status voor `DEVICE`- en `SERVICE`-accounts. Elk apparaat heeft een unieke, stabiele apparaat-ID; serviceaccounts hebben geen apparaat-ID.
- `mqtt_grant`: exacte topics per MQTT-account, met aparte `PUBLISH`- en `SUBSCRIBE`-rechten. Wildcards en systeemtopics zijn in deze opslagbasis niet toegestaan.

De opslag volgt een naar binnen gerichte afhankelijkheidsstructuur:

```text
identity/domain/model                Frameworkvrije domeinmodellen
identity/domain/port                 Repositoryinterfaces voor opslag en ophalen
identity/application                 Frameworkvrije gebruikersbeheer-use-cases en veilige uitvoermodellen
identity/application/port            Grenzen voor hashing en exclusieve beheertransacties
identity/infrastructure/persistence   JPA-entiteiten, Spring Data-repositories en adapters
identity/infrastructure/web           Gevalideerde HTTP-requestmodellen, controllers en foutafhandeling
```

Applicatielogica gebruikt de domeininterfaces, niet de JPA-entiteiten of Spring Data-repositories. De adapters vertalen tussen domeinmodellen en database-entiteiten en begrenzen transacties. Spring Data genereert de opslag- en zoekqueries met gebonden parameters; maatwerkqueries staan uitsluitend als `@Query` op Spring Data-repositories. Alleen de testrepositories hebben native `@Query`-probes om databaseconstraints onafhankelijk van Java-validatie te controleren. De tests bewaken dat domein en applicatielogica niet van Spring, JPA of infrastructuur afhankelijk zijn.

Flyway blijft als enige verantwoordelijk voor schemawijzigingen. Hibernate valideert uitsluitend de entiteitmapping tegen het bestaande schema (`ddl-auto: validate`) en wijzigt geen tabellen; `open-in-view` is uitgeschakeld.

Er worden geen accounts, wachtwoorden of topicrechten door migraties aangemaakt. Nieuwe wachtwoorden worden met de gedeelde Spring `PasswordEncoder` als BCrypt-hash met `{bcrypt}`-prefix opgeslagen. De Java-modellen en databaseconstraints weigeren plaintext en `{noop}`-waarden. Hashes worden in modeldiagnostiek afgeschermd; gebruik deze interne opslagmodellen niet als API-respons. Een nieuw hashalgoritme vraagt een expliciete beleids- en schemamigratie; de prefix maakt gecontroleerde ondersteuning van meerdere algoritmen mogelijk.

Wijzig nooit een toegepaste migratie. Voeg een nieuwe versie toe, maak vóór een migratie een databaseback-up en herstel bij een probleem een compatibele versie of pas een gecontroleerde forward-fix toe. MariaDB-DDL is niet als geheel transactioneel: een mislukte migratie kan gedeeltelijke tabellen achterlaten. Onderzoek en herstel die situatie voordat je Flyway opnieuw uitvoert; gebruik geen automatische `clean` of `repair` als herstelstrategie.

HTTP-authenticatie gebruikt de opgeslagen gebruikers. Repositoryfouten worden doorgegeven, niet vertaald naar ontbrekende accounts of een permissieve fallback. MQTT-authenticatie en topicautorisatie volgen in tickets #5 en #6; meetgegevens worden nog niet opgeslagen.

### Eerste beheerder en gebruikersbeheer

Bij de eerste start met een lege gebruikerstabel moet de operator `BOOTSTRAP_ADMIN_USERNAME` en `BOOTSTRAP_ADMIN_PASSWORD` via de secretconfiguratie aanbieden. Er bestaat geen standaardaccount en geen publiek bootstrapendpoint. Namen bevatten 1–64 ASCII-tekens uit `A-Z`, `a-z`, `0-9`, `_`, `.`, `@` en `-`. Wachtwoorden hebben minimaal 12 tekens en maximaal 72 UTF-8-bytes, passend bij BCrypt.

De initialisatie maakt precies één actieve `ADMIN` aan en wordt vóór beschikbaarheid van HTTP en MQTT uitgevoerd. Zodra er gebruikers bestaan, worden bootstrapcredentials genegeerd: opnieuw starten wijzigt geen accounts of wachtwoorden. Verwijder de bootstrapsecret na de eerste start uit de configuratie. Een niet-lege database zonder actieve beheerder wordt niet automatisch hersteld met een nieuw account; opstarten faalt en vereist een beperkte operatorherstelactie of een bekende, compatibele back-up. Een beheerder kan wachtwoorden van bestaande accounts via de beheer-API herstellen.

Alle HTTP-aanvragen gebruiken Basic-authenticatie. Er worden geen authenticatiesessies of tokens opgeslagen. Ontbrekende, onjuiste en uitgeschakelde credentials leveren bij geldige JSON-API-aanvragen `401` op; onvoldoende rechten leveren `403` op.

| Aanvraag | Vereiste rol | JSON-inhoud |
| --- | --- | --- |
| `POST /bme680` | `SENSOR` | Bestaand meetgegevensformaat |
| `GET /admin/users` | `ADMIN` | Geen; retourneert alleen ID, naam, actieve status en rollen |
| `POST /admin/users` | `ADMIN` | `username`, `password`, `enabled` en niet-lege `roles` |
| `PUT /admin/users/{id}/roles` | `ADMIN` | `{"roles":["USER"]}` |
| `PUT /admin/users/{id}/password` | `ADMIN` | `password`; bij succes `204`, geen responsbody |
| `PUT /admin/users/{id}/enabled` | `ADMIN` | `{"enabled":false}` |

Rollen zijn `USER`, `ADMIN` en `SENSOR`; meerdere rollen kunnen expliciet worden toegekend. `ADMIN` geeft niet automatisch toegang tot ingestie en `USER` geeft geen impliciete beheer- of ingestietoegang. Niet-genoemde API-mogelijkheden zijn standaard geblokkeerd, inclusief geneste `/bme680/...`-paden. MQTT-accounts kunnen zich niet als HTTP-gebruiker aanmelden. MQTT-beheerendpoints worden pas in ticket #4 toegevoegd.

Alle mutaties vereisen `Content-Type: application/json` en een gevalideerde JSON-body. Verplicht ontbrekende of ongeldige velden leveren `400` op; een bestaande gebruikersnaam of verwijderen van de laatste actieve beheerder geeft `409`; een onbekende gebruikers-ID geeft `404`. Gebruikersnamen zijn niet wijzigbaar. Aanmaken geeft `201` met uitsluitend veilige accountmetadata terug. Wachtwoorden en hashes worden nooit teruggestuurd of als requestdiagnostiek gelogd.

Rolwijzigingen, wachtwoordresets en uitschakelen gelden voor de volgende aanvraag; gebruikersgegevens worden niet gecachet. Alle gebruikersmutaties en bootstrap delen een transactionele database-rijlock (`V2__serialize_user_administration.sql`), zodat ook twee applicatie-instanties niet gelijktijdig de laatste actieve beheerder kunnen uitschakelen of diens beheerrol verwijderen. Het runtime-databaseaccount heeft ook `SELECT`/`UPDATE` op `user_administration_lock` nodig.

**CSRF-keuze:** Basic-credentials kunnen door browsers automatisch worden meegestuurd; statelessness is daarom geen reden om CSRF uit te schakelen. De CSRF-filter blijft actief voor eenvoudige browseraanvragen zoals formulieren en `text/plain`. Alleen de genoemde JSON-mutaties zijn uitgezonderd: `application/json` is geen CORS-safelisted content type en cross-origin browseraanvragen vereisen een preflight. De applicatie staat geen cross-origin toegang toe. Voeg niet zonder herbeoordeling CORS-toestemming, alternatieve eenvoudige contenttypes of muterende GET/DELETE-endpoints toe. De bestaande JSON-sensorclients hebben hierdoor geen nieuw CSRF-token nodig.

**Overstap vanaf de POC:** de drie vaste HTTP-testaccounts zijn verwijderd. Beschouw alle eerder ingecheckte wachtwoorden als gecompromitteerd; hergebruik ze niet voor nieuwe accounts. Initialiseer de beheerder en maak vóór omschakelen van HTTP-clients nieuwe accounts met de vereiste rollen aan. HTTP Basic beveiligt het transport niet: gebruik HTTPS (eventueel via een reverse proxy) buiten een expliciet vertrouwd, geïsoleerd netwerk.

De applicatie-entrypoint is `nl.tufa.poc.bme680.App`. Instellingen kunnen ook via command-line-argumenten worden overschreven, bijvoorbeeld voor uitsluitend lokale toegang:

```sh
java -jar target/bme680-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=8181 \
  --mqtt.broker.host=127.0.0.1 --mqtt.broker.port=1884
```

De MQTT-broker staat voorlopig nog anonieme verbindingen toe. Stel deze niet bloot aan een onvertrouwd netwerk; brokerbeveiliging volgt in tickets #5 en #6.

## Roadmap

1. **Beveiligingsbasis voor de POC leggen.** Richt Spring Security in voor gebruikersauthenticatie en toegangsbeheer. Sluit MariaDB aan voor gebruikersbeheer en leg per aangesloten apparaat een eigen MQTT-account vast. Sla wachtwoorden van gebruikers en apparaten als hashes op. Laat de MQTT-broker de inloggegevens van apparaten controleren en beperk per apparaat op welke topics het mag publiceren.
2. **Metingen bewaren.** Sla sensorgegevens op in MariaDB en maak ze opvraagbaar per bron en periode.
3. **Metingen zichtbaar maken.** Bouw een webdashboard met live waarden, historische grafieken en periodieke overzichten.
4. **Meer bronnen aansluiten.** Voeg de Remeha-installatie, een gratis weer-API en de slimme meter toe.
5. **Bronnen vergelijken.** Toon meerdere meetreeksen op één tijdlijn en voeg functies toe om correlaties te onderzoeken.
