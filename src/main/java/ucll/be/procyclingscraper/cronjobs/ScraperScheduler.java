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

    @Scheduled(cron = "0 * 1 * * *")
    public void runRaceScraper() throws IOException {
        teamService.scrape();

        cyclistService.scrapeCyclists();

        raceService.scrapeRaces();

        stageService.scrapeStages();

        stageResultService.getStageResultsForAllStagesInCompetitions();

        raceResultService.getRaceResultsForAllRacesInCompetitions();
    }

    @Scheduled(cron = "0 * 3 * * *")
    public void runPointsHandler() {
        stagePointsService.createStagePointsForAllExistingResults();

        racePointsService.createRacePointsForAllExistingResults();
    }
}
