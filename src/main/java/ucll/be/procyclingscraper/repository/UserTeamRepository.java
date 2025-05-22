package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ucll.be.procyclingscraper.model.UserTeam;

@Repository
public interface UserTeamRepository extends JpaRepository<UserTeam,Long> {
    UserTeam findByName(String name);
}