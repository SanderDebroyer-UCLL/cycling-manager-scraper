package ucll.be.procyclingscraper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.val;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.repository.StageRepository;

@Service
public class StageService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    private StageRepository stageRepository;

    public List<Stage> scrapStages() {
        List<Stage> stages = new ArrayList<>();
        try {
            Document doc = Jsoup.connect("https://www.procyclingstats.com/races.php?season=2025&month=&category=1&racelevel=2&pracelevel=smallerorequal&racenation=&class=&filter=Filter&p=uci&s=calendar-plus-filters")
                                .userAgent(USER_AGENT)
                                .get();
            Elements raceRows = doc.select("tbody tr");
            for (Element row : raceRows) {
                Elements cells = row.select("td");
                if (cells.size() >= 3) {
                    String raceName = cells.get(1).select("a").text();
                    String raceLevel = cells.get(2).text();

                    // Create a Stage object and set the extracted values
                    Stage stage = scrapeStageDetails(raceName);
                    stage.setName(raceName);
                    stage.setNiveau(raceLevel);

                    // Save the stage to the repository or add it to the list
                    stageRepository.save(stage);
                    stages.add(stage);
                    System.out.println(stage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stages;
    }

    public Stage scrapeStageDetails(String raceName) {
        Stage stage = new Stage();
        String stageUrl = "https://www.procyclingstats.com/race/" + raceName.replace(" ", "-").toLowerCase() + "/2025";
    
        try {
            Document doc = Jsoup.connect(stageUrl)
                                .userAgent(USER_AGENT)
                                .get();
    
            // Print the HTML to verify the structure
            System.out.println("Fetched HTML: " + doc.html());
    
            // Try the initial selector
            Elements infoList = doc.select("ul.infolist.fs13 li");
    
            // If the initial selector fails, try a more specific one
            if (infoList.isEmpty()) {
                infoList = doc.select("div.w50.left.mb_w100.mg_r2 > div:nth-child(4) > ul.infolist.fs13 li");
            }
    
            // Check if the selector matched any elements
            if (infoList.isEmpty()) {
                System.err.println("No elements matched the selectors.");
            }
    
            for (Element info : infoList) {
                String label = info.select("div").first().text();
                String value = info.select("div").last().text();
    
                switch (label) {
                    case "Startdate:":
                        stage.setStartDate(value);
                        System.out.println("Startdate: " + value);
                        break;
                    case "Enddate:":
                        stage.setEndDate(value);
                        System.out.println("Enddate: " + value);
                        break;
                    case "Total distance:":
                        try {
                            stage.setDistance(Integer.parseInt(value));
                            System.out.println("Total distance: " + value);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid distance format: " + value);
                        }
                        break;
                    default:
                        // Handle other labels if needed
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error accessing Stage URL: " + stageUrl);
            e.printStackTrace();
        }
    
        // Print the stage object to verify the extracted data
        System.out.println(stage);
        return stage;
    }
}    