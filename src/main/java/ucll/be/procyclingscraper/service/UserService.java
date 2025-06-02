package ucll.be.procyclingscraper.service;

import java.util.List;

import org.hibernate.service.spi.ServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.CreateUserData;
import ucll.be.procyclingscraper.dto.UserDTO;
import ucll.be.procyclingscraper.model.RacePoints;
import ucll.be.procyclingscraper.model.Role;
import ucll.be.procyclingscraper.model.StagePoints;
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
        return userRepo.findAll().stream()
                .map(user -> new UserDTO(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                        user.getRole(), user.getRacePoints().stream()
                                .mapToInt(RacePoints::getValue)
                                .sum()
                                + user.getStagePoints().stream()
                                        .mapToInt(StagePoints::getValue)
                                        .sum()))
                .toList();
    }

    public UserDTO getLoggedInUser(String email) {
        User user = userRepo.findUserByEmail(email);
        if (user == null) {
            return null;
        }
        return new UserDTO(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole(), 0);
    }

    public User addUser(CreateUserData userData) throws ServiceException {
        User existingUser = userRepo.findUserByEmail(userData.getEmail());
        if (existingUser != null) {
            throw new ServiceException("Uh, oh! User with email " + existingUser.getEmail() + " already exists.");
        }

        User newUser = new User();

        newUser.setFirstName(userData.getFirstName());
        newUser.setLastName(userData.getLastName());
        newUser.setEmail(userData.getEmail());
        newUser.setPassword(passwordEncoder.encode(userData.getPassword()));
        newUser.setRole(Role.USER);

        userRepo.save(newUser);

        return newUser;
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
