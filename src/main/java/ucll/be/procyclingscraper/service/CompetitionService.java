package ucll.be.procyclingscraper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucll.be.procyclingscraper.dto.CompetitionDTO;
import ucll.be.procyclingscraper.dto.CompetitionModel;
import ucll.be.procyclingscraper.dto.CountNotification;
import ucll.be.procyclingscraper.dto.CreateCompetitionData;
import ucll.be.procyclingscraper.dto.OrderNotification;
import ucll.be.procyclingscraper.dto.RaceDTO;
import ucll.be.procyclingscraper.dto.StageDTO;
import ucll.be.procyclingscraper.dto.StatusNotification;
import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.dto.UserModel;
import ucll.be.procyclingscraper.model.Competition;
import ucll.be.procyclingscraper.model.CompetitionPick;
import ucll.be.procyclingscraper.model.CompetitionStatus;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.model.UserTeam;
import ucll.be.procyclingscraper.repository.CompetitionRepository;
import ucll.be.procyclingscraper.repository.RaceRepository;
import ucll.be.procyclingscraper.repository.UserRepository;
import ucll.be.procyclingscraper.repository.UserTeamRepository;

@Service
public class CompetitionService {

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private StagePointsService stagePointsService;

    @Autowired
    private RaceResultService raceResultService;

    @Autowired
    private RacePointsService racePointsService;

    @Autowired
    private StageService stageService;

    CompetitionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Competition> getAllCompetitions() {
        return competitionRepository.findAll();
    }

    public Boolean getResults(Long competitionId) throws IOException {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        if (!competition.getRaces().isEmpty()
                && !competition.getRaces().stream().findFirst().get().getStages().isEmpty()) {
            stageResultService.getStageResultsForAllStagesInCompetition(competitionId);
            stagePointsService.createStagePointsForAllExistingResults();
        } else if (!competition.getRaces().isEmpty()) {
            raceResultService.getRaceResultsForAllRacesInCompetition(competitionId);
            racePointsService.createRacePointsForAllExistingResults();
        } else {
            throw new IllegalArgumentException("Competition with ID " + competitionId + " has no races or stages.");
        }
        return true;
    }

    public CompetitionDTO scrapeCompetitionStages(Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        Set<Race> races = competition.getRaces();

        ExecutorService executor = Executors.newFixedThreadPool(8); // Tune pool size based on expected load

        List<CompletableFuture<Void>> scrapeFutures = races.stream()
                .map(race -> CompletableFuture.runAsync(() -> stageService.scrapeStagesByRaceId(race.getId()),
                        executor))
                .toList();

        // Wait until all scraping is done
        CompletableFuture.allOf(scrapeFutures.toArray(new CompletableFuture[0])).join();
        executor.shutdown(); // Cleanup

        // Re-fetch updated race entities (to make sure we get updated stages from DB)
        competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalStateException("Competition disappeared after scraping"));

        // Convert races to DTOs
        Set<RaceDTO> raceDTOs = competition.getRaces().stream().map(race -> {
            List<StageDTO> stageDTOs = race.getStages().stream().map(stage -> new StageDTO(
                    stage.getId(),
                    stage.getName(),
                    stage.getDeparture(),
                    stage.getArrival(),
                    stage.getDate().toString(),
                    stage.getStartTime(),
                    stage.getDistance(),
                    stage.getStageUrl(),
                    stage.getVerticalMeters(),
                    stage.getParcoursType())).collect(Collectors.toList());

            return new RaceDTO(
                    race.getId(),
                    race.getName(),
                    race.getNiveau(),
                    race.getStartDate().toString(),
                    race.getEndDate().toString(),
                    race.getDistance(),
                    race.getRaceUrl(),
                    race.getCompetitions().stream().map(Competition::getId).collect(Collectors.toList()),
                    stageDTOs);
        }).collect(Collectors.toSet());

