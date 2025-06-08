package ucll.be.procyclingscraper.service;

import java.util.List;

import org.hibernate.service.spi.ServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.CreateUserData;
import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.model.Role;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.repository.UserRepository;

@Service
public class UserService {

    private UserRepository userRepo;
    private PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDTO> getAllUsers() {
        return userRepo.findAllBasicUsers();
    }

    public UserDTO getLoggedInUser(String email) {
        return userRepo.findUserDTOByEmail(email)
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
        if (userRepo.findUserDTOByEmail(userData.getEmail()).isPresent()) {
            throw new ServiceException("Uh, oh! User with email " + userData.getEmail() + " already exists.");
        }

        User newUser = new User();
        newUser.setFirstName(userData.getFirstName());
        newUser.setLastName(userData.getLastName());
        newUser.setEmail(userData.getEmail());
        newUser.setPassword(passwordEncoder.encode(userData.getPassword()));
        newUser.setRole(Role.USER);

        return userRepo.save(newUser);
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
