package ucll.be.procyclingscraper.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import ucll.be.procyclingscraper.model.TimeResult;


@Repository
public interface TimeResultRepository extends JpaRepository<TimeResult,Long> {    
    TimeResult findByStageAndCyclistAndScrapeResultType(Stage stage, Cyclist cyclist, ScrapeResultType type);

    TimeResult findTimeResultByCyclistIdAndStageIdAndScrapeResultType(Long cyclistId, Long stageId,
            ScrapeResultType type);

    List<TimeResult> findTimeResultByStageIdAndScrapeResultType(long stageId, ScrapeResultType gc);

    @Query(
        "SELECT t FROM TimeResult t WHERE t.stage.id = :stageId AND t.scrapeResultType = :scrapeResultType AND t.cyclist.id IN :cyclistIds"
    )
    List<TimeResult> findTimeResultsByStageIdAndScrapeResultTypeAndCyclistIdIn(
        @Param("stageId") long stageId,
        @Param("scrapeResultType") ScrapeResultType scrapeResultType,
        @Param("cyclistIds") List<Long> cyclistIds
    );

    List<TimeResult> findByStageIdAndScrapeResultType(Long stageId, ScrapeResultType stage);
}
