package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class WorkoutPlanTester
{
	//workout planned for today
	@Test
	void test_getTodaysWorkout_1()
	{
		WorkoutPlan plan = new WorkoutPlan(LocalDate.now().minusDays(1));
		WorkoutDay yesterday = new WorkoutDay(LocalDate.now().minusDays(1), "Upper body");
		WorkoutDay today = new WorkoutDay(LocalDate.now(), "Lower body");
		plan.getDays().add(yesterday);
		plan.getDays().add(today);

		assertEquals(today, plan.getTodaysWorkout(), "Failed Test 1 getTodaysWorkout");
	}

	//no workout planned today
	@Test
	void test_getTodaysWorkout_2()
	{
		WorkoutPlan plan = new WorkoutPlan(LocalDate.now().minusDays(1));
		plan.getDays().add(new WorkoutDay(LocalDate.now().minusDays(1), "Upper body"));
		plan.getDays().add(new WorkoutDay(LocalDate.now().plusDays(1), "Lower body"));

		assertNull(plan.getTodaysWorkout(), "Failed Test 2 getTodaysWorkout");
	}

	//plan with no days
	@Test
	void test_getTodaysWorkout_3()
	{
		WorkoutPlan plan = new WorkoutPlan(LocalDate.now());

		assertNull(plan.getTodaysWorkout(), "Failed Test 3 getTodaysWorkout");
	}
}
