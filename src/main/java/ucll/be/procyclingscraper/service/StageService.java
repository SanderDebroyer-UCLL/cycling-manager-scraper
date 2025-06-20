package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import ucll.be.procyclingscraper.dto.StageDTO;
import ucll.be.procyclingscraper.model.ParcoursType;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.repository.CyclistRepository;
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

    @Autowired
    CyclistRepository cyclistRepository;

    @Autowired
    CyclistService cyclistService;

    public List<StageDTO> getStages() {
        return stageRepository.findAllBasicStages();
    }

    public List<Stage> scrapeStages() {
        List<Race> races = raceRepository.findAll(Sort.by("id"));
        System.out.println("Number of races found in the database: " + races.size());
        List<Stage> allStages = new ArrayList<>();

        for (Race race : races) {
            try {
                List<Stage> stages = scrapeStagesByRaceId(race.getId());
                allStages.addAll(stages);
            } catch (Exception e) {
                logger.error("Failed to scrape stages for race: " + race.getName(), e);
            }
        }

        return allStages;
    }

    @Transactional
    public List<Stage> scrapeStagesByRaceId(Long raceId) {
        List<Stage> allStages = new ArrayList<>();

        Race race = raceRepository.findByIdWithStages(raceId)
                .orElseThrow(() -> new IllegalArgumentException());
        // Race race = raceRepository.findById(raceId).orElseThrow(() -> new
        // IllegalArgumentException());
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
                            stage.setDate(date);
                            stage.setName(stageName);
                            stage.setRace(race);
                            logger.info("Scraped stage URL: {}", stageUrl);
                            stagesList.add(scrapeStageDetails(stage));
                        }
                    }

                    List<Stage> existingStages = race.getStages();
                    existingStages.removeIf(stage -> !stagesList.contains(stage));
                    System.out.println("Existing stages of race" + race.getName() + ": " + existingStages.size());
                    race.setStages(stagesList);
                    System.out.println("Persisting race " + race.getName() + " after stage added");
                    raceRepository.save(race);
                    break;
                }
            }

        } catch (IOException e) {
            logger.error("Error scraping stage details from URL: {}", race.getRaceUrl(), e);
        }
        allStages.addAll(stagesList);

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
                    } else if ("Parcours type:".equals(key)) {
                        System.out.println("In de if statement");
                        Element span = cells.get(1).selectFirst("span");
                        if (span != null) {
                            String className = span.className();
                            System.out.println("Class name: " + className);
                            checkParcoursType(stage, className);
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

    private void checkParcoursType(Stage stage, String value) {
        switch (value) {
            case "icon profile p1":
                stage.setParcoursType(ParcoursType.FLAT);
                break;
            case "icon profile p2":
                stage.setParcoursType(ParcoursType.HILLY);
                break;
            case "icon profile p3":
                stage.setParcoursType(ParcoursType.HILLY_HILL_FINISH);
                break;
            case "icon profile p4":
                stage.setParcoursType(ParcoursType.MOUNTAIN);
                break;
            case "icon profile p5":
                stage.setParcoursType(ParcoursType.MOUNTAIN_HILL_FINISH);
                break;
            default:
                stage.setParcoursType(null);
        }
    }
}
