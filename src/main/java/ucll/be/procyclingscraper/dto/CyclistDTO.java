package ucll.be.procyclingscraper.dto;

import java.util.List;

import lombok.Value;

import ucll.be.procyclingscraper.model.Team;

@Value
public class CyclistDTO {
    private Long id;
    private String name;
    private int ranking;
    private int age;
    private String country;
    private String cyclistUrl;
    private Team team;
    private List<String> upcomingRaces;
    private String dnsReason;
}
