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

    @ManyToMany
    @JoinTable(
        name = "user_team_cyclists",
        joinColumns = @JoinColumn(name = "user_team_id"),
        inverseJoinColumns = @JoinColumn(name = "cyclist_id")
    )
    private List<Cyclist> cyclists;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties("userTeams")  // ignore the back link in user when serializing user inside UserTeam
    private User user;

}
