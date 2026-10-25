package fitnesscoach.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class WorkoutLogTester
{
	//new log has no sets
	@Test
	void test_addSet_1()
	{
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);

		assertEquals(0, log.getSets().size(), "Failed Test 1 addSet");
	}

	//set is added to the log
	@Test
	void test_addSet_2()
	{
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);

		log.addSet(new SetLog("Bench Press", 8, 60, 7));

		assertEquals(1, log.getSets().size(), "Failed Test 2 addSet");
	}

	//nothing lifted yet
	@Test
	void test_getWeightLifted_1()
	{
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);

		assertEquals(0, log.getWeightLifted(), "Failed Test 1 getWeightLifted");
	}

	//reps times weight added up for every set (8 x 60 + 6 x 70)
	@Test
	void test_getWeightLifted_2()
	{
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);
		log.addSet(new SetLog("Bench Press", 8, 60, 7));
		log.addSet(new SetLog("Bench Press", 6, 70, 9));

		assertEquals(900, log.getWeightLifted(), "Failed Test 2 getWeightLifted");
	}

	//bodyweight sets add nothing
	@Test
	void test_getWeightLifted_3()
	{
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);
		log.addSet(new SetLog("Push Up", 15, 0, 6));
		log.addSet(new SetLog("Bench Press", 5, 100, 9));

		assertEquals(500, log.getWeightLifted(), "Failed Test 3 getWeightLifted");
	}
}
