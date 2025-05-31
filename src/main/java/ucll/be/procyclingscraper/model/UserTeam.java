package ucll.be.procyclingscraper.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Setter
@Getter
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

    @OneToMany(mappedBy = "userTeam", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<CyclistAssignment> cyclistAssignments;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties("userTeams")
    private User user;
}
