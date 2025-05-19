package ucll.be.procyclingscraper.cronjobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ucll.be.procyclingscraper.service.RaceService;

@Component
public class ScraperScheduler {
    @Autowired
    private RaceService raceService;
    
    
}