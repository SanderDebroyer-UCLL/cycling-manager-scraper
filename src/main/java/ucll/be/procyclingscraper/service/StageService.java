package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.Team;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.StageRepository;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class StageService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
    private static final Logger logger = LoggerFactory.getLogger(StageService.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    StageRepository stageRepository;

    public List<Stage> getStages() {
        return stageRepository.findAll();
    }

    public List<Stage> scrapeStages() {
        List<Race> races = raceRepository.findAll();
        List<Stage> allStages = new ArrayList<>();
    
        for (Race race : races) {
            System.out.println("race: " + race.getName());
            List<Stage> stagesList = new ArrayList<>();
    
            try {
                Document doc = Jsoup.connect(race.getRaceUrl())
                        .userAgent(USER_AGENT)
                        .get();
    
                Elements tables = doc.select("table.basic");
                logger.debug("Number of tables found: {}", tables.size());
    
                for (Element table : tables) {
                    if (isStagesTable(table)) {
                        Elements stageRows = table.select("tbody > tr");
                        logger.debug("Number of stage rows found: {}", stageRows.size());
    
                        for (int i = 0; i < stageRows.size() - 1; i++) {
                            Element stageRow = stageRows.get(i);
                            Elements cells = stageRow.select("td");
                            if (cells.size() >= 4) {
                                String date = cells.get(0).text();
                                String stageName = cells.get(3).select("a").text();
                                String stageUrl = cells.get(3).select("a").attr("abs:href");
                                if (stageUrl.isEmpty() || stageUrl.isBlank()) {
                                    continue;
                                }
                                
                                Stage stage = stageRepository.findByName(stageName);
                                if (stage == null) {
                                    stage = new Stage(); 
                                }
                                stage.setStageUrl(stageUrl);
                                System.out.println("stageURL" + stageUrl);
                                stage.setDate(date);
                                stage.setName(stageName);
                                logger.info("Scraped stage URL: {}", stageUrl);
                                stagesList.add(scrapeStageDetails(stage));
                                logger.info("Added stage: {}", stage);
                            }
                        }
    
                        List<Stage> existingStages = race.getStages();
                        existingStages.removeIf(stage -> !stagesList.contains(stage));
                        race.setStages(stagesList);
                        raceRepository.save(race);
                        break;
                    }
                }
    
            } catch (IOException e) {
                logger.error("Error scraping stage details from URL: {}", race.getRaceUrl(), e);
            }
            allStages.addAll(stagesList);
        }
        return allStages;
    }
    
    private boolean isStagesTable(Element table) {
        Elements headers = table.select("thead th");
        return headers.size() > 0 && "Date".equals(headers.get(0).text().trim());
    }
    
    private Stage scrapeStageDetails(Stage stage) {
        try {
            Document doc = Jsoup.connect(stage.getStageUrl())
                    .userAgent(USER_AGENT)
                    .get();
    
            Elements infoTable = doc.select("div.w30.right.mb_w100 > div > ul.infolist > li");
    
            for (Element row : infoTable) {
                Elements cells = row.select("div");
                if (cells.size() >= 2) {
                    String key = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();
    
                    if ("Start time:".equals(key)) {
                        stage.setStartTime(value);
                    } else if ("Distance:".equals(key)) {
                        if (!value.isEmpty()) {
                        stage.setDistance(Double.parseDouble(value.replaceAll("[^0-9.]", "")));
                        }
                    } else if ("Vertical meters:".equals(key)) {
                        if (!value.isEmpty()) {
                        stage.setVerticalMeters(Double.parseDouble(value.replaceAll("[^0-9.]", "")));
                        }
                    } else if ("Departure:".equals(key)) {
                        stage.setDeparture(value);
                    } else if ("Arrival:".equals(key)) {
                        stage.setArrival(value);
                    }
                }
            }
    
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stage;
    }
    
    
    
    private Date parseDate(String dateString) {
        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            logger.error("Error parsing date: {}", dateString, e);
            return null;
        }
    }
}
