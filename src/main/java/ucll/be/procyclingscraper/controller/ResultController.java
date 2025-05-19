package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.model.TimeResult;
import ucll.be.procyclingscraper.service.ResultService;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/results")
public class ResultController {
    
    @Autowired
    private ResultService resultService;
    
    @GetMapping("/scrape")
    public List<TimeResult> scrapeResults() {
        return resultService.scrapeTimeResult();
    }
    
    @GetMapping("")
    public List<TimeResult> getAllResults() {
        return resultService.findAllResults();
    }
}
