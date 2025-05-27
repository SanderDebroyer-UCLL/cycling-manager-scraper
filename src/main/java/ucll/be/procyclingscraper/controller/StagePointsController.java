package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.StagePointsPerUserDTO;
import ucll.be.procyclingscraper.dto.StagePointsPerUserPerCyclistDTO;
import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.service.StagePointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/stagePoints")
public class StagePointsController {

    @Autowired
    private StagePointsService stagePointsService;

    @GetMapping("/create/{competitionId}")
    public List<StagePoints> createStagePoints(@PathVariable Long competitionId, @RequestParam Long userId,
            @RequestParam Long stageId) {
        return stagePointsService.createStagePoints(competitionId, userId, stageId);
    }

    @GetMapping("/user/{competitionId}")
    public List<StagePointsPerUserPerCyclistDTO> getStagePointsForUser(@PathVariable Long competitionId, @RequestParam Long userId,
            @RequestParam Long stageId) {
        return stagePointsService.getStagePointsForUser(competitionId, userId, stageId);
    }

    @GetMapping("/{competitionId}")
    public List<StagePointsPerUserDTO> getStagePointsForStage(@PathVariable Long competitionId,
            @RequestParam Long stageId) {
        return stagePointsService.getStagePointsForStage(competitionId, stageId);
    }

    @GetMapping("/all/{competitionId}")
    public List<StagePointsPerUserPerCyclistDTO> getAllStagePoints(@PathVariable Long competitionId, @RequestParam Long userId) {
        return stagePointsService.getAllStagePoints(competitionId, userId);
    }
}
