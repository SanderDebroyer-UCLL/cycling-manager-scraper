package ucll.be.procyclingscraper.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserPerCyclistDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.CyclistAssignment;
import ucll.be.procyclingscraper.model.CyclistRole;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.model.RaceResult;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.RacePointsRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@Service
public class RacePointsService {

    @Autowired
    private RacePointsRepository racePointsRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private CyclistRepository cyclistRepository;

    public void createRacePointsForAllExistingResults() {
        List<Competition> competitions = competitionRepository.findAll();
        for (Competition competition : competitions) {
            Set<Race> races = competition.getRaces();
            if (races == null)
                continue;
            for (Race race : races) {
                if (!race.getStages().isEmpty())
                    continue;
                createRacePoints(competition.getId(), race.getId());
            }
        }
    }

    public List<RacePoints> createRacePoints(Long competitionId, Long raceId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new IllegalArgumentException("Race not found with id: " + raceId));

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        // 1. Get all races in competition and sort by start_date
        List<Race> allRaces = new ArrayList<>(competition.getRaces());
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");

        List<Race> orderedRaces = allRaces.stream()
                .sorted(Comparator.comparing((Race r) -> {
                    try {
                        return LocalDate.parse(r.getStartDate(), inputFormatter);
                    } catch (DateTimeParseException e) {
                        // Handle invalid dates gracefully
                        return LocalDate.MIN; // or throw exception based on requirements
                    }
                }))
                .collect(Collectors.toList());

        // 2. Determine current race number from ordered list (1-based index)
        int tempRaceNumber = -1;
        for (int i = 0; i < orderedRaces.size(); i++) {
            if (orderedRaces.get(i).getId().equals(raceId)) {
                tempRaceNumber = i + 1;
                break;
            }
        }

        if (tempRaceNumber == -1) {
            throw new IllegalStateException("Race ID not found in competition races.");
        }

        final int currentRaceNumber = tempRaceNumber;

        // Get user teams
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        // Fetch cyclists with race results
        List<Cyclist> resultCyclists = cyclistRepository.findCyclistsByRaceId(raceId);

        List<RacePoints> allRacePoints = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            List<Cyclist> teamCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(a -> a.getRole() == CyclistRole.MAIN && isCyclistActiveInRace(a, currentRaceNumber))
                    .map(CyclistAssignment::getCyclist)
                    .toList();

