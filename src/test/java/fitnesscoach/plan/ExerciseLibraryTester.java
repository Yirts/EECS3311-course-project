package fitnesscoach.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExerciseLibraryTester
{
	//only one library is ever made
	@Test
	void test_getInstance_1()
	{
		ExerciseLibrary first = ExerciseLibrary.getInstance();
		ExerciseLibrary second = ExerciseLibrary.getInstance();

		assertSame(first, second, "Failed Test 1 getInstance");
	}

	//every exercise in the file is loaded
	@Test
	void test_getExercises_1()
	{
		assertEquals(53, ExerciseLibrary.getInstance().getExercises().size(), "Failed Test 1 getExercises");
	}

	//exact name
	@Test
	void test_findExercise_1()
	{
		Exercise found = ExerciseLibrary.getInstance().findExercise("Barbell Squat");

		assertEquals("Barbell Squat", found.getName(), "Failed Test 1 findExercise");
	}

	//name typed with different capital letters
	@Test
	void test_findExercise_2()
	{
		Exercise found = ExerciseLibrary.getInstance().findExercise("barbell squat");

		assertEquals("Barbell Squat", found.getName(), "Failed Test 2 findExercise");
	}

	//name that is not in the library
	@Test
	void test_findExercise_3()
	{
		assertNull(ExerciseLibrary.getInstance().findExercise("Underwater Squat"), "Failed Test 3 findExercise");
	}

	//alternatives from the file are linked to the exercise
	@Test
	void test_findExercise_4()
	{
		Exercise squat = ExerciseLibrary.getInstance().findExercise("Barbell Squat");

		assertEquals(3, squat.getAlternatives().size(), "Failed Test 4 findExercise");
	}

	//every result works the muscle and uses the user's equipment or none
	@Test
	void test_search_1()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("chest", makeEquipment("dumbbell"));
		boolean allMatch = true;

		for (Exercise exercise : results)
		{
			if (!exercise.getMuscles().contains("chest"))
			{
				allMatch = false;
			}
			if (!exercise.getEquipment().equals("dumbbell") && !exercise.getEquipment().equals("none"))
			{
				allMatch = false;
			}
		}

		assertTrue(allMatch, "Failed Test 1 search");
	}

	//dumbbell exercise is found when the user has dumbbells
	@Test
	void test_search_2()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("chest", makeEquipment("dumbbell"));

		assertTrue(containsName(results, "Dumbbell Bench Press"), "Failed Test 2 search");
	}

	//barbell exercise is left out when the user has no equipment
	@Test
	void test_search_3()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("chest", new ArrayList<>());

		assertFalse(containsName(results, "Barbell Bench Press"), "Failed Test 3 search");
	}

	//bodyweight exercise is still found with no equipment
	@Test
	void test_search_4()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("chest", new ArrayList<>());

		assertTrue(containsName(results, "Push Up"), "Failed Test 4 search");
	}

	//muscle that no exercise works
	@Test
	void test_search_5()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("tail", makeEquipment("dumbbell"));

		assertEquals(0, results.size(), "Failed Test 5 search");
	}

	//muscle and equipment typed with capital letters
	@Test
	void test_search_6()
	{
		List<Exercise> results = ExerciseLibrary.getInstance().search("Chest", makeEquipment("Dumbbell"));

		assertTrue(containsName(results, "Dumbbell Bench Press"), "Failed Test 6 search");
	}


	private List<String> makeEquipment(String item)
	{
		ArrayList<String> equipment = new ArrayList<>();
		equipment.add(item);
		return equipment;
	}


	private boolean containsName(List<Exercise> exercises, String name)
	{
		for (Exercise exercise : exercises)
		{
			if (exercise.getName().equals(name))
			{
				return true;
			}
		}
		return false;
	}
}
