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

HTTP-authenticatie gebruikt de opgeslagen gebruikers. Repositoryfouten worden doorgegeven, niet vertaald naar ontbrekende accounts of een permissieve fallback. MQTT-authenticatie gebruikt uitsluitend afzonderlijke MQTT-accounts; normale MQTT-publish- en subscribe-operaties controleren exacte opgeslagen grants. Zie hieronder de geaccepteerde brokerbeperkingen. Meetgegevens worden nog niet opgeslagen.

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

Rollen zijn `USER`, `ADMIN` en `SENSOR`; meerdere rollen kunnen expliciet worden toegekend. `ADMIN` geeft niet automatisch toegang tot ingestie en `USER` geeft geen impliciete beheer- of ingestietoegang. Niet-genoemde API-mogelijkheden zijn standaard geblokkeerd, inclusief geneste `/bme680/...`-paden. MQTT-accounts kunnen zich niet als HTTP-gebruiker aanmelden.

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

De MQTT-broker weigert anonieme verbindingen en controleert normale publish- en subscribe-operaties. **Uitsluitend bedoeld voor het private, vertrouwde netwerk van de eigenaar; niet publiek bereikbaar maken.** De eigenaar accepteert expliciet de hieronder beschreven Moquette-beperkingen. Dit is geen volledige topicisolatie. De operationele omschakeling vereist nog ticket #7.

### MQTT-accounts en topicrechten beheren

Moquette 0.17 controleert actieve MQTT-accounts en BCrypt-hashes via zijn `IAuthenticator`-extensie. Ontbrekende, ongeldige of onjuist UTF-8-gecodeerde credentials en databasefouten worden geweigerd; HTTP-accounts geven geen MQTT-toegang. De autorisatiepolicy controleert actuele accounts en exacte grants zonder permissiecache. De overgang van de bestaande gedeelde topic naar apparaatgebonden topics en payload-identiteitsbinding volgen in ticket #7. Verander operationele sensornodes nog niet zonder die gecoördineerde omschakeling.

Alle onderstaande aanvragen vereisen HTTP Basic met de rol `ADMIN`. Mutaties vereisen `Content-Type: application/json`; dezelfde JSON/CSRF-regels als bij gebruikersbeheer gelden.

| Aanvraag | JSON-inhoud | Resultaat |
| --- | --- | --- |
| `GET /admin/mqtt-identities` | Geen | Alleen niet-geheime accountmetadata en exacte grants |
| `POST /admin/mqtt-identities/devices` | `username`, `deviceId` | `201`: accountmetadata en eenmalig gegenereerde `secret` |
| `POST /admin/mqtt-identities/services` | `username` | `201`: apart backendaccount en eenmalige `secret` |
| `POST /admin/mqtt-identities/{id}/credentials` | `{}` | `200`: metadata en eenmalige vervangende `secret` |
| `PUT /admin/mqtt-identities/{id}/enabled` | `{"enabled":false}` of `{"enabled":true}` | Alleen metadata, nooit een secret |

Een apparaat-ID bevat 1–64 ASCII-tekens uit `A-Z`, `a-z`, `0-9`, `_` en `-`; slash, punten, wildcards en `$`-systeemnamen zijn verboden. MQTT-gebruikersnamen volgen hetzelfde naamformaat als HTTP-gebruikersnamen, maar worden in een afzonderlijk accountdomein opgeslagen. Dubbele MQTT-namen of apparaat-ID's geven `409`; ongeldige invoer geeft `400`; een onbekende account-ID geeft `404`.

Nieuwe accounts worden ingeschakeld aangemaakt. Elk apparaat krijgt uitsluitend `PUBLISH` op `sensors/<deviceId>/measur` en geen subscribe-recht. Er bestaat geen endpoint om willekeurige grants, wildcards of een ander accounttype aan een apparaat toe te voegen. Gebruikersnaam, apparaat-ID en accounttype zijn niet wijzigbaar; schakel een oud account uit en provision een nieuw account bij een identiteitswijziging.

SERVICE-accounts staan los van apparaten en van HTTP-gebruikers. Elk backendaccount krijgt afzonderlijke `SUBSCRIBE`-grants op de exacte meettopics van alle geregistreerde apparaten, niet op `#`, `sensors/+` of systeemtopics. Nieuwe apparaten voegen ook de benodigde exacte subscriptions toe aan bestaande serviceaccounts. De aanmaakvolgorde verandert deze rechten niet. Dit gebeurt samen met accountaanmaak in één eigen, exclusieve transactie; Flyway V3 voegt daarvoor `mqtt_administration_lock` toe. Geef het runtime-databaseaccount ook `SELECT`/`UPDATE` op die tabel.

