package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import ucll.be.procyclingscraper.model.Cyclist;

@Repository
public interface CyclistRepository extends JpaRepository<Cyclist, Long> {

    Cyclist findByName(String riderName);
    @Query(value = "SELECT c FROM Cyclist c WHERE LOWER(c.name) = LOWER(:name)")
    Cyclist findByNameIgnoreCase(@Param("name") String name);
    Cyclist findCyclistByName(String rider);
    Cyclist findByCyclistUrl(String riderUrl);
    

    @Query("SELECT c FROM Cyclist c JOIN c.results r JOIN r.stage s WHERE s.id = :stageId ORDER BY " +
           "CASE WHEN FUNCTION('regexp', r.position, '^[0-9]+$') = 1 THEN CAST(r.position AS integer) ELSE 9999 END")
    List<Cyclist> findCyclistsByStageId(@Param("stageId") Long stageId);
    
}   