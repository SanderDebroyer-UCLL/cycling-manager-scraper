package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.StageRepository;

import java.io.IOException;
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
                    Race race = new Race();

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
}
