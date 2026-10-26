package fitnesscoach.progression;

import java.util.List;

import fitnesscoach.history.SetLog;
import fitnesscoach.plan.PlannedExercise;

public abstract class ProgressionStrategy
{
	protected double incrementKg = 2.5;


	public abstract double decideNextWeight(PlannedExercise exercise, List<SetLog> sets);
}
