package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import fitnesscoach.data.UserProfile;

class WorkoutPlanTester
{
	private LocalDate monday = LocalDate.of(2026, 11, 2);
	private LocalDate tuesday = LocalDate.of(2026, 11, 3);

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

	//plan that follows every boundary
	@Test
	void test_checkSafety_1()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 3);

		assertEquals(0, plan.checkSafety(makeProfile()).size(), "Failed Test 1 checkSafety");
	}

	//exercise works a muscle the user must avoid
	@Test
	void test_checkSafety_2()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 3);
		UserProfile profile = makeProfile();
		profile.addBoundary("avoid: quads");

		assertEquals(1, plan.checkSafety(profile).size(), "Failed Test 2 checkSafety");
	}

	//exercise the user must avoid by name
	@Test
	void test_checkSafety_3()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 3);
		UserProfile profile = makeProfile();
		profile.addBoundary("avoid: Goblet Squat");

		assertEquals(1, plan.checkSafety(profile).size(), "Failed Test 3 checkSafety");
	}

	//exercise needs equipment the user does not have
	@Test
	void test_checkSafety_4()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Barbell Squat", "quads", "barbell"), 3);

		assertEquals(1, plan.checkSafety(makeProfile()).size(), "Failed Test 4 checkSafety");
	}

	//workout on a day the user is not free
	@Test
	void test_checkSafety_5()
	{
		WorkoutPlan plan = makePlan(tuesday, makeExercise("Goblet Squat", "quads", "dumbbell"), 3);

		assertEquals(1, plan.checkSafety(makeProfile()).size(), "Failed Test 5 checkSafety");
	}

	//no available days set means any day is fine
	@Test
	void test_checkSafety_6()
	{
		WorkoutPlan plan = makePlan(tuesday, makeExercise("Goblet Squat", "quads", "dumbbell"), 3);
		UserProfile profile = makeProfile();
		profile.getAvailableDays().clear();

		assertEquals(0, plan.checkSafety(profile).size(), "Failed Test 6 checkSafety");
	}

	//workout takes longer than the user has (11 sets is about 33 minutes)
	@Test
	void test_checkSafety_7()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 6);
		plan.getDays().get(0).getExercises().add(new PlannedExercise(makeExercise("Push Up", "chest", "none"), 5, 10, 0));
		UserProfile profile = makeProfile();
		profile.setMinutesPerWorkout(30);

		assertEquals(1, plan.checkSafety(profile).size(), "Failed Test 7 checkSafety");
	}

	//workout takes exactly the time the user has (10 sets is about 30 minutes)
	@Test
	void test_checkSafety_8()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 5);
		plan.getDays().get(0).getExercises().add(new PlannedExercise(makeExercise("Push Up", "chest", "none"), 5, 10, 0));
		UserProfile profile = makeProfile();
		profile.setMinutesPerWorkout(30);

		assertEquals(0, plan.checkSafety(profile).size(), "Failed Test 8 checkSafety");
	}

	//too many sets for one muscle
	@Test
	void test_checkSafety_9()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 11);

		assertEquals(1, plan.checkSafety(makeProfile()).size(), "Failed Test 9 checkSafety");
	}

	//exactly the most sets allowed for one muscle
	@Test
	void test_checkSafety_10()
	{
		WorkoutPlan plan = makePlan(monday, makeExercise("Goblet Squat", "quads", "dumbbell"), 10);

		assertEquals(0, plan.checkSafety(makeProfile()).size(), "Failed Test 10 checkSafety");
	}


	private UserProfile makeProfile()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");
		profile.getAvailableDays().add("Monday");
		profile.getAvailableDays().add("Wednesday");
		profile.getAvailableDays().add("Friday");
		profile.setMinutesPerWorkout(60);
		profile.getEquipment().add("dumbbell");
		return profile;
	}


	private Exercise makeExercise(String name, String muscle, String equipment)
	{
		ArrayList<String> muscles = new ArrayList<>();
		muscles.add(muscle);
		return new Exercise(name, muscles, equipment);
	}


	private WorkoutPlan makePlan(LocalDate date, Exercise exercise, int sets)
	{
		WorkoutPlan plan = new WorkoutPlan(date);
		WorkoutDay day = new WorkoutDay(date, "Workout");
		day.getExercises().add(new PlannedExercise(exercise, sets, 10, 20));
		plan.getDays().add(day);
		return plan;
	}
}
