package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.Competition;

@Repository
public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Competition findByName(String name);
        
}
