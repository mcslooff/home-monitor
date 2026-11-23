# Home Monitor

Home Monitor is een Java-project voor het verzamelen, bewaren en bekijken van meetgegevens in en rond huis. Het groeit uit tot een platform voor thuismonitoring, geïnspireerd door Domoticz. Het doel is om op één plek live te zien wat er gebeurt, terug te kijken in de tijd en metingen uit verschillende bronnen naast elkaar te leggen.

## Projectstatus

Home Monitor bevindt zich in de proof-of-conceptfase (POC). De huidige Java-applicatie gebruikt Spring Boot, Apache Camel en Eclipse Paho om MQTT-berichten te publiceren en te ontvangen. Het project bevat Moquette als ingebouwde MQTT-broker. De ESP8266/BME680-node is operationeel en levert via MQTT meetgegevens aan Home Monitor. De overige integraties, de opslag in MariaDB en het dashboard staan op de roadmap.

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

De `pom.xml` specificeert Java 21. Voor lokaal ontwikkelen heb je een JDK en Maven nodig.

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
java -jar target/bme680-0.0.1-SNAPSHOT.jar
```

`verify` voert ook de integratietest uit op de gebouwde JAR. Deze start de applicatie in een tijdelijke werkmap met lokale, geïsoleerde HTTP- en MQTT-poorten, controleert de MQTT-verwerking en stopt het proces na afloop. `mvn test` voert deze verpakte-applicatietest niet uit.

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
