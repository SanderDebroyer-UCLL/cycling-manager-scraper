package ucll.be.procyclingscraper.service;
import org.hibernate.validator.constraints.br.TituloEleitoral;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.CriteriaBuilder.In;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.repository.*;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ResultService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    TimeResultRepository timeResultRepository;

    @Autowired
    PointResultRepository pointResultRepository;

    @Autowired
    StageRepository stageRepository;

    @Autowired
    CyclistRepository cyclistRepository;

    @Autowired
    RaceRepository raceRepository;
    
    @Autowired
    CyclistService cyclistService;

    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) {
        List<TimeResult> results = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        //Change this for higher or lower amount of results
        final int MAX_RESULTS = 1000;
        try { 
            List<Race> races = raceRepository.findAll();
            
            for (Race race : races) {
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                    System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
                    if (resultCount >= MAX_RESULTS) break;
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

                        if (stage.getName().startsWith("Stage 1 |")) {
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

                        saveTimeResult(stage, timeResult, results);
                        resultCount++;
                    }
                }
                if (resultCount >= MAX_RESULTS) break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    public List<PointResult> scrapePointResult(ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        int resultCount = 0;
        //Change this for higher or lower amount of results
        final int MAX_RESULTS = 20;
        try { 
            List<Race> races = raceRepository.findAll();
            
            for (Race race : races) {
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                    System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
                    // if (resultCount >= MAX_RESULTS) break;
                    Document doc = fetchStageDocument(race, stage, scrapeResultType);
                    Elements resultRows = resultRows(doc, stage, scrapeResultType);
                    if (resultRows == null || resultRows.isEmpty()) {
                        System.out.println("No rows found in the selected table.");
                        continue;
                    }
                    Integer targetRowIndex = 0;
                    for (Element row : resultRows) {
                        if (resultCount >= MAX_RESULTS) break;
                        String position = "Unknown";
                        String point = "Unknown";
                        String riderName = "Unknown";
                        PointResult pointResult = null;

                        if (stage.getName().contains("Stage 1 |")) {
                            List<PointResult> pointResults = getPointResultsFromStage1(stage.getStageUrl(), targetRowIndex);
                            if (pointResults == null || pointResults.isEmpty()) {
                                System.out.println("PointResult is null for targetRowIndex: " + targetRowIndex + " in stage: " + stage.getName());
                                targetRowIndex++;
                                continue;
                            }
                            for (PointResult pointResultstage1 : pointResults) {
                                savePointResult(stage, pointResultstage1, results);
                            }

                            targetRowIndex++;
                        }else{
                            Element pointElement = row.selectFirst("td:nth-child(10) a");
                            point = pointElement != null ? pointElement.text() : "Unknown";

                            Element positionElement = row.selectFirst("td:first-child");
                            position = positionElement != null ? positionElement.text() : "Unknown";

                            Element riderElement = row.selectFirst("td:nth-child(7) a");
                            riderName = riderElement != null ? riderElement.text() : "Unknown";

                            point = point.replaceAll("[^\\d]", "");
                            if (point.isEmpty()) {
                                continue;
                            }
                            

                            Cyclist cyclist = cyclistService.searchCyclist(riderName);
                            if (cyclist == null) {
                                System.out.println("Cyclist not found for name: " + riderName);
                                continue;
                            }

                            pointResult = getOrCreatePointResult(stage, cyclist, scrapeResultType);

                            checkForDNFAndMore(position, pointResult);
                            fillPointResultFields(pointResult, position, Integer.parseInt(point), scrapeResultType);

                            savePointResult(stage, pointResult, results);
                        }

                        resultCount++;
                    }
                }
                if (resultCount >= MAX_RESULTS) break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
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
        }else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
            System.out.println("Scraping POINTS results for stage: " + stage.getName());
            stageUrl = stageUrl + "-points";
        }

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1))) {
            if (scrapeResultType.equals(ScrapeResultType.GC)) {
                System.out.println("Last stage in the GC results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/gc";
            }
            else if (scrapeResultType.equals(ScrapeResultType.POINTS)) {
                System.out.println("Last stage in the POINTS results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/points";
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

    public PointResult getOrCreatePointResult(Stage stage, Cyclist cyclist, ScrapeResultType scrapeResultType) {
        PointResult pointResult = pointResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist, scrapeResultType);
        if (pointResult == null) {
            System.out.println("Creating new PointResult for Stage: " + stage.getName());
            pointResult = new PointResult();
            pointResult.setStage(stage);
            pointResult.setCyclist(cyclist);
        }
        return pointResult;
    }
    public void fillTimeResultFields(TimeResult timeResult, String position, LocalTime resultTime, ScrapeResultType scrapeResultType) {
        timeResult.setPosition(position);
        timeResult.setTime(resultTime);
        timeResult.setScrapeResultType(scrapeResultType);
    }

    public void fillPointResultFields(PointResult pointResult, String position, Integer point, ScrapeResultType scrapeResultType) {
        pointResult.setPosition(position);
        pointResult.setPoint(point);
        pointResult.setScrapeResultType(scrapeResultType);
    }

    public void saveTimeResult(Stage stage, TimeResult timeResult, List<TimeResult> results) {
        timeResultRepository.save(timeResult);
        results.add(timeResult);
    }

    public void savePointResult(Stage stage,  PointResult pointResult, List<PointResult> results) {
        pointResultRepository.save(pointResult);
        results.add(pointResult);
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

    // Generic method to set RaceStatus for both TimeResult and PointResult
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

        // Set status if result has setRaceStatus method
        if (result instanceof TimeResult) {
            ((TimeResult) result).setRaceStatus(status);
        } else if (result instanceof PointResult) {
            ((PointResult) result).setRaceStatus(status);
        }
        return result;
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

    
    // private PointResult getPointResultFromStage1(String stageUrl, int targetRowIndex) {
    //     try {
    //         Document doc = Jsoup.connect("https://www.procyclingstats.com/race/tour-down-under/2025/stage-1/info/complementary-results").get();
    //         Elements h3Elements = doc.select("h3");
    //         Elements tables = doc.select("table.basic");

    //         // Ensure h3Elements and tables have the same size and are paired
    //         for (int i = 0; i < h3Elements.size() && i < tables.size(); i++) {
    //             Element h3Element = h3Elements.get(i);
    //             Element table = tables.get(i);
    //             String captionText = h3Element.text();
    //             System.out.println("Caption Text: " + captionText);
    //             if (captionText.startsWith("Sprint |")) {
    //                 Element header = table.selectFirst("thead tr th");
    //                 // Only process if header exists and is a Sprint (not KOM or other)
    //                 if (header == null || !header.text().startsWith("Sprint |")) {
    //                     continue;
    //                 }
    //                     Elements rows = table.select("tbody > tr");

    //                     int currentRow = 0;
    //                     for (Element row : rows) {
    //                         if (currentRow == targetRowIndex) {
    //                             Element pointElement = row.selectFirst("td:nth-child(4)");
    //                             String point = pointElement != null ? pointElement.text() : "0";

    //                             Element positionElement = row.selectFirst("td:first-child");
    //                             String position = positionElement != null ? positionElement.text() : "Unknown";

    //                             Element riderElement = row.selectFirst("td:nth-child(2) a");
    //                             String riderName = riderElement != null ? riderElement.text() : "Unknown";

    //                             PointResult pointResult = new PointResult();
    //                             Cyclist cyclist = cyclistService.searchCyclist(riderName);
    //                             System.out.println("IN DE NIEUWE FUNCTIE");
    //                             System.out.println("Rider Name: " + riderName);
    //                             System.out.println("point: " + point);
    //                             System.out.println("position: " + position);
    //                             if (cyclist == null) {
    //                                 System.out.println("Cyclist not found for name: " + riderName);
    //                                 return null;
    //                             }
    //                             pointResult.setCyclist(cyclist);
    //                             checkForDNFAndMore(position, pointResult);
    //                             fillPointResultFields(pointResult, position, Integer.parseInt(point), ScrapeResultType.POINTS);
    //                             return pointResult;
    //                         }
    //                         currentRow++;
    //                     }
    //                 }
    //             // }
    //         }
    //     } catch (Exception e) {
    //         System.out.println("Failed to retrieve point result from Stage 1");
    //         e.printStackTrace();
    //         return null;
    //     }
    //     return null;
    // }

   
    private List<PointResult> getPointResultsFromStage1(String stageUrl, int targetRowIndex) {
        List<PointResult> results = new ArrayList<>();
        // Map to sum points per rider for the specific sprint/intermediate table (targetRowIndex)
        java.util.Map<String, PointResult> riderResultMap = new java.util.HashMap<>();

        try {
            // Use the provided stageUrl to fetch the correct complementary results page
            Document doc = Jsoup.connect(stageUrl.replaceAll("/[^/]+$", "") + "/info/complementary-results").get();
            Elements h3Elements = doc.select("h3");
            Elements tables = doc.select("table.basic");

            int sprintTableCount = 0;
            for (int i = 0; i < h3Elements.size() && i < tables.size(); i++) {
                Element h3Element = h3Elements.get(i);
                Element table = tables.get(i);
                String captionText = h3Element.text();
                if (captionText.startsWith("Sprint |") || captionText.startsWith("Points at finish")) {
                    if (sprintTableCount == targetRowIndex) {
                        Elements rows = table.select("tbody > tr");
                        for (Element row : rows) {
                            Element pointElement = row.selectFirst("td:nth-child(4)");
                            String point = pointElement != null ? pointElement.text() : "0";

                            Element positionElement = row.selectFirst("td:first-child");
                            String position = positionElement != null ? positionElement.text() : "Unknown";

                            Element riderElement = row.selectFirst("td:nth-child(2) a");
                            String riderName = riderElement != null ? riderElement.text() : "Unknown";

                            Cyclist cyclist = cyclistService.searchCyclist(riderName);
                            if (cyclist != null) {
                                int pointValue = 0;
                                try {
                                    pointValue = Integer.parseInt(point);
                                } catch (NumberFormatException e) {
                                    // ignore, keep as 0
                                }
                                PointResult existing = riderResultMap.get(riderName);
                                if (existing == null) {
                                    PointResult pointResult = new PointResult();
                                    pointResult.setCyclist(cyclist);
                                    checkForDNFAndMore(position, pointResult);
                                    fillPointResultFields(pointResult, position, pointValue, ScrapeResultType.POINTS);
                                    riderResultMap.put(riderName, pointResult);
                                } else {
                                    // Sum up the points for this rider in this specific sprint/intermediate
                                    existing.setPoint(existing.getPoint() + pointValue);
                                }
                            }
                        }
                        break; // Only process the specific sprint/intermediate table
                    }
                    sprintTableCount++;
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to retrieve point results from Stage 1");
            e.printStackTrace();
        }
        results.addAll(riderResultMap.values());
        return results;
    }
}