package ucll.be.procyclingscraper.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.model.RaceResult;
import ucll.be.procyclingscraper.service.RaceResultService;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/raceResults")
@CrossOrigin(origins = "*")
public class RaceResultController {
    
    @Autowired 
    private RaceResultService raceResultService;

    @GetMapping("/scrape")
    public List<RaceResult> getOneDayRaceResult() throws IOException {
        System.out.println("I entered the controller");
        return raceResultService.scrapeOneDayRaceResults();
    }

    @GetMapping()
    public List<RaceResult> getRaceResults() {
        return raceResultService.getRaceResults();
    }

    @GetMapping("/race/{id}")
    public List<RaceResult> getRaceResultByRaceId(@PathVariable String id) {
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
