package fitnesscoach.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

public class DataStore
{
	private String folder;
	private Gson gson;


	public DataStore(String folder)
	{
		this.folder = folder;
		new File(folder).mkdirs();

		//gson cannot save a LocalDate by itself, so dates are saved as text like 2026-10-25
		gson = new GsonBuilder()
				.registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (date, type, context) -> new JsonPrimitive(date.toString()))
				.registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, type, context) -> LocalDate.parse(json.getAsString()))
				.setPrettyPrinting()
				.create();
	}


	public void saveProfile(UserProfile profile)
	{
		writeFile("profile.json", gson.toJson(profile));
	}


	public UserProfile loadProfile()
	{
		String text = readFile("profile.json");
		if (text == null)
		{
			return null; //no profile saved yet
		}
		return gson.fromJson(text, UserProfile.class);
	}


	public void savePlan(WorkoutPlan plan)
	{
		writeFile("plan.json", gson.toJson(plan));
	}


    public WorkoutPlan loadPlan() {
        String text = readFile("plan.json");
        if (text == null) {
            return null; // no plan saved yet
        }
        WorkoutPlan plan = gson.fromJson(text, WorkoutPlan.class);
        useLibraryExercises(plan);
        return plan;
    }

    // the facade's undo stack (oldest first), so "fitagent plan undo" still works the next time the app starts (F03)
    public void saveOldPlans(List<WorkoutPlan> oldPlans) {
        writeFile("old-plans.json", gson.toJson(oldPlans));
    }

    public List<WorkoutPlan> loadOldPlans() {
        ArrayList<WorkoutPlan> oldPlans = new ArrayList<>();
        String text = readFile("old-plans.json");
        if (text == null) {
            return oldPlans; // nothing saved yet
        }
        for (WorkoutPlan plan : gson.fromJson(text, WorkoutPlan[].class)) {
            useLibraryExercises(plan);
            oldPlans.add(plan);
        }
        return oldPlans;
    }


	public void saveHistory(WorkoutHistory history)
	{
		writeFile("history.json", gson.toJson(history));
	}


	public WorkoutHistory loadHistory()
	{
		String text = readFile("history.json");
		if (text == null)
		{
			return new WorkoutHistory(); //nothing saved yet so start empty
		}
		return gson.fromJson(text, WorkoutHistory.class);
	}


	public void saveMemory(List<String> memory)
	{
		String text = "";

		for (String fact : memory)
		{
			text += fact.replace("\n", " ") + "\n"; //one fact per line
		}
		writeFile("memory.txt", text);
	}


	public List<String> loadMemory()
	{
		ArrayList<String> memory = new ArrayList<>();
		String text = readFile("memory.txt");

		if (text != null)
		{
			for (String line : text.split("\n"))
			{
				if (!line.isBlank())
				{
					memory.add(line.trim());
				}
			}
		}
		return memory;
	}


	public void saveReport(String report)
	{
		writeFile("report-" + LocalDate.now() + ".txt", report);
	}


	public Map<String, String> loadConfig(String filePath)
	{
		HashMap<String, String> config = new HashMap<>();

		try
		{
			for (String line : Files.readAllLines(Path.of(filePath)))
			{
				line = line.trim();
				if (!line.startsWith("#") && line.contains("="))
				{
					int split = line.indexOf("=");
					config.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
				}
			}
		}
		catch(IOException error)
		{
			System.out.println("Error: " + filePath + " not found. Copy .env.example to .env and put your API key in it.");
		}
		return config;
	}


	private void writeFile(String name, String text)
	{
		try
		{
			Files.writeString(Path.of(folder, name), text);
		}
		catch(IOException error)
		{
			System.out.println("Error: could not save " + name);
		}
	}


	private String readFile(String name)
	{
		try
		{
			return Files.readString(Path.of(folder, name));
		}
		catch(IOException error)
		{
			return null; //the file does not exist yet
		}
	}


    // use the library's copy of each exercise, so the whole app shares the same ones (with their alternatives)
    private void useLibraryExercises(WorkoutPlan plan) {
        for (WorkoutDay day : plan.getDays()) {
            for (PlannedExercise planned : day.getExercises()) {
                Exercise fromLibrary = ExerciseLibrary.getInstance().findExercise(planned.getExercise().getName());
                if (fromLibrary != null) {
                    planned.setExercise(fromLibrary);
                }
            }
        }
    }
}
