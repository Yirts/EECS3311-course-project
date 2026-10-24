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
}
