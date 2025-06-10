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

        @Query("""
                            SELECT u.id, u.firstName, u.lastName, u.email, u.role,
                                   COALESCE(SUM(sp.value), 0), COALESCE(SUM(rp.value), 0)
                            FROM User u
                            LEFT JOIN u.stagePoints sp
                            LEFT JOIN u.racePoints rp
                            GROUP BY u.id, u.firstName, u.lastName, u.email, u.role
                        """)
        List<Object[]> findAllBasicUsersWithPointsRaw();

}
