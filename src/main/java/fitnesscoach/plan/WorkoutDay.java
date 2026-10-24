package fitnesscoach.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WorkoutDay
{
	private LocalDate date;
	private String note;
	private List<PlannedExercise> exercises;


	public WorkoutDay(LocalDate date, String note)
	{
		this.date = date;
		this.note = note;
		this.exercises = new ArrayList<>();
	}


	public LocalDate getDate()
	{
		return date;
	}


	public String getNote()
	{
		return note;
	}


	public void setNote(String note)
	{
		this.note = note;
	}


	public List<PlannedExercise> getExercises()
	{
		return exercises;
	}
}
