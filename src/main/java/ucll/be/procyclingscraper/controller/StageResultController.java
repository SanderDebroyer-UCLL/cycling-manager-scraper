package ucll.be.procyclingscraper.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.StagePointResultDTO;
import ucll.be.procyclingscraper.dto.StageResultWithCyclistDTO;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
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
    public List<TimeResult> scrapeResults() throws IOException {
        return stageResultService.scrapeTimeResult(ScrapeResultType.STAGE);
    }

    @GetMapping("/scrape/race/{raceId}")
    public List<TimeResult> scrapeResults(@PathVariable Long raceId) throws IOException {
        return stageResultService.scrapeTimeResultForRace(ScrapeResultType.STAGE, raceId);
    }

    @GetMapping("/scrape/stage/{stageId}")
    public List<TimeResult> scrapeTimeResultsForStage(@PathVariable Long stageId) throws IOException {
        return stageResultService.scrapeTimeResultsForStage(stageId);
    }

    @GetMapping("/scrape/gc")
    public List<TimeResult> scrapeGcPerStage() throws IOException {
        return stageResultService.scrapeTimeResult(ScrapeResultType.GC);
    }

    @GetMapping("/scrape/points")
    public List<PointResult> scrapePointsPerStage() {
        return stageResultService.scrapePointResult(ScrapeResultType.POINTS);
    }

    @GetMapping("/scrape/kom")
    public List<PointResult> scrapeKomPerStage() {
        return stageResultService.scrapePointResult(ScrapeResultType.KOM);
    }

    @GetMapping("/scrape/youth")
    public List<TimeResult> scrapeYouthPerStageTest() {
        return stageResultService.calculateYouthTimeResult(ScrapeResultType.YOUTH);
    }
    
    @GetMapping("")
    public List<TimeResult> getAllResults() {
        return stageResultService.findAllResults();
    }

    @GetMapping("/points/{id}")
    public List<StagePointResultDTO> getStagePointsFromStageId(@PathVariable Long id) {
        return stageResultService.findCyclistInByStageIdAndTypeDto(id, "POINTS");
    }

    @GetMapping("/kom/{id}")
    public List<StagePointResultDTO> getStageKomFromStageId(@PathVariable Long id) {
        return stageResultService.findCyclistInByStageIdAndTypeDto(id, "KOM");
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

    @GetMapping("/scrape/TTTStages")
    public List<TimeResult> scrapeTTTStages() throws IOException {
        return stageResultService.scrapeResultsForTTTStages();
    }

}
