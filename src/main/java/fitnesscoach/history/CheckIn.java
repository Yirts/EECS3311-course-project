package fitnesscoach.history;

import java.time.LocalDate;

public class CheckIn
{
	private LocalDate date;
	private double sleepHours;
	private int energy;
	private int stress;
	private int soreness;
	private String comments;


	public CheckIn(LocalDate date, double sleepHours, int energy, int stress, int soreness, String comments)
	{
		if (sleepHours < 0 || sleepHours > 24)
		{
			throw new IllegalArgumentException("Sleep must be between 0 and 24 hours");
		}
		if (energy < 1 || energy > 5 || stress < 1 || stress > 5 || soreness < 1 || soreness > 5)
		{
			throw new IllegalArgumentException("Energy, stress and soreness must be between 1 and 5");
		}
		this.date = date;
		this.sleepHours = sleepHours;
		this.energy = energy;
		this.stress = stress;
		this.soreness = soreness;
		this.comments = comments;
	}


	public int calculateScore()
	{
		double sleepPoints = sleepHours;
		if (sleepPoints > 8)
		{
			sleepPoints = 8; //more than 8 hours does not add more points
		}

		//each answer is worth up to 25 points, so the score is out of 100
		double score = sleepPoints / 8 * 25;
		score += (energy - 1) / 4.0 * 25;
		score += (5 - stress) / 4.0 * 25;
		score += (5 - soreness) / 4.0 * 25;
		return (int) Math.round(score);
	}


	public LocalDate getDate()
	{
		return date;
	}


	public double getSleepHours()
	{
		return sleepHours;
	}


	public int getEnergy()
	{
		return energy;
	}


	public int getStress()
	{
		return stress;
	}


	public int getSoreness()
	{
		return soreness;
	}


	public String getComments()
	{
		return comments;
	}
}
