package fitnesscoach.plan;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ExerciseLibrary
{
	private static ExerciseLibrary instance;
	private List<Exercise> exercises;


	private ExerciseLibrary()
	{
		exercises = new ArrayList<>();
		ArrayList<String> alternativeNames = new ArrayList<>();
		InputStream file = ExerciseLibrary.class.getResourceAsStream("/exercises.csv");

		if (file == null)
		{
			System.out.println("Error: exercises.csv not found.");
		}
		else
		{
			Scanner readFile = new Scanner(file);
			readFile.nextLine(); //skip the header

			while (readFile.hasNextLine())
			{
				String[] parts = readFile.nextLine().split(",");
				ArrayList<String> muscles = new ArrayList<>();

				for (String muscle : parts[1].split(";"))
				{
					muscles.add(muscle);
				}
				exercises.add(new Exercise(parts[0], muscles, parts[2]));

				if (parts.length > 3)
				{
					alternativeNames.add(parts[3]);
				}
				else
				{
					alternativeNames.add("");
				}
			}
			readFile.close();

			//link alternatives after every exercise is loaded
			for (int i = 0; i < exercises.size(); i++)
			{
				for (String name : alternativeNames.get(i).split(";"))
				{
					Exercise alternative = findExercise(name);
					if (alternative != null)
					{
						exercises.get(i).getAlternatives().add(alternative);
					}
				}
			}
		}
	}


	public static ExerciseLibrary getInstance()
	{
		if (instance == null)
		{
			instance = new ExerciseLibrary();
		}
		return instance;
	}


	public List<Exercise> getExercises()
	{
		return exercises;
	}


	public List<Exercise> search(String muscle, List<String> equipment)
	{
		ArrayList<Exercise> ans = new ArrayList<>();

		for (Exercise exercise : exercises)
		{
			boolean worksMuscle = false;
			for (String m : exercise.getMuscles())
			{
				if (m.equalsIgnoreCase(muscle))
				{
					worksMuscle = true;
				}
			}

			boolean hasEquipment = false;
			if (exercise.getEquipment().equals("none"))
			{
				hasEquipment = true;
			}
			for (String item : equipment)
			{
				if (item.equalsIgnoreCase(exercise.getEquipment()))
				{
					hasEquipment = true;
				}
			}

			if (worksMuscle && hasEquipment)
			{
				ans.add(exercise);
			}
		}
		return ans;
	}


	public Exercise findExercise(String name)
	{
		for (Exercise exercise : exercises)
		{
			if (exercise.getName().equalsIgnoreCase(name))
			{
				return exercise;
			}
		}
		return null; //no exercise with that name
	}
}
