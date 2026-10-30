package fitnesscoach.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import fitnesscoach.data.UserProfile;
import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

public class FitnessAgent
{
	private static final int MAX_STEPS = 6;
	private static final int MAX_MEMORY = 50;
	private static final String TOOL_INSTRUCTIONS =
			"You are the AI coach inside a fitness app called AI Fitness Plan Coach.\n"
			+ "You can use these tools to look things up:\n"
			+ "- search: finds exercises in the app's exercise library. Input: a muscle | the equipment the user has, separated by commas.\n"
			+ "  Example: TOOL search: chest | dumbbell, bench\n"
			+ "  Muscles: back, biceps, calves, chest, core, glutes, hamstrings, quads, shoulders, triceps.\n"
			+ "  Equipment: band, barbell, cable, dumbbell, kettlebell, machine, pull-up bar (bodyweight exercises are always included).\n"
			+ "- history: the user's workouts and daily check-ins from the last few days. Input: the number of days.\n"
			+ "  Example: TOOL history: 14\n"
			+ "- remember: saves something important about the user for next time. Input: the fact.\n"
			+ "  Example: TOOL remember: the user prefers morning workouts\n"
			+ "To use a tool, reply with only one line that starts with TOOL, like the examples. You will get the result and can use another tool.\n"
			+ "Always use the history tool before you answer a question about the user's workouts, progress or check-ins. "
			+ "Never say there is no data before you have looked with the history tool.\n"
			+ "When you have what you need, reply with your final answer and do not write TOOL.\n";

    // the kinds of boundaries the AI can write, like "avoid: quads" (F01)
    private static final String[] BOUNDARY_KINDS = {"goal", "days", "minutes", "equipment", "avoid", "note", "question", "doctor"};
    private static final String BOUNDARY_FORMAT =
            "Turn the user's notes into a list of boundaries that every workout plan must follow.\n"
            + "Write one boundary per line, and start each line with one of these kinds:\n"
            + "goal: the user's goal, for example build muscle, lose weight or get stronger\n"
            + "days: the days the user can work out, as full day names, for example monday, wednesday, friday\n"
            + "minutes: how long one workout can be, as a number of minutes\n"
            + "equipment: the equipment the user has, from this list: band, barbell, cable, dumbbell, kettlebell, machine, pull-up bar\n"
            + "avoid: a muscle or an exercise the user must not train because of an injury, one per line. "
            + "Use the muscle names from the search tool, for example a knee injury means avoid: quads\n"
            + "note: anything else the planner should know\n"
            + "question: a follow-up question when something important is unclear, for example how serious an injury is\n"
            + "doctor: a short warning to see a doctor before starting, only if the notes mention a serious medical problem\n"
            + "Only write lines like these, with nothing before or after them.\n";

    // how the AI writes a plan (F02, F03). The same format is used to show the AI the current plan
    private static final String PLAN_FORMAT =
            "Write the workouts in exactly this format, with nothing before or after them:\n"
            + "DAY <day>: <a short name for the workout>\n"
            + "<exercise name> | <sets> | <reps> | <weight in kg, 0 for bodyweight>\n"
            + "For example:\n"
            + "DAY monday: Upper body\n"
            + "Dumbbell Bench Press | 3 | 10 | 12\n"
            + "Push Up | 3 | 12 | 0\n"
            + "Use the exercise names exactly as the search tool writes them. "
            + "Each set takes about 3 minutes, and one muscle can have at most 10 sets in a workout.\n";

    // the starter plan trains these muscles, in this order, until the workout is full
    private static final String[] STARTER_MUSCLES = {"quads", "chest", "back", "shoulders", "core", "hamstrings", "glutes", "biceps", "triceps", "calves"};

	private AIModel mainModel;
	private AIModel backupModel;
	private WorkoutHistory history;
	private List<String> memory;


	public FitnessAgent(AIModel mainModel, AIModel backupModel, WorkoutHistory history)
	{
		this.mainModel = mainModel;
		this.backupModel = backupModel;
		this.history = history;
		this.memory = new ArrayList<>();
	}


