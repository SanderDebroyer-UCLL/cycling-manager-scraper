package ucll.be.procyclingscraper.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.repository.*;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public List<Cyclist> findCyclistInByStageId(Long stage_id, String type) {
        return cyclistRepository.findCyclistsByStageIdAndResultType(stage_id, type);
    }

    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) {

        
        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        //Change this for higher or lower amount of results
        final int MAX_RESULTS = 1000;
        try { 
            List<Race> races = raceRepository.findAll(Sort.by("id"));
            
            for (Race race : races) {
                System.out.println("Huidige race: " + race.getName());
                List<Stage> stages = race.getStages();
                System.out.println("Fetched stages size: " + stages.size());
                for (Stage stage : stages) {
                    System.out.println("Huidige stage: " + stage.getName());
                    if (resultCount >= MAX_RESULTS) break;
                    List<TimeResult> stageResults = new ArrayList<>();
                    Document doc = fetchStageDocument(race, stage, scrapeResultType);


                    Elements resultRows = resultRows(doc, stage, scrapeResultType);
                    LocalTime cumulativeTime = LocalTime.MIDNIGHT; 

                    for (Element row : resultRows) {
                        if (resultCount >= MAX_RESULTS) break;
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

                        LocalTime resultTime = timeHandlerWithCumulative(time, cumulativeTime);
                        if (resultTime != null) {
                            cumulativeTime = resultTime;
                        }


                        if (stage.getName().startsWith("Stage 1 |") && scrapeResultType.equals(ScrapeResultType.GC)) {
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
                if (resultCount >= MAX_RESULTS) break;
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
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1)) && scrapeResultType.equals(ScrapeResultType.GC)) {
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
        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist, scrapeResultType);
        if (timeResult == null) {
            System.out.println("Creating new TimeResult for Stage: " + stage.getName());
            timeResult = new TimeResult();
            timeResult.setStage(stage);
            timeResult.setCyclist(cyclist);
        }
        return timeResult;
    }

    public void fillTimeResultFields(TimeResult timeResult, String position, LocalTime resultTime, ScrapeResultType scrapeResultType) {
        timeResult.setPosition(position);
        timeResult.setTime(resultTime);
        timeResult.setScrapeResultType(scrapeResultType);
    }

    public void saveResult(Stage stage, TimeResult timeResult, List<TimeResult> results) {
        timeResultRepository.save(timeResult);
        results.add(timeResult);
    }

    public LocalTime timeHandlerWithCumulative(String time, LocalTime cumulativeTime) {
        try {
            String cleanedTime = time.trim();

            if (!cleanedTime.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                return cumulativeTime;
            }
            LocalTime inputTime = parseToLocalTime(cleanedTime);
            System.out.println("Input Time: " + inputTime);
            if (cumulativeTime.equals(LocalTime.MIDNIGHT)) {
                return inputTime;
            } else {
                LocalTime newCumulative = cumulativeTime
                        .plusHours(inputTime.getHour())
                        .plusMinutes(inputTime.getMinute())
                        .plusSeconds(inputTime.getSecond());
                System.out.println("Cumulative Time: " + newCumulative);
                
                return newCumulative;
            }
            
        } catch (Exception e) {
            System.out.println("Failed to parse time: " + time);
            e.printStackTrace();
            return cumulativeTime;
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
        System.out.println("Parsed Time: " + hours + ":" + minutes + ":" + seconds);
        return LocalTime.of(hours, minutes, seconds);
    }

    private TimeResult checkForDNFAndMore(String position, TimeResult timeResult){
        if (position.equalsIgnoreCase("DNS")) {
            timeResult.setRaceStatus(RaceStatus.DNS);
        }
        else if (position.equalsIgnoreCase("DNF")) {
            timeResult.setRaceStatus(RaceStatus.DNF);
        }
        else if (position.equalsIgnoreCase("DSQ")) {
            timeResult.setRaceStatus(RaceStatus.DSQ);
        }
        else if (position.equalsIgnoreCase("OTL")) {
            timeResult.setRaceStatus(RaceStatus.OTL);
        }
        else{
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

    public LocalTime subtractFromCumulative(LocalTime cumulativeTime, String boniSeconds) {
        try {
            int secondsToSubtract = 0;
            String cleaned = boniSeconds.replaceAll("[^\\d]", "");
            if (!cleaned.isEmpty()) {
                secondsToSubtract = Integer.parseInt(cleaned);
            }
            System.out.println("Cleaned boni seconds: " + secondsToSubtract);

            LocalTime resultTime = cumulativeTime.minusSeconds(secondsToSubtract);
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
                    
                    numericResults.sort(
                        Comparator.comparing(TimeResult::getTime)
                        .thenComparing(r -> {
                            return Integer.parseInt(r.getPosition());   
                        })
                    );
                    
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
}
