package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.service.StagePointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app/")
@RequestMapping("/stagePoints")
public class StagePointsController {

    @Autowired
    private StagePointsService stagePointsService;

    @GetMapping("/create/{competitionId}")
    public List<StagePoints> createStagePoints(@PathVariable Long competitionId,
            @RequestParam Long stageId) {
        return stagePointsService.createStagePoints(competitionId, stageId);
    }

    @GetMapping("/user/{competitionId}")
    public MainReserveCyclistPointsDTO getStagePointsForUser(@PathVariable Long competitionId,
            @RequestParam Long userId,
            @RequestParam Long stageId) {
        return stagePointsService.getStagePointsForUserPerCylicst(competitionId, userId, stageId);
    }

    @GetMapping("/{competitionId}")
    public MainReserveCyclistPointsDTO getStagePointsForStage(@PathVariable Long competitionId,
            @RequestParam Long stageId) {
        return stagePointsService.getStagePointsForStage(competitionId, stageId);
    }

    @GetMapping("/all/{competitionId}")
    public MainReserveCyclistPointsDTO getAllStagePoints(@PathVariable Long competitionId,
            @RequestParam Long userId) {
        return stagePointsService.getAllStagePoints(competitionId, userId);
    }

    @GetMapping("/all/users/{competitionId}")
    public List<PointsPerUserDTO> getAllStagePointsForAllUsers(@PathVariable Long competitionId) {
        return stagePointsService.getAllStagePointsForAllUsers(competitionId);
    }

}
