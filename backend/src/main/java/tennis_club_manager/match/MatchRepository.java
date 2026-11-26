package tennis_club_manager.match;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface MatchRepository extends JpaRepository<TennisMatch, UUID> {

    List<TennisMatch> findByClubIdOrderByScheduledDateAscScheduledTimeAsc(UUID clubId);

    List<TennisMatch> findByClubIdAndStatusOrderByScheduledDateAscScheduledTimeAsc(UUID clubId, MatchStatus status);

    Optional<TennisMatch> findByIdAndClubId(UUID id, UUID clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TennisMatch m WHERE m.id = :id AND m.clubId = :clubId")
    Optional<TennisMatch> findByIdAndClubIdForUpdate(@Param("id") UUID id, @Param("clubId") UUID clubId);
}