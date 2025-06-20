package ucll.be.procyclingscraper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.UserTeam;

@Repository
public interface UserTeamRepository extends JpaRepository<UserTeam, Long> {
    UserTeam findByName(String name);

    List<UserTeam> findByCompetitionId(Long competitionId);

    UserTeam findByCompetitionIdAndUser_Id(Long competitionId, Long userId);
}