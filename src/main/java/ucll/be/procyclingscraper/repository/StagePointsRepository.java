package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.StagePoints;
import ucll.be.procyclingscraper.model.StageResult;

@Repository
public interface StagePointsRepository extends JpaRepository<StagePoints, Long> {
    List<StagePoints> findByCompetition_idAndStageResult_Stage_id(Long competitionId, Long stageId);

    boolean existsByStageResultAndReason(StageResult stageResult, String reason);
}
