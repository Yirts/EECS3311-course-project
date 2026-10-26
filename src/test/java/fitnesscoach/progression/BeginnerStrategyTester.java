package fitnesscoach.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fitnesscoach.history.SetLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.PlannedExercise;

class BeginnerStrategyTester
{
	//every set hit the planned reps
	@Test
	void test_decideNextWeight_1()
	{
		PlannedExercise planned = makePlanned(3, 8, 60);

		assertEquals(62.5, new BeginnerStrategy().decideNextWeight(planned, makeSets(8, 8, 8)), "Failed Test 1 decideNextWeight");
	}

	//one set missed the planned reps
	@Test
	void test_decideNextWeight_2()
	{
		PlannedExercise planned = makePlanned(3, 8, 60);

		assertEquals(60, new BeginnerStrategy().decideNextWeight(planned, makeSets(8, 8, 7)), "Failed Test 2 decideNextWeight");
	}

	//fewer sets than planned
	@Test
	void test_decideNextWeight_3()
	{
		PlannedExercise planned = makePlanned(3, 8, 60);

		assertEquals(60, new BeginnerStrategy().decideNextWeight(planned, makeSets(8, 8)), "Failed Test 3 decideNextWeight");
	}

	//more reps than planned still counts
	@Test
	void test_decideNextWeight_4()
	{
		PlannedExercise planned = makePlanned(3, 8, 60);

		assertEquals(62.5, new BeginnerStrategy().decideNextWeight(planned, makeSets(10, 9, 8)), "Failed Test 4 decideNextWeight");
	}


	private PlannedExercise makePlanned(int sets, int reps, double weightKg)
	{
		Exercise bench = new Exercise("Bench Press", new ArrayList<>(), "barbell");
		return new PlannedExercise(bench, sets, reps, weightKg);
	}


	private List<SetLog> makeSets(int... reps)
	{
		ArrayList<SetLog> sets = new ArrayList<>();
		for (int r : reps)
		{
			sets.add(new SetLog("Bench Press", r, 60, 8));
		}
		return sets;
	}
}
