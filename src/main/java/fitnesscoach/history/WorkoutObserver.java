package fitnesscoach.history;

public interface WorkoutObserver
{
	void onWorkoutLogged(WorkoutLog log);
}
