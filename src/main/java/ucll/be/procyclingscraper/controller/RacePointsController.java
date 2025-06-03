package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.service.RacePointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/racePoints")
public class RacePointsController {

    @Autowired
    private RacePointsService racePointsService;

    @GetMapping("/create/{competitionId}")
    public List<RacePoints> createRacePoints(@PathVariable Long competitionId,
            @RequestParam Long raceId) {
        return racePointsService.createRacePoints(competitionId, raceId);
    }

    @GetMapping("/user/{competitionId}")
    public MainReserveCyclistPointsDTO getRacePointsForUser(@PathVariable Long competitionId,
            @RequestParam Long userId,
            @RequestParam Long raceId) {
        return racePointsService.getRacePointsForUserPerCyclist(competitionId, userId, raceId);
    }

    @GetMapping("/{competitionId}")
    public MainReserveCyclistPointsDTO getRacePointsForRace(@PathVariable Long competitionId,
            @RequestParam Long raceId) {
        return racePointsService.getRacePointsForRace(competitionId, raceId);
    }

    @GetMapping("/all/{competitionId}")
    public MainReserveCyclistPointsDTO getAllRacePoints(@PathVariable Long competitionId,
            @RequestParam Long userId) {
        return racePointsService.getAllRacePoints(competitionId, userId);
    }
}
