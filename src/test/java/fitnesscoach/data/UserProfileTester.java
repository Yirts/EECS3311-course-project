package fitnesscoach.data;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class UserProfileTester
{
	//normal values
	@Test
	void test_checkBasicInfo_1()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 1 checkBasicInfo");
	}

	//age just below the minimum
	@Test
	void test_checkBasicInfo_2()
	{
		UserProfile profile = new UserProfile(12, 180, 80, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 2 checkBasicInfo");
	}

	//age at the minimum
	@Test
	void test_checkBasicInfo_3()
	{
		UserProfile profile = new UserProfile(13, 180, 80, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 3 checkBasicInfo");
	}

	//age at the maximum
	@Test
	void test_checkBasicInfo_4()
	{
		UserProfile profile = new UserProfile(100, 180, 80, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 4 checkBasicInfo");
	}

	//age just above the maximum
	@Test
	void test_checkBasicInfo_5()
	{
		UserProfile profile = new UserProfile(101, 180, 80, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 5 checkBasicInfo");
	}

	//height just below the minimum
	@Test
	void test_checkBasicInfo_6()
	{
		UserProfile profile = new UserProfile(25, 99.9, 80, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 6 checkBasicInfo");
	}

	//height at the minimum
	@Test
	void test_checkBasicInfo_7()
	{
		UserProfile profile = new UserProfile(25, 100, 80, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 7 checkBasicInfo");
	}

	//height at the maximum
	@Test
	void test_checkBasicInfo_8()
	{
		UserProfile profile = new UserProfile(25, 250, 80, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 8 checkBasicInfo");
	}

	//height just above the maximum
	@Test
	void test_checkBasicInfo_9()
	{
		UserProfile profile = new UserProfile(25, 250.1, 80, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 9 checkBasicInfo");
	}

	//weight just below the minimum
	@Test
	void test_checkBasicInfo_10()
	{
		UserProfile profile = new UserProfile(25, 180, 29.9, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 10 checkBasicInfo");
	}

	//weight at the minimum
	@Test
	void test_checkBasicInfo_11()
	{
		UserProfile profile = new UserProfile(25, 180, 30, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 11 checkBasicInfo");
	}

	//weight at the maximum
	@Test
	void test_checkBasicInfo_12()
	{
		UserProfile profile = new UserProfile(25, 180, 300, "beginner");

		assertTrue(profile.checkBasicInfo(), "Failed Test 12 checkBasicInfo");
	}

	//weight just above the maximum
	@Test
	void test_checkBasicInfo_13()
	{
		UserProfile profile = new UserProfile(25, 180, 300.1, "beginner");

		assertFalse(profile.checkBasicInfo(), "Failed Test 13 checkBasicInfo");
	}

	//experience left empty
	@Test
	void test_checkBasicInfo_14()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "");

		assertFalse(profile.checkBasicInfo(), "Failed Test 14 checkBasicInfo");
	}

	//experience that is not beginner or intermediate
	@Test
	void test_checkBasicInfo_15()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "expert");

		assertFalse(profile.checkBasicInfo(), "Failed Test 15 checkBasicInfo");
	}

	//experience typed with a capital letter
	@Test
	void test_checkBasicInfo_16()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "Intermediate");

		assertTrue(profile.checkBasicInfo(), "Failed Test 16 checkBasicInfo");
	}

	//new boundary is saved
	@Test
	void test_addBoundary_1()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		profile.addBoundary("avoid: quads");

		assertEquals("avoid: quads", profile.getBoundaries().get(0), "Failed Test 1 addBoundary");
	}

	//same boundary is not saved twice
	@Test
	void test_addBoundary_2()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		profile.addBoundary("avoid: quads");
		profile.addBoundary("avoid: quads");

		assertEquals(1, profile.getBoundaries().size(), "Failed Test 2 addBoundary");
	}

	//new body weight becomes the current weight
	@Test
	void test_addBodyWeight_1()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		profile.addBodyWeight(78.5);

		assertEquals(78.5, profile.getWeightKg(), "Failed Test 1 addBodyWeight");
	}

	//new body weight is saved in the history under today's date
	@Test
	void test_addBodyWeight_2()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		profile.addBodyWeight(78.5);

		assertEquals(78.5, profile.getWeightHistory().get(LocalDate.now()), "Failed Test 2 addBodyWeight");
	}

	//unrealistic body weight is rejected
	@Test
	void test_addBodyWeight_3()
	{
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");

		assertThrows(IllegalArgumentException.class, () -> profile.addBodyWeight(600), "Failed Test 3 addBodyWeight");
	}

	//losing weight, 3 days a week
	@Test
	void test_calculateTargets_1()
	{
		UserProfile profile = makeProfile(30, 175, 70, "lose weight", 3);

		assertEquals(1653, profile.calculateTargets().get("calories"), "Failed Test 1 calculateTargets");
	}

	//protein for the same profile
	@Test
	void test_calculateTargets_2()
	{
		UserProfile profile = makeProfile(30, 175, 70, "lose weight", 3);

		assertEquals(126, profile.calculateTargets().get("protein"), "Failed Test 2 calculateTargets");
	}

	//fat for the same profile
	@Test
	void test_calculateTargets_3()
	{
		UserProfile profile = makeProfile(30, 175, 70, "lose weight", 3);

		assertEquals(46, profile.calculateTargets().get("fat"), "Failed Test 3 calculateTargets");
	}

	//carbs for the same profile
	@Test
	void test_calculateTargets_4()
	{
		UserProfile profile = makeProfile(30, 175, 70, "lose weight", 3);

		assertEquals(184, profile.calculateTargets().get("carbs"), "Failed Test 4 calculateTargets");
	}

	//building muscle adds calories
	@Test
	void test_calculateTargets_5()
	{
		UserProfile profile = makeProfile(25, 180, 80, "build muscle", 3);

		assertEquals(2668, profile.calculateTargets().get("calories"), "Failed Test 5 calculateTargets");
	}

	//staying the same, 1 day a week
	@Test
	void test_calculateTargets_6()
	{
		UserProfile profile = makeProfile(25, 180, 80, "stay healthy", 1);

		assertEquals(2066, profile.calculateTargets().get("calories"), "Failed Test 6 calculateTargets");
	}

	//staying the same, 5 days a week
	@Test
	void test_calculateTargets_7()
	{
		UserProfile profile = makeProfile(25, 180, 80, "stay healthy", 5);

		assertEquals(2669, profile.calculateTargets().get("calories"), "Failed Test 7 calculateTargets");
	}

	//staying the same, 6 days a week
	@Test
	void test_calculateTargets_8()
	{
		UserProfile profile = makeProfile(25, 180, 80, "stay healthy", 6);

		assertEquals(2970, profile.calculateTargets().get("calories"), "Failed Test 8 calculateTargets");
	}

	//calories are raised to the safe minimum
	@Test
	void test_calculateTargets_9()
	{
		UserProfile profile = makeProfile(60, 150, 45, "lose weight", 0);

		assertEquals(1200, profile.calculateTargets().get("calories"), "Failed Test 9 calculateTargets");
	}


    // ----- addBoundary() fills in the matching field (F01) -----

    @Test
    void addBoundaryWithAGoalSetsTheGoal() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("goal: build muscle");

        assertEquals("build muscle", profile.getGoal());
    }

    @Test
    void addBoundaryWithDaysAddsOnlyRealDayNames() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("days: Monday, wednesday, someday");

        assertEquals(List.of("monday", "wednesday"), profile.getAvailableDays());
    }

    @Test
    void addBoundaryWithDaysReadsAnd() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("days: monday and friday");

        assertEquals(List.of("monday", "friday"), profile.getAvailableDays());
    }

    @Test
    void addBoundaryWithMinutesSetsTheWorkoutLength() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("minutes: 45");

        assertEquals(45, profile.getMinutesPerWorkout());
    }

    @Test
    void addBoundaryWithAMinutesRangeUsesTheFirstNumber() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("minutes: 30-45 minutes");

        assertEquals(30, profile.getMinutesPerWorkout());
    }

    @Test
    void addBoundaryWithEquipmentAddsEachItemWithoutPlurals() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("equipment: Dumbbells, pull-up bar");

        assertEquals(List.of("dumbbell", "pull-up bar"), profile.getEquipment());
    }

    @Test
    void addBoundaryKeepsTheBoundaryText() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("goal: build muscle");

        assertEquals(List.of("goal: build muscle"), profile.getBoundaries());
    }

    @Test
    void addBoundaryWithANoteDoesNotChangeTheOtherFields() {
        UserProfile profile = new UserProfile(25, 180, 80, "beginner");

        profile.addBoundary("note: likes to work out in the morning");

        assertEquals("", profile.getGoal());
    }


	private UserProfile makeProfile(int age, double heightCm, double weightKg, String goal, int daysPerWeek)
	{
		String[] week = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
		UserProfile profile = new UserProfile(age, heightCm, weightKg, "beginner");
		profile.setGoal(goal);

		for (int i = 0; i < daysPerWeek; i++)
		{
			profile.getAvailableDays().add(week[i]);
		}
		return profile;
	}
}
