package ucll.be.procyclingscraper.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.CyclistAssignmentDTO;
import ucll.be.procyclingscraper.dto.CyclistDTO;
import ucll.be.procyclingscraper.dto.PickNotification;
import ucll.be.procyclingscraper.dto.UpdateUserTeamDTO;
import ucll.be.procyclingscraper.dto.UserTeamDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.CyclistAssignment;
import ucll.be.procyclingscraper.model.CyclistRole;
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
        CyclistService cyclistService;

        @Autowired
        UserService userService;

        @Autowired
        private CompetitionRepository competitionRepository;

        public List<UserTeamDTO> getTeams() {
                List<UserTeam> teams = userTeamRepository.findAll();
                return teams.stream()
                                .map(this::mapToUserTeamDTO)
                                .collect(Collectors.toList());
        }

        public List<CyclistDTO> getCyclistsWithDNS(Long competitionId) {
                List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);
                Competition competition = competitionRepository.findById(competitionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Competition not found with id: " + competitionId));

                // Flatten all stage results in this competition
                List<StageResult> stageResults = competition.getRaces().stream()
                                .flatMap(race -> race.getStages().stream())
                                .flatMap(stage -> stage.getResults().stream())
                                .toList();

                // Get all active MAIN cyclists from assignments
                Set<Cyclist> activeMainCyclists = userTeams.stream()
                                .flatMap(userTeam -> userTeam.getCyclistAssignments().stream())
                                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                                .map(CyclistAssignment::getCyclist)
                                .collect(Collectors.toSet());
// 
                // Find all active main cyclists that had a DNF/DQS/DNS
                Set<Cyclist> cyclistsWithDNS = stageResults.stream()
                                .filter(sr -> activeMainCyclists.contains(sr.getCyclist()))
                                .filter(sr -> {
                                        String pos = sr.getPosition();
                                        return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos)
                                                        || "OTL".equals(pos);
                                })
                                .map(StageResult::getCyclist)
                                .collect(Collectors.toSet());

                // Map to DTOs with reason
                List<CyclistDTO> cyclistDTOs = cyclistsWithDNS.stream()
                                .map(cyclist -> {
                                        String dnsReason = stageResults.stream()
                                                        .filter(sr -> sr.getCyclist().equals(cyclist))
                                                        .map(StageResult::getPosition)
                                                        .filter(pos -> "DNF".equals(pos) || "DQS".equals(pos)
                                                                        || "DNS".equals(pos))
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

        public List<UserTeam> updateUserTeam(Long userTeamId, String email, UpdateUserTeamDTO updateUserTeamDTO,
                        int currentStage) {

                UserTeam userTeam = userTeamRepository.findById(userTeamId)
                                .orElseThrow(() -> new RuntimeException("User team not found with ID: " + userTeamId));

                List<String> updatedMainIds = updateUserTeamDTO.getMainCyclistIds();
                List<String> updatedReserveIds = updateUserTeamDTO.getReserveCyclistIds();

                Set<Long> updatedMain = updatedMainIds.stream().map(Long::parseLong).collect(Collectors.toSet());
                Set<Long> updatedReserve = updatedReserveIds.stream().map(Long::parseLong).collect(Collectors.toSet());

                List<CyclistAssignment> existingAssignments = userTeam.getCyclistAssignments();

                // 1. Mark current assignments as expired if they're not in the updated lists
                for (CyclistAssignment assignment : existingAssignments) {
                        if (assignment.getToEvent() == null) {
                                Long cyclistId = assignment.getCyclist().getId();
                                boolean stillInMain = updatedMain.contains(cyclistId)
                                                && assignment.getRole() == CyclistRole.MAIN;
                                boolean stillInReserve = updatedReserve.contains(cyclistId)
                                                && assignment.getRole() == CyclistRole.RESERVE;

                                if (!(stillInMain || stillInReserve)) {
                                        assignment.setToEvent(currentStage - 1);
                                }
                        }
                }

                // 2. Add new assignments if the cyclist is new or their role changed
                for (Long cyclistId : updatedMain) {
                        boolean alreadyAssigned = existingAssignments.stream()
                                        .anyMatch(a -> a.getCyclist().getId().equals(cyclistId)
                                                        && a.getRole() == CyclistRole.MAIN
                                                        && a.getFromEvent() == currentStage);

                        if (!alreadyAssigned) {
                                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                                .orElseThrow(() -> new RuntimeException(
                                                                "Cyclist not found: " + cyclistId));

                                CyclistAssignment newAssignment = CyclistAssignment.builder()
                                                .userTeam(userTeam)
                                                .cyclist(cyclist)
                                                .role(CyclistRole.MAIN)
                                                .fromEvent(currentStage)
                                                .toEvent(null)
                                                .build();

                                existingAssignments.add(newAssignment);
                        }
                }

                for (Long cyclistId : updatedReserve) {
                        boolean alreadyAssigned = existingAssignments.stream()
                                        .anyMatch(a -> a.getCyclist().getId().equals(cyclistId)
                                                        && a.getRole() == CyclistRole.RESERVE
                                                        && a.getFromEvent() == currentStage);

                        if (!alreadyAssigned) {
                                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                                .orElseThrow(() -> new RuntimeException(
                                                                "Cyclist not found: " + cyclistId));

                                CyclistAssignment newAssignment = CyclistAssignment.builder()
                                                .userTeam(userTeam)
                                                .cyclist(cyclist)
                                                .role(CyclistRole.RESERVE)
                                                .fromEvent(currentStage)
                                                .toEvent(null)
                                                .build();

                                existingAssignments.add(newAssignment);
                        }
                }

                userTeamRepository.save(userTeam);
                return List.of(userTeam);
        }

        @Transactional
        public PickNotification addCyclistToUserTeam(String email, Long cyclistId, Long competitionId) {
                User user = userRepository.findUserByEmail(email);

                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));

                Competition competition = competitionRepository.findById(competitionId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Competition not found with ID: " + competitionId));

                Long currentPick = competition.getCurrentPick();

                CompetitionPick currentCompetitionPick = competition.getCompetitionPicks()
                                .stream()
                                .filter(pick -> pick.getPickOrder().equals(currentPick))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException(
                                                "Current pick not found for competition ID: " + competitionId));

                if (!currentCompetitionPick.getUserId().equals(user.getId())) {
                        throw new RuntimeException("It's not your turn to pick");
                }

                // Validate user participation
                if (user.getCompetitions().stream().noneMatch(c -> c.getId().equals(competitionId))) {
                        throw new RuntimeException("User not in competition");
                }

                // Find user's team
                UserTeam userTeam = user.getUserTeams().stream()
                                .filter(team -> team.getCompetitionId().equals(competitionId))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException(
                                                "User does not have a team in this competition"));

                // Prevent duplicate picks
                for (User otherUser : competition.getUsers()) {
                        for (UserTeam otherTeam : otherUser.getUserTeams()) {
                                if (!otherTeam.getCompetitionId().equals(competitionId))
                                        continue;

                                boolean alreadyPicked = otherTeam.getCyclistAssignments().stream()
                                                .anyMatch(a -> a.getCyclist().getId().equals(cyclistId)
                                                                && a.getToEvent() == null);

                                if (alreadyPicked) {
                                        throw new RuntimeException("Cyclist is already in a team in this competition");
                                }
                        }
                }

                // Count current assignments
                long currentMain = userTeam.getCyclistAssignments().stream()
                                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                                .count();

                long currentReserve = userTeam.getCyclistAssignments().stream()
                                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToEvent() == null)
                                .count();

                int maxMain = competition.getMaxMainCyclists();
                int maxReserve = competition.getMaxReserveCyclists();

                if (currentMain + currentReserve >= maxMain + maxReserve) {
                        throw new RuntimeException("User already has max number of cyclists in their team");
                }

                CyclistRole roleToAssign;
                if (currentMain < maxMain) {
                        roleToAssign = CyclistRole.MAIN;
                } else if (currentReserve < maxReserve) {
                        roleToAssign = CyclistRole.RESERVE;
                } else {
                        throw new RuntimeException("No available slot for this cyclist");
                }

                System.out.println("current stage: " + competition.getCurrentStage());

                // Create new assignment
                CyclistAssignment assignment = CyclistAssignment.builder()
                                .cyclist(cyclist)
                                .userTeam(userTeam)
                                .fromEvent(competition.getCurrentStage())
                                .toEvent(null)
                                .role(roleToAssign)
                                .build();

                userTeam.getCyclistAssignments().add(assignment);
                userTeamRepository.save(userTeam);

                // Move to next pick
                int totalUsers = competition.getUsers().size();
                competition.setCurrentPick((currentPick >= totalUsers) ? 1L : currentPick + 1);
                competitionRepository.save(competition);

                // Return result
                return new PickNotification(cyclist.getName(), cyclist.getId(), email, competition.getCurrentPick());
        }

        private UserTeamDTO mapToUserTeamDTO(UserTeam userTeam) {
                List<CyclistAssignmentDTO> assignments = userTeam.getCyclistAssignments().stream()
                                .map(assignment -> new CyclistAssignmentDTO(
                                                assignment.getId(),
                                                cyclistService.mapToCyclistDTO(assignment.getCyclist()),
                                                assignment.getRole(),
                                                assignment.getFromEvent(),
                                                assignment.getToEvent()))
                                .collect(Collectors.toList());

                return new UserTeamDTO(
                                userTeam.getId(),
                                userTeam.getName(),
                                userTeam.getCompetitionId(),
                                assignments,
                                userService.mapToUserDTO(userTeam.getUser()));
        }

}
