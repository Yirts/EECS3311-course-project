package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class WorkoutDayTester
{
	//new day has no exercises yet
	@Test
	void test_getExercises_1()
	{
		WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");

		assertEquals(0, day.getExercises().size(), "Failed Test 1 getExercises");
	}

	//exercise added to the day
	@Test
	void test_getExercises_2()
	{
		WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");
		Exercise bench = new Exercise("Bench Press", new ArrayList<>(), "barbell");
		PlannedExercise planned = new PlannedExercise(bench, 3, 10, 40);

		day.getExercises().add(planned);

		assertEquals(planned, day.getExercises().get(0), "Failed Test 2 getExercises");
	}


    // ----- copy() -----

    @Test
    void copyKeepsTheStuckCount() {
        WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");
        PlannedExercise planned = new PlannedExercise(new Exercise("Bench Press", new ArrayList<>(), "barbell"), 3, 10, 40);
        planned.setStuckCount(2);
        day.getExercises().add(planned);

        WorkoutDay copy = day.copy(LocalDate.now().plusWeeks(1));

        assertEquals(2, copy.getExercises().get(0).getStuckCount());
    }

    @Test
    void copyHasItsOwnExercises() {
        WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");
        PlannedExercise planned = new PlannedExercise(new Exercise("Bench Press", new ArrayList<>(), "barbell"), 3, 10, 40);
        day.getExercises().add(planned);

        day.copy(LocalDate.now().plusWeeks(1)).getExercises().get(0).setSets(5);

        assertEquals(3, planned.getSets());
    }
}
