package fitnesscoach.progression;

import java.util.List;

import fitnesscoach.history.SetLog;
import fitnesscoach.plan.PlannedExercise;

public class BeginnerStrategy extends ProgressionStrategy
{
	@Override
	public double decideNextWeight(PlannedExercise exercise, List<SetLog> sets)
	{
		if (sets.size() < exercise.getSets())
		{
			return exercise.getWeightKg(); //not every set was done
		}

		for (SetLog set : sets)
		{
			if (set.getReps() < exercise.getReps())
			{
				return exercise.getWeightKg();
			}
		}
		return exercise.getWeightKg() + incrementKg; //every set hit the planned reps
	}
}
