package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.StageResultWithCyclistDTO;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.service.StageResultService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/stageResults")
public class StageResultController {

    @Autowired
    private StageResultService stageResultService;

    @GetMapping("/scrape/stage")
    public List<TimeResult> scrapeResults() {
        return stageResultService.scrapeTimeResult(ScrapeResultType.STAGE);
    }

    @GetMapping("/scrape/gc")
    public List<TimeResult> scrapeGcPerStage() {
        return stageResultService.scrapeTimeResult(ScrapeResultType.GC);
    }

    @GetMapping("")
    public List<TimeResult> getAllResults() {
        return stageResultService.findAllResults();
    }

    @DeleteMapping("/delete")
    public void deleteAllResults() {
        stageResultService.deleteAllResults();
    }

    @GetMapping("/{stageId}")
    public List<StageResultWithCyclistDTO> getStageResultsByType(
            @PathVariable Long stageId,
            @RequestParam ScrapeResultType type) {
        return stageResultService.getStageResultsByStageIdAndType(stageId, type);
    }

}
