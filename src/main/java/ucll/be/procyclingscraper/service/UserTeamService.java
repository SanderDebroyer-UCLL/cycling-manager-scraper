package ucll.be.procyclingscraper.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.UserRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@Service
public class UserTeamService {

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private CyclistRepository cyclistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    public List<UserTeam> getTeams() {
        // This method should return the list of user teams.
        // For now, we will return an empty list.
        return userTeamRepository.findAll();
    }

    @Transactional
    public PickNotification addCyclistToUserTeam(String email, Long cyclistId, Long competitionId) {
        // Fetch core entities
        User user = userRepository.findUserByEmail(email);

        Cyclist cyclist = cyclistRepository.findById(cyclistId)
            .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new RuntimeException("Competition not found with ID: " + competitionId));

        Long currentPick = competition.getCurrentPick();

        CompetitionPick currentCompetitionPick = competition.getCompetitionPicks()
            .stream()
            .filter(pick -> pick.getPickOrder().equals(currentPick))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Current pick not found for competition ID: " + competitionId));

        if (!currentCompetitionPick.getUserId().equals(user.getId())) {
            throw new RuntimeException("It's not your turn to pick");
        }

        // Check user is in competition
        if (user.getCompetitions().stream().noneMatch(c -> c.getId().equals(competitionId))) {
            throw new RuntimeException("User not in competition");
        }

        // Find user's team in this competition
        UserTeam userTeam = user.getUserTeams().stream()
            .filter(team -> team.getCompetitionId().equals(competitionId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("User does not have a team in this competition"));

        // Check if the cyclist is already picked by any user in this competition
        for (User otherUser : competition.getUsers()) {
            for (UserTeam otherTeam : otherUser.getUserTeams()) {
                if (otherTeam.getCompetitionId().equals(competitionId) &&
                    otherTeam.getCyclists().contains(cyclist)) {
                    throw new RuntimeException("Cyclist is already in a team in this competition");
                }
            }
        }

        if (userTeam.getCyclists().size() == 20) {
            // Check if the user has already picked a cyclist in this competition
            throw new RuntimeException("User has already picked a cyclist in this competition");
        }
        // Add cyclist to the user's team
        userTeam.getCyclists().add(cyclist);
        userTeamRepository.save(userTeam);

        // Update current pick
        int totalUsers = competition.getUsers().size();
        competition.setCurrentPick((currentPick >= totalUsers) ? 1L : currentPick + 1);
        competitionRepository.save(competition);

        // Return pick notification
        PickNotification notification = new PickNotification();
        notification.setCyclistName(cyclist.getName());
        notification.setEmail(email);
        return notification;
    }
}
