package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.dto.CyclistStageResult;
import ucll.be.procyclingscraper.model.Cyclist;

@Repository
public interface CyclistRepository extends JpaRepository<Cyclist, Long> {

    Cyclist findByName(String riderName);
    @Query(value = "SELECT c FROM Cyclist c WHERE LOWER(c.name) = LOWER(:name)")
    Cyclist findByNameIgnoreCase(@Param("name") String name);
    Cyclist findCyclistByName(String rider);
    Cyclist findByCyclistUrl(String riderUrl);
    
    @Query(value = """
        SELECT name, stage_url, position
        FROM (
            SELECT DISTINCT c.name, s.stage_url, sr.position,
                CASE 
                WHEN sr.position ~ '^[0-9]+$' THEN CAST(sr.position AS INTEGER)
                ELSE 9999
                END AS position_order
            FROM cyclist c
            JOIN stage_result sr ON c.id = sr.cyclist_id
            JOIN stage s ON sr.stage_id = s.id
            WHERE s.id = :stageId
            AND sr.scrape_result_type = :scrapeResultType
        ) sub
        ORDER BY position_order
        """, nativeQuery = true)
    List<CyclistStageResult> findCyclistStageResults(
        @Param("stageId") Long stageId,
        @Param("scrapeResultType") String scrapeResultType
    );


    Cyclist findCyclistById(int id);
    
}   