package fitnesscoach.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import fitnesscoach.data.UserProfile;

public class WorkoutPlan
{
	private static final int MINUTES_PER_SET = 3;
	private static final int MAX_SETS_PER_MUSCLE = 10;

	private LocalDate startDate;
	private List<WorkoutDay> days;


	public WorkoutPlan(LocalDate startDate)
	{
		this.startDate = startDate;
		this.days = new ArrayList<>();
	}


	public LocalDate getStartDate()
	{
		return startDate;
	}


	public List<WorkoutDay> getDays()
	{
		return days;
	}


	public WorkoutDay getTodaysWorkout()
	{
		LocalDate today = LocalDate.now();

		for (WorkoutDay day : days)
		{
			if (day.getDate().equals(today))
			{
				return day;
			}
		}
		return null; //nothing planned today
	}


	public List<String> checkSafety(UserProfile profile)
	{
		ArrayList<String> problems = new ArrayList<>();

		for (WorkoutDay day : days)
		{
			String dayName = day.getDate().getDayOfWeek().toString();
			boolean freeThatDay = profile.getAvailableDays().isEmpty(); //no days set means any day is fine
			for (String available : profile.getAvailableDays())
			{
				if (available.equalsIgnoreCase(dayName))
				{
					freeThatDay = true;
				}
			}
			if (!freeThatDay)
			{
				problems.add(day.getDate() + ": the user is not free on " + dayName.toLowerCase());
			}

			int totalSets = 0;
			TreeMap<String, Integer> setsPerMuscle = new TreeMap<>();

			for (PlannedExercise planned : day.getExercises())
			{
				Exercise exercise = planned.getExercise();
				totalSets += planned.getSets();

				//injuries are saved as boundaries like "avoid: quads" or "avoid: Barbell Squat"
				for (String boundary : profile.getBoundaries())
				{
					if (boundary.toLowerCase().startsWith("avoid:"))
					{
						String avoid = boundary.substring(6).trim();
						boolean strains = exercise.getName().equalsIgnoreCase(avoid);
						for (String muscle : exercise.getMuscles())
						{
							if (muscle.equalsIgnoreCase(avoid))
							{
								strains = true;
							}
						}
						if (strains)
						{
							problems.add(day.getDate() + ": " + exercise.getName() + " goes against \"" + boundary + "\"");
						}
					}
				}

				boolean hasEquipment = false;
				if (exercise.getEquipment().equals("none"))
				{
					hasEquipment = true;
				}
				for (String item : profile.getEquipment())
				{
					if (item.equalsIgnoreCase(exercise.getEquipment()))
					{
						hasEquipment = true;
					}
				}
				if (!hasEquipment)
				{
					problems.add(day.getDate() + ": " + exercise.getName() + " needs a " + exercise.getEquipment() + ", which the user does not have");
				}

				if (!exercise.getMuscles().isEmpty())
				{
					String mainMuscle = exercise.getMuscles().get(0); //first muscle is the main one
					if (setsPerMuscle.get(mainMuscle) == null)
					{
						setsPerMuscle.put(mainMuscle, planned.getSets());
					}
					else
					{
						setsPerMuscle.put(mainMuscle, setsPerMuscle.get(mainMuscle) + planned.getSets());
					}
				}
			}

			int minutes = totalSets * MINUTES_PER_SET;
			if (profile.getMinutesPerWorkout() > 0 && minutes > profile.getMinutesPerWorkout())
			{
				problems.add(day.getDate() + ": the workout takes about " + minutes + " minutes but the user only has " + profile.getMinutesPerWorkout());
			}

			for (String muscle : setsPerMuscle.keySet())
			{
				if (setsPerMuscle.get(muscle) > MAX_SETS_PER_MUSCLE)
				{
					problems.add(day.getDate() + ": " + setsPerMuscle.get(muscle) + " sets for " + muscle + " is too many (the limit is " + MAX_SETS_PER_MUSCLE + ")");
				}
			}
		}
		return problems;
	}
}
