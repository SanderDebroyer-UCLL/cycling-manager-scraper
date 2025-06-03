package ucll.be.procyclingscraper.repository;
    import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.TimeResult;


@Repository
public interface TimeResultRepository extends JpaRepository<TimeResult,Long> {    
    TimeResult findByStageAndCyclistAndScrapeResultType(Stage stage, Cyclist cyclist, ScrapeResultType type);
    List<TimeResult> findByStageAndScrapeResultType(Stage stage, ScrapeResultType type);
}
