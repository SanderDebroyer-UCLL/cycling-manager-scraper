package ucll.be.procyclingscraper.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;

import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.service.CompetitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;

    @GetMapping()
    public List<Competition> getCompetitions() {
        return competitionService.getAllCompetitions();
    }

    @PostMapping("/create-competition")
    public Competition createCompetition(@RequestBody @Valid CreateCompetitionData competition) {
        return competitionService.createCompetition(competition);
    }
    
    
    

}
