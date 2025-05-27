package ucll.be.procyclingscraper.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_team")
public class UserTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long competitionId;

    // Main team
    @ManyToMany
    @JoinTable(
        name = "user_team_main_cyclists",
        joinColumns = @JoinColumn(name = "user_team_id"),
        inverseJoinColumns = @JoinColumn(name = "cyclist_id")
    )
    private List<Cyclist> mainCyclists;

    // Reserve team
    @ManyToMany
    @JoinTable(
        name = "user_team_reserve_cyclists",
        joinColumns = @JoinColumn(name = "user_team_id"),
        inverseJoinColumns = @JoinColumn(name = "cyclist_id")
    )
    private List<Cyclist> reserveCyclists;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties("userTeams")
    private User user;
}
