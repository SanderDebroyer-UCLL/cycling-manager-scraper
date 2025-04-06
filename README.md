
# ProCycling manager - Documentatie

Dit is een Java Spring Boot applicatie. Deze applicatie dient voor het maken van wieler ploegen en competities onder vrienden.
We halen de data op via het scrapen vna de website https://www.procyclingstats.com/index.php.

---
# Opstart

Voor het opstarten moet je deze commando's uitvoeren in de folder: `\CyclingManager`
```sh
mvn clean install
mvn compile
mvn spring-boot:run
```

---
## Pakketsstructuur

### `Models`
Dit zijn de modellen van elke entiteit.
We gebruiken hier verschillende annotaties:
```java
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
```

#### `Cyclist`
Vertegenwoordigt een professionele wielrenner.

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

String name;
int ranking;
int age;
String country;
String teamName;
```

#### `Team`
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

private String name;
private int ranking;
@OneToMany(mappedBy = "id")
private List<Cyclist> cyclists;
private String teamUrl;
```
Vertegenwoordigt een wielerploeg met bijbehorende renners.
Hiervoor is er ook een one-To-Many relatie gelged met de cyclist.

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
  - Dit moet aangezien de UCI ranking kan verplaatsen van plaats per renner. Dit door de pcs ranking en all time ranking ook hangt het af van social media iconen erboven. 


#### `TeamService`
- Scrape van de topteams van ProCyclingStats.
- Slaat teams op met hun URL en ranking.

---

### `Repository`
JPA Repositories voor toegang tot de data van de database.

- `CyclistRepository extends JpaRepository<Cyclist, String>`
- `TeamRepository extends JpaRepository<Team, String>`

---

#### `CyclistController`
- **GET /cyclists** – API definities voor alle linken met Cyclist.


#### `TeamController`
- **GET /teams** – API definities voor alle linken met Team.

---

## Gebruikte Technologieën

- Spring Boot (Web, JPA)
- Lombok
- Jsoup (HTML parsing)
- PostgreSQL / MySQL (als database)
- REST API met JSON output


