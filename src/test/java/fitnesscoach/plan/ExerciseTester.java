package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class ExerciseTester
{
	//new exercise has no alternatives yet
	@Test
	void test_getAlternatives_1()
	{
		Exercise bench = new Exercise("Bench Press", new ArrayList<>(), "barbell");

		assertEquals(0, bench.getAlternatives().size(), "Failed Test 1 getAlternatives");
	}

	//alternative added to the list
	@Test
	void test_getAlternatives_2()
	{
		Exercise bench = new Exercise("Bench Press", new ArrayList<>(), "barbell");
		Exercise pushUp = new Exercise("Push Up", new ArrayList<>(), "none");

		bench.getAlternatives().add(pushUp);

		assertEquals(pushUp, bench.getAlternatives().get(0), "Failed Test 2 getAlternatives");
	}
}
