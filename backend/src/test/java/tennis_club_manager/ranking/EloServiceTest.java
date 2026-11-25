package tennis_club_manager.ranking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EloServiceTest {

    private final EloService eloService = new EloService();

    @Test
    void testWinnerRating() {
        int result = eloService.calculateNewRating(1000, 1000, 1.0);

        assertEquals(1016, result);
    }

    @Test
    void testLoserRating() {
        int result = eloService.calculateNewRating(1000, 1000, 0.0);

        assertEquals(984, result);
    }

    @Test
    void testDrawRating() {
        int result = eloService.calculateNewRating(1000, 1000, 0.5);

        assertEquals(1000, result);
    }
}
