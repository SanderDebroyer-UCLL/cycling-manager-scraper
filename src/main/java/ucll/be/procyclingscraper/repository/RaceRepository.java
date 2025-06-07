package ucll.be.procyclingscraper.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Race;

@Repository
public interface RaceRepository extends JpaRepository<Race, Long> {

    @Query("SELECT r FROM Race r LEFT JOIN FETCH r.stages WHERE r.id = :id")
    Optional<Race> findByIdWithStages(@Param("id") Long id);

    @Query("SELECT r FROM Race r LEFT JOIN FETCH r.stages LEFT JOIN FETCH r.competitions")
    List<Race> findAllWithStagesAndCompetitions();

    Race findByName(String raceName);

    List<Race> findRaceByStagesIsEmpty();

    @Query("SELECT r FROM Race r JOIN r.competitions c WHERE c.id = :competitionId ORDER BY r.startDate ASC")
    List<Race> findByCompetitionIdOrderByStartDateAsc(@Param("competitionId") Long competitionId);
}
