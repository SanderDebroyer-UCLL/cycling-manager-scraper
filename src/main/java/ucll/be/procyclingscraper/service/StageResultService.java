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
import java.util.List;
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
    CompetitionRepository competitionRepository;

    public List<Cyclist> findCyclistInByStageId(Long stage_id, String type) {
        return cyclistRepository.findCyclistsByStageIdAndResultType(stage_id, type);
    }

    public void getStageResultsForAllStagesInCompetitions() {
        List<Competition> competitions = competitionRepository.findAll();

        List<Race> uniqueRaces = competitions.stream()
                .flatMap(competition -> competition.getRaces().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Race race : uniqueRaces) {
            scrapeTimeResultByRace(ScrapeResultType.STAGE, race.getId());
        }
    }

    public void getStageResultsForAllStagesInCompetition(Long competitionId) {
        Optional<Competition> competitions = competitionRepository.findById(competitionId);

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
                    LocalTime time = null;

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
        // Change this for higher or lower amount of results
        final int MAX_RESULTS = 2100;
        try {
            List<Race> races = raceRepository.findAll();

            for (Race race : races) {
                LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
                if (raceStartTime.isAfter(LocalDate.now())) {
                    System.out.println("Race " + race.getName() + " has not started yet.");
                    break;
                }
                List<Stage> stages = race.getStages();
                for (Stage stage : stages) {
                    System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
                    if (resultCount >= MAX_RESULTS)
                        break;
                    List<TimeResult> stageResults = new ArrayList<>();

                    Document doc = fetchStageDocument(race, stage, scrapeResultType);

                    Elements resultRows = resultRows(doc, stage, scrapeResultType);
                    LocalTime cumulativeTime = null;

                    for (Element row : resultRows) {
                        if (resultCount >= MAX_RESULTS)
                            break;
                        String position;
                        String rawTime = "Unknown";

                        Element positionElement = row.selectFirst("td:first-child");
                        position = positionElement != null ? positionElement.text() : "Unknown";

                        Element timeElement = row.selectFirst("td.time.ar");
                        rawTime = timeElement != null ? timeElement.text() : "Unknown";
                        // System.out.println("Raw time: " + rawTime);
                        Element riderElement = row.selectFirst("td:nth-child(7) a");
                        String riderName = riderElement != null ? riderElement.text() : "Unknown";

                        String[] parts = rawTime.split(" ");
                        String time = parts[0];

                        if (position.equals("Unknown") || riderName.equals("Unknown") || time.equals("Unknown")) {
                            System.out.println("Skipping row due to missing data");
                            continue;
                        }

                        LocalTime resultTime;
                        if (cumulativeTime == null) {
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
                LocalTime cumulativeTime = LocalTime.MIDNIGHT;

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

                    LocalTime resultTime;
                    if (cumulativeTime.equals(LocalTime.MIDNIGHT)) {
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

        List<Stage> stages = race.getStages();
        if (!stages.isEmpty() && stage.equals(stages.get(stages.size() - 1))
                && scrapeResultType.equals(ScrapeResultType.GC)) {
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
        System.out.println("First Finisher Time: " + firstFinisherTime);
        try {
            String cleanedTime = time.trim();
            // If the time is in hh:mm:ss
            if (cleanedTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                LocalTime inputTime = parseToLocalTime(cleanedTime);
                return inputTime;
            }
            // If the time is in mm:ss
            else if (cleanedTime.matches("\\d{1,2}:\\d{2}")) {
                LocalTime parsed = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) {
                    return parsed;
                } else {
                    LocalTime resultTime = firstFinisherTime
                            .plusMinutes(parsed.getMinute())
                            .plusSeconds(parsed.getSecond());
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            }
            // If the time is in m.ss or mm.ss
            else if (cleanedTime.matches("\\d{1,2}\\.\\d{2}")) {
                cleanedTime = cleanedTime.replace(".", ":");
                LocalTime gapTime = parseToLocalTime(cleanedTime);
                if (firstFinisherTime == null) {
                    return gapTime;
                } else {
                    LocalTime resultTime = firstFinisherTime
                            .plusMinutes(gapTime.getMinute())
                            .plusSeconds(gapTime.getSecond());
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            } else {
                System.out.println("Unknown time format, returning first finisher's time.");
                return firstFinisherTime;
            }
        } catch (Exception e) {
            System.out.println("Failed to parse time: " + time);
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
        System.out.println("Parsed Time: " + hours + ":" + minutes + ":" + seconds);
        return LocalTime.of(hours, minutes, seconds);
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
        return resultRows;
    }
}
