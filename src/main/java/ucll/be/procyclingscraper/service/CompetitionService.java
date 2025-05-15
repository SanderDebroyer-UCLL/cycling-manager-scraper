package ucll.be.procyclingscraper.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.validation.Valid;
import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.UserRepository;

@Service
public class CompetitionService {

    @Autowired
    private  UserRepository userRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private RaceRepository raceRepository;

    CompetitionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Competition> getAllCompetitions() {
        return competitionRepository.findAll();
    }

    public Competition createCompetition(CreateCompetitionData competitionData) {

        Competition existingComepetition = competitionRepository.findByName(competitionData.getName());
        if (existingComepetition != null) {
            throw new IllegalArgumentException("Competition with this name already exists");
        }

        Competition competition = new Competition(competitionData.getName());

        for (String name : competitionData.getUsernames()) {
            User user = userRepository.findUserByName(name);
            if (user == null) {
                throw new IllegalArgumentException("User with this ID already exists in the competition");
            } else {
                competition.getUsers().add(user);
            }
        }

        for (String raceId : competitionData.getRaceIds()) {
            Race race = raceRepository.findRaceById(Integer.parseInt(raceId));

            if (race == null) {
                throw new IllegalArgumentException("Race with this ID already exists in the competition");
            } else {
                competition.getRaces().add(race);
            }
        }

        return competitionRepository.save(competition);
    }
    
}
