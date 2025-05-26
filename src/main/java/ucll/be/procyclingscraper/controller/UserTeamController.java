package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.service.UserTeamService;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend.vercel.app")
@RequestMapping("/user-teams")
public class UserTeamController {

    @Autowired
    private UserTeamService userTeamService;

    @GetMapping()
    public List<UserTeam> getTeams() {
        return userTeamService.getTeams();
    }
}
