package fitnesscoach.history;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import fitnesscoach.plan.WorkoutDay;

public class WorkoutLog
{
	private LocalDate date;
	private WorkoutDay day;
	private List<SetLog> sets;


	public WorkoutLog(LocalDate date, WorkoutDay day)
	{
		this.date = date;
		this.day = day;
		this.sets = new ArrayList<>();
	}


	public void addSet(SetLog set)
	{
		sets.add(set);
	}


	public double getWeightLifted()
	{
		double total = 0;

		for (SetLog set : sets)
		{
			total += set.getReps() * set.getWeightKg();
		}
		return total;
	}


	public LocalDate getDate()
	{
		return date;
	}


	public WorkoutDay getDay()
	{
		return day;
	}


    // a saved workout has its own copy of the day, so after loading it is pointed back at the plan's day
    public void setDay(WorkoutDay day) {
        this.day = day;
    }


	public List<SetLog> getSets()
	{
		return sets;
	}
}
