package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.dto.StageDTO;
import ucll.be.procyclingscraper.model.Stage;

@Repository
public interface StageRepository extends JpaRepository<Stage, Long> {
    Stage findByName(String stageName);

    Stage findStageById(Long id);

    List<Stage> findAllByOrderByIdDesc();

    @Query("SELECT new ucll.be.procyclingscraper.dto.StageDTO(" +
            "s.id, s.name, s.departure, s.arrival, s.date, s.startTime, " +
            "s.distance, s.stageUrl, s.verticalMeters, s.parcoursType) " +
            "FROM Stage s")
    List<StageDTO> findAllBasicStages();
}
