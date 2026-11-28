package tennis_club_manager.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchPlayerPort {

    record PlayerSummary(
            UUID id,
            UUID clubId,
            String firstName,
            String lastName,
            String role,
            int eloRating
    ) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    Optional<PlayerSummary> findByIdAndClubId(UUID playerId, UUID clubId);

    List<PlayerSummary> findPlayersByClubIdOrderByEloDesc(UUID clubId);

    void updateEloRating(UUID playerId, UUID clubId, int newEloRating);
}