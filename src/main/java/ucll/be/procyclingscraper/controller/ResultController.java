package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.service.ResultService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/results")
public class ResultController {
    
    @Autowired
    private ResultService resultService;
    
    @GetMapping("/scrape/stageResults/stage")
    public List<TimeResult> scrapeResults() {
        return resultService.scrapeTimeResult(ScrapeResultType.STAGE);
    }
    @GetMapping("/scrape/stageResults/gc")
    public List<TimeResult> scrapeGcPerStage() {
        return resultService.scrapeTimeResult(ScrapeResultType.GC);
    }

    // @GetMapping("/scrape/stageResults/youth")
    // public List<TimeResult> scrapeYouthPerStage() {
    //     return resultService.calculateYouthResults();
    // }
    
    @GetMapping("")
    public List<TimeResult> getAllResults() {
        return resultService.findAllResults();
    }

    @GetMapping("/gc/timeResult/{cyclistId}/{stageId}")
    public TimeResult getGCTimeResultByCyclistIdAndRaceId(@PathVariable Long cyclistId, @PathVariable Long stageId) {
        return resultService.findGCTimeResultByCyclistIdAndStageId(cyclistId, stageId);
    }

    @GetMapping("/gc/timeResult/{stageId}/cylistUnderAge24")
    public List<TimeResult> getGCTimeResultsByStageIdAndCylistUnderAge24(@PathVariable String stageId) {
        return resultService.findGCTimeResultsByStageIdAndCylistUnderAge24(stageId);
    }
    

    @DeleteMapping("/delete")
    public void deleteAllResults() {
        resultService.deleteAllResults();
    }
}
