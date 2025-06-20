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

import ucll.be.procyclingscraper.dto.CreateStagePointsDTO;
import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
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
import ucll.be.procyclingscraper.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    public boolean createNewStagePoints(CreateStagePointsDTO stagePoints, String email) {
        StagePoints newStagePoints = StagePoints.builder()
                .competition(competitionRepository.findById(stagePoints.getCompetitionId())
                        .orElseThrow(() -> new IllegalArgumentException("Competitie niet gevonden met ID: "
                                + stagePoints.getCompetitionId())))
                .stageId(stagePoints.getStageId())
                .value(stagePoints.getValue())
                .reason(stagePoints.getReason())
                .user(userRepository.findUserByEmail(email))
                .stageResult(null)
                .build();

        stagePointsRepository.save(newStagePoints);
        return true;
    }

    public List<StagePoints> createStagePoints(Long competitionId, Long stageId) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Etappe niet gevonden met ID: " + stageId));

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competitie niet gevonden met ID: " + competitionId));

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
            throw new IllegalStateException("Etappe ID niet gevonden in de competitie etappes.");
        }

        final int currentStageNumber = tempStageNumber;
        final boolean isLastStage = currentStageNumber == orderedStages.size();

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

        // Add additional result types for last stage
        if (isLastStage) {
            resultTypeToCyclists.put(ScrapeResultType.KOM,
                    cyclistRepository.findCyclistsByStageIdAndResultType(stageId,
                            ScrapeResultType.KOM.toString()));
            resultTypeToCyclists.put(ScrapeResultType.YOUTH,
                    cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.YOUTH.toString()));
        }

        List<StagePoints> allStagePoints = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            List<Cyclist> teamCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(a -> a.getRole() == CyclistRole.MAIN
                            && isCyclistActiveInStage(a, currentStageNumber, orderedStages.size()))
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
                    String reasonPrefix;

                    if (resultOption.isPresent()) {
                        StageResult result = resultOption.get();
                        try {
                            position = Integer.parseInt(result.getPosition());
                        } catch (NumberFormatException e) {
                            continue; // Skip if position is not a valid number
                        }
                        // Calculate points based on result type and whether it's the last stage
                        if (isLastStage && (resultType == ScrapeResultType.GC ||
                                resultType == ScrapeResultType.POINTS ||
                                resultType == ScrapeResultType.KOM ||
                                resultType == ScrapeResultType.YOUTH)) {
                            points = calculateLastStagePoints(resultType, position);
                            reasonPrefix = "Eindklassement ";
                        } else {
                            points = calculatePoints(resultType, position);
                            reasonPrefix = "";
                        }

                    } else {
                        points = 0;
                        reasonPrefix = "";
                    }

                    if (points <= 0) {
                        continue;
                    }

                    StageResult matchingStageResult = cyclist.getResults().stream()
                            .filter(sr -> sr.getScrapeResultType() == resultType &&
                                    sr.getStage().equals(stage))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "StageResult niet gevonden voor renner " + cyclist.getName()));

                    String reason = reasonPrefix + position + "e plaats in " + getResultTypeInDutch(resultType);

                    boolean exists = stagePointsRepository.existsByStageIdAndReason(stageId, reason);

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

    // Helper method to calculate points for the last stage based on result type
    private int calculateLastStagePoints(ScrapeResultType resultType, int position) {
        switch (resultType) {
            case GC:
                return calculatePointsForEndGC(position);
            case POINTS:
            case KOM:
                return calculatePointsForEndPointsAndMountain(position);
            case YOUTH:
                return calculatePointsForEndYouth(position);
            default:
                return calculatePoints(resultType, position);
        }
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
                .orElseThrow(() -> new IllegalArgumentException("Competitie niet gevonden met ID: " + competitionId));

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
                .toList();

        // Only stage points relevant to the stage
        List<StagePoints> stagePointsList = users.stream()
                .peek(user -> System.out.println("User: " + user.getUsername()))
                .flatMap(user -> user.getStagePoints().stream())
                .filter(sp -> stageId.equals(sp.getStageResult().getStage().getId()))
                .toList();

        // Map for efficient lookup: userId -> cyclistId -> StagePoints
        Map<Long, Map<Long, List<StagePoints>>> userCyclistPointsMap = stagePointsList.stream()
                .collect(Collectors.groupingBy(
                        sp -> sp.getUser().getId(),
                        Collectors.groupingBy(sp -> sp.getStageResult().getCyclist().getId())));

        List<PointsPerUserPerCyclistDTO> allMainCyclists = new ArrayList<>();
        List<PointsPerUserPerCyclistDTO> allReserveCyclists = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            User user = userTeam.getUser();
            Long userId = user.getId();

            Map<Long, List<StagePoints>> cyclistPointsMap = userCyclistPointsMap.getOrDefault(userId,
                    Collections.emptyMap());

            List<PointsPerUserPerCyclistDTO> mainCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.MAIN)
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createStagePointsDTO(cyclist, cyclistPointsMap, userId, true))
                    .filter(dto -> dto.getPoints() > 0)
                    .toList();

            List<PointsPerUserPerCyclistDTO> reserveCyclists = userTeam.getCyclistAssignments().stream()
                    .filter(ca -> ca.getRole() == CyclistRole.RESERVE)
                    .map(CyclistAssignment::getCyclist)
                    .map(cyclist -> createStagePointsDTO(cyclist, cyclistPointsMap, userId, false))
                    .filter(dto -> dto.getPoints() > 0)
                    .toList();

            allMainCyclists.addAll(mainCyclists);
            allReserveCyclists.addAll(reserveCyclists);
        }

        return new MainReserveCyclistPointsDTO(allMainCyclists, allReserveCyclists);
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

    public List<PointsPerUserDTO> getAllStagePointsForAllUsers(Long competitionId) {
        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        List<PointsPerUserDTO> result = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            Long userId = userTeam.getUser().getId();
            String fullName = userTeam.getUser().getFirstName() + " " + userTeam.getUser().getLastName(); // Assumes
                                                                                                          // getFullName()
                                                                                                          // exists
            MainReserveCyclistPointsDTO stagePoints = getAllStagePoints(competitionId, userId);

            int totalPoints = 0;

            for (PointsPerUserPerCyclistDTO cyclist : stagePoints.getMainCyclists()) {
                totalPoints += cyclist.getPoints();
            }

            for (PointsPerUserPerCyclistDTO cyclist : stagePoints.getReserveCyclists()) {
                totalPoints += cyclist.getPoints();
            }

            result.add(new PointsPerUserDTO(totalPoints, fullName, userId));
        }

        return result;
    }

    public MainReserveCyclistPointsDTO getAllStagePoints(Long competitionId, Long userId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competitie niet gevonden met ID: " + competitionId));

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

    boolean isCyclistActiveInStage(CyclistAssignment assignment, int stageNumber, int lastStageNumber) {

        if (assignment.getFromEvent() == null && assignment.getToEvent() == null) {
            return false;
        }

        if (assignment.getFromEvent() != null && assignment.getFromEvent() > lastStageNumber) {
            return false;
        }

        if (assignment.getFromEvent() != null && assignment.getFromEvent() > stageNumber) {
            return false;
        }

        // If fromEvent is set and current stage is before it, not active
        if (assignment.getFromEvent() != null && stageNumber < assignment.getFromEvent()) {
            return false;
        }

        // If toEvent is set and current stage is after it, not active
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

    private int calculatePointsForEndGC(int position) {
        switch (position) {
            case 1:
                return 300;
            case 2:
                return 240;
            case 3:
                return 195;
            case 4:
                return 165;
            case 5:
                return 135;
            case 6:
                return 105;
            case 7:
                return 90;
            case 8:
                return 75;
            case 9:
                return 60;
            case 10:
                return 51;
            case 11:
                return 45;
            case 12:
                return 42;
            case 13:
                return 39;
            case 14:
                return 36;
            case 15:
                return 33;
            case 16:
                return 30;
            case 17:
                return 27;
            case 18:
                return 24;
            case 19:
                return 21;
            case 20:
                return 18;
            case 21:
                return 15;
            case 22:
                return 12;
            case 23:
                return 9;
            case 24:
                return 6;
            case 25:
                return 3;
            default:
                return 0;
        }
    }

    private int calculatePointsForEndPointsAndMountain(int position) {
        switch (position) {
            case 1:
                return 150;
            case 2:
                return 120;
            case 3:
                return 100;
            case 4:
                return 80;
            case 5:
                return 60;
            case 6:
                return 40;
            case 7:
                return 30;
            case 8:
                return 20;
            case 9:
                return 10;
            case 10:
                return 5;
            default:
                return 0;
        }
    }

    private int calculatePointsForEndYouth(int position) {
        switch (position) {
            case 1:
                return 80;
            case 2:
                return 60;
            case 3:
                return 40;
            case 4:
                return 30;
            case 5:
                return 25;
            case 6:
                return 20;
            case 7:
                return 15;
            case 8:
                return 10;
            case 9:
                return 5;
            case 10:
                return 2;
            default:
                return 0;
        }
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
