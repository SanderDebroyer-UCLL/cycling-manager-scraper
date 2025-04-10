package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Stage;

@Repository
public interface StageRepository extends JpaRepository<Stage,Long> {
    
}
