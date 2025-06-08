package ucll.be.procyclingscraper.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findUserById(long id);

    @Query("SELECT new ucll.be.procyclingscraper.dto.UserDTO(" +
            "u.id, u.firstName, u.lastName, u.email, u.role, null) " +
            "FROM User u " +
            "WHERE u.email = :email")
    Optional<UserDTO> findUserDTOByEmail(@Param("email") String email);

    User findUserByEmail(String username);

    @Query("SELECT new ucll.be.procyclingscraper.dto.UserDTO(" +
            "u.id, u.firstName, u.lastName, u.email, u.role, null) " +
            "FROM User u")
    List<UserDTO> findAllBasicUsers();

}
