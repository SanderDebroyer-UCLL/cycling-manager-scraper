package ucll.be.procyclingscraper.service;

import java.util.List;

import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.CreateUserData;
import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.model.Role;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    public List<UserDTO> getAllUsers() {
        List<Object[]> rawUsers = userRepository.findAllBasicUsersWithPointsRaw();

        return rawUsers.stream().map(row -> new UserDTO(
                (Long) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (Role) row[4],
                ((Long) row[5]).intValue() + ((Long) row[6]).intValue() // sum and cast to int
        )).toList();
    }

    public UserDTO getLoggedInUser(String email) {
        return userRepository.findUserDTOByEmail(email)
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getRole(),
                        0 // explicitly resetting totalPoints
                ))
                .orElse(null);
    }

    public User addUser(CreateUserData userData) throws ServiceException {
        if (userRepository.findUserDTOByEmail(userData.getEmail()).isPresent()) {
            throw new ServiceException("Uh, oh! User with email " + userData.getEmail() + " already exists.");
        }

        User newUser = new User();
        newUser.setFirstName(userData.getFirstName());
        newUser.setLastName(userData.getLastName());
        newUser.setEmail(userData.getEmail());
        newUser.setPassword(passwordEncoder.encode(userData.getPassword()));
        newUser.setRole(Role.USER);

        return userRepository.save(newUser);
    }

    public UserDTO mapToUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(), 0);
    }
}
