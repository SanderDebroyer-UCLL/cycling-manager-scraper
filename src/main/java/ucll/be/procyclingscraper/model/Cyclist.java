package ucll.be.procyclingscraper.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Cyclist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int ranking;
    private int age;
    private String country;
    private String cyclistUrl;
    @ManyToOne
    @JoinColumn(name = "team_id")
    @JsonBackReference
    private Team team;
    
    private List<String> upcomingRaces;


    @OneToMany(mappedBy = "cyclist")
    private List<Result> results = new ArrayList<>();

    public void addRace(String raceName) {
        if (this.upcomingRaces == null) {
            this.upcomingRaces = new ArrayList<>();
        }
        this.upcomingRaces.add(raceName);
    }
    
}
