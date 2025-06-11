package ucll.be.procyclingscraper.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.RaceResultWithCyclistDTO;
import ucll.be.procyclingscraper.model.RaceResult;
import ucll.be.procyclingscraper.service.RaceResultService;

@RestController
@RequestMapping("/raceResults")
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app")
public class RaceResultController {

    @Autowired
    private RaceResultService raceResultService;

    @GetMapping("/scrape")
    public List<RaceResult> getOneDayRaceResult() throws IOException {
        return raceResultService.scrapeOneDayRaceResults();
    }

    @GetMapping("/scrape/{raceId}")
    public List<RaceResult> getOneDayRaceResultById(@PathVariable Long raceId) throws IOException {
        return raceResultService.scrapeOneDayRaceResultsById(raceId);
    }

    @GetMapping()
    public List<RaceResult> getRaceResults() {
        return raceResultService.getRaceResults();
    }

    @GetMapping("/race/{id}")
    public List<RaceResultWithCyclistDTO> getRaceResultByRaceId(@PathVariable String id) {
        return raceResultService.getRaceResultByRaceId(id);
    }

    @GetMapping("/cyclist/{id}")
    public List<RaceResult> getRaceResultsByCyclistId(@PathVariable String id) {
        return raceResultService.getRaceResultsByCyclistId(id);
    }

    @GetMapping("/raceAndCyclist/{raceId}/{cyclistId}")
    public RaceResult getRaceResultByRaceIdAndCyclistId(@PathVariable String raceId, @PathVariable String cyclistId) {
        return raceResultService.getRaceResultByRaceIdAndCyclistId(raceId, cyclistId);
    }
}
