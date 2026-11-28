package tennis_club_manager.match;

import org.springframework.http.HttpStatus;

public class MatchException extends RuntimeException {

    private final HttpStatus status;

    public MatchException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static MatchException badRequest(String message) {
        return new MatchException(HttpStatus.BAD_REQUEST, message);
    }

    public static MatchException forbidden(String message) {
        return new MatchException(HttpStatus.FORBIDDEN, message);
    }

    public static MatchException notFound(String message) {
        return new MatchException(HttpStatus.NOT_FOUND, message);
    }

    public static MatchException conflict(String message) {
        return new MatchException(HttpStatus.CONFLICT, message);
    }
}