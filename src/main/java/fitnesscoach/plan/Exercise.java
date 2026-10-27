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
}
