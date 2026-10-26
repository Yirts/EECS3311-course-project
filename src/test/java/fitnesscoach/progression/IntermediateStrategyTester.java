package fitnesscoach.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fitnesscoach.history.SetLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.PlannedExercise;

class IntermediateStrategyTester
{
	//planned reps reached but not the top of the range (8 to 10)
	@Test
	void test_decideNextWeight_1()
	{
		PlannedExercise planned = makePlanned(3, 8, 80);

		assertEquals(80, new IntermediateStrategy().decideNextWeight(planned, makeSets(8, 8, 8)), "Failed Test 1 decideNextWeight");
	}

	//every set reached the top of the range
	@Test
	void test_decideNextWeight_2()
	{
		PlannedExercise planned = makePlanned(3, 8, 80);

		assertEquals(82.5, new IntermediateStrategy().decideNextWeight(planned, makeSets(10, 10, 10)), "Failed Test 2 decideNextWeight");
	}

	//one set just below the top of the range
	@Test
	void test_decideNextWeight_3()
	{
		PlannedExercise planned = makePlanned(3, 8, 80);

		assertEquals(80, new IntermediateStrategy().decideNextWeight(planned, makeSets(10, 10, 9)), "Failed Test 3 decideNextWeight");
	}

	//fewer sets than planned
	@Test
	void test_decideNextWeight_4()
	{
		PlannedExercise planned = makePlanned(3, 8, 80);

		assertEquals(80, new IntermediateStrategy().decideNextWeight(planned, makeSets(10, 10)), "Failed Test 4 decideNextWeight");
	}


	private PlannedExercise makePlanned(int sets, int reps, double weightKg)
	{
		Exercise squat = new Exercise("Barbell Squat", new ArrayList<>(), "barbell");
		return new PlannedExercise(squat, sets, reps, weightKg);
	}


	private List<SetLog> makeSets(int... reps)
	{
		ArrayList<SetLog> sets = new ArrayList<>();
		for (int r : reps)
		{
			sets.add(new SetLog("Barbell Squat", r, 80, 8));
		}
		return sets;
	}
}
