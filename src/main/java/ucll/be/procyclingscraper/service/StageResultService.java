package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.StageResultWithCyclistDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.StageResult;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.repository.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StageResultService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    TimeResultRepository timeResultRepository;

    @Autowired
    StageRepository stageRepository;

    @Autowired
    CyclistRepository cyclistRepository;

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    CyclistService cyclistService;

    @Autowired
    PointResultRepository pointResultRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    public List<Cyclist> findCyclistInByStageId(Long stage_id, String type) {
        return cyclistRepository.findCyclistsByStageIdAndResultType(stage_id, type);
    }

    public void getStageResultsForAllStagesInCompetitions() throws IOException {
        List<Competition> competitions = competitionRepository.findAll();

        List<Race> uniqueRaces = competitions.stream()
                .flatMap(competition -> competition.getRaces().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Race race : uniqueRaces) {
            scrapeTimeResultForRace(ScrapeResultType.STAGE, race.getId());
        }
    }

    public void getStageResultsForAllStagesInCompetition(Long competitionId) throws IOException {
        Optional<Competition> competitions = competitionRepository.findById(competitionId);

        List<Race> uniqueRaces = competitions.stream()
                .flatMap(competition -> competition.getRaces().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Race race : uniqueRaces) {
            scrapeTimeResultForRace(ScrapeResultType.STAGE, race.getId());
            scrapeTimeResultForRace(ScrapeResultType.GC, race.getId());
        }
    }

    private List<PointResult> getPointResultsFromStage1(String stageUrl, ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        Map<String, PointResult> riderResultMap = new HashMap<>();
        String klassementType = scrapeResultType == ScrapeResultType.POINTS ? "POINTS" : "KOM";

        System.out.println("\n ======= START STAGE 1 " + klassementType + " SCRAPING =======");

        try {
            String complementaryUrl = stageUrl + "/info/complementary-results";
            System.out.println(" Fetching complementary results from: " + complementaryUrl);

            Document doc = Jsoup.connect(complementaryUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();

            Elements h3Elements = doc.select("h3");
            Elements tables = doc.select("table.basic");
            System.out.println(" Found " + tables.size() + " complementary tables");

            for (int i = 0; i < h3Elements.size() && i < tables.size(); i++) {
                Element h3Element = h3Elements.get(i);
                Element table = tables.get(i);
                String captionText = h3Element.text();

                boolean isRelevantTable = false;
                if (scrapeResultType == ScrapeResultType.POINTS) {
                    if (captionText.startsWith("Sprint |") || captionText.startsWith("Points at finish")) {
                        isRelevantTable = true;
                        System.out.println("\n Processing POINTS table: " + captionText);
                    }
                } else if (scrapeResultType == ScrapeResultType.KOM) {
                    if (captionText.startsWith("KOM Sprint")) {
                        isRelevantTable = true;
                        System.out.println("\n Processing KOM table: " + captionText);
                    }
                }

                if (!isRelevantTable) {
                    System.out.println(" Skipping irrelevant table: " + captionText);
                    continue;
                }

                Elements rows = table.select("tbody > tr");
                System.out.println(" Found " + rows.size() + " result rows");

                for (Element row : rows) {
                    Element positionElement = row.selectFirst("td:first-child");
                    String position = positionElement != null ? positionElement.text() : "N/A";

                    Element pointElement = row.selectFirst("td:nth-child(4)");
                    String point = pointElement != null ? pointElement.text() : "0";

                    Element riderElement = row.selectFirst("td:nth-child(2) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";

                    Cyclist cyclist = cyclistService.searchCyclist(riderName);
                    if (cyclist == null) {
                        System.out.println(" Cyclist not found: " + riderName);
                        continue;
                    }

                    int pointValue = 0;
                    try {
                        pointValue = Integer.parseInt(point.replaceAll("[^\\d]", ""));
                    } catch (NumberFormatException e) {
                        System.out.println(" Invalid point value: " + point);
                    }

                    System.out.println(cyclist.getName() + " (ID:" + cyclist.getId() +
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
                        System.out.println(" Updated total: " + currentPoints + " + " +
                                pointValue + " = " + pointResult.getPoint());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(" Failed to retrieve point results: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n FINAL " + klassementType + " RESULTS FOR STAGE 1:");
        for (Map.Entry<String, PointResult> entry : riderResultMap.entrySet()) {
            PointResult pointResult = entry.getValue();
            results.add(pointResult);
            System.out.println(pointResult.getCyclist().getName() + " (ID:" + 
                              pointResult.getCyclist().getId() + "): " +
                    pointResult.getPoint() + " total points");
        }

        System.out.println(" Found " + results.size() + " riders with points");
        System.out.println(" ======= END STAGE 1 " + klassementType + " SCRAPING =======\n");
        return results;
    }

    public void savePointResult(Stage stage, PointResult pointResult, List<PointResult> results) {
        pointResultRepository.save(pointResult);
        results.add(pointResult);
    }

    public List<PointResult> scrapePointResult(ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        int resultCount = 0;
        final int MAX_RESULTS = 10000;
        try {
            List<Race> races = raceRepository.findAll();

            for (Race race : races) {
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                    System.out.println("\n Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");

                    if (stage.getName().contains("Stage 1 |")) {
                        System.out.println(" === SPECIAL PROCESSING FOR STAGE 1 ===");
                        List<PointResult> pointResults = getPointResultsFromStage1(stage.getStageUrl(),
                                scrapeResultType);
                        for (PointResult pr : pointResults) {
                            if (resultCount >= MAX_RESULTS)
                                break;
                            pr.setStage(stage);
                            pr.setScrapeResultType(scrapeResultType);
                            savePointResult(stage, pr, results);
                            resultCount++;
                            System.out.println(" Saved PointResult for " + pr.getCyclist().getName() +
                                    " (ID:" + pr.getCyclist().getId() + "): " + pr.getPoint() + " points");
                        }
                        continue;
                    }

                    Document doc = fetchStageDocument(race, stage, scrapeResultType);
                    Elements resultRows = resultRows(doc, stage, scrapeResultType);
                    if (resultRows == null || resultRows.isEmpty()) {
                        System.out.println(" No rows found in the selected table.");
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
                            System.out.println(" Skipping row with empty points for: " + riderName);
                            continue;
                        }

                        Cyclist cyclist = cyclistService.searchCyclist(riderName);
                        if (cyclist == null) {
                            System.out.println(" Cyclist not found for name: " + riderName);
                            continue;
                        }

                        pointResult = getOrCreatePointResult(stage, cyclist, scrapeResultType);
                        pointResult = (PointResult) checkForDNFAndMore(position, pointResult);
                        fillPointResultFields(pointResult, position, Integer.parseInt(point), scrapeResultType);

                        savePointResult(stage, pointResult, results);
                        resultCount++;
                        System.out.println(" Saved PointResult for " + riderName +
                                " (ID:" + cyclist.getId() + "): position " + position + ", " + point + " points");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(" Error scraping point results: " + e.getMessage());
            e.printStackTrace();
        }

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
            System.out.println("✨ Creating new PointResult for Stage: " + stage.getName());
            pointResult = new PointResult();
            pointResult.setStage(stage);
            pointResult.setCyclist(cyclist);
        }
        return pointResult;
    }

    public List<StageResultWithCyclistDTO> getStageResultsByStageIdAndType(Long stageId, ScrapeResultType type) {
        Stage stage = stageRepository.findStageById(stageId);
        List<StageResult> stageResults = stage.getResults();

        return stageResults.stream()
                .filter(result -> result.getScrapeResultType() == type)
                .map(result -> {
                    Cyclist cyclist = result.getCyclist();

                    LocalTime time = null;
                    if (result instanceof TimeResult) {
                        time = ((TimeResult) result).getTime();
                    }

                    int point = 0;
                    if (result instanceof PointResult) {
                        point = ((PointResult) result).getPoint();
                    }

                    return StageResultWithCyclistDTO.builder()
                            .id(result.getId())
                            .time(time)
                            .point(point)
                            .position(result.getPosition())
                            .raceStatus(result.getRaceStatus())
                            .scrapeResultType(result.getScrapeResultType())
                            .cyclistId(cyclist != null ? cyclist.getId() : null)
                            .cyclistName(cyclist != null ? cyclist.getName() : null)
                            .cyclistCountry(cyclist != null ? cyclist.getCountry() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) throws IOException {
        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping...");
        List<Race> races = raceRepository.findAll();

        for (Race race : races) {
            LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
            if (raceStartTime.isAfter(LocalDate.now())) {
                System.out.println(" Race " + race.getName() + " has not started yet.");
                break;
            }
            List<Stage> stages = race.getStages();
            allResults.addAll(scrapeTimeResultByRace(scrapeResultType, stages, race));
        }
        return allResults;
    }

    private static final int MAX_RESULTS = 10000;

    private List<TimeResult> scrapeTimeResultByRace(ScrapeResultType scrapeResultType, List<Stage> stages, Race race)
            throws IOException {
        List<TimeResult> allResults = new ArrayList<>();
        int resultCount = 0;

        for (Stage stage : stages) {
            System.out.println("\n Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
            List<TimeResult> stageResults = new ArrayList<>();

            Document doc = fetchStageDocument(race, stage, scrapeResultType);

            Elements resultRows = resultRows(doc, stage, scrapeResultType);
            LocalTime cumulativeTime = null;

            for (Element row : resultRows) {
                if (resultCount >= MAX_RESULTS) {
                    System.out.println(" Reached MAX_RESULTS limit");
                    break;
                }

                String position;
                String rawTime = "Unknown";

                Element positionElement = row.selectFirst("td:first-child");
                position = positionElement != null ? positionElement.text() : "Unknown";

                Element timeElement = row.selectFirst("td.time.ar");
                rawTime = timeElement != null ? timeElement.text() : "Unknown";
                
                Element riderElement = row.selectFirst("td:nth-child(7) a");
                String riderName = riderElement != null ? riderElement.text() : "Unknown";

                String[] parts = rawTime.split(" ");
                String time = parts[0];

                if (position.equals("Unknown") || riderName.equals("Unknown") || time.equals("Unknown")) {
                    System.out.println(" Skipping row due to missing data");
                    continue;
                }

                LocalTime resultTime;
                if (cumulativeTime == null) {
                    resultTime = timeHandlerWithCumulative(time, null);
                    cumulativeTime = resultTime;
                } else {
                    resultTime = timeHandlerWithCumulative(time, cumulativeTime);
                }

                if (stage.getName().startsWith("Stage 1 |") && scrapeResultType == ScrapeResultType.GC) {
                    String boniSeconds = "0";
                    Element boniSecondsElement = row.selectFirst("td.bonis.ar.fs11.cu600 > div > a");
                    boniSeconds = boniSecondsElement != null ? boniSecondsElement.text() : "0";
                    resultTime = subtractFromCumulative(resultTime, boniSeconds);
                }

                Cyclist cyclist = cyclistService.searchCyclist(riderName);
                if (cyclist == null) {
                    System.out.println(" Cyclist not found for name: " + riderName);
                    continue;
                }

                TimeResult timeResult = getOrCreateTimeResult(stage, cyclist, scrapeResultType);

                if (time.contains("-")) {
                    checkForDNFAndMore(position, timeResult);
                }
                timeResult = (TimeResult) checkForDNFAndMore(position, timeResult);

                fillTimeResultFields(timeResult, position, resultTime, scrapeResultType);

                saveResult(stage, timeResult, stageResults);
                resultCount++;
            }
            
            if (scrapeResultType == ScrapeResultType.GC) {
                stageResults.sort((r1, r2) -> r1.getTime().compareTo(r2.getTime()));
                System.out.println(" Sorting results by time for GC stage: " + stage.getName());
                int positionCounter = 1;
                for (TimeResult r : stageResults) {
                    if (r.getRaceStatus() == RaceStatus.FINISHED) {
                        r.setPosition(String.valueOf(positionCounter));
                        positionCounter++;
                    }
                    timeResultRepository.save(r);
                }
                allResults.addAll(stageResults);
                stageResults.clear();
            }
            
            if (resultCount >= MAX_RESULTS) {
                System.out.println(" Reached MAX_RESULTS limit");
                break;
            }
        }
        return allResults;
    }

    public List<TimeResult> scrapeTimeResultForRace(ScrapeResultType scrapeResultType, Long raceId) throws IOException {
        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping for race ID: " + raceId);
        
        Race race;
        if (raceId != null) {
            Optional<Race> optionalRace = raceRepository.findById(raceId);
            if (!optionalRace.isPresent()) {
                System.out.println(" Race not found with ID: " + raceId);
                return allResults;
            }
            race = optionalRace.get();
        } else {
            return allResults;
        }

        LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
        if (raceStartTime.isAfter(LocalDate.now())) {
            System.out.println(" Race " + race.getName() + " has not started yet.");
            return allResults;
        }
        
        List<Stage> stages = race.getStages();
        allResults.addAll(scrapeTimeResultByRace(scrapeResultType, stages, race));
        System.out.println(" Finished scraping for race ID: " + raceId + ", found " + allResults.size() + " results");
        
        return allResults;
    }

    private static String modifyUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            url = url.substring(0, lastSlashIndex);
        }
        return url;
    }

    private Document fetchStageDocument(Race race, Stage stage, ScrapeResultType scrapeResultType) throws IOException {
        String stageUrl = stage.getStageUrl();
        if (scrapeResultType.equals(ScrapeResultType.GC)) {
            System.out.println(" Scraping GC results for stage: " + stage.getName());
            stageUrl = stageUrl + "-gc";
        } else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
            System.out.println(" Scraping POINTS results for stage: " + stage.getName());
            stageUrl = stageUrl + "-points";
        } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
            System.out.println(" Scraping KOM results for stage: " + stage.getName());
            stageUrl = stageUrl + "-kom";
        }

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1))) {
            if (scrapeResultType.equals(ScrapeResultType.GC)) {
                System.out.println(" Last stage in the GC results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/gc";
            } else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
                System.out.println(" Last stage in the POINTS results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/points";
            } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
                System.out.println(" Last stage in the KOM results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/kom";
            }
        }

        System.out.println(" Final URL: " + stageUrl);

        try {
            return Jsoup.connect(stageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            System.err.println(" Failed to fetch document from URL: " + stageUrl);
            throw e;
        }
    }

    public TimeResult getOrCreateTimeResult(Stage stage, Cyclist cyclist, ScrapeResultType scrapeResultType) {
        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist,
                scrapeResultType);
        if (timeResult == null) {
            System.out.println(" Creating new TimeResult for Stage: " + stage.getName());
            timeResult = new TimeResult();
            timeResult.setStage(stage);
            timeResult.setCyclist(cyclist);
        }
        return timeResult;
    }

    public void fillTimeResultFields(TimeResult timeResult, String position, LocalTime resultTime,
            ScrapeResultType scrapeResultType) {
        timeResult.setPosition(position);
        timeResult.setTime(resultTime);
        timeResult.setScrapeResultType(scrapeResultType);
    }

    public void saveResult(Stage stage, TimeResult timeResult, List<TimeResult> results) {
        timeResultRepository.save(timeResult);
        results.add(timeResult);
    }

    public LocalTime timeHandlerWithCumulative(String time, LocalTime firstFinisherTime) {
        try {
            String cleanedTime = time.trim();
            if (cleanedTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                return parseToLocalTime(cleanedTime);
            } else if (cleanedTime.matches("\\d{1,2}:\\d{2}")) {
                LocalTime parsed = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) return parsed;
                return firstFinisherTime
                        .plusMinutes(parsed.getMinute())
                        .plusSeconds(parsed.getSecond());
            } else if (cleanedTime.matches("\\d{1,2}\\.\\d{2}")) {
                cleanedTime = cleanedTime.replace(".", ":");
                LocalTime gapTime = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) return gapTime;
                return firstFinisherTime
                        .plusMinutes(gapTime.getMinute())
                        .plusSeconds(gapTime.getSecond());
            } else {
                System.out.println(" Unknown time format: " + time);
                return firstFinisherTime;
            }
        } catch (Exception e) {
            System.out.println(" Failed to parse time: " + time);
            e.printStackTrace();
            return firstFinisherTime;
        }
    }

    public LocalTime parseToLocalTime(String timeStr) {
        String[] parts = timeStr.split(":");
        int hours = 0, minutes = 0, seconds = 0;

        if (parts.length == 3) {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
            seconds = Integer.parseInt(parts[2]);
        } else if (parts.length == 2) {
            minutes = Integer.parseInt(parts[0]);
            seconds = Integer.parseInt(parts[1]);
        } else if (parts.length == 1) {
            seconds = Integer.parseInt(parts[0]);
        }
        return LocalTime.of(hours, minutes, seconds);
    }

    public List<TimeResult> findAllResults() {
        return timeResultRepository.findAll();
    }

    public void deleteAllResults() {
        timeResultRepository.deleteAll();
    }

    public LocalTime subtractFromCumulative(LocalTime cumulativeTime, String boniSeconds) {
        try {
            int secondsToSubtract = 0;
            String cleaned = boniSeconds.replaceAll("[^\\d]", "");
            if (!cleaned.isEmpty()) {
                secondsToSubtract = Integer.parseInt(cleaned);
            }
            System.out.println(" Cleaned boni seconds: " + secondsToSubtract);

            LocalTime resultTime = cumulativeTime.minusSeconds(secondsToSubtract);
            System.out.println(" Result time after subtraction: " + resultTime);
            return resultTime;
        } catch (Exception e) {
            System.out.println(" Failed to subtract boni seconds: " + boniSeconds);
            return cumulativeTime;
        }
    }

    private Elements resultRows(Document doc, Stage stage, ScrapeResultType scrapeResultType) {
        Elements tables = doc.select("table.results");
        System.out.println("Number of tables found: " + tables.size());

        Elements resultRows = new Elements();

        try {
            if (scrapeResultType.equals(ScrapeResultType.GC)) {
                if (stage.getName().startsWith("Stage 1 |")) {
                    if (tables.size() > 0) {
                        resultRows = tables.get(0).select("tbody > tr");
                    } else {
                        System.out.println(" Expected at least 1 table for GC Stage 1, found none.");
                    }
                } else {
                    if (tables.size() > 1) {
                        resultRows = tables.get(1).select("tbody > tr");
                    } else {
                        System.out.println(" Expected at least 2 tables for GC, found: " + tables.size());
                    }
                }
            } else if (scrapeResultType.equals(ScrapeResultType.POINTS) || 
                       scrapeResultType.equals(ScrapeResultType.KOM)) {
                if (tables.size() > 2) {
                    resultRows = tables.get(2).select("tbody > tr");
                } else if (tables.size() > 0) {
                    resultRows = tables.get(0).select("tbody > tr");
                }
            } else {
                if (tables.size() > 0) {
                    resultRows = tables.get(0).select("tbody > tr");
                } else {
                    System.out.println(" Expected at least 1 table for non-GC result, found none.");
                }
            }
        } catch (Exception e) {
            System.err.println(" Error while selecting result rows: " + e.getMessage());
        }

        if (resultRows.isEmpty()) {
            System.out.println(" No rows found in the selected table.");
        } else {
            System.out.println(" Found " + resultRows.size() + " result rows");
        }

        return resultRows;
    }

}