package ucll.be.procyclingscraper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.model.RaceResult;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.RacePointsRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@ExtendWith(MockitoExtension.class)
public class RacePointsServiceTest {

    @Mock
    private RacePointsRepository racePointsRepository;

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private UserTeamRepository userTeamRepository;

    @Mock
    private CyclistRepository cyclistRepository;

    @InjectMocks
    @Spy
    private RacePointsService racePointsService;

    @Test
    void testCreateRacePointsForAllExistingResults() {
        Race race1 = new Race();
        race1.setId(1L);
        race1.setStages(Collections.emptyList()); 

        Race race2 = new Race();
        race2.setId(2L);
        race2.setStages(List.of(new Stage())); 

        Competition competition1 = new Competition();
        competition1.setId(100L);
        competition1.setRaces(new HashSet<>(List.of(race1, race2)));

        when(competitionRepository.findAll()).thenReturn(List.of(competition1));

        doReturn(Collections.emptyList())
            .when(racePointsService).createRacePoints(100L, 1L);

        // Call method
        racePointsService.createRacePointsForAllExistingResults();

        verify(racePointsService).createRacePoints(100L, 1L);
        verify(racePointsService, never()).createRacePoints(100L, 2L);
    }

    // My 
    @Test
    void testCreateRacePoints_success() {
        Long competitionId = 1L;
        Long raceId = 2L;

        Race race = new Race();
        race.setId(raceId);
        race.setStartDate("06-01-2025");

        Competition competition = new Competition();
        competition.setId(competitionId);
        competition.setRaces(Set.of(race));

        Cyclist cyclist = new Cyclist();
        cyclist.setId(100L);
        cyclist.setName("Jane Doe");

        RaceResult raceResult = new RaceResult();
        raceResult.setRace(race);
        raceResult.setPosition("1");
        raceResult.setRacePoints(new HashSet<>());
        cyclist.setRaceResults(List.of(raceResult));

        CyclistAssignment assignment = new CyclistAssignment();
        assignment.setCyclist(cyclist);
        assignment.setRole(CyclistRole.MAIN);

        User user = new User();
        user.setId(999L);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(assignment));

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam));
        when(cyclistRepository.findCyclistsByRaceId(raceId)).thenReturn(List.of(cyclist));
        when(racePointsRepository.existsByRaceIdAndReason(eq(raceId), anyString())).thenReturn(false);
        doReturn(true).when(racePointsService).isCyclistActiveInRace(any(), anyInt());
        // doReturn(100).when(racePointsService).calculateRacePoints(1);

        ArgumentCaptor<RacePoints> captor = ArgumentCaptor.forClass(RacePoints.class);
        when(racePointsRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RacePoints> result = racePointsService.createRacePoints(competitionId, raceId);

        assertEquals(1, result.size());

        RacePoints savedPoints = captor.getValue();
        assertEquals(100, savedPoints.getValue());
        assertEquals("1e", savedPoints.getReason());
        assertEquals(user, savedPoints.getUser());
    }

    @Test
    void testGetRacePointsForUserPerCyclist_returnsCorrectPointsAndClassification() {
        Long competitionId = 1L;
        Long userId = 10L;
        Long raceId = 100L;

        User user = new User();
        user.setId(userId);

        Cyclist mainCyclist = new Cyclist();
        mainCyclist.setId(1L);
        mainCyclist.setName("Main Rider");

        Cyclist reserveCyclist = new Cyclist();
        reserveCyclist.setId(2L);
        reserveCyclist.setName("Reserve Rider");

        CyclistAssignment mainAssignment = new CyclistAssignment();
        mainAssignment.setCyclist(mainCyclist);
        mainAssignment.setRole(CyclistRole.MAIN);

        CyclistAssignment reserveAssignment = new CyclistAssignment();
        reserveAssignment.setCyclist(reserveCyclist);
        reserveAssignment.setRole(CyclistRole.RESERVE);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(mainAssignment, reserveAssignment));

        Race race = new Race();
        race.setId(raceId);

        RaceResult mainResult = new RaceResult();
        mainResult.setCyclist(mainCyclist);
        mainResult.setPosition("1");

        RaceResult reserveResult = new RaceResult();
        reserveResult.setCyclist(reserveCyclist);
        reserveResult.setPosition("2");

        race.setRaceResult(List.of(mainResult, reserveResult));

        Competition competition = new Competition();
        competition.setId(competitionId);
        competition.setRaces(Set.of(race));

        RacePoints mainPoints = new RacePoints();
        mainPoints.setValue(100);
        mainPoints.setRaceResult(mainResult);

        RacePoints reservePoints = new RacePoints();
        reservePoints.setValue(60);
        reservePoints.setRaceResult(reserveResult);

        when(userTeamRepository.findByCompetitionIdAndUser_Id(competitionId, userId)).thenReturn(userTeam);
        when(racePointsRepository.findByCompetition_idAndRaceResult_Race_id(competitionId, raceId))
            .thenReturn(List.of(mainPoints, reservePoints));
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));

        MainReserveCyclistPointsDTO result = racePointsService.getRacePointsForUserPerCyclist(
            competitionId, userId, raceId
        );

        assertEquals(1, result.getMainCyclists().size());
        assertEquals(1, result.getReserveCyclists().size());

        PointsPerUserPerCyclistDTO mainDto = result.getMainCyclists().get(0);
        assertEquals("Main Rider", mainDto.getCyclistName());
        assertEquals(100, mainDto.getPoints());
        assertTrue(mainDto.getIsCyclistActive());
        assertEquals(userId, mainDto.getUserId());

        PointsPerUserPerCyclistDTO reserveDto = result.getReserveCyclists().get(0);
        assertEquals("Reserve Rider", reserveDto.getCyclistName());
        assertEquals(60, reserveDto.getPoints());
        assertTrue(reserveDto.getIsCyclistActive());
        assertEquals(userId, reserveDto.getUserId());
    }

    @Test
    void testGetAllRacePointsForAllUsers_calculatesTotalPointsCorrectly() {
        Long competitionId = 1L;

        User user = new User();
        user.setId(10L);
        user.setFirstName("John");
        user.setLastName("Doe");

        Cyclist mainCyclist = new Cyclist();
        mainCyclist.setId(1L);
        mainCyclist.setName("Main Cyclist");

        Cyclist reserveCyclist = new Cyclist();
        reserveCyclist.setId(2L);
        reserveCyclist.setName("Reserve Cyclist");

        CyclistAssignment mainAssignment = new CyclistAssignment();
        mainAssignment.setCyclist(mainCyclist);
        mainAssignment.setRole(CyclistRole.MAIN);

        CyclistAssignment reserveAssignment = new CyclistAssignment();
        reserveAssignment.setCyclist(reserveCyclist);
        reserveAssignment.setRole(CyclistRole.RESERVE);

        UserTeam userTeam = new UserTeam();
        userTeam.setUser(user);
        userTeam.setCyclistAssignments(List.of(mainAssignment, reserveAssignment));

        MainReserveCyclistPointsDTO dto = new MainReserveCyclistPointsDTO(
            List.of(new PointsPerUserPerCyclistDTO(40, "Main Cyclist", 1L, null, true, 10L)),
            List.of(new PointsPerUserPerCyclistDTO(20, "Reserve Cyclist", 2L, null, true, 10L))
        );

        when(userTeamRepository.findByCompetitionId(competitionId)).thenReturn(List.of(userTeam));
        doReturn(dto).when(racePointsService).getAllRacePoints(competitionId, 10L);

        List<PointsPerUserDTO> result = racePointsService.getAllRacePointsForAllUsers(competitionId);

        assertEquals(1, result.size());
        assertEquals(60, result.get(0).getPoints());
        assertEquals("John Doe", result.get(0).getFullName());
        assertEquals(10L, result.get(0).getUserId());
    }
}