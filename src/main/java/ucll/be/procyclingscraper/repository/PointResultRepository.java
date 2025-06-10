package ucll.be.procyclingscraper.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.PointResult;
import ucll.be.procyclingscraper.model.ScrapeResultType;
import ucll.be.procyclingscraper.model.Stage;
import java.util.List;


@Repository
public interface PointResultRepository extends JpaRepository<PointResult,Long> {    
        PointResult findByStageAndCyclistAndScrapeResultType(Stage stage, Cyclist cyclist, ScrapeResultType type);

        PointResult findByCyclistAndScrapeResultType(Cyclist cyclist, ScrapeResultType scrapeResultType);

        PointResult findByStageIdAndCyclistAndScrapeResultType(Long stageId, Cyclist cyclist, ScrapeResultType scrapeResultType);

        List<PointResult> findByStageIdAndScrapeResultType(Long stageId, ScrapeResultType scrapeResultType);

        @Query("SELECT p FROM PointResult p WHERE p.stage.id = :stageId AND p.scrapeResultType = :scrapeResultType ORDER BY p.point DESC")
        List<PointResult> findByStageIdAndScrapeResultTypeOrderByPointDesc(
            @Param("stageId") Long stageId,
            @Param("scrapeResultType") ScrapeResultType scrapeResultType
        );
}
