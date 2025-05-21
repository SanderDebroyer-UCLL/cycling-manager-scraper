package ucll.be.procyclingscraper.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
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

    public List<UserTeam> getTeams() {
        // This method should return the list of user teams.
        // For now, we will return an empty list.
        return userTeamRepository.findAll();
    }

    @Transactional
    public PickNotification addCyclistToUserTeam(String email, Long cyclistId, Long competitionId) {
        User user = userRepository.findUserByEmail(email);

        Cyclist cyclist = cyclistRepository.findById(cyclistId)
            .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));

        for (UserTeam team : user.getUserTeams()) {
            if (team.getCompetitionId() == competitionId) {
                if (team.getCyclists().contains(cyclist)) {
                    throw new RuntimeException("Cyclist already in team");
                }
                team.getCyclists().add(cyclist);
                userTeamRepository.save(team);
            }
        }  

        PickNotification notification = new PickNotification();
        notification.setCyclistName(cyclist.getName());
        notification.setEmail(email);

        return notification;
    }

    
}
