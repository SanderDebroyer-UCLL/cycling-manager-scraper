package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.StageModel;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.ParcoursType;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.PointResultRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.StageRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    PointResultRepository pointResultRepository;

    public List<Stage> getStages() {
        return stageRepository.findAll();
    }

    public List<StageModel> getStageDTOs() {
        List<Stage> stages = stageRepository.findAll();
        List<StageModel> stageDTOs = new ArrayList<>();
        for (Stage stage : stages) {
            StageModel stageModel = new StageModel();
            stageModel.setId(stage.getId());
            stageModel.setName(stage.getName());
            stageModel.setStageUrl(stage.getStageUrl());
            stageDTOs.add(stageModel);
        }

        return stageDTOs;
    }

    public List<PointResult> scrapePointResult(ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        int resultCount = 0;
        final int MAX_RESULTS = 1000;
        try {
            List<Race> races = raceRepository.findAll();

            for (Race race : races) {
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                    System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");

                    if (stage.getName().contains("Stage 1 |")) {
                        System.out.println("=== SPECIAL PROCESSING FOR STAGE 1 ===");
                        List<PointResult> pointResults = getPointResultsFromStage1(stage.getStageUrl(),
                                scrapeResultType);
                        for (PointResult pr : pointResults) {
                            if (resultCount >= MAX_RESULTS)
                                break;
                            pr.setStage(stage);
                            pr.setScrapeResultType(scrapeResultType);
                            savePointResult(stage, pr, results);
                            resultCount++;
                            System.out.println("Saved PointResult for " + pr.getCyclist().getName() +
                                    ": " + pr.getPoint() + " points");
                        }
                        continue;
                    }

                    Document doc = fetchStageDocument(race, stage, scrapeResultType);
                    Elements resultRows = resultRows(doc, stage, scrapeResultType);
                    if (resultRows == null || resultRows.isEmpty()) {
                        System.out.println("No rows found in the selected table.");
                        continue;
                    }

                    for (Element row : resultRows) {
                        if (resultCount >= MAX_RESULTS)
                            break;
                        String position = "Unknown";
                        String point = "Unknown";
                        String riderName = "Unknown";
                        PointResult pointResult = null;

                        Element pointElement = row.selectFirst("td:nth-child(10) a");
                        point = pointElement != null ? pointElement.text() : "Unknown";

                        Element positionElement = row.selectFirst("td:first-child");
                        position = positionElement != null ? positionElement.text() : "Unknown";

                        Element riderElement = row.selectFirst("td:nth-child(7) a");
                        riderName = riderElement != null ? riderElement.text() : "Unknown";

                        point = point.replaceAll("[^\\d]", "");
                        if (point.isEmpty()) {
                            System.out.println("Skipping row with empty points for: " + riderName);
                            continue;
                        }

                        Cyclist cyclist = cyclistService.searchCyclist(riderName);
                        if (cyclist == null) {
                            System.out.println("Cyclist not found for name: " + riderName);
                            continue;
                        }

                        pointResult = getOrCreatePointResult(stage, cyclist, scrapeResultType);
                        pointResult = (PointResult) checkForDNFAndMore(position, pointResult);
                        fillPointResultFields(pointResult, position, Integer.parseInt(point), scrapeResultType);

                        savePointResult(stage, pointResult, results);
                        resultCount++;
                        System.out.println("Saved PointResult for " + riderName +
                                ": position " + position + ", " + point + " points");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    public void fillPointResultFields(PointResult pointResult, String position, Integer point,
            ScrapeResultType scrapeResultType) {
        pointResult.setPosition(position);
        pointResult.setPoint(point);
        pointResult.setScrapeResultType(scrapeResultType);
    }

    public PointResult getOrCreatePointResult(Stage stage, Cyclist cyclist, ScrapeResultType scrapeResultType) {
        PointResult pointResult = pointResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist,
                scrapeResultType);
        if (pointResult == null) {
            System.out.println("Creating new PointResult for Stage: " + stage.getName());
            pointResult = new PointResult();
            pointResult.setStage(stage);
            pointResult.setCyclist(cyclist);
        }
        return pointResult;
    }

    public void savePointResult(Stage stage, PointResult pointResult, List<PointResult> results) {
        pointResultRepository.save(pointResult);
        results.add(pointResult);
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
                                stage.setDate(date);
                                stage.setName(stageName);
                                logger.info("Scraped stage URL: {}", stageUrl);
                                stagesList.add(scrapeStageDetails(stage));
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

    private List<PointResult> getPointResultsFromStage1(String stageUrl, ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        Map<String, PointResult> riderResultMap = new HashMap<>();
        String klassementType = scrapeResultType == ScrapeResultType.POINTS ? "POINTS" : "KOM";

        System.out.println("\n======= START STAGE 1 " + klassementType + " SCRAPING =======");

        try {
            String complementaryUrl = stageUrl + "/info/complementary-results";
            System.out.println("Fetching complementary results from: " + complementaryUrl);

            Document doc = Jsoup.connect(complementaryUrl)
                    .userAgent(USER_AGENT)
                    .get();

            Elements h3Elements = doc.select("h3");
            Elements tables = doc.select("table.basic");
            System.out.println("Found " + tables.size() + " complementary tables");

            for (int i = 0; i < h3Elements.size() && i < tables.size(); i++) {
                Element h3Element = h3Elements.get(i);
                Element table = tables.get(i);
                String captionText = h3Element.text();

                boolean isRelevantTable = false;
                if (scrapeResultType == ScrapeResultType.POINTS) {
                    if (captionText.startsWith("Sprint |") || captionText.startsWith("Points at finish")) {
                        isRelevantTable = true;
                        System.out.println("\nProcessing POINTS table: " + captionText);
                    }
                } else if (scrapeResultType == ScrapeResultType.KOM) {
                    if (captionText.startsWith("KOM Sprint")) {
                        isRelevantTable = true;
                        System.out.println("\nProcessing KOM table: " + captionText);
                    }
                }

                if (!isRelevantTable) {
                    System.out.println("Skipping irrelevant table: " + captionText);
                    continue;
                }

                Elements rows = table.select("tbody > tr");
                System.out.println("Found " + rows.size() + " result rows");

                for (Element row : rows) {
                    Element positionElement = row.selectFirst("td:first-child");
                    String position = positionElement != null ? positionElement.text() : "N/A";

                    Element pointElement = row.selectFirst("td:nth-child(4)");
                    String point = pointElement != null ? pointElement.text() : "0";

                    Element riderElement = row.selectFirst("td:nth-child(2) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";

                    Cyclist cyclist = cyclistService.searchCyclist(riderName);
                    if (cyclist == null) {
                        System.out.println("  Cyclist not found: " + riderName);
                        continue;
                    }

                    int pointValue = 0;
                    try {
                        pointValue = Integer.parseInt(point.replaceAll("[^\\d]", ""));
                    } catch (NumberFormatException e) {
                        System.out.println("  Invalid point value: " + point);
                    }

                    System.out.println("  " + cyclist.getName() + " (ID:" + cyclist.getId() +
                            ") earned " + pointValue + " points at position " + position);

                    PointResult pointResult = riderResultMap.get(riderName);
                    if (pointResult == null) {
                        pointResult = new PointResult();
                        pointResult.setCyclist(cyclist);
                        pointResult.setPosition(position);
                        pointResult.setPoint(pointValue);
                        pointResult.setRaceStatus(RaceStatus.FINISHED);
                        riderResultMap.put(riderName, pointResult);
                    } else {
                        int currentPoints = pointResult.getPoint();
                        pointResult.setPoint(currentPoints + pointValue);
                        System.out.println("    Updated total: " + currentPoints + " + " +
                                pointValue + " = " + pointResult.getPoint());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to retrieve point results: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\nFINAL " + klassementType + " RESULTS FOR STAGE 1:");
        for (Map.Entry<String, PointResult> entry : riderResultMap.entrySet()) {
            PointResult pointResult = entry.getValue();
            results.add(pointResult);
            System.out.println("  " + pointResult.getCyclist().getName() + ": " +
                    pointResult.getPoint() + " total points");
        }

        System.out.println("Found " + results.size() + " riders with points");
        System.out.println("======= END STAGE 1 " + klassementType + " SCRAPING =======\n");
        return results;
    }

    private <T> T checkForDNFAndMore(String position, T result) {
        RaceStatus status;
        if (position.equalsIgnoreCase("DNS")) {
            status = RaceStatus.DNS;
        } else if (position.equalsIgnoreCase("DNF")) {
            status = RaceStatus.DNF;
        } else if (position.equalsIgnoreCase("DSQ")) {
            status = RaceStatus.DSQ;
        } else if (position.equalsIgnoreCase("OTL")) {
            status = RaceStatus.OTL;
        } else {
            status = RaceStatus.FINISHED;
        }

        if (result instanceof TimeResult) {
            ((TimeResult) result).setRaceStatus(status);
        } else if (result instanceof PointResult) {
            ((PointResult) result).setRaceStatus(status);
        }
        return result;
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

    private Document fetchStageDocument(Race race, Stage stage, ScrapeResultType scrapeResultType) throws IOException {
        String stageUrl = stage.getStageUrl();
        if (scrapeResultType.equals(ScrapeResultType.GC)) {
            System.out.println("Scraping GC results for stage: " + stage.getName());
            stageUrl = stageUrl + "-gc";
        } else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
            System.out.println("Scraping POINTS results for stage: " + stage.getName());
            stageUrl = stageUrl + "-points";
        } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
            System.out.println("Scraping KOM results for stage: " + stage.getName());
            stageUrl = stageUrl + "-kom";
        }

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1))) {
            if (scrapeResultType.equals(ScrapeResultType.GC)) {
                System.out.println("Last stage in the GC results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/gc";
            } else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
                System.out.println("Last stage in the POINTS results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/points";
            } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
                System.out.println("Last stage in the KOM results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/kom";
            }
        }

        System.out.println("Final URL: " + stageUrl);

        try {
            return Jsoup.connect(stageUrl)
                    .userAgent(USER_AGENT)
                    .get();
        } catch (IOException e) {
            System.err.println("Failed to fetch document from URL: " + stageUrl);
            throw e;
        }
    }

    private static String modifyUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            url = url.substring(0, lastSlashIndex);
        }

        return url;
    }

    private Elements resultRows(Document doc, Stage stage, ScrapeResultType scrapeResultType) {
        Elements tables = doc.select("table.results");
        System.out.println("Number of tables found: " + tables.size());

        Elements resultRows = null;
        if (tables.isEmpty()) {
            System.out.println("No tables found in the document.");
            return null;
        }

        if (scrapeResultType.equals(ScrapeResultType.GC) && stage.getName().startsWith("Stage 1 |")) {
            if (tables.size() > 0) {
                resultRows = tables.get(0).select("tbody > tr");
            }
        } else if (scrapeResultType.equals(ScrapeResultType.GC)) {
            if (tables.size() > 1) {
                resultRows = tables.get(1).select("tbody > tr");
            }
        } else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
            if (tables.size() > 2) {
                resultRows = tables.get(2).select("tbody > tr");
            } else if (tables.size() > 0) {
                System.out.println("POINTS table not found at index 2, using first table as fallback.");
                resultRows = tables.get(0).select("tbody > tr");
            }
        } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
            if (tables.size() > 2) {
                resultRows = tables.get(3).select("tbody > tr");
            } else if (tables.size() > 0) {
                System.out.println("POINTS table not found at index 2, using first table as fallback.");
                resultRows = tables.get(0).select("tbody > tr");
            }
        } else {
            if (tables.size() > 0) {
                resultRows = tables.get(0).select("tbody > tr");
            }
        }

        if (resultRows == null || resultRows.isEmpty()) {
            System.out.println("No rows found in the selected table.");
            return null;
        }
        return resultRows;
    }
}
