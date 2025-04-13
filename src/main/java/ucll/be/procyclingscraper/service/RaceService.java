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
                    Element raceLinkElement = cells.get(1).selectFirst("a"); // <td> with race name
                    String raceName = raceLinkElement.text();
                    String raceHref = raceLinkElement.attr("href"); // <-- This is what you want
                    String raceLevel = cells.get(2).text();
                    String raceUrl = "https://www.procyclingstats.com/" + raceHref;
                    Race race = new Race();
                    Stage stage = stageService.scrapeStageDetails(raceUrl);
            
                    race.setName(raceName);
                    race.setNiveau(raceLevel);
                    // race.setUrl(raceHref); // Assuming you have a setUrl() method in Race class
            
                    System.out.println("Race: " + raceName + ", URL: " + raceUrl);
            
                    stageRepository.save(stage);
                    race.setStages(List.of(stage));
                    races.add(race);
                    raceRepository.save(race);
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        return races;
    }

    private String cleanRaceName(String raceName) {
        raceName = raceName.toLowerCase();
        raceName = raceName.replaceAll("[^a-z0-9]", "-");
        raceName = raceName.replaceAll("-+", "-");
        raceName = raceName.replaceAll("^-|-$", "");
        return raceName;
    }
}
