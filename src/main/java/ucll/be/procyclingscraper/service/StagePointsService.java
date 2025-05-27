package ucll.be.procyclingscraper.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.Cyclist;
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

    public List<StagePoints> createStagePoints(Long competitionId, Long userId, Long stageId) {

        UserTeam userTeam = userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId);
        if (userTeam == null) {
            throw new IllegalArgumentException(
                    "User team not found with competitionId: " + competitionId + " and userId: " + userId);
        }

        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Stage not found with id: " + stageId));

        List<Cyclist> teamCyclists = userTeam.getMainCyclists();

        Map<ScrapeResultType, List<Cyclist>> resultTypeToCyclists = new HashMap<>();
        resultTypeToCyclists.put(ScrapeResultType.STAGE,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.STAGE.toString()));
        resultTypeToCyclists.put(ScrapeResultType.GC,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.GC.toString()));
        resultTypeToCyclists.put(ScrapeResultType.POINTS,
                cyclistRepository.findCyclistsByStageIdAndResultType(stageId, ScrapeResultType.POINTS.toString()));

        List<StagePoints> stagePointsList = new ArrayList<>();

        for (Cyclist cyclist : teamCyclists) {
            System.out.println("Cyclist: " + cyclist.getName() + " - Id: " + cyclist.getId());
        }

        for (Map.Entry<ScrapeResultType, List<Cyclist>> entry : resultTypeToCyclists.entrySet()) {
            ScrapeResultType resultType = entry.getKey();
            List<Cyclist> resultCyclists = entry.getValue();

            for (Cyclist cyclist : resultCyclists) {
                System.out.println("Checking cyclist: " + cyclist.getName() + " - Id: " + cyclist.getId());
                if (teamCyclists.stream().anyMatch(c -> c.getId().equals(cyclist.getId()))) {
                    System.out.println("MATCH: " + cyclist.getName());
                }
            }

            for (int i = 0; i < resultCyclists.size(); i++) {
                Cyclist cyclist = resultCyclists.get(i);
                if (teamCyclists.stream().anyMatch(c -> c.getId().equals(cyclist.getId()))) {
                    System.out.println("Cyclist: " + cyclist.getName() + " - Result Type: " + resultType);

                    Optional<StageResult> resultOption = cyclist.getResults().stream()
                            .filter(r -> r.getId().equals(stageId) &&
                                    r.getScrapeResultType() == resultType)
                            .findFirst();

                    int points;
                    int position = 0;

                    if (resultOption.isPresent()) {
                        StageResult result = resultOption.get();
                        position = Integer.parseInt(result.getPosition());
                        points = calculatePoints(resultType, position);
                    } else {
                        // Handle not found
                        points = 0; // or some default/error value
                    }

                    // Find matching StageResult for this cyclist and resultType
                    StageResult matchingStageResult = cyclist.getResults().stream()
                            .filter(sr -> sr.getScrapeResultType() == resultType &&
                                    sr.getStage().equals(stage)) // assuming you have a `stage` variable
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "StageResult not found for cyclist " + cyclist.getName()));

                    // Create and save StagePoints
                    StagePoints stagePoints = StagePoints.builder()
                            .competition(competitionRepository.findById(competitionId).orElseThrow())
                            .stageResult(matchingStageResult)
                            .value(points)
                            .reason(resultType.name() + " - position " + position + " - cyclist " + cyclist.getName())
                            .build();

                    // stagePointsRepository.save(stagePoints);
                    matchingStageResult.getStagePoints().add(stagePoints); // Optional: update bi-directional link
                    stagePointsList.add(stagePoints);
                }
            }
        }
        return stagePointsList;
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
