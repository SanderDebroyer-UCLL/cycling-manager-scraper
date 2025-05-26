package ucll.be.procyclingscraper.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;

import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.CompetitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;




@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app/")
@RequestMapping("/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private JwtHelper jwtHelper;

    @GetMapping("/all")
    public List<Competition> getAllCompetitions() {
        return competitionService.getAllCompetitions();
    }

    @PostMapping("/create-competition")
    public Competition createCompetition(@RequestBody @Valid CreateCompetitionData competition) {
        return competitionService.createCompetition(competition);
    }

    @GetMapping("/{id}")
    public Competition getCompetition(@RequestHeader(name="Authorization") String token, @PathVariable Long id) {
        return competitionService.getCompetitionById(id);
    }

    @GetMapping()
    public Set<Competition> getCompetitions(@RequestHeader(name="Authorization") String token) {
        String username = jwtHelper.getUsernameFromToken(token.substring(7));
        return competitionService.getCompetitions(username);
    }
    
}
