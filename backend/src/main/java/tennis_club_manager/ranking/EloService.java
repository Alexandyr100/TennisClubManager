package tennis_club_manager.ranking;

import org.springframework.stereotype.Service;

@Service
public class EloService {

    private static final int K = 32;

    public int calculateNewRating(
            int playerRating,
            int opponentRating,
            double actualScore) {

        double expected = 1.0 /
                (1.0 + Math.pow(10,
                    (opponentRating - playerRating) / 400.0));

        return (int) Math.round(
                playerRating + K * (actualScore - expected)
        );
    }
}