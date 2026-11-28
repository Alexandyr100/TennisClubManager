package tennis_club_manager.match;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchDto(
        UUID id,
        UUID clubId,
        UUID creatorId,
        String creatorName,
        UUID opponentId,
        String opponentName,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        String level,
        MatchStatus status,
        String score,
        UUID winnerId,
        UUID confirmedByAdminId,
        OffsetDateTime createdAt
) {
}