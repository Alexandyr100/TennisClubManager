
package tennis_club_manager.training.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupTrainingCapacityPolicyTest {

    // Проверка на записване в основната група
    @Test
    void shouldAssignMainPlace() {
        var result =
                GroupTrainingCapacityPolicy.choosePlacement(3, 0);

        assertEquals(
                GroupTrainingCapacityPolicy.Placement.MAIN,
                result
        );
    }

    // Проверка на записване като резерва
    @Test
    void shouldAssignReservePlace() {
        var result =
                GroupTrainingCapacityPolicy.choosePlacement(4, 0);

        assertEquals(
                GroupTrainingCapacityPolicy.Placement.RESERVE,
                result
        );
    }

    // Проверка на последното резервно място
    @Test
    void shouldAssignLastReservePlace() {
        var result =
                GroupTrainingCapacityPolicy.choosePlacement(4, 3);

        assertEquals(
                GroupTrainingCapacityPolicy.Placement.RESERVE,
                result
        );
    }

    // Проверка при напълно запълнена тренировка
    @Test
    void shouldRejectWhenAllPlacesAreFull() {
        var result =
                GroupTrainingCapacityPolicy.choosePlacement(4, 4);

        assertEquals(
                GroupTrainingCapacityPolicy.Placement.FULL,
                result
        );
    }

    // Проверка за невалиден брой участници
    @Test
    void shouldRejectInvalidPlayerCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GroupTrainingCapacityPolicy
                        .choosePlacement(5, 0)
        );
    }
}

