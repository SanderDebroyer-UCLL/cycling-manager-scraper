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
    
    @GetMapping("")
    public List<TimeResult> getAllResults() {
        return resultService.findAllResults();
    }

    @DeleteMapping("/delete")
    public void deleteAllResults() {
        resultService.deleteAllResults();
    }
}
