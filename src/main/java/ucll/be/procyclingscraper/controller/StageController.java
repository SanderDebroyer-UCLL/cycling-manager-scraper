package ucll.be.procyclingscraper.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.service.StageService;

import java.util.List;

@RestController
@RequestMapping("/stages")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @GetMapping("/scrape")
    public List<Stage> scrapeStages() {
        return stageService.scrapeStages();
    }
    
    @GetMapping()
    public List<Stage> getStages() {
        return stageService.getStages();
    }
}
