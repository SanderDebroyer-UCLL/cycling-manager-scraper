package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.StageModel;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.service.StageService;

import java.util.List;

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

    @GetMapping("/scrape/stageResults/points")
    public List<PointResult> scrapePointsPerStage() {
        return stageService.scrapePointResult(ScrapeResultType.POINTS);
    }

    @GetMapping("/scrape/stageResults/kom")
    public List<PointResult> scrapeKomPerStage() {
        return stageService.scrapePointResult(ScrapeResultType.KOM);
    }

    @GetMapping()
    public List<Stage> getStages() {
        return stageService.getStages();
    }

    @GetMapping("/stageDTOs")
    public List<StageModel> getStageDTOs() {
        return stageService.getStageDTOs();
    }
}
