package ucll.be.procyclingscraper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ucll.be.procyclingscraper.dto.MainReserveCyclistPointsDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserDTO;
import ucll.be.procyclingscraper.dto.PointsPerUserPerCyclistDTO;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.CyclistAssignment;
import ucll.be.procyclingscraper.model.CyclistRole;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RaceStatus;
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

@ExtendWith(MockitoExtension.class)
class StagePointsServiceTest {

    @InjectMocks
    @Spy
    private StagePointsService stagePointsService;

    @Mock
    private StagePointsRepository stagePointsRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private UserTeamRepository userTeamRepository;

    @Mock
    private CyclistRepository cyclistRepository;

    @Mock
    private StageRepository stageRepository;

    @Test
    void createStagePoints_shouldReturnStagePoints_whenValidDataProvided() {
        Long competitionId = 1L;
        Long stageId = 2L;

        // Set up Stage
        Stage stage = new Stage();
        stage.setId(stageId);
        stage.setDate("05/06");

        // Set up Race with Stage
        Race race = new Race();
        stage.setRace(race);
        race.setStages(List.of(stage));

        // Set up Competition with Race
        Competition competition = new Competition();
        competition.setId(competitionId);
        competition.setRaces(Set.of(race));

        // Set up Cyclist and Result
        Cyclist cyclist = new Cyclist();
        cyclist.setId(101L);
        cyclist.setName("John Cyclist");

        StageResult stageResult = new PointResult();
        stageResult.setStage(stage);
        stageResult.setCyclist(cyclist);
        stageResult.setScrapeResultType(ScrapeResultType.STAGE);
        stageResult.setPosition("1");
        ((PointResult) stageResult).setPoint(100); 
        stageResult.setRaceStatus(RaceStatus.FINISHED);

        cyclist.setResults(List.of(stageResult));

        // Set up User and Team
        User user = new User();
        user.setId(10L);
        user.setFirstName("Bob");
        user.setLastName("Smith");

        CyclistAssignment assignment = new CyclistAssignment();
        assignment.setCyclist(cyclist);
        assignment.setRole(CyclistRole.MAIN);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(assignment));

