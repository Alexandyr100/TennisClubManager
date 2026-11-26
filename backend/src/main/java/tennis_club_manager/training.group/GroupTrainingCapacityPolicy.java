
package tennis_club_manager.training.group;

public class GroupTrainingCapacityPolicy {

    public static final int MAX_MAIN_PLAYERS = 4;
    public static final int MAX_RESERVE_PLAYERS = 4;

    public enum Placement {
        MAIN,
        RESERVE,
        FULL
    }

    public static Placement choosePlacement(
            int mainPlayers,
            int reservePlayers
    ) {
        if (mainPlayers < 0 || mainPlayers > MAX_MAIN_PLAYERS
                || reservePlayers < 0
                || reservePlayers > MAX_RESERVE_PLAYERS) {
            throw new IllegalArgumentException(
                    "Invalid number of players"
            );
        }

        if (mainPlayers < MAX_MAIN_PLAYERS) {
            return Placement.MAIN;
        }

        if (reservePlayers < MAX_RESERVE_PLAYERS) {
            return Placement.RESERVE;
        }

        return Placement.FULL;
    }
}
