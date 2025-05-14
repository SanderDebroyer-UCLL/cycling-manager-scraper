package ucll.be.procyclingscraper.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.Team;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.StageRepository;
import ucll.be.procyclingscraper.repository.TeamRepository;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class RaceService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    private StageService stageService;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private CyclistRepository cyclistRepository;

    public List<Race> getRaces() {
        return raceRepository.findAll();
    }

    public List<Race> scrapeRaces() {
        List<Race> races = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://www.procyclingstats.com/races.php?season=2025&month=&category=1&racelevel=2&pracelevel=smallerorequal&racenation=&class=&filter=Filter&p=uci&s=calendar-plus-filters")
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
                    if(race == null){
                        race = new Race();
                    }

                    try {
                        Document docRaceInfo = Jsoup.connect(raceUrl).userAgent(USER_AGENT).get();
                        // stages = stageService.scrapeStageDetails(raceUrl);
                        race.setName(raceName);
                        race.setNiveau(raceLevel);
                        race.setRaceUrl(raceUrl);
                        System.out.println("Race: " + raceName + ", URL: " + raceUrl);

                        Element startDateElement = docRaceInfo.select("ul.infolist.fs13 li:contains(Startdate:) div:last-child").first();
                        if (startDateElement != null) {
                            race.setStartDate(startDateElement.text());
                            System.out.println("Start date: " + startDateElement.text());
                        } else {
                            System.err.println("Start date element not found.");
                        }

                        Element endDateElement = docRaceInfo.select("ul.infolist.fs13 li:contains(Enddate:) div:last-child").first();
                        if (endDateElement != null) {
                            race.setEndDate(endDateElement.text());
                            System.out.println("End date: " + endDateElement.text());
                        } else {
                            System.err.println("End date element not found.");
                        }

                        Element distanceElement = docRaceInfo.select("ul.infolist.fs13 li:contains(Total distance:) div:last-child").first();
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
                        List <Cyclist> startlist = scrapeAndSaveStartlist(raceUrl + "/startlist", race);
                        race.setStartList(startlist);
                        race.setStages(stages);
                        races.add(race);
                        raceRepository.save(race);
                    } catch (Exception e) {
                        System.err.println("Error scraping stages for race: " + raceName);
                        e.printStackTrace();
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return races;
    }

        public List<Cyclist> scrapeAndSaveStartlist(String url, Race race) {
        List<Cyclist> startList = new ArrayList<>();
        System.out.println(url);
        System.out.println(race);

        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            Elements ridersContainers = doc.select("div.ridersCont");

            for (Element ridersCont : ridersContainers) {
                String teamName = ridersCont.select("a.team").text()
                    .replace("(WT)", "")
                    .replace("(CT)", "")
                    .replace("(PRT)", "")
                    .trim();

                System.out.println("Scraped Team Name: " + teamName);

                Team team = teamRepository.findByName(teamName);
                System.out.println("Found Team: " + team);

                if (team != null) {
                Elements riderElements = ridersCont.select("ul li a");
                for (Element riderElement : riderElements) {
                    String riderName = riderElement.text().toLowerCase();
                    System.out.println("Extracted Rider Name: " + riderName);
                    String[] nameParts = riderName.split(" ");
                    String firstName = nameParts[nameParts.length - 1];
                    StringBuilder lastNameBuilder = new StringBuilder();
                    for (int i = 0; i < nameParts.length - 1; i++) {
                        if (i > 0) {
                            lastNameBuilder.append(" ");
                        }
                        lastNameBuilder.append(nameParts[i]);
                    }
                    String lastName = lastNameBuilder.toString();

                    String fixedName = firstName + " " + lastName;
                    System.out.println("Rearranged Rider Name: " + fixedName);

                    
                    Cyclist cyclist = cyclistRepository.findByNameIgnoreCase(fixedName);
                    if (cyclist != null) {  
                        System.out.println("Found Cyclist: " + cyclist.getName());
                        LocalDate currentDate = LocalDate.now();
                        String startDateString = race.getStartDate();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Adjust the pattern as needed
                        LocalDate startDate = LocalDate.parse(startDateString, formatter);
                        if(startDate.isBefore(currentDate)){
                            cyclist.addRace(race.getName());
                        }
                        startList.add(cyclist);
                        cyclistRepository.save(cyclist);
                    } else {
                        System.out.println("Cyclist not found in repository: " + riderName);
                    }
                }

            }
        }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(startList);
        return startList;
    }


}
