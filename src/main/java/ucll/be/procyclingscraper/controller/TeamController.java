package ucll.be.procyclingscraper.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ucll.be.procyclingscraper.dto.TeamModel;
import ucll.be.procyclingscraper.model.Team;
import ucll.be.procyclingscraper.service.TeamService;

import java.util.List;


@RestController
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/scrape")
    public List<Team> scrape() {
        return teamService.scrape();
    }
    
    @GetMapping()
    public List<Team> getTeams() {
        return teamService.getTeams();
    }

    @GetMapping("/teamDTOs")
    public List<TeamModel> getTeamDTOs() {
        return teamService.getTeamDTOs();
    }
    
}
