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


    // a copy of this day on another date, with its own copies of the exercises,
    // so changing one day never changes another
    public WorkoutDay copy(LocalDate newDate) {
        WorkoutDay copy = new WorkoutDay(newDate, note);
        for (PlannedExercise planned : exercises) {
            PlannedExercise plannedCopy = new PlannedExercise(planned.getExercise(), planned.getSets(), planned.getReps(), planned.getWeightKg());
            plannedCopy.setStuckCount(planned.getStuckCount());
            plannedCopy.setNote(planned.getNote());
            copy.getExercises().add(plannedCopy);
        }
        return copy;
    }
}
