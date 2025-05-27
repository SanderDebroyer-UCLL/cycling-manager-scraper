package ucll.be.procyclingscraper.cronjobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ucll.be.procyclingscraper.service.CyclistService;
import ucll.be.procyclingscraper.service.RaceService;
import ucll.be.procyclingscraper.service.StageResultService;
import ucll.be.procyclingscraper.service.StageService;
import ucll.be.procyclingscraper.service.TeamService;

@Component
public class ScraperScheduler {
    @Autowired
    private RaceService raceService;
    
    @Autowired
    private TeamService teamService;

    @Autowired 
    private CyclistService cyclistService;

    @Autowired 
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Scheduled(cron = "0 * 1 * * *")
    public void runRaceScraper() {
        teamService.scrape();

        cyclistService.scrapeCyclists();

        raceService.scrapeRaces();

        stageService.scrapeStages();
        
        stageResultService.getStageResultsForAllStagesICompetitions();
    }
}