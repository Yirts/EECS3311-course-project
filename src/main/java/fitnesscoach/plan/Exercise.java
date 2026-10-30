package fitnesscoach.plan;

import java.util.ArrayList;
import java.util.List;

public class Exercise
{
	private String name;
	private List<String> muscles;
	private String equipment;
	private transient List<Exercise> alternatives; //not saved, the library links them again


	public Exercise(String name, List<String> muscles, String equipment)
	{
		this.name = name;
		this.muscles = muscles;
		this.equipment = equipment;
		this.alternatives = new ArrayList<>();
	}


	public String getName()
	{
		return name;
	}


	public List<String> getMuscles()
	{
		return muscles;
	}


	public String getEquipment()
	{
		return equipment;
	}


	public List<Exercise> getAlternatives()
	{
		return alternatives;
	}


    // a light weight to start with on this exercise's equipment. The weight progression raises it after each workout
    public double getStartingWeight() {
        switch (equipment) {
            case "barbell":
                return 20; // the empty bar
            case "dumbbell":
                return 5;
            case "kettlebell":
                return 8;
            case "machine":
            case "cable":
                return 10;
            default:
                return 0; // bodyweight, bands and the pull-up bar
        }
    }
}
