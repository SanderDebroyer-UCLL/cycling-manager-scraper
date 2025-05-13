package ucll.be.procyclingscraper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtRes {

    private String jwtToken;
    private String email;
    private String role;

}