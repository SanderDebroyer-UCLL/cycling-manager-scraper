package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.CreateStagePointsDTO;
import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.StagePointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/stagePoints")
public class StagePointsController {

    @Autowired
    private StagePointsService stagePointsService;

    @Autowired
    private JwtHelper jwtHelper;

    @GetMapping("/create/{competitionId}")
    public List<StagePoints> createStagePoints(@PathVariable Long competitionId,
            @RequestParam Long stageId) {
        return stagePointsService.createStagePoints(competitionId, stageId);
    }

    @PostMapping()
    public boolean createStagePoints(@RequestBody CreateStagePointsDTO stagePoints,
            @RequestHeader(name = "Authorization") String token) {
        String username = jwtHelper.getUsernameFromToken(token.substring(7));
        return stagePointsService.createNewStagePoints(stagePoints, username);
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