    /** F01: asks the AI to turn the user's notes into boundaries like "avoid: quads". Returns null when no AI model can be reached. */
    public List<String> readBoundaries(UserProfile profile, String notes) {
        String prompt = "A user is setting up their profile: " + profile.getAge() + " years old, " + profile.getHeightCm() + " cm, "
                + profile.getWeightKg() + " kg, " + profile.getExperience() + " at working out.\n"
                + "Their notes for the workout planner:\n" + notes + "\n\n" + BOUNDARY_FORMAT;

        String reply = runAgentLoop(prompt);
        if (reply == null) {
            return null;
        }

        // keep only the lines that start with a boundary kind
        ArrayList<String> boundaries = new ArrayList<>();
        for (String line : reply.split("\n")) {
            line = cleanLine(line);
            for (String kind : BOUNDARY_KINDS) {
                if (line.toLowerCase().startsWith(kind + ":")) {
                    String value = line.substring(kind.length() + 1).trim();
                    if (kind.equals("avoid")) {
                        // the safety check needs one muscle or exercise in each "avoid:" boundary
                        for (String part : value.split(",")) {
                            if (!part.isBlank()) {
                                boundaries.add("avoid: " + part.trim());
                            }
                        }
                    } else if (!value.isEmpty()) {
                        boundaries.add(kind + ": " + value);
                    }
                }
            }
        }
        return boundaries;
    }

    /**
     * F02: asks the AI for one week of workouts and repeats it for the number of weeks.
     * When no AI model can be reached, or its answer has no workouts in it, a simple starter week is used instead (UC02 4a).
     */
    public WorkoutPlan buildPlan(UserProfile profile, int weeks) {
        String prompt = "Make a workout plan for this user.\n" + describeProfile(profile)
                + "Plan one week of workouts. The app repeats your week for all " + weeks + " weeks "
                + "and raises the weights as the user gets stronger.\n"
                + "Use the search tool to find exercises for the user's equipment, and the history tool to see their past workouts. "
                + "Only plan workouts on the user's free days, and follow every boundary. "
                + "Give each workout 4 to 6 exercises that fit in the user's time.\n"
                + PLAN_FORMAT;

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(weeks * 7L - 1);
        WorkoutPlan plan = new WorkoutPlan(start);

        String reply = runAgentLoop(prompt);
        if (reply != null) {
            applyDays(plan, reply, start, end);
        }
        if (plan.getDays().isEmpty()) {
            applyDays(plan, writeStarterWeek(profile), start, end);
        }
        return plan;
    }

    /**
     * F02 and F03: asks the AI to change the plan, for a request from the user or for problems found by the safety check.
     * Returns a changed copy, so the old plan stays the same for undo, or null when no AI model can be reached.
     */
    public WorkoutPlan changePlan(WorkoutPlan plan, UserProfile profile, String request) {
        String prompt = "Here is the user's workout plan from today on:\n" + describePlan(plan) + "\n"
                + describeProfile(profile)
                + "Change the plan for this request:\n" + request + "\n"
                + "Only write the days that change. \"DAY YYYY-MM-DD: ...\" changes one date, "
                + "and \"DAY monday: ...\" changes every monday in the plan. "
                + "To remove a workout, name it rest and write no exercises under it, like \"DAY 2026-11-03: rest\". "
                + "If the change is only for a while, like next week, only change those dates, so the plan goes back to normal after. "
                + "If the request is not safe, make the closest safe change instead. "
                + "Put the reason for each change in the workout name, for example \"DAY tuesday: Upper body (30 minutes, as you asked)\".\n"
                + PLAN_FORMAT;

        String reply = runAgentLoop(prompt);
        if (reply == null) {
            return null;
        }

        WorkoutPlan changed = copyPlan(plan);
        LocalDate from = LocalDate.now(); // days that already happened are never changed
        if (plan.getStartDate().isAfter(from)) {
            from = plan.getStartDate();
        }
        applyDays(changed, reply, from, findEnd(plan));
        return changed;
    }

