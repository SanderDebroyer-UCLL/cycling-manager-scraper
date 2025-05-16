package ucll.be.procyclingscraper.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "competition")
public class Competition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    public Competition(String name) {
        this.name = name;
    }

    @JsonBackReference("competition_user")
    @ManyToMany
    @JoinTable(name = "competition_user",
            joinColumns = @JoinColumn(name = "competition_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    public Set<User> users = new HashSet<>();

    @JsonBackReference("competition_race")
    @ManyToMany
    @JoinTable(name = "competition_race",
            joinColumns = @JoinColumn(name = "competition_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "race_id", 
            referencedColumnName = "id"))
    private Set<Race> races = new HashSet<>();}