Secrets worden server-side gemaakt met 32 cryptografisch willekeurige bytes (256 bits), weergegeven als 43 Base64URL-tekens. De API accepteert geen zelf opgegeven wachtwoorden. Alleen een BCrypt-hash wordt opgeslagen. Alleen de succesvolle aanmaak- of rotatierespons bevat de plaintext `secret`, met `Cache-Control: no-store`; kopieer deze direct naar de beveiligde apparaat- of backendconfiguratie. List- en enabled-responses bevatten geen secrets of hashes. Bij verlies van een secret moet deze worden geroteerd, niet teruggelezen. Gebruik HTTPS en voorkom het loggen van secret-responsen door clients of proxies.

Rotatie vervangt de hash atomair en bewaart apparaat-ID, grants en enabled-status; een uitgeschakeld account wordt daardoor niet opnieuw ingeschakeld. De oude secret matcht daarna niet meer met de opgeslagen hash. De enabled-status wordt eveneens persistent bijgewerkt.

**Client-ID en credentialgeneratie:** metadata bevat `credentialGeneration` en aanbevolen `clientIds`. Een DEVICE gebruikt exact `<username>:v<generation>`; een SERVICE mag bovendien een suffix `:<connection>` gebruiken (1–32 tekens uit letters, cijfers, `_` en `-`), zoals `backend:v1:consumer` en `backend:v1:producer`. De broker controleert gebruikersnaam, generatie, accounttype én wachtwoord vóór sessieovername. Een client-ID is geen authenticatiemiddel. Flyway V4 voegt de persistente generatie toe; bestaande accounts beginnen bij 1.

**Revocatie met brokerbrede herstart:** rotatie en uitschakelen sluiten eerst brokeradmissie en stoppen de ingebouwde broker, inclusief alle verbindingen, subscriptions en in-memory wachtrijen. Daarna commit de databasewijziging en start een nieuwe broker. Ook niet-betrokken clients moeten opnieuw verbinden en subscriben; verwacht kortstondig verlies van beschikbaarheid en mogelijk berichtenverlies (geen duurzame sessies/opslag). Zowel rotatie als uitschakelen verhogen de generatie; opnieuw inschakelen maakt een oude client-ID niet geldig. Rotatie vereist de nieuwe secret én client-ID.

Een database-/commitfout tijdens deze operatie laat de listener uitgeschakeld. Een mislukte brokerherstart geeft `503`; de credentialwijziging kan dan al gecommit zijn en de eenmalige secret is niet uitleesbaar. Herstel database/poort/configuratie en herhaal een rotatie of herstart de applicatie; roteer opnieuw als een secret niet ontvangen is. Er is nooit een anonieme fallback. Onbekende account-ID's worden vóór de herstart afgewezen. Meerdere applicatiereplica's en directe databasewijzigingen zijn niet ondersteund: beheer loopt via deze API op de ene ingebouwde broker.

Optionele Moquette-telemetrie is expliciet uitgeschakeld met `telemetry_enabled=false`; starten vereist geen `data/`-map en maakt geen telemetry-UUID-bestand. Authenticatieweigeringen worden zonder wachtwoorden/hashes gelogd.

### Topiccontroles en geaccepteerde beperkingen

Bij normale publish- en subscribe-aanvragen wordt de ingelogde MQTT-gebruikersnaam opnieuw opgezocht. Enabled-status, credentialgeneratie en een exact passende grant zijn vereist. DEVICE-accounts mogen uitsluitend publiceren op hun eigen `sensors/<deviceId>/measur` en niet subscriben, ook niet als er per ongeluk extra grants zijn opgeslagen. SERVICE-accounts hebben alleen de expliciete operatie per exact topic; geen beheerdersprivileges. Alleen concrete meettopics worden toegestaan, zonder wildcarduitbreiding, root-/systeemtopics of gedeelde subscriptions. Camel accepteert voor `mqtt.topic` eveneens uitsluitend één exact meettopic.

Lookupfouten weigeren de normale operatie. Een succesvolle client-API-call of MQTT PUBACK is op zichzelf geen bewijs van toegestane aflevering; afhandeling en time-outs verschillen per QoS en clientbibliotheek. Retained replay via een nieuwe subscription vereist dat die exacte subscription wordt toegestaan.