    /**
     * F04: finds library exercises that could replace this one, then asks the AI to pick the best 3 for the reason,
     * each with a short explanation, like "Goblet Squat: easier on the lower back".
     * When no AI model can be reached, the options are only the exercise names (UC04 3a).
     */
    public List<String> pickSwapOptions(PlannedExercise exercise, String reason, UserProfile profile) {
        ArrayList<String> options = new ArrayList<>();
        List<Exercise> matches = findSwapMatches(exercise.getExercise(), profile);
        if (matches.isEmpty()) {
            return options;
        }

        String matchList = "";
        for (Exercise match : matches) {
            matchList += "- " + match.getName() + " (works " + String.join(", ", match.getMuscles()) + ", needs " + match.getEquipment() + ")\n";
        }
        String prompt = "The user wants to replace " + exercise.getExercise().getName() + " in their plan because: " + reason + "\n"
                + "These exercises from the library work the same muscles and fit the user's equipment:\n" + matchList + "\n"
                + describeProfile(profile)
                + "Pick the 3 best replacements for the user's reason, best first, and say in one short sentence why each one fits.\n"
                + "Write exactly 3 lines in this format, with nothing before or after them:\n"
                + "<exercise name from the list> | <why it is a good replacement>\n";

        String reply = runAgentLoop(prompt);
        if (reply != null) {
            for (String line : reply.split("\n")) {
                line = cleanLine(line);
                if (line.contains("|")) {
                    String name = line.substring(0, line.indexOf("|")).trim();
                    String why = line.substring(line.indexOf("|") + 1).trim();
                    for (Exercise match : matches) {
                        // only exercises from the list, so the AI can not make one up
                        if (options.size() < 3 && match.getName().equalsIgnoreCase(name) && !hasOption(options, match.getName())) {
                            if (why.isEmpty()) {
                                options.add(match.getName());
                            } else {
                                options.add(match.getName() + ": " + why);
                            }
                        }
                    }
                }
            }
        }

        // no AI, or it picked fewer than 3, so fill up with the first matches, without an explanation
        for (Exercise match : matches) {
            if (options.size() < 3 && !hasOption(options, match.getName())) {
                options.add(match.getName());
            }
        }
        return options;
    }


	public String answerQuestion(String question)
	{
		String prompt = "The user asks: " + question + "\n"
				+ "Answer as a friendly fitness coach, using the user's own data from the tools when it helps. "
				+ "Say where your information came from, for example \"from your workouts on 2026-10-20\". "
				+ "Only give general fitness advice and tell the user to see a doctor about anything medical. "
				+ "If the question is not about fitness, politely bring the talk back to fitness. "
				+ "If the answer is not in the user's data, say so instead of guessing.";

		String answer = runAgentLoop(prompt);
		if (answer == null)
		{
			return "The coach is offline right now. Check your internet connection or start the local Llama model.";
		}

		memory.add("The user asked: " + question); //so the coach remembers the conversation next time
		if (memory.size() > MAX_MEMORY)
		{
			memory.remove(0);
		}
		return answer;
	}


	public List<String> getMemory()
	{
		return memory;
	}


	public void setMemory(List<String> memory)
	{
		this.memory = memory;
	}


