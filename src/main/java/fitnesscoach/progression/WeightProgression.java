package fitnesscoach.progression;

import java.util.ArrayList;

import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.history.WorkoutObserver;
import fitnesscoach.plan.PlannedExercise;

public class WeightProgression implements WorkoutObserver
{
	private ProgressionStrategy strategy;


	public WeightProgression(ProgressionStrategy strategy)
	{
		this.strategy = strategy;
	}


	public void setStrategy(ProgressionStrategy strategy)
	{
		this.strategy = strategy;
	}


	@Override
	public void onWorkoutLogged(WorkoutLog log)
	{
		if (log.getDay() == null)
		{
			return; //imported workouts are not linked to a planned day
		}

		for (PlannedExercise planned : log.getDay().getExercises())
		{
			ArrayList<SetLog> sets = new ArrayList<>();
			for (SetLog set : log.getSets())
			{
				if (set.getExerciseName().equalsIgnoreCase(planned.getExercise().getName()))
				{
					sets.add(set);
				}
			}

			if (!sets.isEmpty() && planned.getWeightKg() > 0) //skipped and bodyweight exercises stay the same
			{
				double nextWeight = strategy.decideNextWeight(planned, sets);

				if (nextWeight > planned.getWeightKg())
				{
					planned.setStuckCount(0);
				}
				else
				{
					planned.setStuckCount(planned.getStuckCount() + 1);
				}
				planned.setWeightKg(nextWeight);
			}
		}
	}
}
