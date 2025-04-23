package ucll.be.procyclingscraper.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.service.RaceService;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
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
    
}
