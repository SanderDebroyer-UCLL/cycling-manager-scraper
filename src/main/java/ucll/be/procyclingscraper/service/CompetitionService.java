package ucll.be.procyclingscraper.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.repository.CompetitionRepository;

@Service
public class CompetitionService {

    @Autowired
    private CompetitionRepository competitionRepository;

    public List<Competition> getAllCompetitions() {
        return competitionRepository.findAll();
    }
    
}
