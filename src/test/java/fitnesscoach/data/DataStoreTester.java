package fitnesscoach.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

class DataStoreTester
{
	@TempDir
	Path folder; //junit makes a new empty folder for each test

	//nothing saved yet
	@Test
	void test_loadProfile_1()
	{
		DataStore store = new DataStore(folder.toString());

		assertNull(store.loadProfile(), "Failed Test 1 loadProfile");
	}

	//saved profile comes back with the same age
	@Test
	void test_loadProfile_2()
	{
		DataStore store = new DataStore(folder.toString());
		store.saveProfile(new UserProfile(25, 180, 80, "beginner"));

		assertEquals(25, store.loadProfile().getAge(), "Failed Test 2 loadProfile");
	}

	//boundaries are saved too
	@Test
	void test_loadProfile_3()
	{
		DataStore store = new DataStore(folder.toString());
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");
		profile.addBoundary("avoid: quads");
		store.saveProfile(profile);

		assertEquals("avoid: quads", store.loadProfile().getBoundaries().get(0), "Failed Test 3 loadProfile");
	}

	//body weight history keeps its dates
	@Test
	void test_loadProfile_4()
	{
		DataStore store = new DataStore(folder.toString());
		UserProfile profile = new UserProfile(25, 180, 80, "beginner");
		profile.addBodyWeight(78.5);
		store.saveProfile(profile);

		assertEquals(78.5, store.loadProfile().getWeightHistory().get(LocalDate.now()), "Failed Test 4 loadProfile");
	}

	//nothing saved yet
	@Test
	void test_loadPlan_1()
	{
		DataStore store = new DataStore(folder.toString());

		assertNull(store.loadPlan(), "Failed Test 1 loadPlan");
	}

	//saved plan keeps its dates
	@Test
	void test_loadPlan_2()
	{
		DataStore store = new DataStore(folder.toString());
		store.savePlan(makePlan());

		assertEquals(LocalDate.of(2026, 11, 2), store.loadPlan().getDays().get(0).getDate(), "Failed Test 2 loadPlan");
	}

	//loaded exercises are the library's own copies
	@Test
	void test_loadPlan_3()
	{
		DataStore store = new DataStore(folder.toString());
		store.savePlan(makePlan());

		PlannedExercise loaded = store.loadPlan().getDays().get(0).getExercises().get(0);

		assertSame(ExerciseLibrary.getInstance().findExercise("Barbell Squat"), loaded.getExercise(), "Failed Test 3 loadPlan");
	}

	//loaded exercises have their alternatives again
	@Test
	void test_loadPlan_4()
	{
		DataStore store = new DataStore(folder.toString());
		store.savePlan(makePlan());

		PlannedExercise loaded = store.loadPlan().getDays().get(0).getExercises().get(0);

		assertEquals(3, loaded.getExercise().getAlternatives().size(), "Failed Test 4 loadPlan");
	}

	//nothing saved yet means an empty history
	@Test
	void test_loadHistory_1()
	{
		DataStore store = new DataStore(folder.toString());

		assertEquals(0, store.loadHistory().getLogs().size(), "Failed Test 1 loadHistory");
	}

	//finished workouts are saved
	@Test
	void test_loadHistory_2()
	{
		DataStore store = new DataStore(folder.toString());
		WorkoutHistory history = new WorkoutHistory();
		history.startWorkout(null);
		history.logSet(new SetLog("Bench Press", 8, 60, 7));
		history.finishWorkout();
		store.saveHistory(history);

		assertEquals(1, store.loadHistory().getLogs().size(), "Failed Test 2 loadHistory");
	}

	//workout in progress is saved so it can be finished after the app closes
	@Test
	void test_loadHistory_3()
	{
		DataStore store = new DataStore(folder.toString());
		WorkoutHistory history = new WorkoutHistory();
		history.startWorkout(null);
		history.logSet(new SetLog("Bench Press", 8, 60, 7));
		store.saveHistory(history);

		assertEquals(1, store.loadHistory().getCurrentLog().getSets().size(), "Failed Test 3 loadHistory");
	}

	//check-ins keep their dates
	@Test
	void test_loadHistory_4()
	{
		DataStore store = new DataStore(folder.toString());
		WorkoutHistory history = new WorkoutHistory();
		history.addCheckIn(new CheckIn(LocalDate.of(2026, 10, 25), 7, 3, 3, 3, ""));
		store.saveHistory(history);

		assertEquals(LocalDate.of(2026, 10, 25), store.loadHistory().getCheckIns().get(0).getDate(), "Failed Test 4 loadHistory");
	}

