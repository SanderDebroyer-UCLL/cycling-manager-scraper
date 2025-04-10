package ucll.be.procyclingscraper.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import ucll.be.procyclingscraper.model.Cyclist;

@Repository
public interface CyclistRepository extends JpaRepository<Cyclist, Long> {
    
}