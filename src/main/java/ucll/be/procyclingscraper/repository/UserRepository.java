package ucll.be.procyclingscraper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ucll.be.procyclingscraper.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findUserById(long id);

    User findUserByUsername(String username);

    User findUserByEmail(String username);
        User findUserByName(String name);
}
