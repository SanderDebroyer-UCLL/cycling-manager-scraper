package ucll.be.procyclingscraper.cronjobs;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ucll.be.procyclingscraper.service.*;

@Component
public class ScraperScheduler {

    @Autowired
    private RaceService raceService;

    @Autowired
    private StagePointsService stagePointsService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private CyclistService cyclistService;

    @Autowired
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private RaceResultService raceResultService;

    @Autowired
    private RacePointsService racePointsService;

    @Scheduled(cron = "0 0 1 * * FRI") // Friday at 01:00
    public void scrapeTeams() throws IOException {
        teamService.scrape();
    }

    @Scheduled(cron = "0 30 1 * * FRI") // Friday 01:30
    public void scrapeCyclists() throws IOException {
        cyclistService.scrapeCyclists();
    }

    @Scheduled(cron = "0 0 2 * * FRI") // Friday 02:00
    public void scrapeRaces() throws IOException {
        raceService.scrapeRaces();
    }

    @Scheduled(cron = "0 30 2 * * FRI") // Friday 02:30
    public void scrapeStages() throws IOException {
        stageService.scrapeStages();
    }

    @Scheduled(cron = "0 0 2 * * *") // 02:00
    public void scrapeStageResults() throws IOException {
        stageResultService.getStageResultsForAllStagesInCompetitions();
    }

    @Scheduled(cron = "0 0 3 * * *") // 03:00
    public void scrapeRaceResults() throws IOException {
        raceResultService.getRaceResultsForAllRacesInCompetitions();
    }

    @Scheduled(cron = "0 0 4 * * *") // 04:00
    public void runStagePointsHandler() {
        stagePointsService.createStagePointsForAllExistingResults();
    }

    @Scheduled(cron = "0 5 4 * * *") // 04:05
    public void runRacePointsHandler() {
        racePointsService.createRacePointsForAllExistingResults();
    }

}
