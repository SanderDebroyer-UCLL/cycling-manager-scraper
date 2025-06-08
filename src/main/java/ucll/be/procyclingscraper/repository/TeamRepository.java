package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.dto.TeamDTO;
import ucll.be.procyclingscraper.model.Team;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Team findByName(String teamName);

    @Query("SELECT new ucll.be.procyclingscraper.dto.TeamDTO(t.teamUrl, t.name) FROM Team t")
    List<TeamDTO> findAllTeams();

}