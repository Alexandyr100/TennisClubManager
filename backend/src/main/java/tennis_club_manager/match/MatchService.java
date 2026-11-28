package tennis_club_manager.match;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tennis_club_manager.match.MatchPlayerPort.PlayerSummary;
import tennis_club_manager.ranking.EloService;
import tennis_club_manager.ranking.PlayerRankingDto;

@Service
public class MatchService {

    public static final String EVENT_MATCH_ACCEPTED = "MATCH_ACCEPTED";
    public static final String EVENT_MATCH_UPDATED = "MATCH_UPDATED";
    public static final String EVENT_MATCH_RESULT_SUBMITTED = "MATCH_RESULT_SUBMITTED";
    public static final String EVENT_MATCH_CONFIRMED = "MATCH_CONFIRMED";
    public static final String EVENT_MATCH_CANCELLED = "MATCH_CANCELLED";

    private final MatchRepository matchRepository;
    private final EloService eloService;
    private final ObjectProvider<MatchPlayerPort> playerPortProvider;
    private final ObjectProvider<MatchNotificationPort> notificationPortProvider;

    public MatchService(
            MatchRepository matchRepository,
            EloService eloService,
            ObjectProvider<MatchPlayerPort> playerPortProvider,
            ObjectProvider<MatchNotificationPort> notificationPortProvider
    ) {
        this.matchRepository = matchRepository;
        this.eloService = eloService;
        this.playerPortProvider = playerPortProvider;
        this.notificationPortProvider = notificationPortProvider;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getClubMatches(UUID clubId, MatchStatus status) {
        List<TennisMatch> matches = (status == null)
                ? matchRepository.findByClubIdOrderByScheduledDateAscScheduledTimeAsc(clubId)
                : matchRepository.findByClubIdAndStatusOrderByScheduledDateAscScheduledTimeAsc(clubId, status);

        return matches.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchDto getMatchById(UUID clubId, UUID matchId) {
        TennisMatch match = matchRepository.findByIdAndClubId(matchId, clubId)
                .orElseThrow(() -> MatchException.notFound("Мачът не е намерен в този клуб."));
        return toDto(match);
    }

    @Transactional
    public MatchDto createMatch(
            UUID clubId,
            UUID creatorId,
            String role,
            LocalDate scheduledDate,
            LocalTime scheduledTime,
            String level
    ) {
        requirePlayerRole(role);
        validateSchedule(scheduledDate, scheduledTime);
        if (level == null || level.isBlank()) {
            throw MatchException.badRequest("Нивото на мача е задължително.");
        }

        TennisMatch match = new TennisMatch(
                clubId,
                creatorId,
                scheduledDate,
                scheduledTime,
                level.trim()
        );
        TennisMatch saved = matchRepository.save(match);
        return toDto(saved);
    }

    @Transactional
    public MatchDto acceptMatch(UUID clubId, UUID matchId, UUID playerId, String role) {
        requirePlayerRole(role);

        TennisMatch match = lockMatchInClub(matchId, clubId);

        if (match.getStatus() != MatchStatus.open || match.getOpponentId() != null) {
            throw MatchException.conflict("Този мач вече е приет или не е отворен за записване.");
        }
        if (match.getCreatorId().equals(playerId)) {
            throw MatchException.conflict("Не можете да приемете собствената си покана за мач.");
        }

        match.setOpponentId(playerId);
        match.setStatus(MatchStatus.accepted);
        TennisMatch saved = matchRepository.save(match);

        notifyUser(clubId, saved.getCreatorId(), EVENT_MATCH_ACCEPTED, buildMatchPayload(saved, "Поканата за мач беше приета."));
        notifyUser(clubId, playerId, EVENT_MATCH_ACCEPTED, buildMatchPayload(saved, "Успешно приехте поканата за мач."));

        return toDto(saved);
    }

    @Transactional
    public MatchDto updateMatchSchedule(
            UUID clubId,
            UUID matchId,
            UUID currentUserId,
            LocalDate newDate,
            LocalTime newTime
    ) {
        validateSchedule(newDate, newTime);

        TennisMatch match = lockMatchInClub(matchId, clubId);

        if (!match.getCreatorId().equals(currentUserId)) {
            throw MatchException.forbidden("Само създателят на мача може да променя датата и часа.");
        }
        if (match.getStatus() != MatchStatus.open && match.getStatus() != MatchStatus.accepted) {
            throw MatchException.conflict("Дата и час могат да се променят само за мач със статус open или accepted.");
        }

        match.setScheduledDate(newDate);
        match.setScheduledTime(newTime);
        TennisMatch saved = matchRepository.save(match);

        Map<String, Object> payload = buildMatchPayload(saved, "Датата или часът на мача бяха променени.");
        notifyUser(clubId, saved.getCreatorId(), EVENT_MATCH_UPDATED, payload);
        if (saved.getOpponentId() != null) {
            notifyUser(clubId, saved.getOpponentId(), EVENT_MATCH_UPDATED, payload);
        }

        return toDto(saved);
    }

    @Transactional
    public MatchDto submitResult(
            UUID clubId,
            UUID matchId,
            UUID currentUserId,
            UUID winnerId,
            String score
    ) {
        if (winnerId == null || score == null || score.isBlank()) {
            throw MatchException.badRequest("Победителят и резултатът са задължителни.");
        }

        TennisMatch match = lockMatchInClub(matchId, clubId);

        if (!isParticipant(match, currentUserId)) {
            throw MatchException.forbidden("Само участник в мача може да въведе резултат.");
        }
        if (match.getStatus() != MatchStatus.accepted) {
            throw MatchException.conflict("Резултат може да се въведе само за приет мач (accepted).");
        }
        if (!winnerId.equals(match.getCreatorId()) && !winnerId.equals(match.getOpponentId())) {
            throw MatchException.badRequest("Победителят трябва да е един от двамата участници в мача.");
        }

        match.setWinnerId(winnerId);
        match.setScore(score.trim());
        match.setStatus(MatchStatus.pending_confirmation);
        TennisMatch saved = matchRepository.save(match);

        Map<String, Object> payload = buildMatchPayload(saved, "Въведен е резултат и очаква потвърждение от администратор.");
        notifyUser(clubId, saved.getCreatorId(), EVENT_MATCH_RESULT_SUBMITTED, payload);
        if (saved.getOpponentId() != null) {
            notifyUser(clubId, saved.getOpponentId(), EVENT_MATCH_RESULT_SUBMITTED, payload);
        }

        return toDto(saved);
    }

    @Transactional
    public MatchDto confirmResult(UUID clubId, UUID matchId, UUID adminId, String role) {
        if (!"admin".equalsIgnoreCase(role)) {
            throw MatchException.forbidden("Само администратор на клуба може да потвърждава резултати.");
        }

        TennisMatch match = lockMatchInClub(matchId, clubId);

        if (match.getStatus() != MatchStatus.pending_confirmation || match.getConfirmedByAdminId() != null) {
            throw MatchException.conflict("Мачът не чака потвърждение или резултатът вече е потвърден.");
        }
        if (match.getOpponentId() == null || match.getWinnerId() == null) {
            throw MatchException.conflict("Липсва противник или победител за потвърждение.");
        }

        MatchPlayerPort playerPort = requirePlayerPort();
        PlayerSummary creator = playerPort.findByIdAndClubId(match.getCreatorId(), clubId)
                .orElseThrow(() -> MatchException.notFound("Създателят на мача не е намерен в клуба."));
        PlayerSummary opponent = playerPort.findByIdAndClubId(match.getOpponentId(), clubId)
                .orElseThrow(() -> MatchException.notFound("Противникът в мача не е намерен в клуба."));

        boolean creatorWon = match.getWinnerId().equals(creator.id());
        double creatorActualScore = creatorWon ? 1.0 : 0.0;
        double opponentActualScore = creatorWon ? 0.0 : 1.0;

        int newCreatorElo = eloService.calculateNewRating(creator.eloRating(), opponent.eloRating(), creatorActualScore);
        int newOpponentElo = eloService.calculateNewRating(opponent.eloRating(), creator.eloRating(), opponentActualScore);

        playerPort.updateEloRating(creator.id(), clubId, newCreatorElo);
        playerPort.updateEloRating(opponent.id(), clubId, newOpponentElo);

        match.setStatus(MatchStatus.completed);
        match.setConfirmedByAdminId(adminId);
        TennisMatch saved = matchRepository.save(match);

        Map<String, Object> payload = buildMatchPayload(saved, "Резултатът от мача е потвърден и Elo точките са обновени.");
        notifyUser(clubId, creator.id(), EVENT_MATCH_CONFIRMED, payload);
        notifyUser(clubId, opponent.id(), EVENT_MATCH_CONFIRMED, payload);

        return toDto(saved);
    }

    @Transactional
    public MatchDto cancelMatch(UUID clubId, UUID matchId, UUID currentUserId, String role) {
        TennisMatch match = lockMatchInClub(matchId, clubId);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        if (!isAdmin && !isParticipant(match, currentUserId)) {
            throw MatchException.forbidden("Само участник в мача или администратор може да го отмени.");
        }
        if (match.getStatus() == MatchStatus.completed || match.getStatus() == MatchStatus.cancelled) {
            throw MatchException.conflict("Завършен или вече отменен мач не може да бъде отменен.");
        }

        match.setStatus(MatchStatus.cancelled);
        TennisMatch saved = matchRepository.save(match);

        Map<String, Object> payload = buildMatchPayload(saved, "Мачът беше отменен.");
        if (!saved.getCreatorId().equals(currentUserId)) {
            notifyUser(clubId, saved.getCreatorId(), EVENT_MATCH_CANCELLED, payload);
        }
        if (saved.getOpponentId() != null && !saved.getOpponentId().equals(currentUserId)) {
            notifyUser(clubId, saved.getOpponentId(), EVENT_MATCH_CANCELLED, payload);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PlayerRankingDto> getClubRankings(UUID clubId) {
        MatchPlayerPort playerPort = playerPortProvider.getIfAvailable();
        if (playerPort == null) {
            return List.of();
        }

        List<PlayerSummary> players = playerPort.findPlayersByClubIdOrderByEloDesc(clubId);
        List<PlayerRankingDto> rankings = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            PlayerSummary p = players.get(i);
            rankings.add(new PlayerRankingDto(
                    i + 1,
                    p.id(),
                    p.firstName(),
                    p.lastName(),
                    p.eloRating()
            ));
        }
        return rankings;
    }

    private TennisMatch lockMatchInClub(UUID matchId, UUID clubId) {
        return matchRepository.findByIdAndClubIdForUpdate(matchId, clubId)
                .orElseThrow(() -> MatchException.notFound("Мачът не е намерен в този клуб."));
    }

    private void requirePlayerRole(String role) {
        if (!"player".equalsIgnoreCase(role)) {
            throw MatchException.forbidden("Само потребител с роля player може да извърши това действие.");
        }
    }

    private void validateSchedule(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            throw MatchException.badRequest("Датата и часът на мача са задължителни.");
        }
    }

    private boolean isParticipant(TennisMatch match, UUID userId) {
        return match.getCreatorId().equals(userId)
                || (match.getOpponentId() != null && match.getOpponentId().equals(userId));
    }

    private MatchPlayerPort requirePlayerPort() {
        MatchPlayerPort port = playerPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("MatchPlayerPort не е конфигуриран.");
        }
        return port;
    }

    private void notifyUser(UUID clubId, UUID recipientUserId, String eventType, Map<String, Object> payload) {
        MatchNotificationPort notificationPort = notificationPortProvider.getIfAvailable();
        if (notificationPort != null && recipientUserId != null) {
            notificationPort.scheduleNotification(clubId, recipientUserId, eventType, payload);
        }
    }

    private Map<String, Object> buildMatchPayload(TennisMatch match, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", match.getId());
        data.put("clubId", match.getClubId());
        data.put("scheduledDate", match.getScheduledDate().toString());
        data.put("scheduledTime", match.getScheduledTime().toString());
        data.put("level", match.getLevel());
        data.put("status", match.getStatus().name());
        if (match.getScore() != null) {
            data.put("score", match.getScore());
        }
        if (match.getWinnerId() != null) {
            data.put("winnerId", match.getWinnerId());
        }
        data.put("reason", reason);
        return data;
    }

    private MatchDto toDto(TennisMatch match) {
        String creatorName = resolvePlayerName(match.getCreatorId(), match.getClubId());
        String opponentName = resolvePlayerName(match.getOpponentId(), match.getClubId());

        return new MatchDto(
                match.getId(),
                match.getClubId(),
                match.getCreatorId(),
                creatorName,
                match.getOpponentId(),
                opponentName,
                match.getScheduledDate(),
                match.getScheduledTime(),
                match.getLevel(),
                match.getStatus(),
                match.getScore(),
                match.getWinnerId(),
                match.getConfirmedByAdminId(),
                match.getCreatedAt()
        );
    }

    private String resolvePlayerName(UUID playerId, UUID clubId) {
        if (playerId == null) {
            return null;
        }
        MatchPlayerPort playerPort = playerPortProvider.getIfAvailable();
        if (playerPort == null) {
            return null;
        }
        return playerPort.findByIdAndClubId(playerId, clubId)
                .map(PlayerSummary::fullName)
                .orElse(null);
    }
}