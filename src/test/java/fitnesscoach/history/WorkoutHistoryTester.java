package fitnesscoach.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkoutHistoryTester
{
	//set goes into the workout that was started
	@Test
	void test_logSet_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.startWorkout(null);

		history.logSet(new SetLog("Bench Press", 8, 60, 7));

		assertEquals(1, history.getCurrentLog().getSets().size(), "Failed Test 1 logSet");
	}

	//no workout started yet
	@Test
	void test_logSet_2()
	{
		WorkoutHistory history = new WorkoutHistory();

		assertThrows(IllegalStateException.class, () -> history.logSet(new SetLog("Bench Press", 8, 60, 7)), "Failed Test 2 logSet");
	}

	//finished workout is saved in the history
	@Test
	void test_finishWorkout_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.startWorkout(null);
		history.logSet(new SetLog("Bench Press", 8, 60, 7));

		history.finishWorkout();

		assertEquals(1, history.getLogs().size(), "Failed Test 1 finishWorkout");
	}

	//no workout is in progress after finishing
	@Test
	void test_finishWorkout_2()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.startWorkout(null);

		history.finishWorkout();

		assertNull(history.getCurrentLog(), "Failed Test 2 finishWorkout");
	}

	//nothing to finish
	@Test
	void test_finishWorkout_3()
	{
		WorkoutHistory history = new WorkoutHistory();

		assertThrows(IllegalStateException.class, () -> history.finishWorkout(), "Failed Test 3 finishWorkout");
	}

	//observer is told when a workout is finished
	@Test
	void test_notifyObservers_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		CountingObserver observer = new CountingObserver();
		history.addObserver(observer);
		history.startWorkout(null);

		history.finishWorkout();

		assertEquals(1, observer.count, "Failed Test 1 notifyObservers");
	}

	//observer gets the workout that was just finished
	@Test
	void test_notifyObservers_2()
	{
		WorkoutHistory history = new WorkoutHistory();
		CountingObserver observer = new CountingObserver();
		history.addObserver(observer);
		history.startWorkout(null);

		WorkoutLog finished = history.finishWorkout();

		assertEquals(finished, observer.lastLog, "Failed Test 2 notifyObservers");
	}

	//every observer is told (like the dashboard and the weight progression)
	@Test
	void test_notifyObservers_3()
	{
		WorkoutHistory history = new WorkoutHistory();
		CountingObserver dashboard = new CountingObserver();
		CountingObserver progression = new CountingObserver();
		history.addObserver(dashboard);
		history.addObserver(progression);
		history.startWorkout(null);

		history.finishWorkout();

		assertEquals(2, dashboard.count + progression.count, "Failed Test 3 notifyObservers");
	}

	//imported workout also tells the observers
	@Test
	void test_addWorkout_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		CountingObserver observer = new CountingObserver();
		history.addObserver(observer);

		history.addWorkout(new WorkoutLog(LocalDate.now().minusDays(30), null));

		assertEquals(1, observer.count, "Failed Test 1 addWorkout");
	}

	//imported workout is saved in the history
	@Test
	void test_addWorkout_2()
	{
		WorkoutHistory history = new WorkoutHistory();

		history.addWorkout(new WorkoutLog(LocalDate.now().minusDays(30), null));

		assertEquals(1, history.getLogs().size(), "Failed Test 2 addWorkout");
	}

	//check-in is saved
	@Test
	void test_addCheckIn_1()
	{
		WorkoutHistory history = new WorkoutHistory();

		history.addCheckIn(new CheckIn(LocalDate.now(), 7, 3, 3, 3, ""));

		assertEquals(1, history.getCheckIns().size(), "Failed Test 1 addCheckIn");
	}

	//6 days ago is in the last 7 days but 7 days ago is not
	@Test
	void test_getRecentWorkouts_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.addWorkout(new WorkoutLog(LocalDate.now().minusDays(6), null));
		history.addWorkout(new WorkoutLog(LocalDate.now().minusDays(7), null));

		assertEquals(1, history.getRecentWorkouts(7).size(), "Failed Test 1 getRecentWorkouts");
	}

	//workout from today counts
	@Test
	void test_getRecentWorkouts_2()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.addWorkout(new WorkoutLog(LocalDate.now(), null));

		assertEquals(1, history.getRecentWorkouts(7).size(), "Failed Test 2 getRecentWorkouts");
	}

	//6 days ago is in the last 7 days but 7 days ago is not
	@Test
	void test_getRecentCheckIns_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		history.addCheckIn(new CheckIn(LocalDate.now().minusDays(6), 7, 3, 3, 3, ""));
		history.addCheckIn(new CheckIn(LocalDate.now().minusDays(7), 7, 3, 3, 3, ""));

		assertEquals(1, history.getRecentCheckIns(7).size(), "Failed Test 1 getRecentCheckIns");
	}

	//heaviest weight of the day is used
	@Test
	void test_getProgress_1()
	{
		WorkoutHistory history = new WorkoutHistory();
		LocalDate day = LocalDate.now().minusDays(3);
		WorkoutLog log = new WorkoutLog(day, null);
		log.addSet(new SetLog("Bench Press", 8, 60, 7));
		log.addSet(new SetLog("Bench Press", 5, 65, 9));
		history.addWorkout(log);

		assertEquals(65, history.getProgress("Bench Press").get(day), "Failed Test 1 getProgress");
	}

	//one point for each workout day
	@Test
	void test_getProgress_2()
	{
		WorkoutHistory history = new WorkoutHistory();
		WorkoutLog first = new WorkoutLog(LocalDate.now().minusDays(7), null);
		first.addSet(new SetLog("Bench Press", 8, 60, 7));
		WorkoutLog second = new WorkoutLog(LocalDate.now(), null);
		second.addSet(new SetLog("Bench Press", 8, 62.5, 8));
		history.addWorkout(first);
		history.addWorkout(second);

		assertEquals(2, history.getProgress("Bench Press").size(), "Failed Test 2 getProgress");
	}

	//other exercises are left out
	@Test
	void test_getProgress_3()
	{
		WorkoutHistory history = new WorkoutHistory();
		WorkoutLog log = new WorkoutLog(LocalDate.now(), null);
		log.addSet(new SetLog("Barbell Squat", 5, 100, 8));
		history.addWorkout(log);

		Map<LocalDate, Double> progress = history.getProgress("Bench Press");

		assertEquals(0, progress.size(), "Failed Test 3 getProgress");
	}
}

class CountingObserver implements WorkoutObserver
{
	int count = 0;
	WorkoutLog lastLog = null;


	@Override
	public void onWorkoutLogged(WorkoutLog log)
	{
		count++;
		lastLog = log;
	}
}
