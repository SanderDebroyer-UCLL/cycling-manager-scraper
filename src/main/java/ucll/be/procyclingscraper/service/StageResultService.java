package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import ucll.be.procyclingscraper.dto.StagePointResultDTO;
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
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
    StageResultRepository stageResultRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    // For TTT Stage format: "31.58,21" => 31 min, 58 sec, 21 hundredths
    public List<Integer> manipulateStringF1(String timeStr) {
        // Split on comma
        String[] minSecMs = timeStr.split(",");
        String minSecPart = minSecMs[0]; // "39.19"
        String msPart = minSecMs.length > 1 ? minSecMs[1] : "0"; // "993"

        String[] minSec = minSecPart.split("\\.");
        int minutes = Integer.parseInt(minSec[0]);
        int seconds = minSec.length > 1 ? Integer.parseInt(minSec[1]) : 0;
        int milliseconds = Integer.parseInt(msPart); // <-- no *10

        return List.of(0, minutes, seconds, milliseconds); // hours, minutes, seconds, milliseconds
    }

    // For F1 format (assuming something like "1:23:45" or "+1:23:45")
    public List<Integer> manipulateStringF2(String timeStr) {
        String cleanTimeString = timeStr.replace("+", "");
        String[] parts = cleanTimeString.split(":");

        int hours = 0, minutes = 0, seconds = 0, milliseconds = 0;

        // Handle different formats: H:M:S, M:S, or H:M:S.ms
        if (parts.length == 3) {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);

            // Check if seconds part contains milliseconds
            String secondsPart = parts[2];
            if (secondsPart.contains(".")) {
                String[] secMs = secondsPart.split("\\.");
                seconds = Integer.parseInt(secMs[0]);
                // Pad or truncate milliseconds to 3 digits
                String msStr = secMs[1];
                if (msStr.length() == 1)
                    msStr += "00";
                else if (msStr.length() == 2)
                    msStr += "0";
                else if (msStr.length() > 3)
                    msStr = msStr.substring(0, 3);
                milliseconds = Integer.parseInt(msStr);
            } else {
                seconds = Integer.parseInt(secondsPart);
            }
        } else if (parts.length == 2) {
            // Assume M:S format

            minutes = Integer.parseInt(parts[0]);
            seconds = Integer.parseInt(parts[1]);
        }

        return List.of(hours, minutes, seconds, milliseconds);
    }

    public Duration parseToDuration(int hours, int minutes, int seconds, int milliseconds) {
        return Duration.ofHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
                .plusMillis(milliseconds);
    }

    public Duration parseF2ToDuration(String timeStr) {
        List<Integer> components = manipulateStringF2(timeStr);
        return parseToDuration(components.get(0), components.get(1), components.get(2), components.get(3));
    }

    public Duration parseF1ToDuration(String timeStr) {
        List<Integer> components = manipulateStringF1(timeStr);
        return parseToDuration(components.get(0), components.get(1), components.get(2), components.get(3));
    }

    public List<TimeResult> scrapeTimeResultTTTStage(Stage stage, Race race) {
        try {
            Document doc = fetchStageDocument(race, stage, ScrapeResultType.STAGE);

            // Select the correct table based on the class name
            Element table = doc.selectFirst("table.results-ttt");
            Elements tableRows = table.select("tbody > tr");
            List<TimeResult> results = new ArrayList<>();

            Duration cumulativeTime = Duration.ZERO;
            // String cumulativeTimeString = "";
            int positionCounter = 1;
            List<Cyclist> cyclistToChange = new ArrayList<>();
            for (Element row : tableRows) {

                // Get class name for the row
                String rowClassName = row.className();
                if (rowClassName.equals("team")) {

                    // We extract the third td element from the row element
                    Element scrapedTimeElement = row.selectFirst("td:nth-child(3)");
                    // We need to extract the font element from screapedTimeElement
                    Element fontElement = scrapedTimeElement.selectFirst("font");
                    // We need to extract the text from the font element
                    String scrapedMilsecValue = fontElement != null ? fontElement.text() : "Unknown";
                    System.out.println("Scraped miliseconds value: " + scrapedMilsecValue);

                    String scrapedTimeValue = scrapedTimeElement != null ? scrapedTimeElement.text() : "Unknown";
                    // cumulativeTimeString = scrapedTimeValue;
                    System.out.println("Scraped time value: " + scrapedTimeValue);

                    Duration scrapedTimeInDuration;
                    if (scrapedTimeValue.matches("\\d{1,2}:\\d{2}")) {
                        scrapedTimeInDuration = parseToDuration(scrapedTimeValue);
                    } else {
                        scrapedTimeInDuration = parseF1ToDuration(scrapedTimeValue);
                        cumulativeTime = Duration.ZERO; // Reset cumulative time for TTT stages
                        cumulativeTime = cumulativeTime.plus(scrapedTimeInDuration);
                    }
                    System.out.println("Scraped time converted in Duration: " + scrapedTimeInDuration);
                    System.out.println("Cumulative time: " + cumulativeTime);
                } else {
                    // Haal alleen de directe tekst (zonder child-elementen) uit de td
                    Element riderElement = row.selectFirst("td:nth-child(2) a");
                    String riderName = riderElement != null ? riderElement.text() : "Unknown";
                    // Pak de laatste <span> voor extraTime
                    Elements spans = row.select("td:nth-child(2) span");
                    String extraTimeText = spans.isEmpty() ? "0" : spans.last().text();
                    // Pak de eerste <span> voor raceStatus
                    Element raceStatusElement = spans.isEmpty() ? null : spans.first();
                    String raceStatusText = raceStatusElement != null ? raceStatusElement.text() : "Unknown";
                    System.out.println("Rider Name: " + riderName);
                    System.out.println("Extra Time: " + extraTimeText);
                    System.out.println("Race Status: " + raceStatusText);

                    Duration extraTimeDuration = parseF2ToDuration(extraTimeText);
                    Duration totalTime = cumulativeTime.plus(extraTimeDuration);
                    System.out.println("Extra Time converted in Duration: " + extraTimeDuration);

                    Cyclist cyclist = cyclistService.searchCyclist(riderName);
                    if (riderName.equals("MILAN Jonathan") || riderName.equals("VACEK Mathias")) {
                        cyclistToChange.add(cyclist);
                        System.out.println("Name added to cyclistToChange: " + cyclist.getName());
                    }

                    if (cyclist == null) {
                        System.out.println("Cyclist not found for name: " + riderName);
                        continue;
                    }

                    TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage,
                            cyclist,
                            ScrapeResultType.STAGE);
                    if (timeResult == null) {
                        System.out.println("Creating new TimeResult for Stage: " + stage.getName());
                        System.out.println("Creating new TimeResult for Stage: " + stage.getName());
                        timeResult = new TimeResult();
                        System.out.println("Current postion: " + positionCounter);
                        timeResult.setPosition(String.valueOf(positionCounter));
                        timeResult.setTime(totalTime);
                        timeResult.setStage(stage);
                        TimeResult timeResultUpdated = (TimeResult) checkForDNFAndMore(raceStatusText, timeResult);
                        timeResultUpdated.setCyclist(cyclist);
                        timeResultUpdated.setScrapeResultType(ScrapeResultType.STAGE);
                        timeResultRepository.save(timeResultUpdated);
                        results.add(timeResultUpdated);
                    }
                    positionCounter++;
                }

            }
            System.out.println("Cylists to change: " + cyclistToChange.size());
            if (stage.getName().equals("Stage 1 (TTT) | Orihuela - Orihuela")) {
                for (Cyclist cyclist : cyclistToChange) {
                    System.out.println("Updating position for cyclist: " + cyclist.getName());
                    TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage,
                            cyclist,
                            ScrapeResultType.STAGE);
                    if (timeResult != null) {
                        int position = 1;
                        if (timeResult.getCyclist().getName().equals("Mathias Vacek")) {
                            timeResult.setPosition(String.valueOf(position));
                        } else {
                            timeResult.setPosition(String.valueOf(2));
                        }
                        timeResultRepository.save(timeResult);
                    }
                }
            }
            return results;
        } catch (IOException e) {
            System.out.println("Error fetching document for TTT stage: " + stage.getName() + " - " + e.getMessage());
            System.out.println("Returning an empty results list for TTT stage: " + stage.getName());
            return new ArrayList<>();
        }

    }

    private void assignPositionsForStage(Long stageId, ScrapeResultType scrapeResultType) {
        List<PointResult> pointResults = pointResultRepository.findByStageIdAndScrapeResultTypeOrderByPointDesc(stageId, scrapeResultType);
        List<TimeResult> stageResults = timeResultRepository.findByStageIdAndScrapeResultType(stageId, ScrapeResultType.STAGE);

        // Build a map of cyclist name to position from time results
        Map<String, String> cyclistWithPosition = new HashMap<>();
        for (TimeResult stageResult : stageResults) {
            if (stageResult.getCyclist() != null) {
                String cyclistName = stageResult.getCyclist().getName();
                String position = stageResult.getPosition();
                cyclistWithPosition.put(cyclistName, position);
            }
        }

        // Sort point results by points desc, then by position in time results
        pointResults.sort((a, b) -> {
            int pointCompare = Integer.compare(b.getPoint(), a.getPoint());
            if (pointCompare != 0) return pointCompare;

            String nameA = a.getCyclist().getName();
            String nameB = b.getCyclist().getName();
            String posAStr = cyclistWithPosition.getOrDefault(nameA, "999");
            String posBStr = cyclistWithPosition.getOrDefault(nameB, "999");
            int posA = posAStr.matches("\\d+") ? Integer.parseInt(posAStr) : 999;
            int posB = posBStr.matches("\\d+") ? Integer.parseInt(posBStr) : 999;
            return Integer.compare(posA, posB);
        });

        int rank = 1;
        for (PointResult result : pointResults) {
            String origPos = cyclistWithPosition.getOrDefault(
                result.getCyclist() != null ? result.getCyclist().getName() : "", "999"
            );
            // If original position is DNS, DNF, NR, OTL, DSQ, use that instead of rank
            if (origPos.equalsIgnoreCase("DNS") ||
                origPos.equalsIgnoreCase("DNF") ||
                origPos.equalsIgnoreCase("NR") ||
                origPos.equalsIgnoreCase("OTL") ||
                origPos.equalsIgnoreCase("DSQ")) {
                result.setPosition(origPos);
            } else {
                result.setPosition(String.valueOf(rank));
                rank++;
            }
            stageResultRepository.save(result);
        }
    }

    public List<TimeResult> scrapeResultsForTTTStages() {
        List<Stage> allStages = stageRepository.findAll(Sort.by("id"));
        List<Stage> tttStages = new ArrayList<>();
        List<TimeResult> allResults = new ArrayList<>();
        for (Stage stage : allStages) {
            if (stage.getName().contains("TTT")) {
                System.out.println("Found TTT Stage: " + stage.getName());
                tttStages.add(stage);
            }
        }
        for (Stage stage : tttStages) {
            List<TimeResult> res = scrapeTimeResultTTTStage(stage, stage.getRace());
            allResults.addAll(res);
        }
        return allResults;
    }

    public List<Cyclist> findCyclistInByStageId(Long stage_id, String type) {
        return cyclistRepository.findCyclistsByStageIdAndResultType(stage_id, type);
    }

    public List<StagePointResultDTO> findCyclistInByStageIdAndTypeDto(Long stageId, String type) {
        List<Cyclist> cyclists = cyclistRepository.findCyclistsByStageIdAndResultType(stageId, type);
        ArrayList<StagePointResultDTO> results = new ArrayList<>();
        for (Cyclist cyclist : cyclists) {
            // Find the corresponding StageResult for this cyclist, stage, and type
            StageResult stageResult = null;
            List<StageResult> stageResults = cyclist.getResults();
            if (stageResults != null) {
                for (StageResult result : stageResults) {
                    if (result.getStage().getId().equals(stageId) &&
                            result.getScrapeResultType().name().equalsIgnoreCase(type)) {
                        stageResult = result;
                        break;
                    }
                }
            }

            Integer point = null;
            String position = null;
            if (stageResult instanceof PointResult) {
                point = ((PointResult) stageResult).getPoint();
            }
            if (stageResult != null) {
                position = stageResult.getPosition();
            }

            StagePointResultDTO dto = new StagePointResultDTO(
                    cyclist.getId(),
                    position,
                    cyclist.getName(),
                    point,
                    stageResult.getStage().getId(),
                    stageResult.getStage().getName(),
                    stageResult.getStage().getStageUrl());
            results.add(dto);
        }
        return results;
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
            scrapePointResultForRace(ScrapeResultType.POINTS, race.getId());
            scrapePointResultForRace(ScrapeResultType.KOM, race.getId());
            calculateYouthTimeResultForRace(race.getId(), ScrapeResultType.YOUTH);
        }
    }

    private List<PointResult> getPointResultsFromStage1(String stageUrl, ScrapeResultType scrapeResultType, Long stageId) {
        Stage stageOpt = stageRepository.findStageById(stageId);
        // if (stageOpt == null) {
        //     List<PointResult> existingResults = pointResultRepository.findByStageIdAndScrapeResultType(stageId, scrapeResultType);
        //     pointResultRepository.deleteAll(existingResults);
        //     System.out.println("Deleted existing results for Stage ID: " + stageId + ", Type: " + scrapeResultType);
        // }

        List<PointResult> results = new ArrayList<>();
        Map<String, PointResult> riderResultMap = new HashMap<>();
        String klassementType = scrapeResultType == ScrapeResultType.POINTS ? "POINTS" : "KOM";

        System.out.println("\n ======= START STAGE" + stageId + " " + klassementType + " SCRAPING =======");

        try {
            String complementaryUrl = stageUrl + "/info/complementary-results";
            System.out.println(" Fetching complementary results from: " + complementaryUrl);

            Document doc = Jsoup.connect(complementaryUrl)
                    .userAgent(USER_AGENT)
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
                        // Check for negative sign before parsing
                        String cleanedPoint = point.replaceAll("[^\\d-]", "");
                        pointValue = Integer.parseInt(cleanedPoint);
                    } catch (NumberFormatException e) {
                        System.out.println(" Invalid point value: " + point);
                        pointValue = 0;
                    }

                    System.out.println(cyclist.getName() + " (ID:" + cyclist.getId() +
                            ") earned " + pointValue + " points at position " + position);

                    PointResult pointResult = pointResultRepository.findByStageIdAndCyclistAndScrapeResultType(stageId, cyclist, scrapeResultType);
                    if (pointResult == null) {
                        pointResult = new PointResult();
                        pointResult.setCyclist(cyclist);
                        pointResult.setPosition(position);
                        pointResult.setPoint(pointValue);
                        pointResult.setRaceStatus(RaceStatus.FINISHED);
                        pointResult.setStage(stageOpt);
                        pointResult.setScrapeResultType(scrapeResultType);
                        riderResultMap.put(riderName, pointResult);
                        pointResultRepository.save(pointResult);
                    } else {
                        int currentPoints = pointResult.getPoint();
                        pointResult.setPoint(currentPoints + pointValue);
                        System.out.println(" Updated total: " + currentPoints + " + " +
                                pointValue + " = " + pointResult.getPoint());
                        pointResultRepository.save(pointResult);        
                    }
                }
                
            }

            assignPositionsForStage(stageId, scrapeResultType);
        } catch (Exception e) {
            System.err.println(" Failed to retrieve point results: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n FINAL " + klassementType + " RESULTS FOR STAGE: " + stageOpt.getName());
        for (Map.Entry<String, PointResult> entry : riderResultMap.entrySet()) {
            PointResult pointResult = entry.getValue();
            results.add(pointResult);
            System.out.println(pointResult.getCyclist().getName() + " (ID:" +
                    pointResult.getCyclist().getId() + "): " +
                    pointResult.getPoint() + " total points");
        }

        System.out.println(" Found " + results.size() + " riders with points");
        System.out.println(" ======= END STAGE " + stageOpt.getName() + " " + klassementType + " SCRAPING =======\n");
        return results;
    }

    public void savePointResult(Stage stage, PointResult pointResult, List<PointResult> results) {
        pointResultRepository.save(pointResult);
        results.add(pointResult);
    }

    public List<PointResult> scrapePointResult(ScrapeResultType scrapeResultType) {
        List<PointResult> results = new ArrayList<>();
        List<Race> races = raceRepository.findAllOrderByIdAsc();
        for (Race race : races) {
            results.addAll(scrapePointResultForRace(scrapeResultType, race.getId()));
        }
        return results;
    }

    public List<PointResult> scrapePointResultForRace(ScrapeResultType scrapeResultType, Long raceId) {
        List<PointResult> results = new ArrayList<>();

        try {
            Optional<Race> raceOpt = raceRepository.findById(raceId);
            if (raceOpt.isEmpty()) {
                System.err.println("Race not found for ID: " + raceId);
                return results;
            }

            Race race = raceOpt.get();
            LocalDate raceStartTime = LocalDate.parse(race.getStartDate());

            if (raceStartTime.isAfter(LocalDate.now())) {
                System.out.println("Race " + race.getName() + " has not started yet.");
                return results;
            }

            for (Stage stage : race.getStages()) {
                List<PointResult> stageResults = scrapePointResultForStage(scrapeResultType, stage.getId());
                results.addAll(stageResults);
            }
        } catch (Exception e) {
            System.err.println(" Error scraping point results for raceId=" + raceId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    @Transactional
    public List<PointResult> scrapePointResultForStage(ScrapeResultType scrapeResultType, Long stageId) {
        
        pointResultRepository.deleteByStageIdAndScrapeResultType(stageId, scrapeResultType);

        List<PointResult> results = new ArrayList<>();
        int resultCount = 0;
        final int MAX_RESULTS = 1000;
        Optional<Stage> stageOpt = stageRepository.findById(stageId);

        if (stageOpt.isEmpty()) {
            System.err.println("Stage not found for ID: " + stageId);
            return results;
        }
        Stage stage = stageOpt.get();
        
        System.out.println("\n Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");

        // if (stage.getName().contains("Stage 1")) {
            System.out.println(" === SPECIAL PROCESSING FOR STAGE " + stageId + " " + stage.getName() + " ===");
            List<PointResult> pointResults = getPointResultsFromStage1(stage.getStageUrl(), scrapeResultType, stageId);
            // return pointResults;
        // }    
        // else{
        //     Document doc = fetchStageDocument(stage.getRace(), stage, scrapeResultType);
        //     Elements resultRows = resultRows(doc, stage, scrapeResultType);
        //     if (resultRows == null || resultRows.isEmpty()) {
        //         System.out.println(" No rows found in the selected table.");
        //         return results;
        //     }
        List<TimeResult> stageResults = timeResultRepository.findByStageIdAndScrapeResultType(stageId, ScrapeResultType.STAGE);
        if (!stage.getName().contains("Stage 1")) {
            List<Long> cyclistIds = new ArrayList<>();
            System.out.println("Stage is not 1, fetching previous stages results");
            for (PointResult pr : pointResults) {
                if (pr.getCyclist() != null) {
                    cyclistIds.add(pr.getCyclist().getId());
                }
            }
            System.out.println("Cyclist IDs array: " + cyclistIds);

            List<PointResult> pointResultsPrev = pointResultRepository.findByStageIdAndScrapeResultType(stageId - 1, scrapeResultType);

            for (Long cyclistId : cyclistIds) {
            // Find previous stage's result for this cyclist
                PointResult prevResult = null;
                for (PointResult pr : pointResultsPrev) {
                    if (pr.getCyclist() != null && pr.getCyclist().getId().equals(cyclistId)) {
                        prevResult = pr;
                        break;
                    }
                }
                if (prevResult != null) {
                // Find the current stage result for this cyclist
                PointResult currentResult = null;
                for (PointResult pr : pointResults) {
                    if (pr.getCyclist() != null && pr.getCyclist().getId().equals(cyclistId)) {
                        currentResult = pr;
                        break;
                    }
                }
                if (currentResult != null) {
                    System.out.println("Adding previous stage points to existing result for cyclist: " + currentResult.getCyclist().getName());
                    int updatedPoints = currentResult.getPoint() + prevResult.getPoint();

                    System.out.println("Updated points for cyclist " + currentResult.getCyclist().getName() + ": " + updatedPoints);
                    currentResult.setPoint(updatedPoints);
                    pointResultRepository.save(currentResult);

                    pointResultsPrev.remove(prevResult);

                    // Update the object in pointResults as well
                    for (int i = 0; i < pointResults.size(); i++) {
                        PointResult p = pointResults.get(i);
                        if (p.getCyclist() != null && p.getCyclist().getId().equals(cyclistId)) {
                            pointResults.set(i, currentResult);
                            break;
                        }
                    }
                }
            }
        }
        
        for (PointResult pr: pointResultsPrev) {
            System.out.println("Adding remaining previous stage points to new result for cyclist: " + pr.getCyclist().getName());
            // Only add if position is NOT DNS, DNF, NR, OTL, DSQ (case-insensitive)
            String pos = pr.getPosition();
            // Find the corresponding TimeResult for this cyclist in stageResults
            String stageResultPosition = null;
            for (TimeResult tr : stageResults) {
                if (tr.getCyclist() != null && pr.getCyclist() != null &&
                    tr.getCyclist().getId().equals(pr.getCyclist().getId())) {
                    stageResultPosition = tr.getPosition();
                    break;
                }
            }
            if (stageResultPosition != null &&
                !(stageResultPosition.equalsIgnoreCase("DNS") ||
                  stageResultPosition.equalsIgnoreCase("DNF") ||
                  stageResultPosition.equalsIgnoreCase("NR") ||
                  stageResultPosition.equalsIgnoreCase("OTL") ||
                  stageResultPosition.equalsIgnoreCase("DSQ"))) {
                PointResult newPointResult = getOrCreatePointResult(stage, pr.getCyclist(), scrapeResultType);
                fillPointResultFields(newPointResult, pr.getPosition(), pr.getPoint(), scrapeResultType);
                newPointResult.setRaceStatus(pr.getRaceStatus());
                pointResultRepository.save(newPointResult);
                results.add(newPointResult);
                System.out.println("Added new PointResult for cyclist: " + pr.getCyclist().getName() + " with points: " + pr.getPoint());
            }
        }
        } else {
            System.out.println(" Stage " + stage.getName() + " processed successfully");
        }

        results.addAll(pointResults);
        // List<TimeResult> stageResults = timeResultRepository.findByStageIdAndScrapeResultType(stageId, ScrapeResultType.STAGE);
        // Special fix for Stage 1 where multiple entries can have the same position
        HashMap<String, String> cyclistWithPosition = new HashMap<>();
        // if (stageOpt.getName().contains("Stage 1")) {
       
        System.out.println("Stageresults to use as reference: " + stageResults.size());
        for (TimeResult stageResult : stageResults) {
            if (stageResult.getCyclist() != null) {
                String cyclistName = stageResult.getCyclist().getName();
                String position = stageResult.getPosition();
                cyclistWithPosition.put(cyclistName, position);
            }
        }

        System.out.println("Constructed hashmap of cyclists with positions: " + cyclistWithPosition.size() + " entries");
        assignPositionsForStage(stageId, scrapeResultType);
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
        } else if (position.equalsIgnoreCase("NR")) {
            status = RaceStatus.NR;
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
                System.out.println(pointResult + "Pointresult for stage: " + stage.getName());
        if (pointResult == null) {
            System.out.println("Creating new PointResult for Stage: " + stage.getName());
            pointResult = new PointResult();
            pointResult.setStage(stage);
            pointResult.setCyclist(cyclist);
        }
        return pointResult;
    }

    public List<StageResultWithCyclistDTO> getLastResultsByType(Long raceId, ScrapeResultType type) {
        List<Stage> stages = raceRepository.findById(raceId)
                .orElseThrow(() -> new RuntimeException("Race not found"))
                .getStages();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

        stages.sort((a, b) -> {
            MonthDay monthDayA = MonthDay.parse(a.getDate(), formatter);
            MonthDay monthDayB = MonthDay.parse(b.getDate(), formatter);

            LocalDate dateA = monthDayA.atYear(LocalDate.now().getYear());
            LocalDate dateB = monthDayB.atYear(LocalDate.now().getYear());

            return dateB.compareTo(dateA); // descending order
        });

        for (Stage stage : stages) {
            List<StageResult> stageResults = stage.getResults();

            // Filter by type
            List<StageResult> filteredResults = stageResults.stream()
                    .filter(result -> result.getScrapeResultType() == type)
                    .collect(Collectors.toList());

            if (!filteredResults.isEmpty()) {
                // Map to DTO and return
                return filteredResults.stream()
                        .map(result -> {
                            Cyclist cyclist = result.getCyclist();

                            Duration time = null;
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
        }

        return Collections.emptyList();
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

    private static final int RACE_LIMIT = 10;

    public List<TimeResult> scrapeTimeResult(ScrapeResultType scrapeResultType) throws IOException {
        List<TimeResult> allResults = new ArrayList<>();
        System.out.println("Starting scraping...");

        List<Race> races = raceRepository.findAll(Sort.by("id"));
        int processedRaces = 0;

        for (Race race : races) {
            if (processedRaces >= RACE_LIMIT) {
                break;
            }
            LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
            if (raceStartTime.isAfter(LocalDate.now())) {
                System.out.println(" Race " + race.getName() + " has not started yet.");
                break;
            }
            List<Stage> stages = race.getStages();
            allResults.addAll(scrapeTimeResultByRace(scrapeResultType, stages, race));
            processedRaces++;
        }
        return allResults;
    }

    private static final int MAX_RESULTS = 1000;

    // Parent method - handles multiple stages
    private List<TimeResult> scrapeTimeResultByRace(ScrapeResultType scrapeResultType, List<Stage> stages, Race race)
            throws IOException {
        List<TimeResult> allResults = new ArrayList<>();
        int totalResultCount = 0;

        for (Stage stage : stages) {
            if (totalResultCount >= MAX_RESULTS) {
                break;
            }

            int remainingResults = MAX_RESULTS - totalResultCount;
            List<TimeResult> stageResults = scrapeTimeResultByStage(scrapeResultType, stage, race, remainingResults);

            allResults.addAll(stageResults);
            totalResultCount += stageResults.size();

        }

        return allResults;
    }

    // Child method - handles a single stage
    private List<TimeResult> scrapeTimeResultByStage(ScrapeResultType scrapeResultType, Stage stage, Race race,
            int maxResults)
            throws IOException {
        System.out.println("Processing stage: " + stage.getName() + " (" + stage.getStageUrl() + ")");
        List<TimeResult> stageResults = new ArrayList<>();
        int resultCount = 0;
        Duration latestFinisher = null;
        LocalDate raceStartTime = LocalDate.parse(race.getStartDate());
        if (raceStartTime.isAfter(LocalDate.now())) {
            System.out.println(" stage " + stage.getName() + " has not started yet.");
            return stageResults;
        }
        Document doc = fetchStageDocument(race, stage, scrapeResultType);

        Elements resultRows = resultRows(doc, stage, scrapeResultType);
        if (resultRows == null || resultRows.isEmpty()) {
            System.out.println(" No rows found in the selected table for stage: " + stage.getName());
            return stageResults;
        }
        // This is in format PT.....
        Duration cumulativeTime = null;

        for (Element row : resultRows) {
            if (resultCount >= MAX_RESULTS) {
                System.out.println(" Reached MAX_RESULTS limit");
                break;
            }

            String position;
            String rawTime = "Unknown";

            Element positionElement = row.selectFirst("td:first-child");
            position = positionElement != null ? positionElement.text() : "Unknown";
            System.out.println("Position: " + position);

            Element timeElement = row.selectFirst("td.time.ar");
            rawTime = timeElement != null ? timeElement.text() : "Unknown";
            System.out.println("Raw time: " + rawTime);

            Element riderElement;
            String riderName = "";

            if (stage.getName().equals("Stage 1 (TTT) | Orihuela - Orihuela")) {
                riderElement = row.selectFirst("td:nth-child(5) a");
                riderName = riderElement != null ? riderElement.text() : "Unknown";
                System.out.println("New Rider Name: " + riderName);
            } else {
                riderElement = row.selectFirst("td:nth-child(7) a");
                riderName = riderElement != null ? riderElement.text() : "Unknown";
                System.out.println("Rider Name: " + riderName);
            }

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
                resultTime = timeHandlerWithCumulative(time, cumulativeTime, latestFinisher);
                cumulativeTime = resultTime; // Save for next riders
                System.out.println("New cumulative time: " + cumulativeTime);
            } else {
                // All others: treat as gap relative to first finisher
                resultTime = timeHandlerWithCumulative(time, cumulativeTime, latestFinisher);
            }
            latestFinisher = resultTime;
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
            if (time.contains("-")) {
                checkForDNFAndMore(position, timeResult);
            }
            timeResult = (TimeResult) checkForDNFAndMore(position, timeResult);

            fillTimeResultFields(timeResult, position, resultTime, scrapeResultType);

            saveResult(stage, timeResult, stageResults);
            resultCount++;
        }

        // Handle GC-specific sorting and position assignment for this stage
        if (scrapeResultType == ScrapeResultType.GC) {
            stageResults.sort(Comparator.comparing(TimeResult::getTime, Comparator.nullsLast(Duration::compareTo)));
            System.out.println("Sorting results by time for GC stage: " + stage.getName());

            int positionCounter = 1;
            for (TimeResult result : stageResults) {
                if (result.getRaceStatus() == RaceStatus.FINISHED) {
                    result.setPosition(String.valueOf(positionCounter));
                    positionCounter++;
                }
                timeResultRepository.save(result);
            }
        }

        return stageResults;
    }

    public boolean scrapeTimeResultsForStage(Long stageId) throws IOException {
        Stage stage = stageRepository.findById(stageId).orElse(null);
        if (stage == null) {
            System.out.println("Stage not found for ID: " + stageId);
            return false;
        }

        System.out.println("Scraping results for stage: " + stage.getName());

        scrapeTimeResultByStage(ScrapeResultType.STAGE, stage, stage.getRace(), MAX_RESULTS);
        scrapeTimeResultByStage(ScrapeResultType.GC, stage, stage.getRace(), MAX_RESULTS);
        scrapePointResultForStage(ScrapeResultType.POINTS, stage.getId());
        scrapePointResultForStage(ScrapeResultType.KOM, stage.getId());
        calculateYouthTimeResultForRace(stage.getRace().getId(), ScrapeResultType.YOUTH);

        return true;
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

        // Logic for youth results added
        if (scrapeResultType.equals(ScrapeResultType.YOUTH)) {
            System.out.println("Scraping Youth results for stage: " + stage.getName());
            stageUrl = stageUrl + "-youth";
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
            } else if (scrapeResultType.equals(ScrapeResultType.YOUTH)) {
                System.out.println(" Last stage in the YOUTH results: " + stage.getName());
                stageUrl = modifyUrl(stageUrl);
                stageUrl = stageUrl + "/youth";
            }
        }

        System.out.println(" Final URL: " + stageUrl);

        try {
            return Jsoup.connect(stageUrl)
                    .userAgent(USER_AGENT)
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

    public Duration timeHandlerWithCumulative(String time, Duration firstFinisherTime, Duration latestFinisher) {
        System.out.println("First Finisher Time: " + firstFinisherTime);
        try {
            String cleanedTime = time.trim();
            System.out.println("Cleaned Time: " + cleanedTime);
            if (cleanedTime.matches(",,")) {
                return latestFinisher;
            }
            // Match format: "31.58,21" => 31 min, 58 sec, 21 hundredths
            else if (cleanedTime.matches("\\d{1,2}\\.\\d{2},\\d{1,3}")) {
                Duration parsed = parseF1ToDuration(cleanedTime);
                if (firstFinisherTime == null) {
                    return parsed;
                } else {
                    Duration resultTime = firstFinisherTime.plus(parsed.minus(parseToDuration("0:00")));
                    System.out.println("Calculated Time (relative to first, F1): " + resultTime);
                    return resultTime;
                }
            } else if (cleanedTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                Duration parsed = parseToDuration(cleanedTime);
                if (firstFinisherTime == null) {
                    return parsed;
                } else {
                    Duration resultTime = firstFinisherTime.plus(parsed.minus(parseToDuration("0:00")));
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            }
            // If the time is in mm:ss
            else if (cleanedTime.matches("\\d{1,2}:\\d{2}")) {
                Duration parsed = parseToDuration(cleanedTime);
                if (firstFinisherTime == null) {
                    return parsed;
                } else {
                    Duration resultTime = firstFinisherTime.plus(parsed.minus(parseToDuration("0:00")));
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
            }
            // If the time is in m.ss or mm.ss
            else if (cleanedTime.matches("\\d{1,2}\\.\\d{2}")) {
                cleanedTime = cleanedTime.replace(".", ":");
                Duration gapTime = parseToDuration(cleanedTime);
                if (firstFinisherTime == null) {
                    return gapTime;
                } else {
                    Duration resultTime = firstFinisherTime.plus(gapTime.minus(parseToDuration("0:00")));
                    System.out.println("Calculated Time (relative to first): " + resultTime);
                    return resultTime;
                }
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

    public Duration parseToDuration(String timeStr) {
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

    public List<TimeResult> findAllResults() {
        return timeResultRepository.findAll();
    }

    public void deleteAllResults() {
        timeResultRepository.deleteAll();
        pointResultRepository.deleteAll();
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
            System.out.println(" Failed to subtract boni seconds: " + boniSeconds);
            return cumulativeTime;
        }
    }

    private Elements resultRows(Document doc, Stage stage, ScrapeResultType scrapeResultType) {
        Elements tables = doc.select("table.results");
        System.out.println("Number of tables found: " + tables.size());

        Elements resultRows = null;
        if (tables.isEmpty() || tables.size() < 1) {
            System.out.println("No tables found in the document.");
            return resultRows;
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
                // resultRows = tables.get(0).select("tbody > tr");
            }
        } else if (scrapeResultType.equals(ScrapeResultType.KOM)) {
            if (tables.size() >= 4) {
                resultRows = tables.get(6).select("tbody > tr");
            } else if (tables.size() > 0) {
                // fallback: use the first table if only one exists
                System.out.println("KOM table not found at index 3, using first table as fallback.");
                // resultRows = tables.get(2).select("tbody > tr");
            }
        } else {
            // Default case for STAGE results
            if (tables.size() > 0) {
                resultRows = tables.get(0).select("tbody > tr");
            }
        }

        if (resultRows == null || resultRows.isEmpty()) {
            System.out.println(" No rows found in the selected table.");
        } else {
            System.out.println(" Found " + resultRows.size() + " result rows");
        }

        return resultRows;
    }

    public TimeResult findGCTimeResultByCyclistIdAndStageId(Long cyclistId, Long stageId) {

        TimeResult timeResult = timeResultRepository.findTimeResultByCyclistIdAndStageIdAndScrapeResultType(cyclistId,
                stageId, ScrapeResultType.GC);
        return timeResult;

    }

    public List<TimeResult> calculateYouthTimeResult(ScrapeResultType scrapeResultType) {
        try {
            List<Race> races = raceRepository.findAll(Sort.by("id"));
            System.out.println("Number of races found: " + races.size());
            List<TimeResult> allYouthResults = new ArrayList<>();

            for (Race race : races) {
                System.out.println("Processing race: " + race.getName());
                List<TimeResult> youthResultsForRace = calculateYouthTimeResultForRace(race.getId(), scrapeResultType);
                allYouthResults.addAll(youthResultsForRace);
            }

            return allYouthResults;
        } catch (Exception e) {
            System.out.println("Failed to calculate youth results.");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<TimeResult> calculateYouthTimeResultForRace(Long raceId, ScrapeResultType scrapeResultType) {
        List<TimeResult> youthResults = new ArrayList<>();

        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            System.out.println("Race with ID " + raceId + " not found.");
            return youthResults;
        }

        List<Stage> raceStages = race.getStages();
        List<Long> youthCyclistsIDs = race.getYouthCyclistsIDs();

        System.out.println("Processing race: " + race.getName());
        System.out.println("Number of stages: " + raceStages.size());
        System.out.println("Youth Cyclists count: " + youthCyclistsIDs.size());

        for (Stage stage : raceStages) {
            List<TimeResult> gcResults = getGCTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(
                    stage.getId(), youthCyclistsIDs);

            List<TimeResult> numericResults = new ArrayList<>();
            List<TimeResult> nonNumericResults = new ArrayList<>();

            for (TimeResult result : gcResults) {
                if (result.getPosition().matches("^[0-9]+$")) {
                    numericResults.add(result);
                } else {
                    nonNumericResults.add(result);
                }
            }

            numericResults.sort(Comparator.comparing(TimeResult::getTime));

            int positionCounter = 1;
            for (TimeResult numericResult : numericResults) {
                TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage,
                        numericResult.getCyclist(), scrapeResultType);

                if (timeResult == null) {
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
                TimeResult timeResult = timeResultRepository.findByStageAndCyclistAndScrapeResultType(stage,
                        nonNumericResult.getCyclist(), scrapeResultType);

                if (timeResult == null) {
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

        return youthResults;
    }

    public List<TimeResult> getGCTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(long stageId,
            List<Long> youthCyclistIds) {
        System.out.println("Fetched youth Cyclist IDs: " + youthCyclistIds.size());
        List<TimeResult> stageTimeResultsGC = timeResultRepository
                .findTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(stageId, ScrapeResultType.GC,
                        youthCyclistIds);
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