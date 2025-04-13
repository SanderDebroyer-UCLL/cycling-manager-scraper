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
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.repository.StageRepository;

@Service
public class StageService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    private StageRepository stageRepository;

    public Stage scrapeStageDetails(String raceUrl) {
        Stage stage = new Stage();
        // raceUrl = cleanRaceName(raceUrl);
        // String stageUrl = "https://www.procyclingstats.com/race/" + raceUrl + "/2025";
        System.out.println(raceUrl);
        try {
            Document doc = Jsoup.connect(raceUrl)
                                .userAgent(USER_AGENT)
                                .get();
    
            // Extract start date
            Element startDateElement = doc.select("ul.infolist.fs13 li:contains(Startdate:) div:last-child").first();
            if (startDateElement != null) {
                stage.setStartDate(startDateElement.text());
                System.out.println("Startdate: " + startDateElement.text());
            } else {
                System.err.println("Start date element not found.");
            }
    
            // Extract end date
            Element endDateElement = doc.select("ul.infolist.fs13 li:contains(Enddate:) div:last-child").first();
            if (endDateElement != null) {
                stage.setEndDate(endDateElement.text());
                System.out.println("Enddate: " + endDateElement.text());
            } else {
                System.err.println("End date element not found.");
            }
    
            // Extract total distance
            Element distanceElement = doc.select("ul.infolist.fs13 li:contains(Total distance:) div:last-child").first();
            if (distanceElement != null) {
                try {
                    stage.setDistance(Integer.parseInt(distanceElement.text().replaceAll("[^0-9]", "")));
                    System.out.println("Total distance: " + distanceElement.text());
                } catch (NumberFormatException e) {
                    System.err.println("Invalid distance format: " + distanceElement.text());
                }
            } else {
                System.err.println("Distance element not found.");
            }
    
        } catch (IOException e) {
            System.err.println("Error accessing Stage URL: " + raceUrl);
            e.printStackTrace();
        }
    
        // Print the stage object to verify the extracted data
        System.out.println(stage);
        return stage;
    }
    
    
    
    
    
}    