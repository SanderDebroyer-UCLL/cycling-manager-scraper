package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.CreateRacePointsDTO;
import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.RacePointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app")
@RequestMapping("/racePoints")
public class RacePointsController {

    @Autowired
    private RacePointsService racePointsService;

    @Autowired
    private JwtHelper jwtHelper;

    @PostMapping()
    public boolean createRacePoints(@RequestBody CreateRacePointsDTO racePoints,
            @RequestHeader(name = "Authorization") String token) {
        String username = jwtHelper.getUsernameFromToken(token.substring(7));
        return racePointsService.createNewRacePoints(racePoints, username);
    }

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

    @GetMapping("/all/users/{competitionId}")
    public List<PointsPerUserDTO> getAllRacePointsForAllUsers(@PathVariable Long competitionId) {
        return racePointsService.getAllRacePointsForAllUsers(competitionId);
    }

    public String getMethodName(@RequestParam String param) {
        return new String();
    }

}
