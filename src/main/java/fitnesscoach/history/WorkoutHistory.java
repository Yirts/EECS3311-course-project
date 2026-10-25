package fitnesscoach.history;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fitnesscoach.plan.WorkoutDay;

public class WorkoutHistory
{
	private List<WorkoutLog> logs;
	private WorkoutLog currentLog;
	private List<CheckIn> checkIns;
	private List<WorkoutObserver> observers;


	public WorkoutHistory()
	{
		logs = new ArrayList<>();
		currentLog = null;
		checkIns = new ArrayList<>();
		observers = new ArrayList<>();
	}


	public void addObserver(WorkoutObserver observer)
	{
		observers.add(observer);
	}


	public void startWorkout(WorkoutDay day)
	{
		currentLog = new WorkoutLog(LocalDate.now(), day);
	}


	public void logSet(SetLog set)
	{
		if (currentLog == null)
		{
			throw new IllegalStateException("Start a workout before logging a set");
		}
		currentLog.addSet(set);
	}


	public WorkoutLog finishWorkout()
	{
		if (currentLog == null)
		{
			throw new IllegalStateException("There is no workout to finish");
		}
		WorkoutLog finished = currentLog;
		logs.add(finished);
		currentLog = null;
		notifyObservers(finished);
		return finished;
	}


	public void addWorkout(WorkoutLog log)
	{
		logs.add(log);
		notifyObservers(log);
	}


	public void addCheckIn(CheckIn checkIn)
	{
		checkIns.add(checkIn);
	}


	public List<WorkoutLog> getRecentWorkouts(int days)
	{
		ArrayList<WorkoutLog> ans = new ArrayList<>();
		LocalDate cutoff = LocalDate.now().minusDays(days);

		for (WorkoutLog log : logs)
		{
			if (log.getDate().isAfter(cutoff))
			{
				ans.add(log);
			}
		}
		return ans;
	}


	public List<CheckIn> getRecentCheckIns(int days)
	{
		ArrayList<CheckIn> ans = new ArrayList<>();
		LocalDate cutoff = LocalDate.now().minusDays(days);

		for (CheckIn checkIn : checkIns)
		{
			if (checkIn.getDate().isAfter(cutoff))
			{
				ans.add(checkIn);
			}
		}
		return ans;
	}


	public Map<LocalDate, Double> getProgress(String exerciseName)
	{
		TreeMap<LocalDate, Double> progress = new TreeMap<>(); //sorted by date for the chart

		for (WorkoutLog log : logs)
		{
			for (SetLog set : log.getSets())
			{
				if (set.getExerciseName().equalsIgnoreCase(exerciseName))
				{
					//keep the heaviest weight lifted on each date
					if (progress.get(log.getDate()) == null || set.getWeightKg() > progress.get(log.getDate()))
					{
						progress.put(log.getDate(), set.getWeightKg());
					}
				}
			}
		}
		return progress;
	}


	public List<WorkoutLog> getLogs()
	{
		return logs;
	}


	public WorkoutLog getCurrentLog()
	{
		return currentLog;
	}


	public List<CheckIn> getCheckIns()
	{
		return checkIns;
	}


	private void notifyObservers(WorkoutLog log)
	{
		for (WorkoutObserver observer : observers)
		{
			observer.onWorkoutLogged(log);
		}
	}
}
