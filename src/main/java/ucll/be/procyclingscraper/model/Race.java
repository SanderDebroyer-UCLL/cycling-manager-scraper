package ucll.be.procyclingscraper.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Race {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String niveau;
    private String startDate;
    private String endDate;
    private Integer distance;
    private String raceUrl;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<Stage> stages = new ArrayList<>();

    @ManyToMany
    @JsonManagedReference
    @JoinTable(name = "race_cyclist", joinColumns = @JoinColumn(name = "race_id"), inverseJoinColumns = @JoinColumn(name = "cyclist_id"))
    private List<Cyclist> startList;

    private List<Long> youthCyclistsIDs = new ArrayList<>();

    @JsonIgnore
    @ManyToMany(mappedBy = "races")
    Set<Competition> competitions;

    public String getRaceUrl() {
        return raceUrl;
    }

    @OneToMany(mappedBy = "race")
    @JsonManagedReference
    private List<RaceResult> raceResult = new ArrayList<>();

    public void addRaceResult(RaceResult raceResult) {
        this.raceResult.add(raceResult);
    }

    public void addToYouthCyclistsIDs(Long cyclistId) {
        this.youthCyclistsIDs.add(cyclistId);
    }
}
