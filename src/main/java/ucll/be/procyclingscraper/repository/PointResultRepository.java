package ucll.be.procyclingscraper.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.PointResult;

@Repository
public interface PointResultRepository extends JpaRepository<PointResult,Long> {    
}
