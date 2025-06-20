package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.TeamModel;
import ucll.be.procyclingscraper.model.Team;
import ucll.be.procyclingscraper.repository.TeamRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class TeamService {

    @Autowired
    private TeamRepository teamRepository;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
    private static final int TEAM_LIMIT = 194;

    public List<Team> getTeams() {
        return teamRepository.findAll();
    }

    public List<Team> scrape() {
        List<Team> teams = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://w*+ww.procyclingstats.com/rankings/me/teams")
                    .userAgent(USER_AGENT)
                    .get();
            Elements rows = doc.select("tbody tr");

            for (Element row : rows) {
                if (teams.size() >= TEAM_LIMIT) {
                    break;
                }

                String name = row.select("td a").first().text();
                String rankingText = row.select("td").get(1).text();

                int ranking = 0;
                if (!rankingText.isEmpty()) {
                    try {
                        ranking = Integer.parseInt(rankingText);
                    } catch (NumberFormatException e) {
                        System.err.println("Failed to parse ranking: " + rankingText);
                    }
                }
                Team team = teamRepository.findByName(name);
                if (team == null) {
                    team = new Team();
                }

                team.setName(name);
                team.setRanking(ranking);

                String teamUrl = ("https://www.procyclingstats.com/" + row.select("td a").first().attr("href"));
                team.setTeamUrl(teamUrl);
                teamRepository.save(team);
                teams.add(team);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return teams;
    }

    public List<TeamModel> getTeamDTOs() {
        List<Team> teams = teamRepository.findAll();
        List<TeamModel> teamDTOs = new ArrayList<>();
        for (Team team : teams) {
            TeamModel teamModel = new TeamModel();
            teamModel.setId(team.getId());
            teamModel.setName(team.getName());
            teamModel.setTeamUrl(team.getTeamUrl());
            teamDTOs.add(teamModel);
        }
        return teamDTOs;
    }

}
