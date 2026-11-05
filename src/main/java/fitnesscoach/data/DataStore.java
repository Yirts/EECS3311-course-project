package fitnesscoach.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

public class DataStore
{
	private String folder;
	private Gson gson;
    private List<String> importNotes = new ArrayList<>(); // what the last readCsv() found (F12)


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


    /**
     * F12: reads a CSV file of past workouts from another app, with one row for each set, and returns one WorkoutLog
     * for each date. The columns are found by their names, so files from different apps work. Rows that can't be read
     * are skipped, and getImportNotes() says what happened.
     */
    public List<WorkoutLog> readCsv(String filePath) {
        importNotes = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(filePath));
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("Could not read the file " + filePath);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("The file " + filePath + " is empty.");
        }

        String header = lines.get(0).replace("﻿", ""); // some apps start the file with an invisible mark
        char separator = ',';
        if (!header.contains(",") && header.contains(";")) {
            separator = ';';
        }
        ArrayList<String> columns = new ArrayList<>();
        for (String column : splitCsvLine(header, separator)) {
            columns.add(column.trim().toLowerCase());
        }
        int dateColumn = findColumn(columns, "date", "start_time", "workout date");
        int exerciseColumn = findColumn(columns, "exercise name", "exercise_title", "exercise");
        int repsColumn = findColumn(columns, "reps", "repetitions");
        int weightColumn = findColumn(columns, "weight", "weight_kg", "weight (kg)", "weight_lbs", "weight (lbs)", "weight (lb)", "weight_lb");
        int difficultyColumn = findColumn(columns, "rpe", "difficulty");
        if (dateColumn == -1 || exerciseColumn == -1 || repsColumn == -1 || weightColumn == -1) {
            throw new IllegalArgumentException("The file needs columns for the date, the exercise, the reps and the weight.");
        }

        // UC12 2: which app the file came from
        if (columns.contains("exercise_title")) {
            importNotes.add("This looks like a Hevy export.");
        } else if (columns.contains("exercise name") && columns.contains("set order")) {
            importNotes.add("This looks like a Strong export.");
        } else {
            importNotes.add("This is a workout CSV file.");
        }
        boolean pounds = columns.get(weightColumn).contains("lb");
        if (pounds) {
            importNotes.add("The weights are in pounds, so they were changed to kilograms."); // UC12 2c
        }

        TreeMap<LocalDate, WorkoutLog> logs = new TreeMap<>(); // one workout for each date, in date order
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> cells = splitCsvLine(lines.get(i), separator);
            try {
                LocalDate date = readDate(cell(cells, dateColumn));
                String exercise = cell(cells, exerciseColumn).trim();
                if (date == null) {
                    throw new IllegalArgumentException("there is no date");
                }
                if (exercise.isEmpty()) {
                    throw new IllegalArgumentException("there is no exercise");
                }

                int reps = (int) Double.parseDouble(cell(cells, repsColumn).trim());
                double weightKg = 0;
                if (!cell(cells, weightColumn).isBlank()) {
                    weightKg = Double.parseDouble(cell(cells, weightColumn).trim());
                }
                if (pounds) {
                    weightKg = Math.round(weightKg * 0.45359237 * 10) / 10.0;
                }
                int difficulty = 7;
                if (difficultyColumn != -1) {
                    difficulty = readDifficulty(cell(cells, difficultyColumn));
                }

                SetLog set = new SetLog(exercise, reps, weightKg, difficulty); // throws for numbers that are not realistic
                if (logs.get(date) == null) {
                    logs.put(date, new WorkoutLog(date, null));
                }
                logs.get(date).addSet(set);
            } catch (NumberFormatException error) {
                importNotes.add("Line " + (i + 1) + " was skipped: a number could not be read."); // UC12 2b
            } catch (IllegalArgumentException error) {
                importNotes.add("Line " + (i + 1) + " was skipped: " + error.getMessage().toLowerCase() + ".");
            }
        }
        return new ArrayList<>(logs.values());
    }

    // what happened during the last readCsv(): the app the file came from, the units and the rows that were skipped
    public List<String> getImportNotes() {
        return importNotes;
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


    // splits a CSV line at the separator, but not inside "quotes"
    private List<String> splitCsvLine(String line, char separator) {
        ArrayList<String> cells = new ArrayList<>();
        String cell = "";
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell += '"'; // "" is a quote mark inside a quoted cell
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                cells.add(cell);
                cell = "";
            } else {
                cell += c;
            }
        }
        cells.add(cell);
        return cells;
    }

    // an RPE or difficulty from 1 to 10, or 7 when the cell is empty or can't be read
    private int readDifficulty(String text) {
        try {
            int difficulty = (int) Math.round(Double.parseDouble(text.trim()));
            if (difficulty >= 1 && difficulty <= 10) {
                return difficulty;
            }
        } catch (NumberFormatException error) {
            // most apps don't save how hard a set felt
        }
        return 7;
    }

    // the number of the first column with one of the names, or -1
    private int findColumn(List<String> columns, String... names) {
        for (int i = 0; i < columns.size(); i++) {
            for (String name : names) {
                if (columns.get(i).equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String cell(List<String> cells, int column) {
        if (column < cells.size()) {
            return cells.get(column);
        }
        return "";
    }

    // reads dates like 2026-10-21, 2026-10-21 18:30:00, 21 Oct 2026, 18:30 or 10/21/2026, or returns null
    private LocalDate readDate(String text) {
        text = text.trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (DateTimeParseException error) {
                // not an ISO date, so the other formats are tried below
            }
        }

        String datePart = text;
        if (datePart.contains(",")) {
            datePart = datePart.substring(0, datePart.indexOf(","));
        }
        String firstWord = text.split(" ")[0];
        String[] formats = {"d MMM yyyy", "M/d/yyyy"};
        for (String format : formats) {
            for (String part : new String[] {datePart.trim(), firstWord}) {
                try {
                    return LocalDate.parse(part, DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
                } catch (DateTimeParseException error) {
                    // try the next way of writing a date
                }
            }
        }
        return null;
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
