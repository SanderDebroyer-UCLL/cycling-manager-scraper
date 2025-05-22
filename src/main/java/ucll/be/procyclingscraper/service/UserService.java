package ucll.be.procyclingscraper.service;

import java.util.List;

import org.hibernate.service.spi.ServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.CreateUserData;
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

    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    public User getLoggedInUser(String email) {
        return userRepo.findUserByEmail(email);
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
}
