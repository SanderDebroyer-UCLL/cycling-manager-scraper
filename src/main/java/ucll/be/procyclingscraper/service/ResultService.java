package ucll.be.procyclingscraper.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.model.Stage;
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

    public List<TimeResult> scrapeTimeResult() {
        List<Stage> stages = stageRepository.findAll();
        List<TimeResult> results = new ArrayList<>();
        System.out.println("Starting scraping...");
        int resultCount = 0;
        final int MAX_RESULTS = 140;
        try { 
            for (Stage stage : stages) {
                if (resultCount >= MAX_RESULTS) break;
                Document doc = Jsoup.connect(stage.getStageUrl())
                    .userAgent(USER_AGENT)
                    .get();

                Elements resultRows = doc.select("table.results tr");

                // Use a cumulative time field for each stage
                LocalTime cumulativeTime = LocalTime.MIDNIGHT;

                for (Element row : resultRows) {
                    if (resultCount >= MAX_RESULTS) break;
                    TimeResult timeResult = new TimeResult();

                    Element positionElement = row.selectFirst("td:first-child");
                    String position = positionElement != null ? positionElement.text() : "Unknown";
                    System.out.println("Position: " + position);

                    Element riderElement = row.selectFirst("td:nth-child(7) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";
                    System.out.println("Rider Name: " + riderName);

                    Element timeElement = row.selectFirst("td.time.ar");
                    String rawTime = timeElement != null ? timeElement.text() : "Unknown";
                    String[] parts = rawTime.split(" ");
                    String time = parts[0];
                    System.out.println("Time: " + time);

                    if (position.equals("Unknown") || riderName.equals("Unknown") || time.equals("Unknown")) {
                        System.out.println("Skipping row due to missing data");
                        continue;
                    }

                    if (time.contains("-")) {
                        continue;
                    }

                    LocalTime resultTime = timeHandlerWithCumulative(time, cumulativeTime);
                    if (resultTime != null) {
                        cumulativeTime = resultTime;
                    }
                    System.out.println("Parsed Time: " + resultTime);
                    timeResult = checkForDNFAndMore(position, timeResult);

                    timeResult.setPosition(position);
                    timeResult.setCyclist(searchCyclist(riderName));
                    timeResult.setTime(resultTime);
                    System.out.println(timeResult);
                    stage.addResult(timeResult);
                    timeResultRepository.save(timeResult);
                    stageRepository.save(stage);
                    results.add(timeResult);
                    resultCount++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
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
        if (position.equals("DNS")) {
            timeResult.setRaceStatus(RaceStatus.DNS);
        }
        else if (position.equals("DNF")) {
            timeResult.setRaceStatus(RaceStatus.DNF);
        }
        else if (position.equals("DSQ")) {
            timeResult.setRaceStatus(RaceStatus.DSQ);
        }
        else if (position.equals("OTL")) {
            timeResult.setRaceStatus(RaceStatus.OTL);
        }
        else{
            timeResult.setRaceStatus(RaceStatus.FINISHED);
        }
        return timeResult;
    }

    private Cyclist searchCyclist(String riderName) {
        System.out.println("Extracted Rider Name: " + riderName);

        String[] nameParts = riderName.trim().split("\\s+");

        for (int i = 1; i < nameParts.length; i++) {
            String firstName = String.join(" ", Arrays.copyOfRange(nameParts, i, nameParts.length));
            String lastName = String.join(" ", Arrays.copyOfRange(nameParts, 0, i));
            String fixedName = firstName + " " + lastName;

            System.out.println("Trying rearranged name: " + fixedName);

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

}
