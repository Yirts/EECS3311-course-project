package fitnesscoach.plan;

public class PlannedExercise
{
	private Exercise exercise;
	private int sets;
	private int reps;
	private double weightKg;
	private int stuckCount;
	private String note;


	public PlannedExercise(Exercise exercise, int sets, int reps, double weightKg)
	{
		this.exercise = exercise;
		this.sets = sets;
		this.reps = reps;
		this.weightKg = weightKg;
		this.stuckCount = 0;
		this.note = "";
	}


	public Exercise getExercise()
	{
		return exercise;
	}


	public void setExercise(Exercise exercise)
	{
		this.exercise = exercise;
	}


	public int getSets()
	{
		return sets;
	}


	public void setSets(int sets)
	{
		this.sets = sets;
	}


	public int getReps()
	{
		return reps;
	}


	public void setReps(int reps)
	{
		this.reps = reps;
	}


	public double getWeightKg()
	{
		return weightKg;
	}


	public void setWeightKg(double weightKg)
	{
		this.weightKg = weightKg;
	}


	public int getStuckCount()
	{
		return stuckCount;
	}


	public void setStuckCount(int stuckCount)
	{
		this.stuckCount = stuckCount;
	}


	public String getNote()
	{
		return note;
	}


	public void setNote(String note)
	{
		this.note = note;
	}
}
