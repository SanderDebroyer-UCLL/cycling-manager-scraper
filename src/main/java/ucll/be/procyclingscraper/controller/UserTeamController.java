package ucll.be.procyclingscraper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import ucll.be.procyclingscraper.dto.CyclistDTO;
import ucll.be.procyclingscraper.dto.UpdateUserTeamDTO;
import ucll.be.procyclingscraper.dto.UserTeamDTO;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.UserTeamService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app/")
@RequestMapping("/user-teams")
public class UserTeamController {

    @Autowired
    private UserTeamService userTeamService;

    @Autowired
    private JwtHelper jwtHelper;

    @GetMapping()
    public List<UserTeamDTO> getTeams() {
        return userTeamService.getTeams();
    }

    @GetMapping("/dns/{competitionId}")
    public List<CyclistDTO> getCyclistsWithDNS(@PathVariable Long competitionId) {
        return userTeamService.getCyclistsWithDNS(competitionId);
    }

    @PutMapping("/update/{userTeamId}")
    public List<UserTeam> putMethodName(@PathVariable Long userTeamId, @RequestBody UpdateUserTeamDTO updateUserTeamDTO,
            @RequestHeader(name = "Authorization") String token) {
        String email = jwtHelper.getUsernameFromToken(token.substring(7));
        return userTeamService.updateUserTeam(userTeamId, email, updateUserTeamDTO);
    }
}