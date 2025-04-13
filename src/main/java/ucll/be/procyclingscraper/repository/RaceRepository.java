package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Race;

@Repository
public interface RaceRepository extends JpaRepository<Race,Long> {
    
}
