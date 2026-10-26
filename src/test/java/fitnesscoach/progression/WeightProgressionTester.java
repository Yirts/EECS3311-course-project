package fitnesscoach.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;

class WeightProgressionTester
{
	//beginner rule raises the weight
	@Test
	void test_onWorkoutLogged_1()
	{
		PlannedExercise planned = makePlanned(60);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.onWorkoutLogged(makeLog(planned, 8, 8, 8));

		assertEquals(62.5, planned.getWeightKg(), "Failed Test 1 onWorkoutLogged");
	}

	//weight stays the same so the exercise counts as stuck once more
	@Test
	void test_onWorkoutLogged_2()
	{
		PlannedExercise planned = makePlanned(60);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.onWorkoutLogged(makeLog(planned, 8, 7, 6));

		assertEquals(1, planned.getStuckCount(), "Failed Test 2 onWorkoutLogged");
	}

	//raising the weight means it is not stuck anymore
	@Test
	void test_onWorkoutLogged_3()
	{
		PlannedExercise planned = makePlanned(60);
		planned.setStuckCount(2);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.onWorkoutLogged(makeLog(planned, 8, 8, 8));

		assertEquals(0, planned.getStuckCount(), "Failed Test 3 onWorkoutLogged");
	}

	//skipped exercise stays the same
	@Test
	void test_onWorkoutLogged_4()
	{
		PlannedExercise planned = makePlanned(60);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.onWorkoutLogged(makeLog(planned));

		assertEquals(60, planned.getWeightKg(), "Failed Test 4 onWorkoutLogged");
	}

	//bodyweight exercise stays at 0 kg
	@Test
	void test_onWorkoutLogged_5()
	{
		PlannedExercise planned = makePlanned(0);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.onWorkoutLogged(makeLog(planned, 8, 8, 8));

		assertEquals(0, planned.getWeightKg(), "Failed Test 5 onWorkoutLogged");
	}

	//imported workout has no planned day
	@Test
	void test_onWorkoutLogged_6()
	{
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		assertDoesNotThrow(() -> progression.onWorkoutLogged(new WorkoutLog(LocalDate.now(), null)), "Failed Test 6 onWorkoutLogged");
	}

	//same workout with the intermediate rule after switching
	@Test
	void test_setStrategy_1()
	{
		PlannedExercise planned = makePlanned(60);
		WeightProgression progression = new WeightProgression(new BeginnerStrategy());

		progression.setStrategy(new IntermediateStrategy());
		progression.onWorkoutLogged(makeLog(planned, 8, 8, 8));

		assertEquals(60, planned.getWeightKg(), "Failed Test 1 setStrategy");
	}

	//finishing a workout tells the weight progression, which raises the weight
	@Test
	void test_finishWorkout_1()
	{
		PlannedExercise planned = makePlanned(60);
		WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");
		day.getExercises().add(planned);
		WorkoutHistory history = new WorkoutHistory();
		history.addObserver(new WeightProgression(new BeginnerStrategy()));

		history.startWorkout(day);
		history.logSet(new SetLog("Bench Press", 8, 60, 7));
		history.logSet(new SetLog("Bench Press", 8, 60, 8));
		history.logSet(new SetLog("Bench Press", 8, 60, 9));
		history.finishWorkout();

		assertEquals(62.5, planned.getWeightKg(), "Failed Test 1 finishWorkout");
	}


	private PlannedExercise makePlanned(double weightKg)
	{
		Exercise bench = new Exercise("Bench Press", new ArrayList<>(), "barbell");
		return new PlannedExercise(bench, 3, 8, weightKg);
	}


	private WorkoutLog makeLog(PlannedExercise planned, int... reps)
	{
		WorkoutDay day = new WorkoutDay(LocalDate.now(), "Upper body");
		day.getExercises().add(planned);
		WorkoutLog log = new WorkoutLog(LocalDate.now(), day);

		for (int r : reps)
		{
			log.addSet(new SetLog("Bench Press", r, planned.getWeightKg(), 8));
		}
		return log;
	}
}
