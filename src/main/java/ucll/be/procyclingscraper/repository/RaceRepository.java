package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Race;

@Repository
public interface RaceRepository extends JpaRepository<Race,Long> {

    Race findRaceById(int int1);

    Race findByName(String raceName);

    List<Race> findRaceByStagesIsEmpty();
   
}
