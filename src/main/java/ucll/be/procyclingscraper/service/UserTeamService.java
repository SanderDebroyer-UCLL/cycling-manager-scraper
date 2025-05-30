package ucll.be.procyclingscraper.service;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.CyclistDTO;
import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.dto.UpdateUserTeamDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.StageResult;
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

    public List<CyclistDTO> getCyclistsWithDNS(Long competitionId) {
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<StageResult> stageResults = competition.getRaces().stream().flatMap(race -> race.getStages().stream())
                .flatMap(stage -> stage.getResults().stream()).toList();

        Set<Cyclist> allMainCyclists = userTeams.stream()
                .flatMap(userTeam -> userTeam.getMainCyclists().stream())
                .collect(java.util.stream.Collectors.toSet());

        Set<Cyclist> cyclistsWithDNS = stageResults.stream()
                .filter(stageResult -> allMainCyclists.contains(stageResult.getCyclist()))
                .filter(stageResult -> {
                    String pos = stageResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos);
                })
                .map(StageResult::getCyclist)
                .collect(java.util.stream.Collectors.toSet());

        List<CyclistDTO> cyclistDTOs = cyclistsWithDNS.stream()
                .map(cyclist -> {
                    String dnsReason = stageResults.stream()
                            .filter(stageResult -> stageResult.getCyclist().equals(cyclist))
                            .filter(stageResult -> {
                                String pos = stageResult.getPosition();
                                return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos);
                            })
                            .map(StageResult::getPosition)
                            .findFirst()
                            .orElse("");

                    return new CyclistDTO(
                            cyclist.getId(),
                            cyclist.getName(),
                            cyclist.getRanking(),
                            cyclist.getAge(),
                            cyclist.getCountry(),
                            cyclist.getCyclistUrl(),
                            cyclist.getTeam(),
                            cyclist.getUpcomingRaces(),
                            dnsReason);
                })
                .toList();

        return cyclistDTOs;
    }

    public List<UserTeam> updateUserTeam(Long userTeamId, String email, UpdateUserTeamDTO updateUserTeamDTO) {
        UserTeam userTeam = userTeamRepository.findById(userTeamId)
                .orElseThrow(() -> new RuntimeException("User team not found with ID: " + userTeamId));

        List<String> mainCyclists = updateUserTeamDTO.getMainCyclistIds();
        List<String> reserveCyclists = updateUserTeamDTO.getReserveCyclistIds();

        // Remove main cyclists not in the new list
        userTeam.getMainCyclists().removeIf(cyclist -> !mainCyclists.contains(cyclist.getId().toString()));
        // Add new main cyclists
        for (String cyclistId : mainCyclists) {
            boolean alreadyPresent = userTeam.getMainCyclists().stream()
                    .anyMatch(c -> c.getId().toString().equals(cyclistId));
            if (!alreadyPresent) {
                Cyclist cyclist = cyclistRepository.findById(Long.parseLong(cyclistId))
                        .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));
                userTeam.getMainCyclists().add(cyclist);
            }
        }

        // Remove reserve cyclists not in the new list
        userTeam.getReserveCyclists().removeIf(cyclist -> !reserveCyclists.contains(cyclist.getId().toString()));
        // Add new reserve cyclists
        for (String cyclistId : reserveCyclists) {
            boolean alreadyPresent = userTeam.getReserveCyclists().stream()
                    .anyMatch(c -> c.getId().toString().equals(cyclistId));
            if (!alreadyPresent) {
                Cyclist cyclist = cyclistRepository.findById(Long.parseLong(cyclistId))
                        .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));
                userTeam.getReserveCyclists().add(cyclist);
            }
        }

        userTeamRepository.save(userTeam);
        return List.of(userTeam);
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
                        otherTeam.getMainCyclists().contains(cyclist)) {
                    throw new RuntimeException("Cyclist is already in a team in this competition");
                }
            }
        }

        if (userTeam.getMainCyclists().size() + userTeam.getReserveCyclists().size() >= competition.getMaxMainCyclists()
                + competition.getMaxReserveCyclists()) {
            // Check if the user has already picked a cyclist in this competition
            throw new RuntimeException(
                    "User already has " + (competition.getMaxMainCyclists() + competition.getMaxReserveCyclists())
                            + " cyclists in their team");
        }
        // Add cyclist to the user's team
        if (userTeam.getMainCyclists().size() < competition.getMaxMainCyclists()) {
            // Add to main cyclists if space is available
            userTeam.getMainCyclists().add(cyclist);
        } else if (userTeam.getReserveCyclists().size() < competition.getMaxReserveCyclists()) {
            // Otherwise, add to reserve cyclists
            userTeam.getReserveCyclists().add(cyclist);
        }
        userTeamRepository.save(userTeam);

        // Update current pick
        int totalUsers = competition.getUsers().size();
        competition.setCurrentPick((currentPick >= totalUsers) ? 1L : currentPick + 1);
        competitionRepository.save(competition);

        // Return pick notification
        PickNotification notification = new PickNotification(cyclist.getName(), cyclist.getId(), email,
                competition.getCurrentPick());
        return notification;
    }
}
