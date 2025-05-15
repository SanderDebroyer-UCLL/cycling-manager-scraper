package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ucll.be.procyclingscraper.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findUserById(long id);
    User findUserByEmail(String username);
    Object findByUserId(String userId);
}