            for (Cyclist cyclist : resultCyclists) {
                if (teamCyclists.stream().noneMatch(c -> c.getId().equals(cyclist.getId()))) {
                    continue;
                }

                Optional<RaceResult> resultOption = cyclist.getRaceResults().stream()
                        .filter(r -> r.getRace().getId().equals(raceId))
                        .findFirst();

                int points;
                int position = 0;

                if (resultOption.isPresent()) {
                    RaceResult result = resultOption.get();
                    if ("OTL".equals(result.getPosition()) ||
                            "DNF".equals(result.getPosition()) ||
                            "DQS".equals(result.getPosition()) ||
                            "DNS".equals(result.getPosition())) {
                        continue; // Skip these results
                    }
                    position = Integer.parseInt(result.getPosition());
                    points = calculateRacePoints(position); // You'll need to implement this method
                } else {
                    points = 0;
                }

                if (points <= 0) {
                    continue;
                }

                RaceResult matchingRaceResult = cyclist.getRaceResults().stream()
                        .filter(rr -> rr.getRace().equals(race))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "RaceResult not found for cyclist " + cyclist.getName()));

                String reason = position + "e";

                boolean exists = racePointsRepository.existsByRaceIdAndReason(raceId, reason);
                if (exists) {
                    continue;
                }

                RacePoints racePoints = RacePoints.builder()
                        .competition(competition)
                        .raceResult(matchingRaceResult)
                        .value(points)
                        .reason(reason)
                        .raceId(raceId)
                        .user(userTeam.getUser())
                        .build();

                racePointsRepository.save(racePoints);
                matchingRaceResult.getRacePoints().add(racePoints);
                allRacePoints.add(racePoints);
            }
        }

        return allRacePoints;
    }

    public MainReserveCyclistPointsDTO getRacePointsForUserPerCyclist(Long competitionId, Long userId,
            Long raceId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        List<RacePoints> racePointsList = racePointsRepository.findByCompetition_idAndRaceResult_Race_id(
                competitionId, raceId);

        List<PointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<RaceResult> raceResults = competition.getRaces().stream()
                .flatMap(race -> race.getRaceResult().stream())
                .toList();

        Set<Cyclist> toBeRemovedCyclists = raceResults.stream()
                .filter(raceResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getRole() == CyclistRole.MAIN
                                && a.getToEvent() == null
                                && a.getCyclist().equals(raceResult.getCyclist())))
                .filter(raceResult -> {
                    String pos = raceResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos) || "OTL".equals(pos);
                })
                .map(RaceResult::getCyclist)
                .collect(Collectors.toSet());

        List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                .toList();

        List<CyclistAssignment> reserveAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToEvent() == null)
                .toList();

        CompletableFuture<Void> mainCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : mainAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<RacePoints> cyclistRacePoints = racePointsList.stream()
                        .filter(rp -> rp.getRaceResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                if (!cyclistRacePoints.isEmpty()) {
                    int totalPoints = cyclistRacePoints.stream().mapToInt(RacePoints::getValue).sum();

                    synchronized (mainCyclists) {
                        mainCyclists.add(new PointsPerUserPerCyclistDTO(
                                totalPoints, cyclist.getName(), cyclist.getId(), null, isCyclistActive,
                                userTeam.getUser().getId()));
                    }
                }
            }
        });

        CompletableFuture<Void> reserveCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : reserveAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<RacePoints> cyclistRacePoints = racePointsList.stream()
                        .filter(rp -> rp.getRaceResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                if (!cyclistRacePoints.isEmpty()) {
                    int totalPoints = cyclistRacePoints.stream().mapToInt(RacePoints::getValue).sum();

                    synchronized (reserveCyclists) {
                        reserveCyclists.add(new PointsPerUserPerCyclistDTO(
                                totalPoints, cyclist.getName(), cyclist.getId(), null, isCyclistActive,
                                userTeam.getUser().getId()));
                    }
                }
            }
        });

        CompletableFuture.allOf(mainCyclistsFuture, reserveCyclistsFuture).join();

        return new MainReserveCyclistPointsDTO(mainCyclists, reserveCyclists);
    }

    public MainReserveCyclistPointsDTO getRacePointsForRace(Long competitionId, Long raceId) {
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        List<User> users = userTeams.stream()
                .map(UserTeam::getUser)
                .distinct()
                .collect(Collectors.toList());

        System.out.println("Found " + users.size() + " users for competition " + competitionId);

        // Only race points relevant to the race
        List<RacePoints> racePointsList = users.stream()
                .peek(user -> System.out.println("User: " + user.getUsername()))
                .flatMap(user -> user.getRacePoints().stream())
                .filter(rp -> raceId.equals(rp.getRaceId()))
                .collect(Collectors.toList());

        System.out.println("Found " + racePointsList.size() + " race points for race " + raceId);

        // Create a map for efficient lookup: userId -> cyclistId -> RacePoints
        Map<Long, Map<Long, List<RacePoints>>> userCyclistPointsMap = racePointsList.stream()
                .collect(Collectors.groupingBy(
                        rp -> rp.getUser().getId(),
                        Collectors.groupingBy(rp -> rp.getRaceResult().getCyclist().getId())));

        List<PointsPerUserPerCyclistDTO> allMainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> allReserveCyclists = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            User user = userTeam.getUser();
            Long userId = user.getId();

            // Get user's points map for efficient lookup
            Map<Long, List<RacePoints>> cyclistPointsMap = userCyclistPointsMap.getOrDefault(userId,
                    Collections.emptyMap());

            List<PointsPerUserPerCyclistDTO> mainCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.MAIN)
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createPointsDTO(cyclist, cyclistPointsMap, userId, true))
                    .filter(dto -> dto.getPoints() > 0) // Only show cyclists with points
                    .toList();

            List<PointsPerUserPerCyclistDTO> reserveCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.RESERVE) // Fixed: was MAIN, should be RESERVE
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createPointsDTO(cyclist, cyclistPointsMap, userId, false))
                    .filter(dto -> dto.getPoints() > 0) // Only show cyclists with points
                    .toList();

            allMainCyclists.addAll(mainCyclists);
            allReserveCyclists.addAll(reserveCyclists);
        }

        return new MainReserveCyclistPointsDTO(allMainCyclists, allReserveCyclists);
    }

    private PointsPerUserPerCyclistDTO createPointsDTO(Cyclist cyclist,
            Map<Long, List<RacePoints>> cyclistPointsMap,
            Long userId,
            boolean isMain) {
        List<RacePoints> cyclistPoints = cyclistPointsMap.getOrDefault(cyclist.getId(), Collections.emptyList());

        int points = cyclistPoints.stream()
                .mapToInt(RacePoints::getValue)
                .sum();

        String reason = cyclistPoints.stream()
                .map(RacePoints::getReason)
                .collect(Collectors.joining(", "));

        return new PointsPerUserPerCyclistDTO(
                points,
                cyclist.getName(),
                cyclist.getId(),
                reason.isEmpty() ? null : reason,
                isMain,
                userId);
    }

    public List<PointsPerUserDTO> getAllRacePointsForAllUsers(Long competitionId) {
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        List<PointsPerUserDTO> result = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            User user = userTeam.getUser();
            Long userId = user.getId();
            String fullName = user.getFirstName() + " " + user.getLastName();

            MainReserveCyclistPointsDTO racePointsList = getAllRacePoints(competitionId, userId);

            int totalPoints = 0;
            for (PointsPerUserPerCyclistDTO cyclist : racePointsList.getMainCyclists()) {
                totalPoints += cyclist.getPoints(); // Assuming there's a getPoints() method
            }

            for (PointsPerUserPerCyclistDTO cyclist : racePointsList.getReserveCyclists()) {
                totalPoints += cyclist.getPoints();
            }

            result.add(new PointsPerUserDTO(totalPoints, fullName, userId));
        }

        return result;
    }

    public MainReserveCyclistPointsDTO getAllRacePoints(Long competitionId, Long userId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<RaceResult> raceResults = competition.getRaces().stream()
                .flatMap(race -> race.getRaceResult().stream())
                .toList();

        Set<RacePoints> racePoints = competition.getRacePoints();

        // Active Cyclist Assignments
        List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                .toList();

        List<CyclistAssignment> reserveAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToEvent() == null)
                .toList();

        // Determine DNF/DNS/DQS cyclists
        Set<Cyclist> toBeRemovedCyclists = raceResults.stream()
                .filter(raceResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getToEvent() == null && a.getCyclist().equals(raceResult.getCyclist())))
                .filter(raceResult -> {
                    String pos = raceResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos) || "OTL".equals(pos);
                })
                .map(RaceResult::getCyclist)
                .collect(Collectors.toSet());

        List<PointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        CompletableFuture<Void> mainCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : mainAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<RacePoints> cyclistRacePoints = racePoints.stream()
                        .filter(rp -> rp.getRaceResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                int totalPoints = cyclistRacePoints.stream().mapToInt(RacePoints::getValue).sum();

                synchronized (mainCyclists) {
                    mainCyclists.add(new PointsPerUserPerCyclistDTO(
                            totalPoints,
                            cyclist.getName(),
                            cyclist.getId(),
                            null, isCyclistActive,
                            userTeam.getUser().getId()));
                }
            }
        });

        CompletableFuture<Void> reserveCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : reserveAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<RacePoints> cyclistRacePoints = racePoints.stream()
                        .filter(rp -> rp.getRaceResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                int totalPoints = cyclistRacePoints.stream().mapToInt(RacePoints::getValue).sum();

                synchronized (reserveCyclists) {
                    reserveCyclists.add(new PointsPerUserPerCyclistDTO(
                            totalPoints,
                            cyclist.getName(),
                            cyclist.getId(),
                            null, isCyclistActive,
                            userTeam.getUser().getId()));
                }
            }
        });

        CompletableFuture.allOf(mainCyclistsFuture, reserveCyclistsFuture).join();

        return new MainReserveCyclistPointsDTO(mainCyclists, reserveCyclists);
    }

    private boolean isCyclistActiveInRace(CyclistAssignment assignment, int raceNumber) {
        if (assignment.getFromEvent() == null && assignment.getToEvent() == null) {
            return false;
        }

        if (assignment.getFromEvent() != null && raceNumber < assignment.getFromEvent()) {
            return false;
        }

        if (assignment.getToEvent() != null && raceNumber > assignment.getToEvent()) {
            return false;
        }

        return true;
    }

    private int calculateRacePoints(int position) {
        switch (position) {
            case 1:
                return 100;
            case 2:
                return 80;
            case 3:
                return 65;
            case 4:
                return 55;
            case 5:
                return 45;
            case 6:
                return 35;
            case 7:
                return 30;
            case 8:
                return 25;
            case 9:
                return 20;
            case 10:
                return 17;
            case 11:
                return 15;
            case 12:
                return 14;
            case 13:
                return 13;
            case 14:
                return 12;
            case 15:
                return 11;
            case 16:
                return 10;
            case 17:
                return 9;
            case 18:
                return 8;
            case 19:
                return 7;
            case 20:
                return 6;
            case 21:
                return 5;
            case 22:
                return 4;
            case 23:
                return 3;
            case 24:
                return 2;
            case 25:
                return 1;
            default:
                return 0;
        }
    }
}
