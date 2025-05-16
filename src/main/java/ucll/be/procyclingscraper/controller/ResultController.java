package ucll.be.procyclingscraper.controller;

import javax.naming.spi.DirStateFactory.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.service.ResultService;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/results")
public class ResultController {
    
    @Autowired
    private ResultService resultService;
    
    @GetMapping("/scrap")
    public Result scrapeResults() {
        return resultService.scrapeResult();
    }
    
}
