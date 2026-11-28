package tennis_club_manager.match;

import java.util.Map;
import java.util.UUID;

public interface MatchNotificationPort {

    void scheduleNotification(
            UUID clubId,
            UUID recipientUserId,
            String eventType,
            Map<String, Object> templateData
    );
}