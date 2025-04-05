package ucll.be.procyclingscraper.controller;

import org.springframework.web.bind.annotation.*;
import ucll.be.procyclingscraper.model.WebScraper;
import ucll.be.procyclingscraper.service.WebScraperService;

@RestController
@RequestMapping("/api/scrape")
public class WebScraperController {

    private final WebScraperService scraperService;

    public WebScraperController(WebScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @GetMapping
    public WebScraper scrape(@RequestParam String url) {
        return scraperService.scrape(url);
    }
}
