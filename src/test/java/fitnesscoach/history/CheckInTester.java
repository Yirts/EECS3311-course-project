package fitnesscoach.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CheckInTester
{
	//best answers
	@Test
	void test_calculateScore_1()
	{
		CheckIn checkIn = new CheckIn(LocalDate.now(), 8, 5, 1, 1, "");

		assertEquals(100, checkIn.calculateScore(), "Failed Test 1 calculateScore");
	}

	//worst answers
	@Test
	void test_calculateScore_2()
	{
		CheckIn checkIn = new CheckIn(LocalDate.now(), 0, 1, 5, 5, "");

		assertEquals(0, checkIn.calculateScore(), "Failed Test 2 calculateScore");
	}

	//middle answers
	@Test
	void test_calculateScore_3()
	{
		CheckIn checkIn = new CheckIn(LocalDate.now(), 6, 3, 3, 3, "");

		assertEquals(56, checkIn.calculateScore(), "Failed Test 3 calculateScore");
	}

	//the example from Stage 1 (sleep 6, energy 2, stress 3)
	@Test
	void test_calculateScore_4()
	{
		CheckIn checkIn = new CheckIn(LocalDate.now(), 6, 2, 3, 3, "tired today");

		assertEquals(50, checkIn.calculateScore(), "Failed Test 4 calculateScore");
	}

	//more than 8 hours of sleep does not go over 100
	@Test
	void test_calculateScore_5()
	{
		CheckIn checkIn = new CheckIn(LocalDate.now(), 10, 5, 1, 1, "");

		assertEquals(100, checkIn.calculateScore(), "Failed Test 5 calculateScore");
	}

	//negative sleep
	@Test
	void test_newCheckIn_1()
	{
		assertThrows(IllegalArgumentException.class, () -> new CheckIn(LocalDate.now(), -1, 3, 3, 3, ""), "Failed Test 1 new CheckIn");
	}

	//more sleep than a day has
	@Test
	void test_newCheckIn_2()
	{
		assertThrows(IllegalArgumentException.class, () -> new CheckIn(LocalDate.now(), 25, 3, 3, 3, ""), "Failed Test 2 new CheckIn");
	}

	//energy below the slider
	@Test
	void test_newCheckIn_3()
	{
		assertThrows(IllegalArgumentException.class, () -> new CheckIn(LocalDate.now(), 7, 0, 3, 3, ""), "Failed Test 3 new CheckIn");
	}

	//stress above the slider
	@Test
	void test_newCheckIn_4()
	{
		assertThrows(IllegalArgumentException.class, () -> new CheckIn(LocalDate.now(), 7, 3, 6, 3, ""), "Failed Test 4 new CheckIn");
	}

	//soreness above the slider
	@Test
	void test_newCheckIn_5()
	{
		assertThrows(IllegalArgumentException.class, () -> new CheckIn(LocalDate.now(), 7, 3, 3, 6, ""), "Failed Test 5 new CheckIn");
	}
}
