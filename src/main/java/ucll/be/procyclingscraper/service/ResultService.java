package ucll.be.procyclingscraper.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Arrays;
import java.util.List;

@Service
public class ResultService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    TimeResultRepository timeResultRepository;

    @Autowired
    StageRepository stageRepository;

    @Autowired
    CyclistRepository cyclistRepository;
    @Autowired
    RaceRepository raceRepository;
    
    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) {
        // List<Stage> stages = stageRepository.findAll();
        List<TimeResult> results = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        final int MAX_RESULTS = 1000;
        try { 
            List<Race> races = raceRepository.findAll();
            for (Race race : races) {
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
                if (resultCount >= MAX_RESULTS) break;
                Document doc = fetchStageDocument(race, stage, scrapeResultType);

                Elements tables = doc.select("table.results");
                System.out.println("Number of tables found: " + tables.size());

                if (tables.isEmpty()) {
                    System.out.println("No tables found with class 'results' for stage: " + stage.getName());
                    continue;
                }

                Elements resultRows;

                if (scrapeResultType.equals(ScrapeResultType.GC) && stage.getName().startsWith("Stage 1 |")) {
                    resultRows = tables.get(0).select("tbody > tr");
                } else if (scrapeResultType.equals(ScrapeResultType.GC)) {
                    resultRows = tables.get(1).select("tbody > tr");
                } else {
                    resultRows = tables.get(0).select("tbody > tr");
                }

                if (resultRows.isEmpty()) {
                    System.out.println("No rows found in the selected table.");
                }

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
                    System.out.println("Parsed Time: " + resultTime);

                    Cyclist cyclist = searchCyclist(riderName);
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

                    saveResult(stage, timeResult, results);
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

    private Document fetchStageDocument(Race race, Stage stage, ScrapeResultType scrapeResultType) throws IOException {
        String stageUrl = stage.getStageUrl();
        if (scrapeResultType.equals(ScrapeResultType.GC)) {
            System.out.println("Scraping GC results for stage: " + stage.getName());
            stageUrl = stageUrl + "-gc";
        }

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1)) && scrapeResultType.equals(ScrapeResultType.GC)) {
            System.out.println("Last stage in the GC results: " + stage.getName());
            stageUrl = modifyUrl(stageUrl);
            stageUrl = stageUrl + "/gc";
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

    public static String modifyUrl(String url) {
        // Remove the last part of the URL
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            url = url.substring(0, lastSlashIndex);
        }

        return url;
    }

    private TimeResult getOrCreateTimeResult(Stage stage, Cyclist cyclist, ScrapeResultType scrapeResultType) {
        TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage, cyclist, scrapeResultType);
        if (timeResult == null) {
            System.out.println("Creating new TimeResult for Stage: " + stage.getName());
            timeResult = new TimeResult();
            timeResult.setStage(stage);
            timeResult.setCyclist(cyclist);
        }
        return timeResult;
    }

    private void fillTimeResultFields(TimeResult timeResult, String position, LocalTime resultTime, ScrapeResultType scrapeResultType) {
        timeResult.setPosition(position);
        timeResult.setTime(resultTime);
        timeResult.setScrapeResultType(scrapeResultType);
    }

    private void saveResult(Stage stage, TimeResult timeResult, List<TimeResult> results) {
        timeResultRepository.save(timeResult);
        results.add(timeResult);
    }

    private LocalTime timeHandlerWithCumulative(String time, LocalTime cumulativeTime) {
        try {
            String cleanedTime = time.trim();

            if (!cleanedTime.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                return cumulativeTime;
            }

            LocalTime inputTime = parseToLocalTime(cleanedTime);

            LocalTime newCumulative = cumulativeTime
                    .plusHours(inputTime.getHour())
                    .plusMinutes(inputTime.getMinute())
                    .plusSeconds(inputTime.getSecond());

            System.out.println("Cumulative Time: " + newCumulative);

            return newCumulative;

        } catch (Exception e) {
            System.out.println("Failed to parse time: " + time);
            e.printStackTrace();
            return cumulativeTime;
        }
    }

    private LocalTime parseToLocalTime(String timeStr) {
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

    private Cyclist searchCyclist(String riderName) {
        // System.out.println("Extracted Rider Name: " + riderName);

        String[] nameParts = riderName.trim().split("\\s+");

        for (int i = 1; i < nameParts.length; i++) {
            String firstName = String.join(" ", Arrays.copyOfRange(nameParts, i, nameParts.length));
            String lastName = String.join(" ", Arrays.copyOfRange(nameParts, 0, i));
            String fixedName = firstName + " " + lastName;

            // System.out.println("Trying rearranged name: " + fixedName);

            Cyclist cyclist = cyclistRepository.findByNameIgnoreCase(fixedName);
            if (cyclist != null) {
                System.out.println("Found cyclist: " + fixedName);
                return cyclist;
            }
        }

        System.out.println("No cyclist found for name: " + riderName);
        return null;
    }
    public List<TimeResult> findAllResults() {
        return timeResultRepository.findAll();
    }

    public void deleteAllResults() {
        timeResultRepository.deleteAll();
    }
}
