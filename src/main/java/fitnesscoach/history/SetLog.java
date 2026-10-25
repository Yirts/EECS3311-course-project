package fitnesscoach.history;

public class SetLog
{
	private String exerciseName;
	private int reps;
	private double weightKg;
	private int difficulty;


	public SetLog(String exerciseName, int reps, double weightKg, int difficulty)
	{
		if (reps < 0 || reps > 100)
		{
			throw new IllegalArgumentException("Reps must be between 0 and 100");
		}
		if (weightKg < 0 || weightKg > 500)
		{
			throw new IllegalArgumentException("Weight must be between 0 and 500 kg");
		}
		if (difficulty < 1 || difficulty > 10)
		{
			throw new IllegalArgumentException("Difficulty must be between 1 and 10");
		}
		this.exerciseName = exerciseName;
		this.reps = reps;
		this.weightKg = weightKg;
		this.difficulty = difficulty;
	}


	public String getExerciseName()
	{
		return exerciseName;
	}


	public int getReps()
	{
		return reps;
	}


	public double getWeightKg()
	{
		return weightKg;
	}


	public int getDifficulty()
	{
		return difficulty;
	}
}
