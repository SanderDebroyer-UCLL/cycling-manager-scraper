package ucll.be.procyclingscraper.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RacePointsTest {

    private RacePoints racePoints;
    private User user;
    private Competition competition;
    private RaceResult raceResult;

    @BeforeEach
    public void init() {
        user = new User();
        user.setId(1L);
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setEmail("alice@example.com");

        competition = new Competition();
        competition.setId(2L);
        competition.setName("Tour de France");

        raceResult = new RaceResult();
        raceResult.setId(3L);
        raceResult.setPosition("1");

        racePoints = RacePoints.builder()
                .id(99L)
                .value(100)
                .reason("Some reason")
                .raceId(101L)
                .user(user)
                .competition(competition)
                .raceResult(raceResult)
                .build();
    }

    // Happy cases
    @Test
    public void testBuilderCreatesValidRacePoints() {
        assertNotNull(racePoints);
        assertEquals(99L, racePoints.getId());
        assertEquals(100, racePoints.getValue());
        assertEquals("Some reason", racePoints.getReason());
        assertEquals(101L, racePoints.getRaceId());
        assertEquals(user, racePoints.getUser());
        assertEquals(competition, racePoints.getCompetition());
        assertEquals(raceResult, racePoints.getRaceResult());
    }

    @Test
    public void testAllArgsConstructorWorks() {
        RacePoints rp = new RacePoints(9L, 70, "Winner", 555L, competition, raceResult, user);
        assertEquals(9L, rp.getId());
        assertEquals(70, rp.getValue());
        assertEquals("Winner", rp.getReason());
        assertEquals(555L, rp.getRaceId());
        assertEquals(competition, rp.getCompetition());
        assertEquals(raceResult, rp.getRaceResult());
        assertEquals(user, rp.getUser());
    }

    @Test
    public void testNoArgsConstructorInitializesNulls() {
        RacePoints rp = new RacePoints();
        assertNull(rp.getId());
        assertNull(rp.getRaceId());
        assertNull(rp.getUser());
        assertNull(rp.getReason());
        assertEquals(0, rp.getValue()); // default int
    }

    @Test
    public void testSettersWorkCorrectly() {
        RacePoints rp = new RacePoints();
        rp.setId(1L);
        rp.setValue(42);
        rp.setReason("Some Reason");
        rp.setRaceId(500L);
        rp.setCompetition(competition);
        rp.setUser(user);
        rp.setRaceResult(raceResult);

        assertEquals(1L, rp.getId());
        assertEquals(42, rp.getValue());
        assertEquals("Some Reason", rp.getReason());
        assertEquals(500L, rp.getRaceId());
        assertEquals(competition, rp.getCompetition());
        assertEquals(user, rp.getUser());
        assertEquals(raceResult, rp.getRaceResult());
    }

    // Unhappy cases
    @Test
    public void testNegativePointsValue() {
        racePoints.setValue(-10);
        assertTrue(racePoints.getValue() < 0, "Points value can be negative unless validation is added");
    }

    @Test
    public void testZeroPointsValue() {
        racePoints.setValue(0);
        assertEquals(0, racePoints.getValue());
    }

    @Test
    public void testNullReasonIsAllowed() {
        racePoints.setReason(null);
        assertNull(racePoints.getReason());
    }

    @Test
    public void testEmptyReasonString() {
        racePoints.setReason("");
        assertEquals("", racePoints.getReason());
    }

    @Test
    public void testBlankReasonString() {
        racePoints.setReason("   ");
        assertTrue(racePoints.getReason().isBlank());
    }

    @Test
    public void testUnusualCharactersInReason() {
        String weirdReason = "\n\t";
        racePoints.setReason(weirdReason);
        assertEquals(weirdReason, racePoints.getReason());
    }

    @Test
    public void testNullRaceId() {
        racePoints.setRaceId(null);
        assertNull(racePoints.getRaceId());
    }

    @Test
    public void testNullCompetition() {
        racePoints.setCompetition(null);
        assertNull(racePoints.getCompetition());
    }

    @Test
    public void testNullUser() {
        racePoints.setUser(null);
        assertNull(racePoints.getUser());
    }

    @Test
    public void testNullRaceResult() {
        racePoints.setRaceResult(null);
        assertNull(racePoints.getRaceResult());
    }
}

