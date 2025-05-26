package ucll.be.procyclingscraper.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.RaceModel;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.service.RaceService;

import java.util.List;

@RestController
@CrossOrigin(origins = "*") 
@RequestMapping("/races")
public class RaceController {

    private final RaceService raceService;

    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @GetMapping("/scrape")
    public List<Race> scrapeRaces() {
        return raceService.scrapeRaces();
    }
    @GetMapping()
    public List<Race> getRaces() {
        return raceService.getRaces();
    }

    @GetMapping("/scrapeSingleRace")
    public ResponseEntity<Race> scrapeSingleRace(@RequestParam String name) {
        Race race = raceService.scrapeRaceByUrl(name);
        if (race != null) {
            return ResponseEntity.ok(race);
        } else {
            return ResponseEntity.notFound().build();
        }
    }


    @GetMapping("/one_day")
    public List<Race> fetchOneDayRaces() {
        return raceService.fetchOneDayRaces();
    }

    @GetMapping("/raceDTOs")
    public List<RaceModel> getRaceDTOs() {
        return raceService.getRaceDTOs();
    }
    
}