        // Mocking repositories
        when(stageRepository.findById(stageId)).thenReturn(Optional.of(stage));
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam));
        when(cyclistRepository.findCyclistsByStageIdAndResultType(stageId, "STAGE")).thenReturn(List.of(cyclist));
        when(cyclistRepository.findCyclistsByStageIdAndResultType(stageId, "GC")).thenReturn(List.of());
        when(cyclistRepository.findCyclistsByStageIdAndResultType(stageId, "POINTS")).thenReturn(List.of());
        when(stagePointsRepository.existsByStageIdAndReason(eq(stageId), anyString())).thenReturn(false);
        when(stagePointsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(true).when(stagePointsService).isCyclistActiveInStage(any(), anyInt(), anyInt());

        List<StagePoints> result = stagePointsService.createStagePoints(competitionId, stageId);

        assertEquals(1, result.size());
        StagePoints points = result.get(0);
        assertEquals(user.getId(), points.getUser().getId());
        assertEquals(cyclist.getId(), points.getStageResult().getCyclist().getId());
        assertTrue(points.getValue() > 0);
        assertTrue(points.getReason().contains("1e plaats"));

        
    }

    @Test
    void createStagePoints_shouldThrowException_whenStageNotFound() {
        when(stageRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            stagePointsService.createStagePoints(10L, 1L));

        assertEquals("Stage not found with id: 1", exception.getMessage());
    }

    @Test
    void createStagePoints_shouldThrowException_whenCompetitionNotFound() {
        Stage stage = new Stage();
        stage.setId(1L);
        stage.setRace(new Race());

        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        when(competitionRepository.findById(10L)).thenReturn(Optional.empty());

        var exception = assertThrows(IllegalArgumentException.class, () ->
            stagePointsService.createStagePoints(10L, 1L));

        assertEquals("Competition not found with id: 10", exception.getMessage());
    }

    @Test
    void createStagePoints_shouldSkipWhenResultPositionIsInvalid() {
        Long stageId = 1L, competitionId = 10L;

        Stage stage = new Stage();
        stage.setId(stageId);
        stage.setDate("06-06-2024");
        Race race = new Race();
        stage.setRace(race);
        race.setStages(List.of(stage));

        Competition competition = new Competition();
        competition.setId(competitionId);
        competition.setRaces(Set.of(race));

        User user = new User();
        user.setId(100L);

        Cyclist cyclist = new Cyclist();
        cyclist.setId(200L);
        cyclist.setName("Joke Doe");

        CyclistAssignment assignment = new CyclistAssignment();
        assignment.setRole(CyclistRole.MAIN);
        assignment.setCyclist(cyclist);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(assignment));

        PointResult result = new PointResult();
        result.setStage(stage);
        result.setScrapeResultType(ScrapeResultType.STAGE);
        result.setRaceStatus(RaceStatus.FINISHED);
        result.setCyclist(cyclist);
        result.setPosition("DNF");

        cyclist.setResults(List.of(result));

        when(stageRepository.findById(stageId)).thenReturn(Optional.of(stage));
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam));
        when(cyclistRepository.findCyclistsByStageIdAndResultType(stageId, "STAGE")).thenReturn(List.of(cyclist));

        List<StagePoints> resultPoints = stagePointsService.createStagePoints(competitionId, stageId);

        assertTrue(resultPoints.isEmpty());
        verify(stagePointsRepository, never()).save(any());
    }

    @Test
    void createStagePointsForAllExistingResults_shouldCallCreateStagePointsForEachStage() {

        Competition comp1 = new Competition();
        comp1.setId(1L);

        Race race1 = new Race();
        Stage stage1 = new Stage();
        stage1.setId(101L);
        Stage stage2 = new Stage();
        stage2.setId(102L);
        race1.setStages(List.of(stage1, stage2));

        Race race2 = new Race();
        race2.setStages(null); 

        comp1.setRaces(Set.of(race1, race2));

        Competition comp2 = new Competition();
        comp2.setId(2L);
        comp2.setRaces(null); 

        when(competitionRepository.findAll()).thenReturn(List.of(comp1, comp2));

        doReturn(Collections.emptyList()).when(stagePointsService).createStagePoints(anyLong(), anyLong());

        stagePointsService.createStagePointsForAllExistingResults();

        verify(stagePointsService).createStagePoints(1L, 101L);
        verify(stagePointsService).createStagePoints(1L, 102L);
        verify(stagePointsService, never()).createStagePoints(eq(2L), anyLong());
        verify(stagePointsService, times(2)).createStagePoints(eq(1L), anyLong());
    }

    @Test
    void getStagePointsForUserPerCyclist_shouldReturnPointsForMainAndReserveCyclists() {
        long competitionId = 10L;
        long userId = 100L;
        long stageId = 50L;

        User user = new User();
        user.setId(userId);
        user.setFirstName("Alice");

        Cyclist cyclist = new Cyclist();
        cyclist.setId(200L);
        cyclist.setName("Cyclist One");

        CyclistAssignment mainAssignment = new CyclistAssignment();
        mainAssignment.setRole(CyclistRole.MAIN);
        mainAssignment.setCyclist(cyclist);

        CyclistAssignment reserveAssignment = new CyclistAssignment();
        reserveAssignment.setRole(CyclistRole.RESERVE);
        reserveAssignment.setCyclist(cyclist);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(mainAssignment, reserveAssignment));

        Competition competition = new Competition();
        competition.setId(competitionId);

        Stage stage = new Stage();
        stage.setId(stageId);

        PointResult pointResult = new PointResult();
        pointResult.setStage(stage);
        pointResult.setCyclist(cyclist);
        pointResult.setPoint(42);
        pointResult.setScrapeResultType(ScrapeResultType.STAGE);
        pointResult.setRaceStatus(RaceStatus.FINISHED);
        pointResult.setPosition("5");

        StagePoints stagePoints = new StagePoints();
        stagePoints.setStageResult(pointResult);
        stagePoints.setValue(42);

        when(userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId)).thenReturn(userTeam);
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(stagePointsRepository.findByCompetition_idAndStageResult_Stage_id(competitionId, stageId))
            .thenReturn(List.of(stagePoints));

        var resultDTO = stagePointsService.getStagePointsForUserPerCylicst(competitionId, userId, stageId);

        assertEquals(1, resultDTO.getMainCyclists().size(), "Expected 1 main cyclist");
        assertEquals(1, resultDTO.getReserveCyclists().size(), "Expected 1 reserve cyclist");
        assertEquals(42, resultDTO.getMainCyclists().get(0).getPoints());
        assertEquals(42, resultDTO.getReserveCyclists().get(0).getPoints());
    }

    @Test
    void getStagePointsForStage_shouldReturnPointsForMainAndReserveCyclists() {
        Long competitionId = 1L;
        Long stageId = 2L;

        User user = new User();
        user.setId(100L);
        user.setFirstName("user1");

        Cyclist cyclist1 = new Cyclist();
        cyclist1.setId(200L);
        cyclist1.setName("Cyclist Main");

        Cyclist cyclist2 = new Cyclist();
        cyclist2.setId(201L);
        cyclist2.setName("Cyclist Reserve");

        CyclistAssignment mainAssignment = new CyclistAssignment();
        mainAssignment.setRole(CyclistRole.MAIN);
        mainAssignment.setCyclist(cyclist1);

        CyclistAssignment reserveAssignment = new CyclistAssignment();
        reserveAssignment.setRole(CyclistRole.RESERVE);
        reserveAssignment.setCyclist(cyclist2);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(mainAssignment, reserveAssignment));

        Stage stage = new Stage();
        stage.setId(stageId);

        PointResult result1 = new PointResult();
        result1.setStage(stage);
        result1.setCyclist(cyclist1);
        result1.setPosition("1");

        PointResult result2 = new PointResult();
        result2.setStage(stage);
        result2.setCyclist(cyclist2);
        result2.setPosition("3");

        StagePoints sp1 = new StagePoints();
        sp1.setUser(user);
        sp1.setStageResult(result1);
        sp1.setValue(50);

        StagePoints sp2 = new StagePoints();
        sp2.setUser(user);
        sp2.setStageResult(result2);
        sp2.setValue(20);

        user.setStagePoints(List.of(sp1, sp2));

        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam));

        MainReserveCyclistPointsDTO result = stagePointsService.getStagePointsForStage(competitionId, stageId);

        assertNotNull(result);

        assertTrue(result.getMainCyclists().stream()
            .anyMatch(dto -> dto.getCyclistId().equals(cyclist1.getId()) && dto.getPoints() == 50));

        assertTrue(result.getReserveCyclists().stream()
            .anyMatch(dto -> dto.getCyclistId().equals(cyclist2.getId()) && dto.getPoints() == 20));
    }


    @Test
    void getAllStagePointsForAllUsers_shouldReturnTotalPointsPerUser() {
        // Arrange
        Long competitionId = 1L;

        User user1 = new User();
        user1.setId(100L);
        user1.setFirstName("Alice");
        user1.setLastName("Smith");

        User user2 = new User();
        user2.setId(101L);
        user2.setFirstName("Bob");
        user2.setLastName("Jones");

        UserTeam userTeam1 = new UserTeam();
        userTeam1.setUser(user1);

        UserTeam userTeam2 = new UserTeam();
        userTeam2.setUser(user2);

        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam1, userTeam2));

        MainReserveCyclistPointsDTO user1Points = new MainReserveCyclistPointsDTO(
            List.of(new PointsPerUserPerCyclistDTO(15, "Cyclist1", 201L, null, true, 100L)),
            List.of(new PointsPerUserPerCyclistDTO(15, "Cyclist2", 202L, null, true, 100L))
        );

        // Total points = 15 + 15 = 30 for user1

        MainReserveCyclistPointsDTO user2Points = new MainReserveCyclistPointsDTO(
            List.of(new PointsPerUserPerCyclistDTO(10, "Cyclist3", 203L, null, true, 101L)),
            List.of()
        );

        // Total points = 10 for user2
        doReturn(user1Points).when(stagePointsService).getAllStagePoints(competitionId, 100L);
        doReturn(user2Points).when(stagePointsService).getAllStagePoints(competitionId, 101L);

        List<PointsPerUserDTO> results = stagePointsService.getAllStagePointsForAllUsers(competitionId);

        assertEquals(2, results.size());

        PointsPerUserDTO dto1 = results.stream().filter(dto -> dto.getUserId() == 100L).findFirst().get();
        assertEquals(30, dto1.getPoints());
        assertEquals("Alice Smith", dto1.getFullName());

        PointsPerUserDTO dto2 = results.stream().filter(dto -> dto.getUserId() == 101L).findFirst().get();
        assertEquals(10, dto2.getPoints());
        assertEquals("Bob Jones", dto2.getFullName());
    }



    
}

