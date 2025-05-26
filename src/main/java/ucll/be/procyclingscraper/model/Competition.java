package ucll.be.procyclingscraper.model;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
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
    private int maxMainCyclists;
    private int maxReserveCyclists;
    private Long currentPick;

    @Enumerated(EnumType.STRING)
    private CompetitionStatus competitionStatus ;

    public Competition(String name) {
        this.name = name;
    }

    @OneToMany(mappedBy = "competition", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // to allow serialization of picks with competition
    private Set<CompetitionPick> competitionPicks = new HashSet<>();

    @ManyToMany
    @JsonBackReference("competition_user")
    @JoinTable(name = "competition_user", 
        joinColumns = @JoinColumn(name = "competition_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    public Set<User> users = new HashSet<>();

    @JsonManagedReference("competition_race")
    @ManyToMany
    @JoinTable(name = "competition_race",
            joinColumns = @JoinColumn(name = "competition_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "race_id", 
            referencedColumnName = "id"))
    private Set<Race> races = new HashSet<>();

    @OneToMany()
    private Set<Stage> stages = new HashSet<>();
}
