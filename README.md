
# ProCycling manager - Documentatie

Dit is een Java Spring Boot applicatie. Deze applicatie dient voor het maken van wielerploegen en competities onder vrienden.
We halen de data op via het scrapen van de website https://www.procyclingstats.com/index.php.

---
# Opstart

Voor het opstarten moet je deze commando's uitvoeren in de folder: `\CyclingManager`
```sh
mvn clean install
mvn compile
mvn spring-boot:run
```

---
## Structuur

### `Models`
Dit zijn de modellen van elke entiteit die we gebruiken in de applicatie.
We gebruiken hier verschillende annotaties voor het vereenvoudigen van het gebruik.
Meer info vindt je hier: [Annotaties](#annotaties)


#### `Cyclist`
Vertegenwoordigt een professionele wielrenner.



`````java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

String name;
int ranking;
int age;
String country;
String teamName;
`````

#### `Team`
Vertegenwoordigt een wielerploeg met bijbehorende renners.
Hiervoor is er ook een one-to-many relatie gelegd met de cyclist. Dus 1 ploeg kan meerdere renners hebben.

`````java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

private String name;
private int ranking;
@OneToMany(mappedBy = "id")
private List<Cyclist> cyclists;
private String teamUrl;
`````


---

#### `CyclistService`
#### scrapeCyclists
- Scrape van de wielrenners data van een team-pagina.
#### scrapeCyclistDetails
- Extract gegevens zoals naam, leeftijd, ranking en land.
##### extractAge
- Extract age from String: "Date of birth: 12th November 2002 (22)"
##### getUciRanking
- Get het correcte element voor de UCI ranking.
  - Dit moet aangezien de UCI ranking kan verplaatsen van plaats per renner. Dit door de PCS ranking en all-time ranking en het hangt af van social media iconen erboven.


#### `TeamService`
- Scrape van de topteams van ProCyclingStats.
- Slaat teams op met hun URL en ranking.

---

### `Repository`
JPA Repositories voor uitvoering van de data die we in de database kunnen vinden. Hierbij zijn verschillende functionaliteiten standaard beschikbaar en moeten we deze niet meer aanmaken. Bijvoorbeeld een findAll of findByValue.

- `CyclistRepository extends JpaRepository<Cyclist, String>`
- `TeamRepository extends JpaRepository<Team, String>`

---

#### `CyclistController`
- **GET /cyclists** – API definitie voor alle linken met Cyclist.


#### `TeamController`
- **GET /teams** – API definitie voor alle linken met Team.

---

## Gebruikte Technologieën

- Spring Boot (Web, JPA)
- Lombok
- Jsoup (HTML parsing)
- PostgreSQL / MySQL (als database)
- REST API met JSON output

----
### Annotaties:
#### Spring Annotaties:
Dit zorgt ervoor dat het gebruik en creatie van de database makkelijker verloopt.
Hierbij gaan we met @Entity zeggen dat dit een entiteit is die aangemaakt moet worden in de database.
@Id gebruiken we voor het aantonen dat de database dit mag gebruiken als identifier en we zullen het ook automatisch laten genereren aan de hand van @GeneratedValue.
`````java
@Entity
@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
`````


#### Lombok:
Lombok is een dependency (pakket) dat we gebruiken in onze applicatie. Dit zorgt ervoor dat we basiscode niet steeds opnieuw moeten schrijven.
Het zal dus zelf alle setters en getters maken die er bijvoorbeeld voor zorgen dat je een renner kan inschrijven in de database of ophalen.

Ook hebben we nog:

`````java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
`````

@Getter en @Setter zorgen automatisch voor het maken van alle getters en setters voor de velden in de klasse.

@AllArgsConstructor Dit zorgt ervoor dat we geen constructor maken. Dit bind alle velden van in de model te samen tot 1 object bijvoorbeeld Cyclist.

@NoArgsConstructor maakt een lege constructor (zonder velden). Deze is bijvoorbeeld nodig als je frameworks zoals JPA gebruikt die een lege constructor nodig heeft.

### Voorbeeld met en zonder lobok

#### Met Lombok:
````Java
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Person {
    private String name;
    private int age;
}

````

#### Zonder Lombok:
````Java
public class Person {
    private String name;
    private int age;

    public Person() {
    }

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}

````