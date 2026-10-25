package fitnesscoach.history;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SetLogTester
{
	//reps just below the minimum
	@Test
	void test_newSetLog_1()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Bench Press", -1, 60, 8), "Failed Test 1 new SetLog");
	}

	//reps at the minimum (a failed set)
	@Test
	void test_newSetLog_2()
	{
		SetLog set = new SetLog("Bench Press", 0, 60, 8);

		assertEquals(0, set.getReps(), "Failed Test 2 new SetLog");
	}

	//reps at the maximum
	@Test
	void test_newSetLog_3()
	{
		SetLog set = new SetLog("Push Up", 100, 0, 8);

		assertEquals(100, set.getReps(), "Failed Test 3 new SetLog");
	}

	//reps just above the maximum
	@Test
	void test_newSetLog_4()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Push Up", 101, 0, 8), "Failed Test 4 new SetLog");
	}

	//negative weight
	@Test
	void test_newSetLog_5()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Bench Press", 8, -1, 8), "Failed Test 5 new SetLog");
	}

	//no weight (bodyweight exercise)
	@Test
	void test_newSetLog_6()
	{
		SetLog set = new SetLog("Push Up", 12, 0, 6);

		assertEquals(0, set.getWeightKg(), "Failed Test 6 new SetLog");
	}

	//weight at the maximum
	@Test
	void test_newSetLog_7()
	{
		SetLog set = new SetLog("Deadlift", 1, 500, 10);

		assertEquals(500, set.getWeightKg(), "Failed Test 7 new SetLog");
	}

	//weight that looks like a typo
	@Test
	void test_newSetLog_8()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Bench Press", 8, 600, 8), "Failed Test 8 new SetLog");
	}

	//difficulty just below the minimum
	@Test
	void test_newSetLog_9()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Bench Press", 8, 60, 0), "Failed Test 9 new SetLog");
	}

	//difficulty at the minimum
	@Test
	void test_newSetLog_10()
	{
		SetLog set = new SetLog("Bench Press", 8, 60, 1);

		assertEquals(1, set.getDifficulty(), "Failed Test 10 new SetLog");
	}

	//difficulty at the maximum
	@Test
	void test_newSetLog_11()
	{
		SetLog set = new SetLog("Bench Press", 8, 60, 10);

		assertEquals(10, set.getDifficulty(), "Failed Test 11 new SetLog");
	}

	//difficulty just above the maximum
	@Test
	void test_newSetLog_12()
	{
		assertThrows(IllegalArgumentException.class, () -> new SetLog("Bench Press", 8, 60, 11), "Failed Test 12 new SetLog");
	}
}
