package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.model.RaceResult;

@Repository
public interface RacePointsRepository extends JpaRepository<RacePoints, Long> {
    List<RacePoints> findByCompetition_idAndRaceResult_Race_id(Long competitionId, Long raceId);

    boolean existsByRaceResultAndReason(RaceResult raceResult, String reason);
}
