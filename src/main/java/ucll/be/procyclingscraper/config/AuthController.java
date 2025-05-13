package ucll.be.procyclingscraper.config;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.service.spi.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ucll.be.procyclingscraper.model.JwtReq;
import ucll.be.procyclingscraper.model.JwtRes;
import ucll.be.procyclingscraper.model.User;
import ucll.be.procyclingscraper.security.JwtHelper;
import ucll.be.procyclingscraper.service.UserService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private UserService userService;

    // @Autowired
    private Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/login")
    public ResponseEntity<JwtRes> login(@RequestBody JwtReq request) throws UsernameNotFoundException {

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        
        this.doAuthenticate(request.getEmail(), request.getPassword());
        String token = this.jwtHelper.generateToken(userDetails);

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String role = authorities.get(0);

        JwtRes response = JwtRes.builder()
                .jwtToken(token)
                .email(userDetails.getUsername())
                .role(role)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private void doAuthenticate(String email, String password) {

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, password);
        try {
            authenticationManager.authenticate(authentication);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid password!!");
        }
    }

    @PostMapping("/create-user")
    public User createUser(@RequestBody @Valid User user) throws ServiceException {
        return userService.addUser(user);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        System.out.println(errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, String>> handleServiceException(ServiceException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({ MethodArgumentNotValidException.class })
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            errorResponse.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
}