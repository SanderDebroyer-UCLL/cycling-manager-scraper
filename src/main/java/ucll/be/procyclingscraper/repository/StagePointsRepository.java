package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.StagePoints;

@Repository
public interface StagePointsRepository extends JpaRepository<StagePoints, Long> {
}
