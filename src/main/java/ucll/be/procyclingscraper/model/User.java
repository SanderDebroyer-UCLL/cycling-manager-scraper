package ucll.be.procyclingscraper.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    @JsonIgnore
    private String password;
    private Role role = Role.USER;

    @ManyToMany(mappedBy = "users")
    @JsonManagedReference("competition_user")
    Set<Competition> competitions;

    public User() {
    }

    @OneToMany(mappedBy = "user")
    @JsonIgnoreProperties("user") // ignore user inside userTeams when serializing User
    private List<UserTeam> userTeams;

    @OneToMany(mappedBy = "user")
    @JsonManagedReference("user_stage_points")
    private List<StagePoints> stagePoints;

    @OneToMany(mappedBy = "user")
    @JsonManagedReference("user_race_points")
    private List<RacePoints> racePoints;

    public User(String firstName, String lastName, String email, String password) {
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        setPassword(password);
        this.role = Role.USER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    // this has to be done for spring security
    @Override
    public String getUsername() {
        return this.email;
    }
}
