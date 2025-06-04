package ucll.be.procyclingscraper.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserPerCyclistDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.CyclistAssignment;
import ucll.be.procyclingscraper.model.CyclistRole;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.model.StageResult;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.StagePointsRepository;
import ucll.be.procyclingscraper.repository.StageRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@Service
public class StagePointsService {

    @Autowired
    private StagePointsRepository stagePointsRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private CyclistRepository cyclistRepository;

    @Autowired
    private StageRepository stageRepository;

    public List<StagePoints> createStagePoints(Long competitionId, Long stageId) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Stage not found with id: " + stageId));

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<Stage> allStages = new ArrayList<>(stage.getRace().getStages());
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM");
        int defaultYear = LocalDate.now().getYear();

        List<Stage> orderedStages = allStages.stream()
                .sorted(Comparator.comparing((Stage s) -> {
                    try {
                        // Parse as MonthDay first, then convert to LocalDate
                        MonthDay monthDay = MonthDay.parse(s.getDate(), inputFormatter);
                        return monthDay.atYear(defaultYear);
                    } catch (DateTimeParseException e) {
                        // Handle invalid dates gracefully
                        return LocalDate.MIN; // or throw exception based on requirements
                    }
                }))
                .collect(Collectors.toList());

        // 2. Determine current stage number from ordered list (1-based index)
        int tempStageNumber = -1;
        for (int i = 0; i < orderedStages.size(); i++) {
            if (orderedStages.get(i).getId().equals(stageId)) {
                tempStageNumber = i + 1;
                break;
            }
        }

        if (tempStageNumber == -1) {
            throw new IllegalStateException("Stage ID not found in competition stages.");
        }

        final int currentStageNumber = tempStageNumber;

