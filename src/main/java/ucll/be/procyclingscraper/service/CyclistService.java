package ucll.be.procyclingscraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import ucll.be.procyclingscraper.dto.CyclistDTO;
import ucll.be.procyclingscraper.model.Cyclist;
import ucll.be.procyclingscraper.repository.CyclistRepository;
import ucll.be.procyclingscraper.repository.TeamRepository;
import ucll.be.procyclingscraper.model.Team;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CyclistService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    @Autowired
    private CyclistRepository cyclistRepository;

    @Autowired
    private TeamRepository teamRepository;

    public List<CyclistDTO> getCyclists() {
        List<Cyclist> cyclists = cyclistRepository.findAll();
        return cyclists.stream()
                .map(cyclist -> new CyclistDTO(
                        cyclist.getId(),
                        cyclist.getName(),
                        cyclist.getRanking(),
                        cyclist.getAge(),
                        cyclist.getCountry(),
                        cyclist.getCyclistUrl(),
                        cyclist.getTeam(),
                        cyclist.getUpcomingRaces(),
                        ""))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Cyclist> scrapeCyclists() {
        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            return null;
        }

        List<Cyclist> cyclists = new ArrayList<>();

        for (Team team : teams) {
            System.out.println("Accessing URL: " + team.getTeamUrl());

            try {
                Document doc = Jsoup.connect(team.getTeamUrl())
                        .userAgent(USER_AGENT)
                        .get();
                Element ridersDiv = doc.selectFirst("div.ridersTab");

                if (ridersDiv != null) {
                    Elements cyclistRows = ridersDiv.select("tbody tr");

                    for (Element row : cyclistRows) {
                        Element nameCell = row.selectFirst("td:nth-child(2) a");
                        if (nameCell != null) {
                            String name = nameCell.text().trim();
                            if (!name.isEmpty()) {
                                String cyclistUrl = "https://www.procyclingstats.com/" + nameCell.attr("href");
                                System.out.println(cyclistUrl);
                                Cyclist cyclist = scrapeCyclistDetails(cyclistUrl);
                                if (cyclist != null) {
                                    cyclist.setCyclistUrl(cyclistUrl);
                                    cyclist.setTeam(team);
                                    System.out.println(cyclist);
                                    cyclists.add(cyclist);
                                    cyclistRepository.save(cyclist);
                                }
                            }
                        }
                    }
                } else {
                    System.err.println("Riders div not found for team: " + team.getName());
                }

            } catch (IOException e) {
                System.err.println("Error accessing URL: " + team.getTeamUrl());
                e.printStackTrace();
            }
        }

        return cyclists;
    }

    public Cyclist scrapeCyclistDetails(String riderUrl) {
        Cyclist cyclist = cyclistRepository.findByCyclistUrl(riderUrl);
        if (cyclist == null) {
            cyclist = new Cyclist();
        }

        try {
            Document doc = Jsoup.connect(riderUrl)
                    .userAgent(USER_AGENT)
                    .get();

            String name = doc.select("body > div.wrapper > div.content > div.page-title > div.main > h1").text();
            cyclist.setName(name);
            System.out.println(name);

            Element ageElement = doc.select(
                    "body > div.wrapper > div.content > div.page-content.page-object.default > div:nth-child(2) > div.left.w75.mb_w100 > div.left.w50.mb_w100 > div.rdr-info-cont")
                    .first();
            if (ageElement != null) {
                String ageText = ageElement.ownText().trim();
                String age = extractAge(ageText);
                if (age != null) {
                    try {
                        cyclist.setAge(Integer.parseInt(age));
                        System.out.println(age);
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing age: " + age);
                    }
                }
            } else {
                System.err.println("Could not get the cyclist age for URL: " + riderUrl);
            }

            Element rankingElement = getUciRanking(doc);
            if (rankingElement != null) {
                String rankingText = rankingElement.ownText().trim();
                try {
                    cyclist.setRanking(Integer.parseInt(rankingText));
                    System.out.println("Ranking: " + rankingText);
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing ranking: " + rankingText);
                }
            } else {
                System.err.println("Could not find the ranking element for URL: " + riderUrl);
            }

            Element countryElement = doc.select(
                    "body > div.wrapper > div.content > div.page-content.page-object.default > div:nth-child(2) > div.left.w75.mb_w100 > div.left.w50.mb_w100 > div.rdr-info-cont > a")
                    .first();
            if (countryElement != null) {
                String countryText = countryElement.ownText().trim();
                System.out.println("Country: " + countryText);
                cyclist.setCountry(countryText);
            } else {
                System.err.println("Could not find the country element for URL: " + riderUrl);
            }

            // getUpcomingRaces();

        } catch (IOException e) {
            System.err.println("Error accessing rider URL: " + riderUrl);
            e.printStackTrace();
        }
        return cyclist;
    }

    public CyclistDTO mapToCyclistDTO(Cyclist cyclist) {
        return new CyclistDTO(
                cyclist.getId(),
                cyclist.getName(),
                cyclist.getRanking(),
                cyclist.getAge(),
                cyclist.getCountry(),
                cyclist.getCyclistUrl(),
                cyclist.getTeam(), // or map to a TeamDTO if needed
                cyclist.getUpcomingRaces(),
                "");
    }

    private String extractAge(String dobText) {
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(dobText);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Element getUciRanking(Document doc) {
        Element titleElement = doc.select("ul.list.horizontal.rdr-rankings li:nth-child(1) .title a").first();
        if (titleElement == null) {
            titleElement = doc.select("ul.list.horizontal.rdr-rankings li:nth-child(1) .title a").first();
        }
        System.out.println("Title Element: " + titleElement);

        if (titleElement != null) {
            String titleText = titleElement.ownText().trim();
            System.out.println("Title Text: " + titleText);
            if ("UCI World".equalsIgnoreCase(titleText)) {
                return doc.select("ul.list.horizontal.rdr-rankings li:nth-child(1) .rnk").first();
            } else if ("All time".equalsIgnoreCase(titleText)) {
                return doc.select("ul.list.horizontal.rdr-rankings li:nth-child(2) .rnk").first();
            } else if ("PCS Ranking".equalsIgnoreCase(titleText)) {
                return doc.select("ul.list.horizontal.rdr-rankings li:nth-child(1) .rnk").first();
            }
        }
        System.err.println("Ranking element not found. Please check the CSS selector.");
        return null;
    }

    public Cyclist searchCyclist(String riderName) {
        System.out.println("Extracted Rider Name: " + riderName);

        String[] nameParts = riderName.trim().split("\\s+");

        for (int i = 1; i < nameParts.length; i++) {
            String firstName = String.join(" ", Arrays.copyOfRange(nameParts, i, nameParts.length));
            String lastName = String.join(" ", Arrays.copyOfRange(nameParts, 0, i));
            String fixedName = firstName + " " + lastName;

            // System.out.println("Trying rearranged name: " + fixedName);

            Cyclist cyclist = cyclistRepository.findByNameIgnoreCase(fixedName);
            if (cyclist != null) {
                System.out.println("Found cyclist: " + fixedName);
                return cyclist;
            }
        }

        System.out.println("No cyclist found for name: " + riderName);
        return null;
    }

}
