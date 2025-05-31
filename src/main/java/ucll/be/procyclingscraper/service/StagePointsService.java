package ucll.be.procyclingscraper.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.MainReserveCyclistStagePointsDTO;
import ucll.be.procyclingscraper.dto.StagePointsPerUserDTO;
import ucll.be.procyclingscraper.dto.StagePointsPerUserPerCyclistDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.CyclistAssignment;
import ucll.be.procyclingscraper.model.CyclistRole;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.model.StageResult;
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
                    .filter(a -> a.getRole() == CyclistRole.MAIN && isCyclistActiveInStage(a, currentStageNumber))
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
                        position = Integer.parseInt(result.getPosition());
                        points = calculatePoints(resultType, position);
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

                    String reason = resultType.name() + " - position " + position + " - cyclist " + cyclist.getName()
                            + " - user " + userTeam.getUser().getUsername();

                    boolean exists = stagePointsRepository.existsByStageResultAndReason(matchingStageResult, reason);
                    if (exists) {
                        continue;
                    }

                    StagePoints stagePoints = StagePoints.builder()
                            .competition(competition)
                            .stageResult(matchingStageResult)
                            .value(points)
                            .reason(reason)
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

    public MainReserveCyclistStagePointsDTO getStagePointsForUserPerCylicst(Long competitionId, Long userId,
            Long stageId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        List<StagePoints> stagePointsList = stagePointsRepository.findByCompetition_idAndStageResult_Stage_id(
                competitionId, stageId);

        List<StagePointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<StagePointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<StageResult> stageResults = competition.getRaces().stream()
                .flatMap(race -> race.getStages().stream())
                .flatMap(s -> s.getResults().stream())
                .toList();

        Set<Cyclist> toBeRemovedCyclists = stageResults.stream()
                .filter(stageResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getRole() == CyclistRole.MAIN
                                && a.getToStage() == null
                                && a.getCyclist().equals(stageResult.getCyclist())))
                .filter(stageResult -> {
                    String pos = stageResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos);
                })
                .map(StageResult::getCyclist)
                .collect(Collectors.toSet());

        List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToStage() == null)
                .toList();

        List<CyclistAssignment> reserveAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToStage() == null)
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
                        mainCyclists.add(new StagePointsPerUserPerCyclistDTO(
                                totalPoints, cyclist.getName(), cyclist.getId(), isCyclistActive,
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
                        reserveCyclists.add(new StagePointsPerUserPerCyclistDTO(
                                totalPoints, cyclist.getName(), cyclist.getId(), isCyclistActive,
                                userTeam.getUser().getId()));
                    }
                }
            }
        });

        CompletableFuture.allOf(mainCyclistsFuture, reserveCyclistsFuture).join();

        return new MainReserveCyclistStagePointsDTO(mainCyclists, reserveCyclists);
    }

    public List<StagePointsPerUserDTO> getStagePointsForStage(Long competitionId, Long stageId) {

        List<UserTeam> userTeams = userTeamRepository.findByCompetitionId(competitionId);

        List<StagePoints> stagePointsList = stagePointsRepository.findByCompetition_idAndStageResult_Stage_id(
                competitionId,
                stageId);

        List<StagePointsPerUserDTO> result = new ArrayList<>();

        for (UserTeam userTeam : userTeams) {
            int totalPoints = 0;

            // Get active MAIN cyclist assignments
            List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                    .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToStage() == null)
                    .toList();

            for (CyclistAssignment assignment : mainAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<StagePoints> cyclistStagePoints = stagePointsList.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                if (!cyclistStagePoints.isEmpty()) {
                    totalPoints += cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();
                }
            }

            result.add(new StagePointsPerUserDTO(
                    totalPoints,
                    userTeam.getUser().getFirstName() + " " + userTeam.getUser().getLastName(),
                    userTeam.getUser().getId()));
        }

        return result;
    }

    public MainReserveCyclistStagePointsDTO getAllStagePoints(Long competitionId, Long userId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);

        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with id: " + competitionId));

        List<StageResult> stageResults = competition.getRaces().stream()
                .flatMap(race -> race.getStages().stream())
                .flatMap(stage -> stage.getResults().stream())
                .toList();

        Set<StagePoints> stagePoints = competition.getStagePoints();

        System.out.println(stageResults.size() + " stage results found for competition " + competitionId);

        // Active Cyclist Assignments
        List<CyclistAssignment> mainAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.MAIN && a.getToStage() == null)
                .toList();

        List<CyclistAssignment> reserveAssignments = userTeam.getCyclistAssignments().stream()
                .filter(a -> a.getRole() == CyclistRole.RESERVE && a.getToStage() == null)
                .toList();

        // Determine DNF/DNS/DQS cyclists
        Set<Cyclist> toBeRemovedCyclists = stageResults.stream()
                .filter(stageResult -> userTeam.getCyclistAssignments().stream()
                        .anyMatch(a -> a.getToStage() == null && a.getCyclist().equals(stageResult.getCyclist())))
                .filter(stageResult -> {
                    String pos = stageResult.getPosition();
                    return "DNF".equals(pos) || "DQS".equals(pos) || "DNS".equals(pos);
                })
                .map(StageResult::getCyclist)
                .collect(Collectors.toSet());

        List<StagePointsPerUserPerCyclistDTO> mainCyclists = new ArrayList<>();
        List<StagePointsPerUserPerCyclistDTO> reserveCyclists = new ArrayList<>();

        CompletableFuture<Void> mainCyclistsFuture = CompletableFuture.runAsync(() -> {
            for (CyclistAssignment assignment : mainAssignments) {
                Cyclist cyclist = assignment.getCyclist();

                List<StagePoints> cyclistStagePoints = stagePoints.stream()
                        .filter(sp -> sp.getStageResult().getCyclist().getId().equals(cyclist.getId()))
                        .toList();

                boolean isCyclistActive = !toBeRemovedCyclists.contains(cyclist);

                int totalPoints = cyclistStagePoints.stream().mapToInt(StagePoints::getValue).sum();

                synchronized (mainCyclists) {
                    mainCyclists.add(new StagePointsPerUserPerCyclistDTO(
                            totalPoints,
                            cyclist.getName(),
                            cyclist.getId(),
                            isCyclistActive,
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
                    reserveCyclists.add(new StagePointsPerUserPerCyclistDTO(
                            totalPoints,
                            cyclist.getName(),
                            cyclist.getId(),
                            isCyclistActive,
                            userTeam.getUser().getId()));
                }
            }
        });

        CompletableFuture.allOf(mainCyclistsFuture, reserveCyclistsFuture).join();

        return new MainReserveCyclistStagePointsDTO(mainCyclists, reserveCyclists);
    }

    private boolean isCyclistActiveInStage(CyclistAssignment assignment, int stageNumber) {
        if (assignment.getFromStage() != null && stageNumber < assignment.getFromStage()) {
            return false;
        }

        if (assignment.getToStage() != null && stageNumber > assignment.getToStage()) {
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