	//loaded history can still tell its observers
	@Test
	void test_loadHistory_5()
	{
		DataStore store = new DataStore(folder.toString());
		store.saveHistory(new WorkoutHistory());
		WorkoutHistory loaded = store.loadHistory();
		loaded.addObserver(log -> System.out.println("workout logged"));
		loaded.startWorkout(null);

		assertDoesNotThrow(() -> loaded.finishWorkout(), "Failed Test 5 loadHistory");
	}

	//memory comes back in the same order
	@Test
	void test_loadMemory_1()
	{
		DataStore store = new DataStore(folder.toString());
		ArrayList<String> memory = new ArrayList<>();
		memory.add("likes morning workouts");
		memory.add("allergic to peanuts");
		store.saveMemory(memory);

		assertEquals("allergic to peanuts", store.loadMemory().get(1), "Failed Test 1 loadMemory");
	}

	//fact with a line break stays one fact
	@Test
	void test_loadMemory_2()
	{
		DataStore store = new DataStore(folder.toString());
		ArrayList<String> memory = new ArrayList<>();
		memory.add("likes morning\nworkouts");
		store.saveMemory(memory);

		assertEquals(1, store.loadMemory().size(), "Failed Test 2 loadMemory");
	}

	//nothing saved yet
	@Test
	void test_loadMemory_3()
	{
		DataStore store = new DataStore(folder.toString());

		assertEquals(0, store.loadMemory().size(), "Failed Test 3 loadMemory");
	}

	//report is saved with today's date in the file name
	@Test
	void test_saveReport_1()
	{
		DataStore store = new DataStore(folder.toString());
		store.saveReport("Good week. 3 workouts done.");

		assertTrue(Files.exists(folder.resolve("report-" + LocalDate.now() + ".txt")), "Failed Test 1 saveReport");
	}

	//settings are read from a .env style file
	@Test
	void test_loadConfig_1() throws IOException
	{
		DataStore store = new DataStore(folder.toString());
		Path file = folder.resolve("test.env");
		Files.writeString(file, "ANTHROPIC_API_KEY=test-key\nCLAUDE_MODEL=claude-haiku-5-5\n");

		assertEquals("claude-haiku-5-5", store.loadConfig(file.toString()).get("CLAUDE_MODEL"), "Failed Test 1 loadConfig");
	}

	//comment lines are skipped
	@Test
	void test_loadConfig_2() throws IOException
	{
		DataStore store = new DataStore(folder.toString());
		Path file = folder.resolve("test.env");
		Files.writeString(file, "# my settings\nCLAUDE_MODEL=claude-sonnet-5\n");

		assertEquals(1, store.loadConfig(file.toString()).size(), "Failed Test 2 loadConfig");
	}

	//missing file gives no settings
	@Test
	void test_loadConfig_3()
	{
		DataStore store = new DataStore(folder.toString());

		assertEquals(0, store.loadConfig(folder.resolve("missing.env").toString()).size(), "Failed Test 3 loadConfig");
	}


    // ----- saveOldPlans() and loadOldPlans(): the undo stack (F03) -----

    @Test
    void loadOldPlansWithNothingSavedIsEmpty() {
        DataStore store = new DataStore(folder.toString());

        assertTrue(store.loadOldPlans().isEmpty());
    }

    @Test
    void loadOldPlansKeepsTheOrder() {
        DataStore store = new DataStore(folder.toString());
        ArrayList<WorkoutPlan> oldPlans = new ArrayList<>();
        oldPlans.add(new WorkoutPlan(LocalDate.of(2026, 10, 1)));
        oldPlans.add(new WorkoutPlan(LocalDate.of(2026, 10, 15)));

        store.saveOldPlans(oldPlans);

        assertEquals(LocalDate.of(2026, 10, 15), store.loadOldPlans().get(1).getStartDate());
    }

    @Test
    void loadOldPlansUsesTheLibraryExercises() {
        DataStore store = new DataStore(folder.toString());
        ArrayList<WorkoutPlan> oldPlans = new ArrayList<>();
        oldPlans.add(makePlan());

        store.saveOldPlans(oldPlans);
        PlannedExercise loaded = store.loadOldPlans().get(0).getDays().get(0).getExercises().get(0);

        assertSame(ExerciseLibrary.getInstance().findExercise("Barbell Squat"), loaded.getExercise());
    }


	private WorkoutPlan makePlan()
	{
		WorkoutPlan plan = new WorkoutPlan(LocalDate.of(2026, 11, 2));
		WorkoutDay day = new WorkoutDay(LocalDate.of(2026, 11, 2), "Legs");
		day.getExercises().add(new PlannedExercise(ExerciseLibrary.getInstance().findExercise("Barbell Squat"), 3, 8, 60));
		plan.getDays().add(day);
		return plan;
	}
}