**Expliciet door de eigenaar geaccepteerd voor het private netwerk:** Moquette 0.17 roept deze policy niet aan voor last wills en controleert bestaande subscriptions niet opnieuw bij iedere aflevering. Een last will valt daardoor buiten deze topiccontrole. Het intrekken van alleen een read-grant (of een databasefout na het subscriben) stopt een bestaande subscription niet gegarandeerd; bestaande queues en retained delivery vormen evenmin een nieuwe autorisatiecheck. Gebruik de beheer-API om het account uit te schakelen/credentials te roteren als alle bestaande sessies moeten worden beëindigd: die operatie herstart de broker en wist de in-memory sessies. Dat maakt de will-beperking niet ongedaan.

Er wordt geen Moquette-fork, dependencywijziging of aanvullende featureblokkade ingevoerd. De oorspronkelijke volledige-isolatiecriteria van #6 zijn daarmee niet allemaal afgedekt. Verificatie blijft op verzoek beperkt tot policy-only unittests en toegestane netwerkoperaties; er zijn geen nieuwe netwerktests voor de uitgesloten operaties.

### Optionele demo-publisher

Zowel de Camel-consumer als `direct:publishMeasurement` zijn standaard gestopt. Zo kan een lege installatie beveiligd starten voor provisioning, zonder anonieme fallback. Schakel gewenste routes pas in nadat een apart SERVICE-account is aangemaakt.

| Variabele | Standaard | Betekenis |
| --- | --- | --- |
| `MQTT_CONSUMER_ENABLED` | `false` | Expliciet starten van MQTT-ingestie |
| `MQTT_USERNAME` | Leeg | Gebruikersnaam van het SERVICE-account voor Camel |
| `MQTT_PASSWORD` | Leeg | Eenmalig verkregen SERVICE-secret |
| `MQTT_CLIENT_ID` | `unconfigured:v1` | Actuele basis-ID uit metadata, bijvoorbeeld `backend:v1`; Camel voegt `:consumer`/`:producer` toe |
| `MQTT_PRODUCER_ENABLED` | `false` | Expliciet starten van de demo-publisher |
| `MQTT_PRODUCER_TOPIC` | Huidige `mqtt.topic` | Eén concreet publish-topic |

Als de publisher ingeschakeld is, krijgt een nieuw SERVICE-account aanvullend uitsluitend `PUBLISH` op het geconfigureerde producer-topic. Dat topic moet exact `sensors/<deviceId>/measur` zijn en verwijzen naar een al geprovisiond apparaat; anders wordt serviceprovisioning geweigerd. Provision daarom eerst het betreffende apparaat. Bij uitgeschakelde publisher krijgen nieuwe serviceaccounts geen publish-grant.

Een configuratiewijziging herschrijft bestaande grants niet automatisch. Schakel een eerder serviceaccount uit en provision een nieuw, passend account als de publisherfunctie of het producer-topic verandert. Configureer SERVICE-credentials expliciet via een secret store/omgeving, niet in broncode of commandoregels. Na SERVICE-rotatie/uitschakelen moet de Camel-configuratie worden bijgewerkt en de applicatie opnieuw gestart; automatic reconnect met oude credentials blijft terecht geweigerd. Externe clients moeten dezelfde generatie-update uitvoeren. De huidige listener is plaintext MQTT: gebruik uitsluitend het private, vertrouwde netwerk; TLS kan het transport aanvullend beveiligen maar lost de geaccepteerde brokerbeperkingen niet op. Payloadbinding en de volledige apparaatomschakeling blijven #7.

## Roadmap

1. **Beveiligingsbasis voor de POC leggen.** Richt Spring Security in voor gebruikersauthenticatie en toegangsbeheer. Sluit MariaDB aan voor gebruikersbeheer en leg per aangesloten apparaat een eigen MQTT-account vast. Sla wachtwoorden van gebruikers en apparaten als hashes op. Laat de MQTT-broker de inloggegevens van apparaten controleren en beperk per apparaat op welke topics het mag publiceren.
2. **Metingen bewaren.** Sla sensorgegevens op in MariaDB en maak ze opvraagbaar per bron en periode.
3. **Metingen zichtbaar maken.** Bouw een webdashboard met live waarden, historische grafieken en periodieke overzichten.
4. **Meer bronnen aansluiten.** Voeg de Remeha-installatie, een gratis weer-API en de slimme meter toe.
5. **Bronnen vergelijken.** Toon meerdere meetreeksen op één tijdlijn en voeg functies toe om correlaties te onderzoeken.
