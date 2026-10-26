package fitnesscoach.progression;

import java.util.List;

import fitnesscoach.history.SetLog;
import fitnesscoach.plan.PlannedExercise;

public class IntermediateStrategy extends ProgressionStrategy
{
	@Override
	public double decideNextWeight(PlannedExercise exercise, List<SetLog> sets)
	{
		int topOfRange = exercise.getReps() + 2; //8 planned reps means a range of 8 to 10

		if (sets.size() < exercise.getSets())
		{
			return exercise.getWeightKg(); //not every set was done
		}

		for (SetLog set : sets)
		{
			if (set.getReps() < topOfRange)
			{
				return exercise.getWeightKg();
			}
		}
		return exercise.getWeightKg() + incrementKg; //every set reached the top of the range
	}
}
