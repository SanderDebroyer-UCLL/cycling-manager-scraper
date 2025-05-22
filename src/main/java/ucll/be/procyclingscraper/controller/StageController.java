package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.service.StageService;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/stages")
public class StageController {

    @Autowired
    private StageService stageService;

    @GetMapping("/scrape")
    public List<Stage> scrapeStages() {
        return stageService.scrapeStages();
    }
    
    @GetMapping()
    public List<Stage> getStages() {
        return stageService.getStages();
    }
    
    @GetMapping("/result/{id}")
    public List<Cyclist> getStageResultFromStageId(@PathVariable Long id) {
        return stageService.findCyclistInByStageId(id, "STAGE");
    }

    @GetMapping("/stageResult/gc")
    public List<Cyclist> getStageGcFromStageId(@RequestParam Long id) {
        return stageService.findCyclistInByStageId(id, "GC");
    }

}
