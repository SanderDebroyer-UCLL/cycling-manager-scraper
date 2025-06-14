package ucll.be.procyclingscraper.controller;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;

import ucll.be.procyclingscraper.dto.CompetitionDTO;
import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.dto.StatusNotification;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionStatus;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.CompetitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@CrossOrigin(origins = "https://cycling-manager-frontend-psi.vercel.app")
@RequestMapping("/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private JwtHelper jwtHelper;

    @GetMapping("/scrape/{competitionId}")
    public CompetitionDTO scrapeCompetition(@PathVariable Long competitionId) {
        return competitionService.scrapeCompetitionStages(competitionId);
    }

    @GetMapping("/all")
    public List<Competition> getAllCompetitions() {
        return competitionService.getAllCompetitions();
    }

    @GetMapping("/results/all")
    public Boolean getResultsForAllCompetitions() {
        return competitionService.getResultsForAllCompetitions();
    }

    @GetMapping("/results/{competitionId}")
    public Boolean getResults(@PathVariable Long competitionId) throws IOException {
        return competitionService.getResults(competitionId);
    }

    @PostMapping()
    public CompetitionDTO createCompetition(@RequestBody @Valid CreateCompetitionData competition) {
        return competitionService.createCompetition(competition);
    }

    @GetMapping("/{id}")
    public CompetitionDTO getCompetition(@RequestHeader(name = "Authorization") String token, @PathVariable Long id) {
        return competitionService.getCompetitionById(id);
    }

    @GetMapping()
    public Set<CompetitionDTO> getCompetitions(@RequestHeader(name = "Authorization") String token) {
        String username = jwtHelper.getUsernameFromToken(token.substring(7));
        return competitionService.getCompetitions(username);
    }

    @PutMapping("/{id}")
    public StatusNotification updateCompetitionStatus(@RequestHeader(name = "Authorization") String token,
            @PathVariable Long id, @RequestParam(required = true) String status) {
        CompetitionStatus inputStatus = CompetitionStatus.valueOf(status);
        return competitionService.updateCompetitionStatus(inputStatus, id);
    }

}