	private String runAgentLoop(String prompt)
	{
		String memoryText = "";
		if (!memory.isEmpty())
		{
			memoryText = "What you remember about the user:\n";
			for (String fact : memory)
			{
				memoryText += "- " + fact + "\n";
			}
		}
		String dayName = LocalDate.now().getDayOfWeek().toString();
		dayName = dayName.charAt(0) + dayName.substring(1).toLowerCase(); //WEDNESDAY becomes Wednesday
		String conversation = TOOL_INSTRUCTIONS + "Today is " + dayName + " " + LocalDate.now() + ".\n\n" + memoryText + "\n" + prompt;

		for (int step = 0; step < MAX_STEPS; step++)
		{
			AIModel model;
			if (mainModel != null && mainModel.isAvailable())
			{
				model = mainModel;
			}
			else if (backupModel != null && backupModel.isAvailable())
			{
				model = backupModel; //claude can not be reached so use the local llama model
			}
			else
			{
				return null; //no model is available
			}

			String reply = model.send(conversation).trim();

			//look for "TOOL name: input" anywhere, small models sometimes write it in the middle of a sentence
			String toolLine = null;
			int toolStart = reply.indexOf("TOOL ");
			if (toolStart != -1)
			{
				toolLine = reply.substring(toolStart);
				if (toolLine.contains("\n"))
				{
					toolLine = toolLine.substring(0, toolLine.indexOf("\n"));
				}
				if (toolLine.contains(")"))
				{
					toolLine = toolLine.substring(0, toolLine.indexOf(")")); //it was written inside brackets
				}
				toolLine = toolLine.trim();
				while (toolLine.endsWith(".") || toolLine.endsWith(","))
				{
					toolLine = toolLine.substring(0, toolLine.length() - 1);
				}
			}

			if (toolLine == null)
			{
				return reply; //no tool asked for, so this is the final answer
			}

			String toolName = toolLine.substring(5);
			String input = "";
			if (toolName.contains(":"))
			{
				input = toolName.substring(toolName.indexOf(":") + 1).trim();
				toolName = toolName.substring(0, toolName.indexOf(":"));
			}
			toolName = toolName.trim();

			conversation += "\n\nYou asked: " + toolLine + "\nTool result:\n" + useTool(toolName, input)
					+ "\nUse this result to answer the user, or use another tool if you need more.";
		}

		//too many tools in a row, so ask for the answer with what it has
		conversation += "\n\nYou can not use any more tools. Give your final answer now.";
		if (mainModel != null && mainModel.isAvailable())
		{
			return mainModel.send(conversation).trim();
		}
		if (backupModel != null && backupModel.isAvailable())
		{
			return backupModel.send(conversation).trim();
		}
		return null;
	}


	private String useTool(String toolName, String input)
	{
		if (toolName.equalsIgnoreCase("search"))
		{
			String[] parts = input.split("\\|");
			String muscle = parts[0].trim();
			ArrayList<String> equipment = new ArrayList<>();
			if (parts.length > 1)
			{
				for (String item : parts[1].split(","))
				{
					if (!item.isBlank())
					{
						equipment.add(item.trim());
					}
				}
			}

			String ans = "";
			for (Exercise exercise : ExerciseLibrary.getInstance().search(muscle, equipment))
			{
				ans += exercise.getName() + " (works " + String.join(", ", exercise.getMuscles()) + ", needs " + exercise.getEquipment() + ")\n";
			}
			if (ans.isEmpty())
			{
				return "No exercises found for that muscle and equipment. The muscle has to be one of: back, biceps, calves, chest, core, glutes, hamstrings, quads, shoulders, triceps.";
			}
			return ans;
		}
		else if (toolName.equalsIgnoreCase("history"))
		{
			int days = 7;
			try
			{
				days = Integer.parseInt(input.trim());
			}
			catch(NumberFormatException error)
			{
				days = 7; //not a number so use one week
			}

			String ans = "Workouts in the last " + days + " days:\n";
			List<WorkoutLog> logs = history.getRecentWorkouts(days);
			if (logs.isEmpty())
			{
				ans += "none\n";
			}
			for (WorkoutLog log : logs)
			{
				ans += log.getDate() + ": ";
				for (SetLog set : log.getSets())
				{
					ans += set.getExerciseName() + " " + set.getReps() + " x " + set.getWeightKg() + " kg (difficulty " + set.getDifficulty() + "), ";
				}
				ans += "total lifted " + log.getWeightLifted() + " kg\n";
			}

			ans += "Daily check-ins in the last " + days + " days:\n";
			List<CheckIn> checkIns = history.getRecentCheckIns(days);
			if (checkIns.isEmpty())
			{
				ans += "none\n";
			}
			for (CheckIn checkIn : checkIns)
			{
				ans += checkIn.getDate() + ": score " + checkIn.calculateScore() + " out of 100, comments: " + checkIn.getComments() + "\n";
			}
			return ans;
		}
		else if (toolName.equalsIgnoreCase("remember"))
		{
			memory.add(input);
			if (memory.size() > MAX_MEMORY)
			{
				memory.remove(0);
			}
			return "Saved.";
		}
		return "Unknown tool: " + toolName + ". The tools are search, history and remember.";
	}


