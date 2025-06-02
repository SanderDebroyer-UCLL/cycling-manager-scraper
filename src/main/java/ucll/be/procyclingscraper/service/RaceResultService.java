package ucll.be.procyclingscraper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.time.Duration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import ucll.be.procyclingscraper.dto.RaceResultWithCyclistDTO;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RaceResult;
import ucll.be.procyclingscraper.model.RaceStatus;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.RaceResultRepository;

@Service
@Transactional
public class RaceResultService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    private final RaceResultRepository raceResultRepository;
    private final StageResultService resultService;
    private final RaceRepository raceRepository;
    private final CyclistRepository cyclistRepository;
    private final CyclistService cyclistService;

    public RaceResultService(RaceResultRepository raceResultRepository, StageResultService resultService,
            RaceRepository raceRepository, CyclistRepository cyclistRepository, CyclistService cyclistService) {
        this.raceResultRepository = raceResultRepository;
        this.resultService = resultService;
        this.raceRepository = raceRepository;
        this.cyclistRepository = cyclistRepository;
        this.cyclistService = cyclistService;
    }

    public HashMap<String, String> getResultsTableData(Element tableRow) {

        Element positionElement = tableRow.selectFirst("td:first-child");
        String position = positionElement != null ? positionElement.text() : "Unknown";
        System.out.println("Position: " + position);

        Element riderElement = tableRow.selectFirst("td:nth-child(5) a");
        String riderName = riderElement != null ? riderElement.text() : "Unknown";
        System.out.println("Rider Name: " + riderName);

        Element timeElement = tableRow.selectFirst("td.time.ar");
        String rawTime = timeElement != null ? timeElement.text() : "Unknown";
        String[] parts = rawTime.split(" ");
        String time = parts[0];
        System.out.println("Time: " + time);

        return new HashMap<String, String>() {
            {
                put("position", position);
                put("riderName", riderName);
                put("time", time);
            }
        };

    }

    public List<RaceResult> scrapeOneDayRaceResults() throws IOException {

        try {
            List<RaceResult> raceResults = new ArrayList<>();
            List<Race> oneDayRaces = raceRepository.findRaceByStagesIsEmpty();

            for (Race race : oneDayRaces) {
                String raceUrl = race.getRaceUrl() + "/result";
                System.out.println("Constructed race URL: " + raceUrl);
                Document doc = Jsoup.connect(raceUrl)
                        .userAgent(USER_AGENT)
                        .get();

                Elements raceResultRows = doc.select("table.results tbody > tr");
                Duration cumulativeTime = Duration.ZERO;
                List<String> ridersToAvoid = Arrays.asList("GUALDI Simone");

                for (Element row : raceResultRows) {
                    HashMap<String, String> resultData = getResultsTableData(row);
                    System.out.println("Fetched race name " + race.getName());
                    String position = resultData.get("position");
                    String riderName = resultData.get("riderName");
                    String time = resultData.get("time");
                    if (position.equals("Unknown") || riderName.equals("Unknown") || time.equals("Unknown")) {
                        System.out.println("Skipping row due to missing data");
                        continue;
                    }

                    Duration resultTime = resultService.timeHandlerWithCumulative(time, cumulativeTime);
                    if (resultTime != null) {
                        cumulativeTime = resultTime;
                    }
                    System.out.println("Parsed Time: " + resultTime);

                    if (ridersToAvoid.contains(riderName)) {
                        System.out.println("Skipping rider: " + riderName);
                        continue;
                    }

                    Cyclist cyclist = cyclistService.searchCyclist(riderName);

                    if (cyclist == null) {
                        System.out.println("Cyclist not found for name: " + riderName);
                        continue;
                    }
                    RaceStatus raceStatus = calculateRaceStatus(position);
                    RaceResult raceResult = raceResultRepository.findRaceResultByRaceAndCyclist(race, cyclist);

                    if (raceResult == null) {
                        System.out.println("RaceResult not found, creating a new one");
                        RaceResult newRaceResult = new RaceResult();
                        newRaceResult.setPosition(position);
                        newRaceResult.setTime(resultTime);
                        newRaceResult.setRaceStatus(raceStatus);
                        newRaceResult.setRace(race);
                        newRaceResult.setCyclist(cyclist);
                        cyclist.addRaceResult(raceResult);
                        race.addRaceResult(raceResult);
                        raceResultRepository.save(newRaceResult);
                        raceResults.add(newRaceResult);
                    }
                }
            }
            return raceResults;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public RaceStatus calculateRaceStatus(String position) {
        if (position.equalsIgnoreCase("DNS")) {
            return RaceStatus.DNS;
        } else if (position.equalsIgnoreCase("DNF")) {
            return RaceStatus.DNF;
        } else if (position.equalsIgnoreCase("DSQ")) {
            return RaceStatus.DSQ;
        } else if (position.equalsIgnoreCase("OTL")) {
            return RaceStatus.OTL;
        } else {
            return RaceStatus.FINISHED;
        }
    }

    public List<RaceResult> getRaceResults() {
        return raceResultRepository.findAll();
    }
    
    public List<RaceResultWithCyclistDTO> getRaceResultByRaceId(String raceId) {
        Race race = raceRepository.findRaceById(Integer.parseInt(raceId));
        List<RaceResult> raceResults = race.getRaceResult();

        return raceResults.stream()
                .map(result -> {
                    Cyclist cyclist = result.getCyclist();
                    return RaceResultWithCyclistDTO.builder()
                            .id(result.getId())
                            .position(result.getPosition())
                            .time(result.getTime())
                            .raceStatus(result.getRaceStatus())
                            .cyclistId(cyclist != null ? cyclist.getId() : null)
                            .cyclistName(cyclist != null ? cyclist.getName() : null)
                            .cyclistCountry(cyclist != null ? cyclist.getCountry() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<RaceResult> getRaceResultsByCyclistId(String cyclistId) {

        Cyclist cyclist = cyclistRepository.findCyclistById(Integer.parseInt(cyclistId));
        List<RaceResult> raceResults = cyclist.getRaceResults();
        return raceResults;
    }

    public RaceResult getRaceResultByRaceIdAndCyclistId(String raceId, String cyclistId) {
        Race race = raceRepository.findRaceById(Integer.parseInt(raceId));
        Cyclist cyclist = cyclistRepository.findCyclistById(Integer.parseInt(cyclistId));
        RaceResult raceResult = raceResultRepository.findRaceResultByRaceAndCyclist(race, cyclist);
        return raceResult;
    }
}