        // 3. Get user teams
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        // 4. Fetch cyclists by result type
        Map<ScrapeResultType, List<Cyclist>> resultTypeToCyclists = new HashMap<>();
        resultTypeToCyclists.put(ScrapeResultType.STAGE,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.STAGE.toString()));
        resultTypeToCyclists.put(ScrapeResultType.GC,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.GC.toString()));
        resultTypeToCyclists.put(ScrapeResultType.POINTS,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.POINTS.toString()));

        List<StagePoints> allStagePoints = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            List<Cyclist> teamCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(a -> isCyclistActiveInStage(a, currentStageNumber))
                    .map(CyclistAssignment::getCyclist)
                    .toList();

            for (Map.Entry<ScrapeResultType, List<Cyclist>> entry : resultTypeToCyclists.entrySet()) {
                ScrapeResultType resultType = entry.getKey();
                List<Cyclist> resultCyclists = entry.getValue();

                for (Cyclist cyclist : resultCyclists) {
                    if (teamCyclists.stream().noneMatch(c -> c.getId().equals(cyclist.getId()))) {
                        continue;
                    }

                    Optional<StageResult> resultOption = cyclist.getResults().stream()
                            .filter(r -> r.getStage().getId().equals(stageId) &&
                                    r.getScrapeResultType() == resultType)
                            .findFirst();

                    int points;
                    int position = 0;

                    if (resultOption.isPresent()) {
                        StageResult result = resultOption.get();
                        if ("OTL".equals(result.getPosition()) ||
                                "DNF".equals(result.getPosition()) ||
                                "DQS".equals(result.getPosition()) ||
                                "DNS".equals(result.getPosition())) {
                            continue; // Skip these results
                        }
                        position = Integer.parseInt(result.getPosition());
                        points = calculatePoints(resultType, position);

                        System.out.println("Calculated points for cyclist " + cyclist.getName() +
                                " in stage " + stage.getName() + ": " + points);

                    } else {
                        points = 0;
                    }

                    if (points <= 0) {
                        continue;
                    }

                    StageResult matchingStageResult = cyclist.getResults().stream()
                            .filter(sr -> sr.getScrapeResultType() == resultType &&
                                    sr.getStage().equals(stage))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "StageResult not found for cyclist " + cyclist.getName()));

                    String reason = position + "e plaats in " + getResultTypeInDutch(resultType);

                    boolean exists = stagePointsRepository.existsByStageIdAndReason(stageId, reason);
                    System.out.println("Checking existence for reason: '" + reason + "'");
                    System.out.println("StageResult ID: " + matchingStageResult.getId());
                    System.out.println("Exists: "
                            + stagePointsRepository.existsByStageIdAndReason(stageId, reason));

                    if (exists) {
                        continue;
                    }

                    StagePoints stagePoints = StagePoints.builder()
                            .competition(competition)
                            .stageResult(matchingStageResult)
                            .value(points)
                            .reason(reason)
                            .stageId(stageId)
                            .user(userTeam.getUser())
                            .build();

                    stagePointsRepository.save(stagePoints);
                    matchingStageResult.getStagePoints().add(stagePoints);
                    allStagePoints.add(stagePoints);
                }
            }
        }

        return allStagePoints;
    }

    public void createStagePointsForAllExistingResults() {
        List<Competition> competitions = competitionRepository.findAll();
        for (Competition competition : competitions) {
            Set<Race> races = competition.getRaces();
            if (races == null)
                continue;
            for (Race race : races) {
                List<Stage> stages = race.getStages();
                if (stages == null)
                    continue;
                for (Stage stage : stages) {
                    createStagePoints(competition.getId(), stage.getId());
                }
            }
        }
    }

    public MainReserveCyclistPointsDTO getStagePointsForUserPerCylicst(Long competitionId, Long userId,
            Long stageId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        List<StagePoints> stagePointsList = stagePointsRepository.findByCompetition_idAndStageResult_Stage_id(
                competitionId, stageId);

        List<PointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<StageResult> stageResults = competition.getRaces().stream()
                .flatMap(race -> race.getStages().stream())
                .flatMap(s -> s.getResults().stream())
                .toList();

        Set<Cyclist> toBeRemovedCyclists = stageResults.stream()
                .filter(stageResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getRole() == CyclistRole.MAIN
                                && a.getToEvent() == null
                                && a.getCyclist().equals(stageResult.getCyclist())))
                .filter(stageResult -> {
                    String pos = stageResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos) || "OTL".equals(pos);
                })
                .map(StageResult::getCyclist)
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

                List<StagePoints> cyclistStagePoints = stagePointsList.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                if (!cyclistStagePoints.isEmpty()) {
                    int totalPoints = cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();

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

                List<StagePoints> cyclistStagePoints = stagePointsList.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                if (!cyclistStagePoints.isEmpty()) {
                    int totalPoints = cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();

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

    private String getResultTypeInDutch(ScrapeResultType resultType) {
        return switch (resultType) {
            case STAGE -> "etappe";
            case GC -> "algemeen klassement";
            case POINTS -> "puntenklassement";
            case KOM -> "bergklassement";
            case YOUTH -> "jongerenklassement";
        };
    }

    public MainReserveCyclistPointsDTO getStagePointsForStage(Long competitionId, Long stageId) {
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        List<User> users = userTeams.stream()
                .map(UserTeam::getUser)
                .distinct()
                .collect(Collectors.toList());

        System.out.println("Found " + users.size() + " users for competition " + competitionId);

        // Only stage points relevant to the stage
        List<StagePoints> stagePointsList = users.stream()
                .peek(user -> System.out.println("User: " + user.getUsername()))
                .flatMap(user -> user.getStagePoints().stream())
                .filter(sp -> stageId.equals(sp.getStageResult().getStage().getId()))
                .collect(Collectors.toList());

        System.out.println("Found " + stagePointsList.size() + " stage points for stage " + stageId);

        // Create a map for efficient lookup: userId -> cyclistId -> StagePoints
        Map<Long, Map<Long, List<StagePoints>>> userCyclistPointsMap = stagePointsList.stream()
                .collect(Collectors.groupingBy(
                        sp -> sp.getUser().getId(),
                        Collectors.groupingBy(sp -> sp.getStageResult().getCyclist().getId())));

        MainReserveCyclistPointsDTO result = new MainReserveCyclistPointsDTO(
                Collections.emptyList(), Collections.emptyList());

        for (UserTeam userTeam : userTeams) {
            User user = userTeam.getUser();
            Long userId = user.getId();

            // Get user's points map for efficient lookup
            Map<Long, List<StagePoints>> cyclistPointsMap = userCyclistPointsMap.getOrDefault(userId,
                    Collections.emptyMap());

            List<PointsPerUserPerCyclistDTO> mainCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.MAIN)
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createStagePointsDTO(cyclist, cyclistPointsMap, userId, true))
                    .filter(dto -> dto.getPoints() > 0) // Only show cyclists with points
                    .toList();

            List<PointsPerUserPerCyclistDTO> reserveCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.RESERVE)
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createStagePointsDTO(cyclist, cyclistPointsMap, userId, false))
                    .filter(dto -> dto.getPoints() > 0) // Only show cyclists with points
                    .toList();

            result = new MainReserveCyclistPointsDTO(mainCyclists, reserveCyclists);
        }

        return result;
    }

    private PointsPerUserPerCyclistDTO createStagePointsDTO(Cyclist cyclist,
            Map<Long, List<StagePoints>> cyclistPointsMap,
            Long userId,
            boolean isMain) {
        List<StagePoints> cyclistStagePoints = cyclistPointsMap.getOrDefault(cyclist.getId(), Collections.emptyList());

        int totalPoints = cyclistStagePoints.stream()
                .mapToInt(StagePoints::getValue)
                .sum();

        // Get the reason from stage points
        String reason = cyclistStagePoints.stream()
                .map(StagePoints::getReason)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        return new PointsPerUserPerCyclistDTO(
                totalPoints,
                cyclist.getName(),
                cyclist.getId(),
                reason,
                isMain,
                userId);
    }

    public MainReserveCyclistPointsDTO getAllStagePoints(Long competitionId, Long userId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<StageResult> stageResults = competition.getRaces().stream()
                .flatMap(race -> race.getStages().stream())
                .flatMap(stage -> stage.getResults().stream())
                .toList();

        Set<StagePoints> stagePoints = competition.getStagePoints();

        // Active Cyclist Assignments
        List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToEvent() == null)
                .toList();

        List<CyclistAssignment> reserveAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToEvent() == null)
                .toList();

        // Determine DNF/DNS/DQS cyclists
        Set<Cyclist> toBeRemovedCyclists = stageResults.stream()
                .filter(stageResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getToEvent() == null && a.getCyclist().equals(stageResult.getCyclist())))
                .filter(stageResult -> {
                    String pos = stageResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos) || "OTL".equals(pos);
                })
                .map(StageResult::getCyclist)
                .collect(Collectors.toSet());

        List<PointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        CompletableFuture<Void> mainCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : mainAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<StagePoints> cyclistStagePoints = stagePoints.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                int totalPoints = cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();

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

                List<StagePoints> cyclistStagePoints = stagePoints.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                int totalPoints = cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();

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

    private boolean isCyclistActiveInStage(CyclistAssignment assignment, int stageNumber) {
        if (assignment.getFromEvent() != null && stageNumber < assignment.getFromEvent()) {
            return false;
        }

        if (assignment.getToEvent() != null && stageNumber > assignment.getToEvent()) {
            return false;
        }

        return true;
    }

    private int calculatePoints(ScrapeResultType type, int position) {
        return switch (type) {
            case STAGE -> calculatePointsForStage(position);
            case GC -> calculatePointsForGC(position);
            case POINTS -> calculatePointsForPoints(position);
            case KOM -> calculatePointsForMountain(position);
            case YOUTH -> calculatePointsForYouth(position);
            default -> 0;
        };
    }

    private int calculatePointsForStage(int position) {
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

    private int calculatePointsForGC(int position) {
        if (position == 1)
            return 20;
        return 0;
    }

    private int calculatePointsForPoints(int position) {
        if (position == 1)
            return 20;
        return 0;
    }

    private int calculatePointsForMountain(int position) {
        if (position == 1)
            return 1;
        return 0;
    }

    private int calculatePointsForYouth(int position) {
        if (position == 1)
            return 5;
        return 0;
    }

}
