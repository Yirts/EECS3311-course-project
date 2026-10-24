package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class PlannedExerciseTester
{
	//new planned exercise is not stuck
	@Test
	void test_newPlannedExercise_1()
	{
		PlannedExercise planned = new PlannedExercise(makeSquat(), 3, 8, 60);

		assertEquals(0, planned.getStuckCount(), "Failed Test 1 new planned exercise");
	}

	//new planned exercise has no note
	@Test
	void test_newPlannedExercise_2()
	{
		PlannedExercise planned = new PlannedExercise(makeSquat(), 3, 8, 60);

		assertEquals("", planned.getNote(), "Failed Test 2 new planned exercise");
	}

	//exercise swapped for a different one
	@Test
	void test_setExercise_1()
	{
		PlannedExercise planned = new PlannedExercise(makeSquat(), 3, 8, 60);
		ArrayList<String> muscles = new ArrayList<>();
		muscles.add("quads");
		Exercise legPress = new Exercise("Leg Press", muscles, "machine");

		planned.setExercise(legPress);

		assertEquals(legPress, planned.getExercise(), "Failed Test 1 setExercise");
	}

	//new weight for next time
	@Test
	void test_setWeightKg_1()
	{
		PlannedExercise planned = new PlannedExercise(makeSquat(), 3, 8, 60);

		planned.setWeightKg(62.5);

		assertEquals(62.5, planned.getWeightKg(), "Failed Test 1 setWeightKg");
	}


	private Exercise makeSquat()
	{
		ArrayList<String> muscles = new ArrayList<>();
		muscles.add("quads");
		muscles.add("glutes");
		return new Exercise("Barbell Squat", muscles, "barbell");
	}
}
