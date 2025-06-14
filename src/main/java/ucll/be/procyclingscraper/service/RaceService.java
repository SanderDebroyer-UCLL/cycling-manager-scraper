package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.RaceDTO;
import ucll.be.procyclingscraper.dto.RaceModel;
import ucll.be.procyclingscraper.dto.StageDTO;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.Team;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.TeamRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RaceService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private CyclistRepository cyclistRepository;

    @Autowired
    private CyclistService cyclistService;

    public List<RaceDTO> getRaces() {
        List<Race> races = raceRepository.findAllWithStagesAndCompetitions();
        List<RaceDTO> raceDTOs = new ArrayList<>();

        for (Race race : races) {
            // Map stages to StageDTOs
            List<StageDTO> stageDTOs = race.getStages().stream().map(stage -> new StageDTO(
                    stage.getId(),
                    stage.getName(),
                    stage.getDeparture(),
                    stage.getArrival(),
                    stage.getDate().toString(), // format as needed
                    stage.getStartTime(),
                    stage.getDistance(),
                    stage.getStageUrl(),
                    stage.getVerticalMeters(),
                    stage.getParcoursType())).collect(Collectors.toList());

            // Create RaceDTO
            RaceDTO raceDTO = new RaceDTO(
                    race.getId(),
                    race.getName(),
                    race.getNiveau(),
                    race.getStartDate() != null ? race.getStartDate().toString() : "",
                    race.getEndDate() != null ? race.getEndDate().toString() : "",
                    race.getDistance(),
                    race.getRaceUrl(),
                    race.getCompetitions().stream().map(c -> c.getId()).toList(),
                    stageDTOs);

            raceDTOs.add(raceDTO);
        }

        return raceDTOs;
    }

    public String getRaceUrlByName(String name) {
        Race race = raceRepository.findByName(name.trim());
        if (race == null) {
            throw new IllegalArgumentException("Race met naam '" + name + "' is niet gevonden in de database.");
        }
        return race.getRaceUrl();
    }

    public List<Race> fetchOneDayRaces() {
        return raceRepository.findRaceByStagesIsEmpty();
    }

    public List<Race> scrapeRaces() {
        List<Race> races = new ArrayList<>();

        try {
            List<String> urls = new ArrayList<>();
            //pas maand aan naar wat ge wilt scrapen
            for (int month = 1; month <= 12; month++) {
                int currentYear = java.time.LocalDate.now().getYear();
                urls.add("https://www.procyclingstats.com/races.php?season=" + currentYear + "&month=" + month + "&category=1&racelevel=4&pracelevel=smallerorequal&racenation=&class=&filter=Filter&p=uci&s=calendar-plus-filters");
            }
            System.out.println("Scraping URLs: " + urls);
            for (String url : urls) {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .get();

                Elements raceRows = doc.select("tbody tr");

                for (Element row : raceRows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 3) {
                        Element raceLinkElement = cells.get(1).selectFirst("a");
                        String raceName = raceLinkElement.text();
                        String raceHref = raceLinkElement.attr("href");
                        String raceLevel = cells.get(2).text();
                        String raceUrl = "https://www.procyclingstats.com/" + raceHref;
                        Race race = raceRepository.findByName(raceName);
                        if (race == null) {
                            race = new Race();
                        }

                        try {
                            Document docRaceInfo = Jsoup.connect(raceUrl).userAgent(USER_AGENT).get();
                            race.setName(raceName);
                            race.setNiveau(raceLevel);
                            race.setRaceUrl(raceUrl);
                            System.out.println("Race: " + raceName + ", URL: " + raceUrl);

                            Element startDateElement = docRaceInfo
                                    .select("ul.infolist.fs13 li:contains(Startdate:) div:last-child").first();
                            if (startDateElement != null) {
                                race.setStartDate(startDateElement.text());
                                System.out.println("Start date: " + startDateElement.text());
                            } else {
                                System.err.println("Start date element not found.");
                            }

                            Element endDateElement = docRaceInfo
                                    .select("ul.infolist.fs13 li:contains(Enddate:) div:last-child").first();
                            if (endDateElement != null) {
                                race.setEndDate(endDateElement.text());
                                System.out.println("End date: " + endDateElement.text());
                            } else {
                                System.err.println("End date element not found.");
                            }

                            Element distanceElement = docRaceInfo
                                    .select("ul.infolist.fs13 li:contains(Total distance:) div:last-child").first();
                            if (distanceElement != null) {
                                try {
                                    race.setDistance(Integer.parseInt(distanceElement.text().replaceAll("[^0-9]", "")));
                                    System.out.println("Total distance: " + distanceElement.text());
                                } catch (NumberFormatException e) {
                                    System.err.println("Invalid distance format: " + distanceElement.text());
                                }
                            } else {
                                System.err.println("Distance element not found.");
                            }
                            List<Cyclist> startlist = scrapeAndSaveStartlist(raceUrl + "/startlist", race);
                            race.setStartList(startlist);
                            race.setStages(new ArrayList<>());
                            races.add(race);
                            raceRepository.save(race);
                        } catch (Exception e) {
                            System.err.println("Error scraping stages for race: " + raceName);
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return races;
    }

    public Race scrapeRaceByUrl(String name) {

        try {

            Document docRaceInfo = Jsoup.connect(getRaceUrlByName(name)).userAgent(USER_AGENT).get();

            String raceName = docRaceInfo.select("h1").text(); // Of haal het uit URL of elders op pagina
            Race race = raceRepository.findByName(raceName);
            if (race == null) {
                race = new Race();
            }

            race.setName(raceName);
            race.setRaceUrl(getRaceUrlByName(name));

            List<Cyclist> startlist = scrapeAndSaveStartlist(getRaceUrlByName(name) + "/startlist", race);
            race.setStartList(startlist);

            raceRepository.save(race);
            return race;

        } catch (IOException e) {
            System.err.println("Fout bij het scrapen van de race: " + getRaceUrlByName(name));
            e.printStackTrace();
            return null;
        }
    }

    public List<Cyclist> scrapeAndSaveStartlist(String url, Race race) {
        List<Cyclist> startList = new ArrayList<>();
        System.out.println(url);
        System.out.println(race);

        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            Elements ridersContainers = doc.select("div.ridersCont");

            if (ridersContainers.isEmpty()) {
                System.out.println("Geen rennerscontainers gevonden op de pagina. Scraping gestopt.");
                return startList;
            }

            // Bepalen of we met een WK te maken hebben
            boolean isChampionship = true;
            for (Element ridersCont : ridersContainers) {
                String teamName = ridersCont.select("a.team").text()
                        .replace("(WT)", "")
                        .replace("(CT)", "")
                        .replace("(PRT)", "")
                        .trim();
                System.out.println("Team Name: " + teamName);

                Team team = teamRepository.findByName(teamName);
                System.out.println("Found Team: " + team);
                if (team != null) {
                    isChampionship = false;
                    break; // Zodra we een team herkennen, is het geen Championship
                }
            }

            System.out.println(" Is Championship (WK/EK): " + isChampionship);

            for (Element ridersCont : ridersContainers) {
                // Team-naam nog steeds uitlezen, puur voor logging
                String teamName = ridersCont.select("a.team").text()
                        .replace("(WT)", "")
                        .replace("(CT)", "")
                        .replace("(PRT)", "")
                        .trim();

                if (!isChampionship) {
                    if (teamName.contains("(NAT)")) {
                        System.out.println("Nationaal team gevonden: " + teamName);
                    } else {
                        Team team = teamRepository.findByName(teamName);
                        if (team == null) {
                            System.out.println("Team niet herkend, overslaan: " + teamName);
                            continue; // We verwerken deze groep niet
                        }
                        System.out.println("Verwerk team: " + teamName);
                    }

                } else {
                    System.out.println("Verwerk nationale selectie: " + teamName);
                }

                Elements listElements = ridersCont.select("ul li");

                for (Element listElement : listElements) {
                    // The rider's name is inside the <a> tag
                    Element riderAnchor = listElement.selectFirst("a");
                    String riderName = riderAnchor != null ? riderAnchor.text() : "";
                    System.out.println("Rider name: " + riderName);

                    // Check for asterisk (youth indicator) in the list element HTML
                    boolean hasAsterisk = listElement.html().contains("*");
                    System.out.println("Asterisk for rider: " + riderName + " - " + hasAsterisk);

                    String asterisks = hasAsterisk ? "*" : "";

                    System.out.println("Extracted Rider Name: " + riderName);

                    String[] nameParts = riderName.trim().split("\s+");
                    String fixedName = "";
                    for (int i = 1; i < nameParts.length; i++) {
                        String firstName = String.join(" ", Arrays.copyOfRange(nameParts, i, nameParts.length));
                        String lastName = String.join(" ", Arrays.copyOfRange(nameParts, 0, i));
                        fixedName = firstName + " " + lastName;

                        System.out.println("Trying rearranged name: " + fixedName);
                    }

                    Cyclist cyclist = cyclistRepository.findByNameIgnoreCase(fixedName);
                    if (cyclist != null) {
                        System.out.println("Found Cyclist: " + cyclist.getName());

                        if (asterisks.contains("*")) {
                            System.out.println("Rider has asterisks: " + riderName);
                            if (!race.getYouthCyclistsIDs().contains(cyclist.getId())) {
                                race.addToYouthCyclistsIDs(cyclist.getId());
                                raceRepository.save(race);
                            }
                        }

                        LocalDate currentDate = LocalDate.now();
                        LocalDate startDate = LocalDate.parse(race.getStartDate(),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                        startList.add(cyclist);
                        System.out.println("Startlist" + startList.get(0).getName());
                        cyclistRepository.save(cyclist);

                        if (startDate.isAfter(currentDate)) {
                            if (!cyclist.getUpcomingRaces().contains(race.getName())) {
                                cyclist.addRace(race.getName());
                            }
                        }

                    } else {
                        System.out.println("Cyclist not found in repository: " + riderName);

                        if (!isChampionship && teamName.contains("(NAT)")) {
                            System.out.println("Nationaal team gevonden: " + teamName);
                            String riderUrl = riderAnchor.attr("href");
                            System.out.println(riderUrl);
                            if (riderUrl != null && !riderUrl.isEmpty()) {
                                riderUrl = "https://www.procyclingstats.com/" + riderUrl;
                                System.out.println("Scraping details from: " + riderUrl);

                                Cyclist newCyclist = cyclistService.scrapeCyclistDetails(riderUrl);
                                newCyclist.setCyclistUrl(riderUrl);

                                if (newCyclist.getName() == null || newCyclist.getName().isEmpty()) {
                                    newCyclist.setName(riderName);
                                }

                                cyclistRepository.save(newCyclist);
                                startList.add(newCyclist);

                                if (asterisks.contains("*")) {
                                    if (!race.getYouthCyclistsIDs().contains(newCyclist.getId())) {
                                        race.addToYouthCyclistsIDs(newCyclist.getId());
                                        raceRepository.save(race);
                                    }
                                }

                                System.out.println("Nieuwe nationale Renner toegevoegd: " + newCyclist.getName());

                                LocalDate currentDate = LocalDate.now();
                                LocalDate startDate = LocalDate.parse(race.getStartDate(),
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                if (startDate.isAfter(currentDate)) {
                                    newCyclist.addRace(race.getName());
                                }
                            } else {
                                System.err.println("Geen URL gevonden voor renner: " + riderName);
                            }
                        }
                    }

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Verwerkte startlijst: " + startList);
        return startList;
    }

    public List<RaceModel> getRaceDTOs() {
        List<Race> races = raceRepository.findAll(Sort.by("id"));
        List<RaceModel> raceDTOs = new ArrayList<>();

        for (Race race : races) {
            RaceModel raceModel = new RaceModel();
            raceModel.setId(race.getId());
            raceModel.setName(race.getName());
            raceModel.setRaceUrl(race.getRaceUrl());
            raceDTOs.add(raceModel);
        }
        return raceDTOs;
    }

}
