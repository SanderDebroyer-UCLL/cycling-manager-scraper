package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{competitionId}")
    public List<StagePoints> createStagePoints(@PathVariable Long competitionId, @RequestParam Long userId,
            @RequestParam Long stageId) {
        return stagePointsService.createStagePoints(competitionId, userId, stageId);
    }

}
