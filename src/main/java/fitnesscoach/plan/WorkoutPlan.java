package fitnesscoach.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WorkoutPlan
{
	private LocalDate startDate;
	private List<WorkoutDay> days;


	public WorkoutPlan(LocalDate startDate)
	{
		this.startDate = startDate;
		this.days = new ArrayList<>();
	}


	public LocalDate getStartDate()
	{
		return startDate;
	}


	public List<WorkoutDay> getDays()
	{
		return days;
	}


	public WorkoutDay getTodaysWorkout()
	{
		LocalDate today = LocalDate.now();

		for (WorkoutDay day : days)
		{
			if (day.getDate().equals(today))
			{
				return day;
			}
		}
		return null; //nothing planned today
	}
}
