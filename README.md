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

Er worden geen accounts, wachtwoorden of topicrechten door migraties aangemaakt. Nieuwe wachtwoorden worden met de gedeelde Spring `PasswordEncoder` als BCrypt-hash met `{bcrypt}`-prefix opgeslagen. De Java-modellen en databaseconstraints weigeren plaintext en `{noop}`-waarden. Hashes worden in modeldiagnostiek afgeschermd; gebruik deze interne opslagmodellen niet als API-respons. Een nieuw hashalgoritme vraagt een expliciete beleids- en schemamigratie; de prefix maakt gecontroleerde ondersteuning van meerdere algoritmen mogelijk.

Wijzig nooit een toegepaste migratie. Voeg een nieuwe versie toe, maak vóór een migratie een databaseback-up en herstel bij een probleem een compatibele versie of pas een gecontroleerde forward-fix toe. MariaDB-DDL is niet als geheel transactioneel: een mislukte migratie kan gedeeltelijke tabellen achterlaten. Onderzoek en herstel die situatie voordat je Flyway opnieuw uitvoert; gebruik geen automatische `clean` of `repair` als herstelstrategie.

Deze stap legt alleen de opslagbasis. HTTP gebruikt tot ticket #3 nog de bestaande POC-testaccounts; MQTT-authenticatie en topicautorisatie volgen in tickets #5 en #6. Repositoryfouten worden doorgegeven, niet vertaald naar een permissieve fallback. Dit is nog geen databasegestuurde runtime-authenticatie en bevat geen meetgegevensopslag.

De applicatie-entrypoint is `nl.tufa.poc.bme680.App`. Instellingen kunnen ook via command-line-argumenten worden overschreven, bijvoorbeeld voor uitsluitend lokale toegang:

```sh
java -jar target/bme680-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=8181 \
  --mqtt.broker.host=127.0.0.1 --mqtt.broker.port=1884
```

De POC bevat nog vaste HTTP-testaccounts en staat anonieme MQTT-verbindingen toe. Deze buildbasis verandert dat niet; stel de applicatie niet bloot aan een onvertrouwd netwerk. De beveiligingsaanpassingen volgen in roadmapstap 1.

## Roadmap

1. **Beveiligingsbasis voor de POC leggen.** Richt Spring Security in voor gebruikersauthenticatie en toegangsbeheer. Sluit MariaDB aan voor gebruikersbeheer en leg per aangesloten apparaat een eigen MQTT-account vast. Sla wachtwoorden van gebruikers en apparaten als hashes op. Laat de MQTT-broker de inloggegevens van apparaten controleren en beperk per apparaat op welke topics het mag publiceren.
2. **Metingen bewaren.** Sla sensorgegevens op in MariaDB en maak ze opvraagbaar per bron en periode.
3. **Metingen zichtbaar maken.** Bouw een webdashboard met live waarden, historische grafieken en periodieke overzichten.
4. **Meer bronnen aansluiten.** Voeg de Remeha-installatie, een gratis weer-API en de slimme meter toe.
5. **Bronnen vergelijken.** Toon meerdere meetreeksen op één tijdlijn en voeg functies toe om correlaties te onderzoeken.
