package ucll.be.procyclingscraper.repository;
    import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.TimeResult;


@Repository
public interface TimeResultRepository extends JpaRepository<TimeResult,Long> {    
    TimeResult findByStageAndCyclist(Stage stage, Cyclist cyclist);
}