    // the profile as text for the AI
    private String describeProfile(UserProfile profile) {
        String text = "The user: " + profile.getAge() + " years old, " + profile.getHeightCm() + " cm, "
                + profile.getWeightKg() + " kg, " + profile.getExperience() + ".\n";
        if (!profile.getGoal().isEmpty()) {
            text += "Goal: " + profile.getGoal() + "\n";
        }
        if (profile.getAvailableDays().isEmpty()) {
            text += "Free days: any day\n";
        } else {
            text += "Free days: " + String.join(", ", profile.getAvailableDays()) + "\n";
        }
        if (profile.getMinutesPerWorkout() > 0) {
            text += "Each workout can be at most " + profile.getMinutesPerWorkout() + " minutes, so at most "
                    + profile.getMinutesPerWorkout() / 3 + " sets.\n";
        }
        if (profile.getEquipment().isEmpty()) {
            text += "Equipment: none, so only bodyweight exercises\n";
        } else {
            text += "Equipment: " + String.join(", ", profile.getEquipment()) + "\n";
        }
        if (!profile.getBoundaries().isEmpty()) {
            text += "Boundaries:\n";
            for (String boundary : profile.getBoundaries()) {
                text += "- " + boundary + "\n";
            }
        }
        return text;
    }

    // the plan from today on, in the same format the AI writes
    private String describePlan(WorkoutPlan plan) {
        String text = "";
        for (WorkoutDay day : plan.getDays()) {
            if (!day.getDate().isBefore(LocalDate.now())) {
                text += "DAY " + day.getDate() + " " + day.getDate().getDayOfWeek().toString().toLowerCase() + ": " + day.getNote() + "\n";
                for (PlannedExercise planned : day.getExercises()) {
                    text += planned.getExercise().getName() + " | " + planned.getSets() + " | " + planned.getReps() + " | " + planned.getWeightKg() + "\n";
                }
            }
        }
        if (text.isEmpty()) {
            return "(no workouts planned from today on)\n";
        }
        return text;
    }

    // reads the DAY blocks in a plan written by the AI and puts them in the plan.
    // "DAY 2026-11-03: ..." changes only that date, "DAY monday: ..." changes every monday from "from" to "end",
    // and "DAY 2026-11-03: rest" with no exercises removes that workout
    private void applyDays(WorkoutPlan plan, String text, LocalDate from, LocalDate end) {
        ArrayList<String> headers = new ArrayList<>();
        ArrayList<WorkoutDay> workouts = new ArrayList<>();
        WorkoutDay workout = null;

        for (String line : text.split("\n")) {
            line = cleanLine(line);
            if (line.toUpperCase().startsWith("DAY ")) {
                String header = line.substring(4);
                String note = "";
                if (header.contains(":")) {
                    note = header.substring(header.indexOf(":") + 1).trim();
                    header = header.substring(0, header.indexOf(":"));
                }
                workout = new WorkoutDay(null, note); // the real dates are set below
                headers.add(header);
                workouts.add(workout);
            } else if (workout != null) {
                PlannedExercise planned = readExercise(line);
                if (planned != null) {
                    workout.getExercises().add(planned);
                }
            }
        }

        for (int i = 0; i < workouts.size(); i++) {
            boolean hasExercises = !workouts.get(i).getExercises().isEmpty();
            boolean rest = !hasExercises && workouts.get(i).getNote().toLowerCase().startsWith("rest");
            // a DAY with no exercises that is not a rest day is a mistake by the AI, so it changes nothing
            if (hasExercises || rest) {
                for (LocalDate date : findDates(headers.get(i), from, end)) {
                    plan.getDays().removeIf(day -> day.getDate().equals(date));
                    if (hasExercises) {
                        plan.getDays().add(copyDay(workouts.get(i), date));
                    }
                }
            }
        }
        plan.getDays().sort((a, b) -> a.getDate().compareTo(b.getDate()));
    }

