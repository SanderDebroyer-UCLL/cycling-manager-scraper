package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    CompetitionRepository competitionRepository;

    public List<Cyclist> findCyclistInByStageId(Long stage_id, String type) {
        return cyclistRepository.findCyclistsByStageIdAndResultType(stage_id, type);
    }

    public void getStageResultsForAllStagesICompetitions() {
        List<Competition> competitions = competitionRepository.findAll();

        List<Race> uniqueRaces = competitions.stream()
                .flatMap(competition -> competition.getRaces().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Race race : uniqueRaces) {
            scrapeTimeResultByRace(ScrapeResultType.STAGE, race.getId());
        }
    }

    public List<StageResultWithCyclistDTO> getStageResultsByStageIdAndType(Long stageId, ScrapeResultType type) {
        Stage stage = stageRepository.findStageById(stageId);
        List<StageResult> stageResults = stage.getResults();

        return stageResults.stream()
                .filter(result -> result.getScrapeResultType() == type)
                .map(result -> {
                    Cyclist cyclist = result.getCyclist();

                    // Default: time is null
                    Duration time = null;

                    // If result is a TimeResult, extract the time
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

    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) {

        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        //Change this for higher or lower amount of results
        final int MAX_RESULTS = 2100;
        try {
            List<Race> races = raceRepository.findAll(Sort.by("id"));
            
            for (Race race : races) {
                LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
                if (raceStartTime.isAfter(LocalDate.now())) {
                    System.out.println("Race " + race.getName() + " has not started yet.");
                    break;
                }
                System.out.println("Huidige race: " + race.getName());
                List<Stage> stages = race.getStages();
                System.out.println("Fetched stages size: " + stages.size());
                for (Stage stage : stages) {
                    System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
                    if (resultCount >= MAX_RESULTS) break;
                    List<TimeResult> stageResults = new ArrayList<>();

                    Document doc = fetchStageDocument(race, stage, scrapeResultType);

                    Elements resultRows = resultRows(doc, stage, scrapeResultType);

                    // This is in format PT.....
                    Duration cumulativeTime = null;

                    for (Element row : resultRows) {
                    if (resultCount >= MAX_RESULTS)
                        break;
                    String position;
                    String rawTime = "Unknown";

                    Element positionElement = row.selectFirst("td:first-child");
                    position = positionElement != null ? positionElement.text() : "Unknown";
                    System.out.println("Position: " + position);

                    Element timeElement = row.selectFirst("td.time.ar");
                    rawTime = timeElement != null ? timeElement.text() : "Unknown";
                    System.out.println("Raw time: " + rawTime);

                    Element riderElement = row.selectFirst("td:nth-child(7) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";
                    System.out.println("Rider Name: " + riderName);

                    String[] parts = rawTime.split(" ");
                    String time = parts[0];
                    System.out.println("First part of time: " + time);

                    
                    if (position.equals("Unknown") || riderName.equals("Unknown") || time.equals("Unknown")) {
                        System.out.println("Skipping row due to missing data");
                        continue;
                    }
    
                    Duration resultTime;
                    if (cumulativeTime == null) {
                        // First finisher: treat as absolute time
                        resultTime = timeHandlerWithCumulative(time, cumulativeTime);
                        cumulativeTime = resultTime; // Save for next riders
                        System.out.println("New cumulative time: " + cumulativeTime);
                    } else {
                        // All others: treat as gap relative to first finisher
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
                        System.out.println("Cyclist not found for name: " + riderName);
                        continue;
                    }

                    TimeResult timeResult = getOrCreateTimeResult(stage, cyclist, scrapeResultType);

                    if (time.contains("-")) {
                        checkForDNFAndMore(position, timeResult);
                    }
                    timeResult = checkForDNFAndMore(position, timeResult);

                    fillTimeResultFields(timeResult, position, resultTime, scrapeResultType);

                    saveResult(stage, timeResult, stageResults);
                    resultCount++;
                    }
                if (scrapeResultType == ScrapeResultType.GC) {
                // Reset results for each stage to avoid accumulating across stages
                stageResults.sort((r1, r2) -> r1.getTime().compareTo(r2.getTime()));
                System.out.println("Sorting results by time for GC stage: " + stage.getName());
                int positionCounter = 1;
                for (TimeResult r : stageResults) {
                    if (r.getRaceStatus() == RaceStatus.FINISHED) {
                    r.setPosition(String.valueOf(positionCounter));
                    positionCounter++;
                    }
                    timeResultRepository.save(r);
                }
                // Add stage results to allResults
                allResults.addAll(stageResults);
                // Clear stageResults for the next stage
                stageResults.clear();
                }
            }
            if (resultCount >= MAX_RESULTS)
                break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return allResults;
    }

    public List<TimeResult> scrapeTimeResultByRace(ScrapeResultType scrapeResultType, Long raceId) {

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + raceId));

        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        // Change this for higher or lower amount of results
        final int MAX_RESULTS = 9999;
        try {
            List<Stage> stages = race.getStages();
            for (Stage stage : stages) {

                if (resultCount >= MAX_RESULTS)
                    break;
                List<TimeResult> stageResults = new ArrayList<>();
                Document doc = fetchStageDocument(race, stage, scrapeResultType);

                Elements resultRows = resultRows(doc, stage, scrapeResultType);
                Duration cumulativeTime = Duration.ZERO;

                for (Element row : resultRows) {
                    if (resultCount >= MAX_RESULTS)
                        break;
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
                        System.out.println("Skipping row due to missing data");
                        continue;
                    }

                    Duration resultTime;
                    if (cumulativeTime.equals(Duration.ZERO)) {
                        // First finisher: treat as absolute time
                        resultTime = timeHandlerWithCumulative(time, null);
                        cumulativeTime = resultTime; // Save for next riders
                    } else {
                        // All others: treat as gap relative to first finisher
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
                        System.out.println("Cyclist not found for name: " + riderName);
                        continue;
                    }

                    TimeResult timeResult = getOrCreateTimeResult(stage, cyclist, scrapeResultType);

                    if (time.contains("-")) {
                        checkForDNFAndMore(position, timeResult);
                    }
                    timeResult = checkForDNFAndMore(position, timeResult);

                    fillTimeResultFields(timeResult, position, resultTime, scrapeResultType);

                    saveResult(stage, timeResult, stageResults);
                    resultCount++;
                }
                if (scrapeResultType == ScrapeResultType.GC) {
                    // Reset results for each stage to avoid accumulating across stages
                    stageResults.sort((r1, r2) -> r1.getTime().compareTo(r2.getTime()));
                    System.out.println("Sorting results by time for GC stage: " + stage.getName());
                    int positionCounter = 1;
                    for (TimeResult r : stageResults) {
                        if (r.getRaceStatus() == RaceStatus.FINISHED) {
                            r.setPosition(String.valueOf(positionCounter));
                            positionCounter++;
                        }
                        timeResultRepository.save(r);
                    }
                    // Add stage results to allResults
                    allResults.addAll(stageResults);
                    // Clear stageResults for the next stage
                    stageResults.clear();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

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
            System.out.println("Scraping GC results for stage: " + stage.getName());
            stageUrl = stageUrl + "-gc";
        }

        // Logic for youth results added
        if (scrapeResultType.equals(ScrapeResultType.YOUTH)) {
            System.out.println("Scraping Youth results for stage: " + stage.getName());
            stageUrl = stageUrl + "-youth";
        }

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1))
                && scrapeResultType.equals(ScrapeResultType.GC)) {
            System.out.println("Last stage in the GC results: " + stage.getName());
            stageUrl = modifyUrl(stageUrl);
            stageUrl = stageUrl + "/gc";
        }

        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1)) && scrapeResultType.equals(ScrapeResultType.YOUTH)) {
            System.out.println("Last stage in the Youth results: " + stage.getName());
            stageUrl = modifyUrl(stageUrl);
            stageUrl = stageUrl + "/youth";
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

    public TimeResult getOrCreateTimeResult(Stage stage, Cyclist cyclist, ScrapeResultType scrapeResultType) {
        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist,
                scrapeResultType);
        if (timeResult == null) {
            System.out.println("Creating new TimeResult for Stage: " + stage.getName());
            timeResult = new TimeResult();
            timeResult.setStage(stage);
            timeResult.setCyclist(cyclist);
        }
        return timeResult;
    }

    public void fillTimeResultFields(TimeResult timeResult, String position, Duration resultTime,
            ScrapeResultType scrapeResultType) {
        timeResult.setPosition(position);
        timeResult.setTime(resultTime);
        timeResult.setScrapeResultType(scrapeResultType);
    }

    public void saveResult(Stage stage, TimeResult timeResult, List<TimeResult> results) {
        timeResultRepository.save(timeResult);
        results.add(timeResult);
    }
    
    public Duration timeHandlerWithCumulative(String time, Duration firstFinisherTime) {
        System.out.println("First Finisher Time: " + firstFinisherTime);
        try {
            String cleanedTime = time.trim();
            System.out.println("Cleaned Time: " + cleanedTime);
            // If the time is in hh:mm:ss
            if (cleanedTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                Duration inputTime = parseToLocalTime(cleanedTime);
                return inputTime;
            }
            // If the time is in mm:ss
            else if (cleanedTime.matches("\\d{1,2}:\\d{2}")) {
                Duration parsed = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) {
                    return parsed;
                } else {
                    Duration resultTime = firstFinisherTime.plus(parsed);
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            }
            // If the time is in m.ss or mm.ss
            else if (cleanedTime.matches("\\d{1,2}\\.\\d{2}")) {
                cleanedTime = cleanedTime.replace(".", ":");
                Duration gapTime = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) {
                    return gapTime;
                } else {
                    Duration resultTime = firstFinisherTime.plus(gapTime);
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            }
            else {
                System.out.println("Unknown time format, returning first finisher's time.");
                return firstFinisherTime;
            }
        } catch (Exception e) {
            System.out.println("Failed to parse time: " + time);
            e.printStackTrace();
            return firstFinisherTime;
        }
    }


    public Duration parseToLocalTime(String timeStr) {
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
        System.out.println("Parsed Time: " + hours + ":" + minutes + ":" + seconds);
        Duration duration = Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        return duration;
    }

    private TimeResult checkForDNFAndMore(String position, TimeResult timeResult) {
        if (position.equalsIgnoreCase("DNS")) {
            timeResult.setRaceStatus(RaceStatus.DNS);
        } else if (position.equalsIgnoreCase("DNF")) {
            timeResult.setRaceStatus(RaceStatus.DNF);
        } else if (position.equalsIgnoreCase("DSQ")) {
            timeResult.setRaceStatus(RaceStatus.DSQ);
        } else if (position.equalsIgnoreCase("OTL")) {
            timeResult.setRaceStatus(RaceStatus.OTL);
        } else {
            timeResult.setRaceStatus(RaceStatus.FINISHED);
        }
        return timeResult;
    }

    public List<TimeResult> findAllResults() {
        return timeResultRepository.findAll();
    }

    public void deleteAllResults() {
        timeResultRepository.deleteAll();
    }

    public Duration subtractFromCumulative(Duration cumulativeTime, String boniSeconds) {
        try {
            int secondsToSubtract = 0;
            Duration boniSecDuration = Duration.ZERO;
            String cleaned = boniSeconds.replaceAll("[^\\d]", "");
            if (!cleaned.isEmpty()) {
                secondsToSubtract = Integer.parseInt(cleaned);
                boniSecDuration = Duration.ofSeconds(secondsToSubtract);

            }
            System.out.println("Cleaned boni seconds: " + secondsToSubtract);
           
            Duration resultTime = cumulativeTime.minus(boniSecDuration);
            System.out.println("Result time after subtraction: " + resultTime);
            return resultTime;
        } catch (Exception e) {
            System.out.println("Failed to subtract boni seconds: " + boniSeconds);
            e.printStackTrace();
            return cumulativeTime;
        }
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
                // fallback: use the first table if only one exists
                System.out.println("POINTS table not found at index 2, using first table as fallback.");
                resultRows = tables.get(0).select("tbody > tr");
            }
        } else if (scrapeResultType.equals(ScrapeResultType.YOUTH)) {
                System.out.println("Selecting youth results table.");
                resultRows = tables.get(4).select("tbody > tr");
                System.out.println("Youth results table selected, number of rows: " + resultRows.size());
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

    public TimeResult findGCTimeResultByCyclistIdAndStageId(Long cyclistId, Long stageId) {
        
        TimeResult timeResult = timeResultRepository.findTimeResultByCyclistIdAndStageIdAndScrapeResultType(cyclistId, stageId, ScrapeResultType.GC);
        return timeResult;
        
    }

    public List<TimeResult> calculateYouthTimeResult(ScrapeResultType scrapeResultType) {
        
        try {

            List<Race> races = raceRepository.findAll(Sort.by("id"));
            System.out.println("Number of races found: " +  races.size());
            List<TimeResult> youthResults = new ArrayList<>();
            
            for (Race race: races) {
                System.out.println("Current processed race: " + race.getName());
                List<Stage> raceStages = race.getStages();
                System.out.println("Number of stages found for race: " + raceStages.size());
                List<Long> youthCyclistsIDs = race.getYouthCyclistsIDs();
                System.out.println("Youth Cylists IDs size: " + youthCyclistsIDs.size());

                for (Stage stage : raceStages) {
                    List<TimeResult> gcResults = getGCTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(stage.getId(), youthCyclistsIDs);

                    List<TimeResult> numericResults = new ArrayList<>();
                    List<TimeResult> nonNumericResults = new ArrayList<>();

                    for (TimeResult result : gcResults) {
                        if (result.getPosition().matches("^[0-9]+$")) {
                            numericResults.add(result);
                        } else {
                            nonNumericResults.add(result);
                        }
                    }
                    
                    // Sort only by position (as integer), ascending
                    numericResults.sort(Comparator.comparing(TimeResult::getTime));
                    
                    int positionCounter = 1;
                    for (TimeResult numericResult: numericResults) {
                        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, numericResult.getCyclist(), scrapeResultType);

                        if (timeResult == null) {
                            System.out.println("Creating new TimeResult Youth for Stage: " + stage.getName());
                            timeResult = new TimeResult();
                            timeResult.setPosition(String.valueOf(positionCounter));
                            timeResult.setRaceStatus(numericResult.getRaceStatus());
                            timeResult.setTime(numericResult.getTime());
                            timeResult.setScrapeResultType(scrapeResultType);
                            timeResult.setStage(stage);
                            timeResult.setCyclist(numericResult.getCyclist());
                            timeResultRepository.save(timeResult);
                            youthResults.add(timeResult);
                            positionCounter++;
                        }
                    }

                    for (TimeResult nonNumericResult : nonNumericResults) {
                        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, nonNumericResult.getCyclist(), scrapeResultType);
                        
                        if (timeResult == null) {
                            System.out.println("Creating new TimeResult Youth for Stage: " + stage.getName());
                            timeResult = new TimeResult();
                            timeResult.setPosition(nonNumericResult.getPosition());
                            timeResult.setRaceStatus(nonNumericResult.getRaceStatus());
                            timeResult.setTime(nonNumericResult.getTime());
                            timeResult.setScrapeResultType(scrapeResultType);
                            timeResult.setStage(stage);
                            timeResult.setCyclist(nonNumericResult.getCyclist());
                            timeResultRepository.save(timeResult);
                            youthResults.add(timeResult);
                        }
                    }
                }
            }
            return youthResults;

        } catch (Exception e) {
            System.out.println("Failed to calculate youth results.");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<TimeResult> getGCTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(long stageId, List<Long> youthCyclistIds) {
        System.out.println("Fetched youth Cyclist IDs: " + youthCyclistIds.size());
        List<TimeResult> stageTimeResultsGC = timeResultRepository.findTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(stageId, ScrapeResultType.GC, youthCyclistIds);
        System.out.println("Number of GC results for stage ID " + stageId + ": " + stageTimeResultsGC.size());
        return stageTimeResultsGC;
    }

    public String getHoursMinSecFromDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
