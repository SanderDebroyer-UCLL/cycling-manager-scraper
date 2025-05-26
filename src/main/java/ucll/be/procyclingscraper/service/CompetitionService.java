package ucll.be.procyclingscraper.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.CountNotification;
import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.dto.OrderNotification;
import ucll.be.procyclingscraper.dto.StatusNotification;
import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.CompetitionStatus;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.UserRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@Service
public class CompetitionService {

    @Autowired
    private UserTeamRepository userTeamRepository;

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

    public Set<Competition> getCompetitions(String email) {
        User currentUser = userRepository.findUserByEmail(email);
        Set<Competition> competitions = currentUser.getCompetitions();
        return competitions;
    }

    public Competition getCompetitionById(Long id) {
        return competitionRepository.findById(id).orElse(null);
    }

    @Transactional
    public CountNotification handleCyclistCount(int maxMainCyclists, int maxReserveCyclists, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));
        if (maxMainCyclists < 0 || maxReserveCyclists < 0) {
            throw new IllegalArgumentException("Cyclist counts must be non-negative");
        }

        competition.setMaxMainCyclists(maxMainCyclists);
        competition.setMaxReserveCyclists(maxReserveCyclists);

        competitionRepository.save(competition);

        CountNotification notification = new CountNotification();
        notification.setMaxMainCyclists(maxMainCyclists);
        notification.setMaxReserveCyclists(maxReserveCyclists);
        return notification;
    }

    @Transactional
    public StatusNotification updateCompetitionStatus(CompetitionStatus status, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        competition.setCompetitionStatus(status);
        competitionRepository.save(competition);
        StatusNotification notification = new StatusNotification();
        notification.setStatus(status);
        return notification;
    }

    @Transactional
    public OrderNotification updateOrderToCompetition(List<UserDTO> users, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        // Clear existing picks if you want to replace them
        competition.getCompetitionPicks().clear();

        // Assign pick order starting from 1
        Long pickOrder = 1L;
        for (UserDTO user : users) {
            CompetitionPick pick = new CompetitionPick();
            pick.setCompetition(competition);
            pick.setUserId(user.getId());
            pick.setPickOrder(pickOrder++);
            competition.getCompetitionPicks().add(pick);
        }

        // Persist the competition with updated pick order
        competitionRepository.save(competition);

        // Notify frontend/system (if needed)
        OrderNotification notification = new OrderNotification();
        notification.setCompetitionPicks(new java.util.HashSet<>(competition.getCompetitionPicks())); // assuming it accepts a set
        return notification;
    }

public Competition createCompetition(CreateCompetitionData competitionData) {

    System.out.println("Received competitionData: " + competitionData);

    Competition existingCompetition = competitionRepository.findByName(competitionData.getName());
    if (existingCompetition != null) {
        throw new IllegalArgumentException("Competition with this name already exists");
    }

    Competition competition = new Competition(competitionData.getName());

    // First save the competition (to get its ID if needed)
    competition = competitionRepository.save(competition);

    competition.setCompetitionStatus(CompetitionStatus.SORTING);
    competition.setCurrentPick(1L);
    competition.setMaxMainCyclists(15);
    competition.setMaxReserveCyclists(5);

    Long pickOrder = 1L; // initialize pick order for users

    for (String email : competitionData.getUserEmails()) {
        User user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("User with email " + email + " not found.");
        } else {
            competition.getUsers().add(user);

            // Create a CompetitionPick for the user
            CompetitionPick pick = new CompetitionPick();
            pick.setCompetition(competition);
            pick.setUserId(user.getId());
            pick.setPickOrder(pickOrder++);
            competition.getCompetitionPicks().add(pick);

            // Create a user team for this user
            UserTeam userTeam = UserTeam.builder()
                .name(user.getFirstName() + " " + user.getLastName() + "'s Team") // Or any naming logic
                .competitionId(competition.getId())
                .user(user)
                .mainCyclists(new ArrayList<>())      // Empty initial list
                .reserveCyclists(new ArrayList<>())    // Empty initial list
                .build();

            userTeamRepository.save(userTeam);
        }
    }

    for (String raceId : competitionData.getRaceIds()) {
        Race race = raceRepository.findRaceById(Integer.parseInt(raceId));

        if (race == null) {
            throw new IllegalArgumentException("Race with ID " + raceId + " not found.");
        } else {
            competition.getRaces().add(race);
        }
    }

    // Save updated competition with users, races, and picks
    return competitionRepository.save(competition);
}
}
