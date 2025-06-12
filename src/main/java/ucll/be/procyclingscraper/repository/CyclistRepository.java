package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.dto.CyclistDTO;
import ucll.be.procyclingscraper.model.Cyclist;

@Repository
public interface CyclistRepository extends JpaRepository<Cyclist, Long> {

        Cyclist findByName(String riderName);

        @Query(value = "SELECT c FROM Cyclist c WHERE LOWER(c.name) = LOWER(:name)")
        Cyclist findByNameIgnoreCase(@Param("name") String name);

        Cyclist findCyclistByName(String rider);

        Cyclist findByCyclistUrl(String riderUrl);

        @Query("SELECT new ucll.be.procyclingscraper.dto.CyclistDTO(" +
                        "c.id, c.name, c.ranking, c.age, c.country, c.cyclistUrl, c.team, null, null) " +
                        "FROM Cyclist c JOIN c.team t")
        List<CyclistDTO> findAllBasicCyclists();

        @Query(value = "SELECT c.* FROM cyclist c " +
                        "JOIN stage_result r ON c.id = r.cyclist_id " +
                        "JOIN stage s ON s.id = r.stage_id " +
                        "WHERE s.id = :stageId " +
                        "ORDER BY " +
                        "CASE WHEN r.position ~ '^[0-9]+$' THEN CAST(r.position AS integer) ELSE 9999 END", nativeQuery = true)
        List<Cyclist> findCyclistsByStageId(@Param("stageId") Long stageId);

        @Query(value = "SELECT c.* FROM cyclist c " +
                        "JOIN race_result r ON c.id = r.cyclist_id " +
                        "JOIN race ra ON ra.id = r.race_id " +
                        "WHERE ra.id = :raceId " +
                        "ORDER BY " +
                        "CASE WHEN r.position ~ '^[0-9]+$' THEN CAST(r.position AS integer) ELSE 9999 END", nativeQuery = true)
        List<Cyclist> findCyclistsByRaceId(@Param("raceId") Long raceId);

        @Query(value = "SELECT c.* FROM cyclist c " +
                        "JOIN stage_result r ON c.id = r.cyclist_id " +
                        "JOIN stage s ON s.id = r.stage_id " +
                        "WHERE s.id = :stageId AND r.scrape_result_type = :scrapeResultType " +
                        "ORDER BY " +
                        "CASE WHEN r.position ~ '^[0-9]+$' THEN CAST(r.position AS integer) ELSE 9999 END", nativeQuery = true)
        List<Cyclist> findCyclistsByStageIdAndResultType(@Param("stageId") Long stageId,
                        @Param("scrapeResultType") String scrapeResultType);

        Cyclist findCyclistById(int int1);

}