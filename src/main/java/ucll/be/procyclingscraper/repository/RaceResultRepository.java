package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.model.Race;
import ucll.be.procyclingscraper.model.RaceResult;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {

    RaceResult findRaceResultByRaceAndCyclist(Race race, Cyclist cyclistId);

    List<RaceResult> findRaceResultByRace(Race race);

    List<RaceResult> findRaceResultByCyclist(Cyclist cyclist);
    
}
