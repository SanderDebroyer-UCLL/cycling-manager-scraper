package ucll.be.procyclingscraper.service;

import java.util.ArrayList;
import java.util.HashSet;
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
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RaceResult;
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

                // Collect stage results and race results separately
                List<StageResult> stageResults = new ArrayList<>();
                List<RaceResult> raceResults = new ArrayList<>();

                for (Race race : competition.getRaces()) {
                        // Add stage results if stages exist
                        if (race.getStages() != null && !race.getStages().isEmpty()) {
                                race.getStages().stream()
                                                .flatMap(stage -> stage.getResults().stream())
                                                .forEach(stageResults::add);
                        }

                        // Add direct race results if they exist
                        if (race.getRaceResult() != null && !race.getRaceResult().isEmpty()) {
                                raceResults.addAll(race.getRaceResult());
                        }
                }

                // Get all active MAIN cyclists from assignments
                Set<Cyclist> activeMainCyclists = userTeams.stream()
                                .flatMap(userTeam -> userTeam.getCyclistAssignments().stream())
                                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                                .map(CyclistAssignment::getCyclist)
                                .collect(Collectors.toSet());

                // Find cyclists with DNS/DNF from stage results
                Set<Cyclist> cyclistsWithDNSFromStages = stageResults.stream()
                                .filter(sr -> activeMainCyclists.contains(sr.getCyclist()))
                                .filter(sr -> {
                                        String pos = sr.getPosition();
                                        if (pos == null)
                                                return false;
                                        pos = pos.toUpperCase().trim();
                                        return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos)
                                                        || "OTL".equals(pos);
                                })
                                .map(StageResult::getCyclist)
                                .collect(Collectors.toSet());

                // Find cyclists with DNS/DNF from race results
                Set<Cyclist> cyclistsWithDNSFromRaces = raceResults.stream()
                                .filter(rr -> activeMainCyclists.contains(rr.getCyclist()))
                                .filter(rr -> {
                                        String pos = rr.getPosition(); // Assuming RaceResult also has getPosition()
                                        if (pos == null)
                                                return false;
                                        pos = pos.toUpperCase().trim();
                                        return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos)
                                                        || "OTL".equals(pos);
                                })
                                .map(RaceResult::getCyclist)
                                .collect(Collectors.toSet());

                // Combine both sets
                Set<Cyclist> cyclistsWithDNS = new HashSet<>();
                cyclistsWithDNS.addAll(cyclistsWithDNSFromStages);
                cyclistsWithDNS.addAll(cyclistsWithDNSFromRaces);

                // Map to DTOs with reason (check both stage and race results)
                List<CyclistDTO> cyclistDTOs = cyclistsWithDNS.stream()
                                .map(cyclist -> {
                                        // First try to find reason in stage results
                                        String dnsReason = stageResults.stream()
                                                        .filter(sr -> sr.getCyclist().equals(cyclist))
                                                        .map(StageResult::getPosition)
                                                        .filter(pos -> {
                                                                if (pos == null)
                                                                        return false;
                                                                pos = pos.toUpperCase().trim();
                                                                return "DNF".equals(pos) || "DQS".equals(pos)
                                                                                || "DNS".equals(pos)
                                                                                || "OTL".equals(pos);
                                                        })
                                                        .findFirst()
                                                        .orElse("");

                                        // If not found in stage results, check race results
                                        if (dnsReason.isEmpty()) {
                                                dnsReason = raceResults.stream()
                                                                .filter(rr -> rr.getCyclist().equals(cyclist))
                                                                .map(RaceResult::getPosition)
                                                                .filter(pos -> {
                                                                        if (pos == null)
                                                                                return false;
                                                                        pos = pos.toUpperCase().trim();
                                                                        return "DNF".equals(pos) || "DQS".equals(pos)
                                                                                        || "DNS".equals(pos)
                                                                                        || "OTL".equals(pos);
                                                                })
                                                                .findFirst()
                                                                .orElse("");
                                        }

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

        public List<UserTeamDTO> updateUserTeam(Long userTeamId, String email, UpdateUserTeamDTO updateUserTeamDTO) {

                UserTeam userTeam = userTeamRepository.findById(userTeamId)
                                .orElseThrow(() -> new RuntimeException("User team not found with ID: " + userTeamId));

                List<String> updatedMainIds = updateUserTeamDTO.getMainCyclistIds();
                List<String> updatedReserveIds = updateUserTeamDTO.getReserveCyclistIds();

                Set<Long> updatedMain = updatedMainIds.stream().map(Long::parseLong).collect(Collectors.toSet());
                Set<Long> updatedReserve = updatedReserveIds.stream().map(Long::parseLong).collect(Collectors.toSet());

                Competition competition = competitionRepository.findById(userTeam.getCompetitionId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Competition not found with ID: " + userTeam.getCompetitionId()));

                Integer currentEvent = competition.getCurrentEvent();

                System.out.println("Current event: " + currentEvent);

                List<CyclistAssignment> existingAssignments = userTeam.getCyclistAssignments();

                // 1. Handle existing assignments - expire those not in updated lists or with
                // wrong role
                for (CyclistAssignment assignment : existingAssignments) {
                        if (assignment.getToEvent() == null) { // Only process active assignments
                                Long cyclistId = assignment.getCyclist().getId();
                                CyclistRole currentRole = assignment.getRole();

                                boolean shouldKeepAssignment = false;

                                if (currentRole == CyclistRole.MAIN && updatedMain.contains(cyclistId)) {
                                        shouldKeepAssignment = true;
                                } else if (currentRole == CyclistRole.RESERVE && updatedReserve.contains(cyclistId)) {
                                        shouldKeepAssignment = true;
                                }

                                if (!shouldKeepAssignment) {
                                        assignment.setToEvent(currentEvent);
                                }
                        }
                }

                // 2. Add new assignments for MAIN cyclists
                for (Long cyclistId : updatedMain) {
                        // Check if cyclist already has an active MAIN assignment
                        boolean hasActiveMainAssignment = existingAssignments.stream()
                                        .anyMatch(a -> a.getCyclist().getId().equals(cyclistId)
                                                        && a.getRole() == CyclistRole.MAIN
                                                        && a.getToEvent() == null);

                        if (!hasActiveMainAssignment) {
                                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                                .orElseThrow(() -> new RuntimeException(
                                                                "Cyclist not found: " + cyclistId));

                                CyclistAssignment newAssignment = CyclistAssignment.builder()
                                                .userTeam(userTeam)
                                                .cyclist(cyclist)
                                                .role(CyclistRole.MAIN)
                                                .fromEvent(currentEvent)
                                                .toEvent(null)
                                                .build();

                                existingAssignments.add(newAssignment);
                        }
                }

                // 3. Add new assignments for RESERVE cyclists
                for (Long cyclistId : updatedReserve) {
                        // Check if cyclist already has an active RESERVE assignment
                        boolean hasActiveReserveAssignment = existingAssignments.stream()
                                        .anyMatch(a -> a.getCyclist().getId().equals(cyclistId)
                                                        && a.getRole() == CyclistRole.RESERVE
                                                        && a.getToEvent() == null);

                        if (!hasActiveReserveAssignment) {
                                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                                .orElseThrow(() -> new RuntimeException(
                                                                "Cyclist not found: " + cyclistId));

                                CyclistAssignment newAssignment = CyclistAssignment.builder()
                                                .userTeam(userTeam)
                                                .cyclist(cyclist)
                                                .role(CyclistRole.RESERVE)
                                                .fromEvent(currentEvent + 1) // Reserve cyclists start from next event
                                                .toEvent(null)
                                                .build();

                                existingAssignments.add(newAssignment);
                        }
                }

                UserTeam savedUserTeam = userTeamRepository.save(userTeam);
                return List.of(mapToUserTeamDTO(savedUserTeam));
        }

        @Transactional
        public PickNotification addCyclistToUserTeam(String email, Long cyclistId, Long competitionId) {
                User user = userRepository.findUserByEmail(email);

                Cyclist cyclist = cyclistRepository.findById(cyclistId)
                                .orElseThrow(() -> new RuntimeException("Cyclist not found with ID: " + cyclistId));

                Competition competition = competitionRepository.findById(competitionId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Competition not found with ID: " + competitionId));

                final Long originalCurrentPick = competition.getCurrentPick();
                Long currentPick = originalCurrentPick;

                CompetitionPick currentCompetitionPick = competition.getCompetitionPicks()
                                .stream()
                                .filter(pick -> pick.getPickOrder().equals(originalCurrentPick))
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

                System.out.println("current stage: " + competition.getCurrentEvent());

                // Create new assignment
                CyclistAssignment assignment = CyclistAssignment.builder()
                                .cyclist(cyclist)
                                .userTeam(userTeam)
                                .fromEvent(roleToAssign == CyclistRole.MAIN ? 1 : null)
                                .toEvent(null)
                                .role(roleToAssign)
                                .build();

                userTeam.getCyclistAssignments().add(assignment);
                userTeamRepository.save(userTeam);

                // Move to next pick
                int totalUsers = competition.getUsers().size();
                Long currentRound = competition.getCurrentRound();

                if (currentRound == null)
                        currentRound = 1L;
                if (currentPick == null)
                        currentPick = 1L;

                boolean isAscending = currentRound % 2 == 1;
                Long nextPick;
                boolean endOfRound = false;

                if (isAscending) {
                        // Last user in ascending order
                        if (currentPick.equals((long) totalUsers)) {
                                endOfRound = true;
                        } else {
                                nextPick = currentPick + 1;
                                competition.setCurrentPick(nextPick);
                                competition.setCurrentRound(currentRound);
                                competitionRepository.save(competition);
                                return new PickNotification(cyclist.getName(), cyclist.getId(), email, nextPick);
                        }
                } else {
                        // First user in descending order
                        if (currentPick.equals(1L)) {
                                endOfRound = true;
                        } else {
                                nextPick = currentPick - 1;
                                competition.setCurrentPick(nextPick);
                                competition.setCurrentRound(currentRound);
                                competitionRepository.save(competition);
                                return new PickNotification(cyclist.getName(), cyclist.getId(), email, nextPick);
                        }
                }

                if (endOfRound) {
                        currentRound += 1;
                        nextPick = (currentRound % 2 == 1) ? 1L : (long) totalUsers;

                        competition.setCurrentRound(currentRound);
                        competition.setCurrentPick(nextPick);
                        competitionRepository.save(competition);
                }

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
