package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.StageResult;

@Repository
public interface StageResultRepository extends JpaRepository<StageResult, Long>  {
    
}
