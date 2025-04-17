package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class StageService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
    private static final Logger logger = LoggerFactory.getLogger(StageService.class);

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    StageRepository stageRepository;
    public List<Stage> scrapeStages() {
        List<Race> races = raceRepository.findAll();
        List<Stage> stagesList = new ArrayList<>();

        for(Race race : races){
            try {
                Document doc = Jsoup.connect(race.getRaceUrl())
                        .userAgent(USER_AGENT)
                        .get();
                System.out.println(race + "search");

                Element header = doc.selectFirst("body > div.wrapper > div.content > div.page-content.page-object.default > div.w48.left.mb_w100 > div:nth-child(1) > h3");
                if (header == null) {
                    logger.error("Required header not found on the page: {}", race.getRaceUrl());
                    continue; //vlogende race
                }

                Elements tables = doc.select("table.basic");
                logger.debug("Number of tables found: {}", tables.size());

                if (tables.size() > 0) {
                    Element stageTable = tables.first();
                    Elements stageTablerow = stageTable.select("tbody > tr");
                    logger.debug("Number of rows found: {}", stageTablerow.size());

                    for (Element stageElement : stageTablerow) {
                        Elements cells = stageElement.select("td");
                        if (cells.size() >= 4) {
                            String date = cells.get(0).text();
                            String stageName = cells.get(3).select("a").text();
                            String stageUrl = cells.get(3).select("a").attr("abs:href");

                            Stage stage = new Stage();
                            stage.setStartDate(date);
                            stage.setName(stageName);
                            logger.info("Scraped stage URL: {}", stageUrl);
                            // stage.setRace(race);
                            stage.setRaceName(race.getName());
                            stageRepository.save(stage);
                            stagesList.add(stage);

                            logger.info("Added stage: {}", stage);

                        }
                    }
                } else {
                    logger.error("No tables found with the selector 'table.basic'");
                }

            } catch (IOException e) {
                logger.error("Error scraping stage details from URL: {}", race.getRaceUrl(), e);
            }
            race.setStages(stagesList);
        }
        return stagesList;
    }
}
