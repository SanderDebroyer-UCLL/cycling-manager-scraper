package ucll.be.procyclingscraper.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.repository.UserRepository;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Logic to fetch user from the database
        User user = userRepo.findUserByEmail(username);
        if (user == null) {
            throw new UsernameNotFoundException("Uh, oh! User with email " + username + " not found.");
        }

        return user;
    }

}