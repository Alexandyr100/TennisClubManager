package tennis_club_manager.ranking;

import java.util.UUID;

public record PlayerRankingDto(
        int rank,
        UUID playerId,
        String firstName,
        String lastName,
        int eloRating
) {
}