        // Convert users
        Set<UserDTO> userDTOs = competition.getUsers().stream().map(user -> new UserDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(), 0)).collect(Collectors.toSet());

        return new CompetitionDTO(
                competition.getId(),
                competition.getName(),
                raceDTOs,
                userDTOs,
                competition.getCompetitionStatus(),
                competition.getMaxMainCyclists(),
                competition.getMaxReserveCyclists(),
                competition.getCurrentPick(),
                competition.getCompetitionPicks());
    }

    public Set<CompetitionDTO> getCompetitions(String email) {
        User currentUser = userRepository.findUserByEmail(email);
        Set<Competition> competitions = currentUser.getCompetitions();

        Set<CompetitionDTO> competitionDTOs = new HashSet<>();
        for (Competition competition : competitions) {

            // Convert races with nested stages
            Set<RaceDTO> raceDTOs = competition.getRaces().stream().map(race -> {
                List<StageDTO> stageDTOs = race.getStages().stream().map(stage -> new StageDTO(
                        stage.getId(),
                        stage.getName(),
                        stage.getDeparture(),
                        stage.getArrival(),
                        stage.getDate().toString(), // format if needed
                        stage.getStartTime(),
                        stage.getDistance(),
                        stage.getStageUrl(),
                        stage.getVerticalMeters(),
                        stage.getParcoursType())).collect(Collectors.toList());

                return new RaceDTO(
                        race.getId(),
                        race.getName(),
                        race.getNiveau(),
                        race.getStartDate().toString(),
                        race.getEndDate().toString(),
                        race.getDistance(),
                        race.getRaceUrl(),
                        race.getCompetitions().stream().map(c -> c.getId()).collect(Collectors.toList()),
                        stageDTOs);
            }).collect(Collectors.toSet());

            // Convert users
            Set<UserDTO> userDTOs = competition.getUsers().stream().map(user -> new UserDTO(
                    user.getId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getRole(), 0)).collect(Collectors.toSet());

            // Create CompetitionDTO
            CompetitionDTO dto = new CompetitionDTO(
                    competition.getId(),
                    competition.getName(),
                    raceDTOs,
                    userDTOs,
                    competition.getCompetitionStatus(),
                    competition.getMaxMainCyclists(),
                    competition.getMaxReserveCyclists(),
                    competition.getCurrentPick(),
                    competition.getCompetitionPicks());

            competitionDTOs.add(dto);
        }

        return competitionDTOs;
    }

    public CompetitionDTO getCompetitionById(Long id) {
        Competition competition = competitionRepository.findById(id).orElse(null);
        if (competition == null) {
            return null;
        }

        // Convert races with nested stages
        Set<RaceDTO> raceDTOs = competition.getRaces().stream().map(race -> {
            List<StageDTO> stageDTOs = race.getStages().stream().map(stage -> new StageDTO(
                    stage.getId(),
                    stage.getName(),
                    stage.getDeparture(),
                    stage.getArrival(),
                    stage.getDate().toString(),
                    stage.getStartTime(),
                    stage.getDistance(),
                    stage.getStageUrl(),
                    stage.getVerticalMeters(),
                    stage.getParcoursType())).collect(Collectors.toList());

            return new RaceDTO(
                    race.getId(),
                    race.getName(),
                    race.getNiveau(),
                    race.getStartDate().toString(),
                    race.getEndDate().toString(),
                    race.getDistance(),
                    race.getRaceUrl(),
                    race.getCompetitions().stream().map(c -> c.getId()).collect(Collectors.toList()),
                    stageDTOs);
        }).collect(Collectors.toSet());

        // Convert users
        Set<UserDTO> userDTOs = competition.getUsers().stream().map(user -> new UserDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(), 0)).collect(Collectors.toSet());

        // Create CompetitionDTO
        return new CompetitionDTO(
                competition.getId(),
                competition.getName(),
                raceDTOs,
                userDTOs,
                competition.getCompetitionStatus(),
                competition.getMaxMainCyclists(),
                competition.getMaxReserveCyclists(),
                competition.getCurrentPick(),
                competition.getCompetitionPicks());
    }

    @Transactional
    public CountNotification handleCyclistCount(int maxMainCyclists, int maxReserveCyclists, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));
        if (maxMainCyclists < 0 || maxReserveCyclists < 0) {
            throw new IllegalArgumentException("Cyclist counts must be non-negative");
        }

        competition.setMaxMainCyclists(maxMainCyclists);
        competition.setMaxReserveCyclists(maxReserveCyclists);

        competitionRepository.save(competition);

        CountNotification notification = new CountNotification();
        notification.setMaxMainCyclists(maxMainCyclists);
        notification.setMaxReserveCyclists(maxReserveCyclists);
        notification.setCompetitionId(competitionId);
        return notification;
    }

    @Transactional
    public StatusNotification updateCompetitionStatus(CompetitionStatus status, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        competition.setCompetitionStatus(status);
        competitionRepository.save(competition);
        StatusNotification notification = new StatusNotification();
        notification.setStatus(status);
        return notification;
    }

    @Transactional
    public OrderNotification updateOrderToCompetition(List<UserModel> users, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found with ID: " + competitionId));

        // Clear existing picks if you want to replace them
        competition.getCompetitionPicks().clear();

        // Assign pick order starting from 1
        Long pickOrder = 1L;
        for (UserModel user : users) {
            CompetitionPick pick = new CompetitionPick();
            pick.setCompetition(competition);
            pick.setUserId(user.getId());
            pick.setPickOrder(pickOrder++);
            competition.getCompetitionPicks().add(pick);
        }

        // Persist the competition with updated pick order
        competitionRepository.save(competition);

        // Notify frontend/system (if needed)
        OrderNotification notification = new OrderNotification();
        notification.setCompetitionPicks(new java.util.HashSet<>(competition.getCompetitionPicks())); // assuming it
                                                                                                      // accepts a set
        return notification;
    }

    public Competition createCompetition(CreateCompetitionData competitionData) {

        System.out.println("Received competitionData: " + competitionData);

        Competition existingCompetition = competitionRepository.findByName(competitionData.getName());
        if (existingCompetition != null) {
            throw new IllegalArgumentException("Competition with this name already exists");
        }

        Competition competition = new Competition(competitionData.getName());

        // First save the competition (to get its ID if needed)
        competition = competitionRepository.save(competition);

        competition.setCompetitionStatus(CompetitionStatus.SORTING);
        competition.setCurrentPick(1L);
        competition.setMaxMainCyclists(15);
        competition.setMaxReserveCyclists(5);

        Long pickOrder = 1L; // initialize pick order for users

        for (String email : competitionData.getUserEmails()) {
            User user = userRepository.findUserByEmail(email);
            if (user == null) {
                throw new IllegalArgumentException("User with email " + email + " not found.");
            } else {
                competition.getUsers().add(user);

                // Create a CompetitionPick for the user
                CompetitionPick pick = new CompetitionPick();
                pick.setCompetition(competition);
                pick.setUserId(user.getId());
                pick.setPickOrder(pickOrder++);
                competition.getCompetitionPicks().add(pick);

                // Create a user team for this user
                UserTeam userTeam = UserTeam.builder()
                        .name(user.getFirstName() + " " + user.getLastName() + "'s Team") // Or any naming logic
                        .competitionId(competition.getId())
                        .user(user)
                        .cyclistAssignments(new ArrayList<>()) // Empty initial list
                        .build();

                userTeamRepository.save(userTeam);
            }
        }

        for (String raceId : competitionData.getRaceIds()) {

            Race race = raceRepository.findById(Long.parseLong(raceId)).orElse(null);
            if (race == null) {
                System.out.println("Race with ID " + raceId + " not found.");
                continue;
            }
            competition.getRaces().add(race);
        }

        // Save updated competition with users, races, and picks
        return competitionRepository.save(competition);
    }

    public List<CompetitionModel> getCompetitionDTOs() {
        List<Competition> competitions = competitionRepository.findAll();
        List<CompetitionModel> competitionDTOs = new ArrayList<>();

        for (Competition competition : competitions) {
            CompetitionModel competitionDTO = new CompetitionModel();
            competitionDTO.setId(competition.getId());
            competitionDTO.setName(competition.getName());
            competitionDTOs.add(competitionDTO);
        }

        return competitionDTOs;
    }
}