    // a date like 2026-11-03 gives that day, and a day name like monday gives every monday from "from" to "end"
    private List<LocalDate> findDates(String header, LocalDate from, LocalDate end) {
        ArrayList<LocalDate> dates = new ArrayList<>();

        for (String word : header.trim().split(" ")) {
            word = word.replace(",", "").replace("(", "").replace(")", "").trim();
            try {
                LocalDate date = LocalDate.parse(word);
                if (!date.isBefore(from)) {
                    dates.add(date);
                }
                return dates;
            } catch (DateTimeParseException error) {
                // not a date, so check if it is a day name
            }

            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                if (dayOfWeek.toString().equalsIgnoreCase(word)) {
                    for (LocalDate date = from; !date.isAfter(end); date = date.plusDays(1)) {
                        if (date.getDayOfWeek() == dayOfWeek) {
                            dates.add(date);
                        }
                    }
                    return dates;
                }
            }
        }
        return dates; // the header has no date or day name in it
    }

    // reads a line like "Push Up | 3 | 12 | 0", or returns null when it is not a library exercise in that format
    private PlannedExercise readExercise(String line) {
        line = line.trim();
        if (line.startsWith("|")) {
            line = line.substring(1); // the AI wrote a markdown table
        }
        String[] parts = line.split("\\|");
        if (parts.length < 4) {
            return null;
        }

        Exercise exercise = ExerciseLibrary.getInstance().findExercise(parts[0].trim());
        int sets = (int) readNumber(parts[1]);
        int reps = (int) readNumber(parts[2]);
        double weightKg = readNumber(parts[3]);
        if (exercise == null || sets < 1 || reps < 1 || weightKg < 0) {
            return null;
        }
        return new PlannedExercise(exercise, sets, reps, weightKg);
    }

    // the first number in text like "12", "8-10" or "40 kg", or -1 when there is no number
    private double readNumber(String text) {
        String number = "";
        for (char c : text.trim().toCharArray()) {
            if (Character.isDigit(c) || (c == '.' && !number.isEmpty() && !number.contains("."))) {
                number += c;
            } else if (!number.isEmpty()) {
                break;
            }
        }
        if (number.isEmpty()) {
            return -1;
        }
        return Double.parseDouble(number);
    }

    // removes the markdown that models sometimes add, like "- ", "* ", "**" and "1. "
    private String cleanLine(String line) {
        line = line.replace("**", "").trim();
        while (line.startsWith("-") || line.startsWith("*") || line.startsWith("#")) {
            line = line.substring(1).trim();
        }
        if (line.length() > 2 && Character.isDigit(line.charAt(0)) && (line.charAt(1) == '.' || line.charAt(1) == ')')) {
            line = line.substring(2).trim(); // a numbered list
        }
        return line;
    }

    // a new day with its own copies of the exercises, so changing one day never changes another
    private WorkoutDay copyDay(WorkoutDay day, LocalDate date) {
        WorkoutDay copy = new WorkoutDay(date, day.getNote());
        for (PlannedExercise planned : day.getExercises()) {
            PlannedExercise plannedCopy = new PlannedExercise(planned.getExercise(), planned.getSets(), planned.getReps(), planned.getWeightKg());
            plannedCopy.setStuckCount(planned.getStuckCount());
            plannedCopy.setNote(planned.getNote());
            copy.getExercises().add(plannedCopy);
        }
        return copy;
    }

    private WorkoutPlan copyPlan(WorkoutPlan plan) {
        WorkoutPlan copy = new WorkoutPlan(plan.getStartDate());
        for (WorkoutDay day : plan.getDays()) {
            copy.getDays().add(copyDay(day, day.getDate()));
        }
        return copy;
    }

    // the last day the plan covers. A plan always covers whole weeks from its start date
    private LocalDate findEnd(WorkoutPlan plan) {
        LocalDate last = plan.getStartDate();
        for (WorkoutDay day : plan.getDays()) {
            if (day.getDate().isAfter(last)) {
                last = day.getDate();
            }
        }
        long weeks = ChronoUnit.DAYS.between(plan.getStartDate(), last) / 7 + 1;
        return plan.getStartDate().plusDays(weeks * 7 - 1);
    }

    // UC02 4a: a simple full body week from the exercise library, written in the same format as the AI's plans
    private String writeStarterWeek(UserProfile profile) {
        ArrayList<String> days = new ArrayList<>(profile.getAvailableDays());
        if (days.isEmpty()) {
            days.add("monday");
            days.add("wednesday");
            days.add("friday");
        }
        if (days.size() > 3) {
            // three full body workouts a week, spread out over the free days
            String first = days.get(0);
            String middle = days.get(days.size() / 2);
            String last = days.get(days.size() - 1);
            days.clear();
            days.add(first);
            days.add(middle);
            days.add(last);
        }

        int maxExercises = 5;
        if (profile.getMinutesPerWorkout() > 0) {
            maxExercises = Math.max(1, Math.min(5, profile.getMinutesPerWorkout() / 9)); // 3 sets of about 3 minutes each
        }
        int reps = 10;
        if ("intermediate".equalsIgnoreCase(profile.getExperience())) {
            reps = 8;
        }

        // one exercise for each muscle, using an exercise where it is the main (first) muscle
        String exercises = "";
        int count = 0;
        for (String muscle : STARTER_MUSCLES) {
            for (Exercise exercise : ExerciseLibrary.getInstance().search(muscle, profile.getEquipment())) {
                if (count < maxExercises && exercise.getMuscles().get(0).equalsIgnoreCase(muscle) && !isAvoided(exercise, profile)) {
                    exercises += exercise.getName() + " | 3 | " + reps + " | " + exercise.getStartingWeight() + "\n";
                    count++;
                    break;
                }
            }
        }

        String week = "";
        for (String day : days) {
            week += "DAY " + day + ": Full body starter workout\n" + exercises;
        }
        return week;
    }

    // true when the exercise goes against an "avoid:" boundary, the same rule as WorkoutPlan.checkSafety()
    private boolean isAvoided(Exercise exercise, UserProfile profile) {
        for (String boundary : profile.getBoundaries()) {
            if (boundary.toLowerCase().startsWith("avoid:")) {
                String avoid = boundary.substring(6).trim();
                if (exercise.getName().equalsIgnoreCase(avoid)) {
                    return true;
                }
                for (String muscle : exercise.getMuscles()) {
                    if (muscle.equalsIgnoreCase(avoid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // library exercises that could replace this one: its own alternatives first, then the other exercises for its main muscle.
    // When fewer than 3 are found, the search is widened to the exercise's other muscles (UC04 2a)
    private List<Exercise> findSwapMatches(Exercise exercise, UserProfile profile) {
        ArrayList<Exercise> matches = new ArrayList<>();
        ExerciseLibrary library = ExerciseLibrary.getInstance();
        Exercise fromLibrary = library.findExercise(exercise.getName()); // the library's copy has the alternatives
        if (fromLibrary == null) {
            return matches;
        }

        for (Exercise alternative : fromLibrary.getAlternatives()) {
            addMatch(matches, alternative, fromLibrary, profile);
        }
        for (Exercise match : library.search(fromLibrary.getMuscles().get(0), profile.getEquipment())) {
            addMatch(matches, match, fromLibrary, profile);
        }
        if (matches.size() < 3) {
            for (String muscle : fromLibrary.getMuscles()) {
                for (Exercise match : library.search(muscle, profile.getEquipment())) {
                    addMatch(matches, match, fromLibrary, profile);
                }
            }
        }
        return matches;
    }

    // adds the match if it is a different exercise that fits the user's equipment and boundaries
    private void addMatch(List<Exercise> matches, Exercise match, Exercise exercise, UserProfile profile) {
        boolean hasEquipment = match.getEquipment().equals("none");
        for (String item : profile.getEquipment()) {
            if (item.equalsIgnoreCase(match.getEquipment())) {
                hasEquipment = true;
            }
        }
        if (match != exercise && !matches.contains(match) && hasEquipment && !isAvoided(match, profile)) {
            matches.add(match);
        }
    }

    // true when one of the options is already this exercise, with or without an explanation
    private boolean hasOption(List<String> options, String name) {
        for (String option : options) {
            if (option.equals(name) || option.startsWith(name + ":")) {
                return true;
            }
        }
        return false;
    }
}
