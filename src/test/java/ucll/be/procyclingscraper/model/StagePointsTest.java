package ucll.be.procyclingscraper.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StagePointsTest {

    private StagePoints stagePoints;
    private User user;
    private Competition competition;
    private StageResult stageResult;

    @BeforeEach
    public void init() {
        user = new User();
        user.setId(10L);
        user.setFirstName("Bob");
        user.setLastName("Johnson");
        user.setEmail("bob@example.com");

        competition = new Competition();
        competition.setId(20L);
        competition.setName("Tour de France");

        stageResult = new StageResult() {}; 
        stageResult.setId(30L);
        stageResult.setPosition("2nd");

        stagePoints = new StagePoints();
        stagePoints.setId(1L);
        stagePoints.setValue(50);
        stagePoints.setReason("Mountain Points");
        stagePoints.setStageId(100L);
        stagePoints.setUser(user);
        stagePoints.setCompetition(competition);
        stagePoints.setStageResult(stageResult);
    }

    // Happy cases
    @Test
    public void testStagePointsFieldsAreSetCorrectly() {
        assertEquals(1L, stagePoints.getId());
        assertEquals(50, stagePoints.getValue());
        assertEquals("Mountain Points", stagePoints.getReason());
        assertEquals(100L, stagePoints.getStageId());
        assertEquals(user, stagePoints.getUser());
        assertEquals(competition, stagePoints.getCompetition());
        assertEquals(stageResult, stagePoints.getStageResult());
    }

    @Test
    public void testNoArgsConstructorDefaults() {
        StagePoints empty = new StagePoints();
        assertNull(empty.getId());
        assertEquals(0, empty.getValue());
        assertNull(empty.getReason());
        assertNull(empty.getStageId());
        assertNull(empty.getUser());
        assertNull(empty.getCompetition());
        assertNull(empty.getStageResult());
    }

    @Test
    public void testSettersAndGettersWorkCorrectly() {
        StagePoints sp = new StagePoints();
        sp.setId(5L);
        sp.setValue(35);
        sp.setReason("Sprint Finish");
        sp.setStageId(200L);
        sp.setUser(user);
        sp.setCompetition(competition);
        sp.setStageResult(stageResult);

        assertEquals(5L, sp.getId());
        assertEquals(35, sp.getValue());
        assertEquals("Sprint Finish", sp.getReason());
        assertEquals(200L, sp.getStageId());
        assertEquals(user, sp.getUser());
        assertEquals(competition, sp.getCompetition());
        assertEquals(stageResult, sp.getStageResult());
    }

    // Unhappy cases
    @Test
    public void testNegativePointsValueAllowedUnlessValidated() {
        stagePoints.setValue(-20);
        assertEquals(-20, stagePoints.getValue(), "Negative values are allowed unless explicitly validated");
    }

    @Test
    public void testZeroValueIsValid() {
        stagePoints.setValue(0);
        assertEquals(0, stagePoints.getValue());
    }

    @Test
    public void testNullReasonIsAllowed() {
        stagePoints.setReason(null);
        assertNull(stagePoints.getReason());
    }

    @Test
    public void testEmptyReasonString() {
        stagePoints.setReason("");
        assertEquals("", stagePoints.getReason());
    }

    @Test
    public void testWhitespaceReasonString() {
        stagePoints.setReason("   ");
        assertTrue(stagePoints.getReason().isBlank());
    }

    @Test
    public void testWeirdCharactersInReason() {
        String emojiReason = "\n\t";
        stagePoints.setReason(emojiReason);
        assertEquals(emojiReason, stagePoints.getReason());
    }

    @Test
    public void testNullStageIdIsAllowed() {
        stagePoints.setStageId(null);
        assertNull(stagePoints.getStageId());
    }

    @Test
    public void testNullUserIsAllowed() {
        stagePoints.setUser(null);
        assertNull(stagePoints.getUser());
    }

    @Test
    public void testNullCompetitionIsAllowed() {
        stagePoints.setCompetition(null);
        assertNull(stagePoints.getCompetition());
    }

    @Test
    public void testNullStageResultIsAllowed() {
        stagePoints.setStageResult(null);
        assertNull(stagePoints.getStageResult());
    }
 
}
