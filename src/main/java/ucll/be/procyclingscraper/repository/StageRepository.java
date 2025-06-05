package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Stage;

@Repository
public interface StageRepository extends JpaRepository<Stage, Long> {
    Stage findByName(String stageName);

    Stage findStageById(Long id);

    List<Stage> findAllByOrderByIdDesc();
}
