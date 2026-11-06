package fitnesscoach.ui;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;

import fitnesscoach.controller.FitnessAppFacade;
import fitnesscoach.data.UserProfile;
import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;
import javafx.application.Application;

public class CommandLineApp
{
    private static final int DEFAULT_WEEKS = 4;
    private static final int MAX_ROUNDS = 3; // times the AI can read the profile notes again

    private FitnessAppFacade app;
    private Scanner input;

    public CommandLineApp() {
        app = new FitnessAppFacade("data", ".env"); // loads the saved data and the AI settings
        input = new Scanner(System.in);
    }


	public static void main(String[] args)
	{
		if (args.length == 0)
		{
			Application.launch(Main.class, args); //no command opens the gui
		}
		else
		{
			new CommandLineApp().runCommand(String.join(" ", args));
		}
	}


    public void runCommand(String command) {
        command = command.trim();

        if (command.equals("help")) {
            printHelp();
        } else if (command.equals("profile setup")) {
            setUpProfile();
        } else if (command.startsWith("plan generate")) {
            generatePlan(command);
        } else if (command.equals("plan show")) {
            showPlan(app.getCurrentPlan());
        } else if (command.startsWith("plan change")) {
            changePlan(command.substring("plan change".length()));
        } else if (command.equals("plan undo")) {
            undoChange();
        } else if (command.startsWith("exercise swap")) {
            swapExercise(command.substring("exercise swap".length()));
        } else if (command.startsWith("checkin")) {
            checkIn(command);
        } else if (command.equals("workout start")) {
            startWorkout();
        } else if (command.startsWith("workout log")) {
            logSet(command);
        } else if (command.equals("workout skip")) {
            skipExercise();
        } else if (command.equals("workout finish")) {
            finishWorkout();
        } else if (command.equals("plan progress")) {
            showProgression();
        } else if (command.startsWith("progress")) {
            showDashboard(command);
        } else if (command.startsWith("chat")) {
            chat(command.substring("chat".length()));
        } else if (command.equals("nutrition targets")) {
            showTargets();
        } else if (command.equals("nutrition meals")) {
            showMealIdeas();
        } else if (command.equals("report weekly")) {
            weeklyReport();
        } else if (command.startsWith("import")) {
            importHistory(command.substring("import".length()));
        } else {
            System.out.println("Unknown command: " + command);
            System.out.println("Type \"fitagent help\" to see the commands.");
        }
    }

    private void printHelp() {
        System.out.println("AI Fitness Plan Coach");
        System.out.println("Usage: fitagent <command>");
        System.out.println();
        printCommand("(no command)", "open the GUI");
        printCommand("help", "show this list");
        printCommand("profile setup", "set up your profile and boundaries");
        printCommand("plan generate --weeks 8", "make a new workout plan (1 to 12 weeks, 4 if no number is given)");
        printCommand("plan show", "show your current plan");
        printCommand("plan change \"only 30 minutes on tuesday\"", "ask the AI to change your plan");
        printCommand("plan undo", "go back to your plan from before the last change");
        printCommand("exercise swap \"Barbell Squat\" --reason pain", "swap an exercise for one of 3 options");
        printCommand("checkin --sleep 6 --energy 2 --stress 3", "daily check-in (also --soreness and --comments)");
        printCommand("workout start", "start today's workout");
        printCommand("workout log --reps 8 --weight 60 --difficulty 8", "log a set (--exercise \"Name\" for another exercise)");
        printCommand("workout skip", "skip the current exercise");
        printCommand("workout finish", "finish the workout and see your next weights");
        printCommand("plan progress", "see your next weights and change one");
        printCommand("progress --exercise \"Barbell Squat\" --range 12w", "your progress dashboard (--bodyweight 78.5 adds a body weight)");
        printCommand("chat", "talk to the AI coach (or: chat \"your question\")");
        printCommand("nutrition targets", "your daily calorie and macro targets");
        printCommand("nutrition meals", "meal ideas that fit your targets and allergies");
        printCommand("report weekly", "the AI's review of your week, with suggestions you can apply");
        printCommand("import sample-data/workout-history.csv", "import past workouts from another app's CSV file");
    }

    private void printCommand(String command, String meaning) {
        System.out.println(String.format("  %-50s %s", command, meaning));
    }

    // F01: fitagent profile setup
    private void setUpProfile() {
        System.out.println("Profile setup");
        int age = (int) askNumber("Age: ");
        double heightCm = askNumber("Height in cm: ");
        double weightKg = askNumber("Weight in kg: ");
        System.out.print("Workout experience (beginner or intermediate): ");
        String experience = input.nextLine().trim().toLowerCase();
        System.out.println("Tell the AI planner anything it should know: injuries, your goal, your equipment,");
        System.out.println("which days you can work out and how long a workout can be.");
        System.out.print("> ");
        String notes = input.nextLine().trim();

        UserProfile profile = new UserProfile(age, heightCm, weightKg, experience);
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            System.out.println("Checking your information and reading your notes...");
            List<String> boundaries;
            try {
                boundaries = app.saveProfile(profile, notes);
            } catch (RuntimeException error) {
                System.out.println(error.getMessage()); // UC01 4a: nothing is saved
                return;
            }
            if (boundaries == null) {
                System.out.println("The AI is offline, so your basic information was saved and your notes were kept as they are.");
                System.out.println("Run \"fitagent profile setup\" again when the AI is back to turn them into boundaries.");
                return;
            }

            // UC01 5a: the AI asks follow-up questions, and the answers are added to the notes
            ArrayList<String> summary = new ArrayList<>();
            boolean asked = false;
            for (String boundary : boundaries) {
                if (!boundary.startsWith("question:")) {
                    summary.add(boundary);
                } else if (round < MAX_ROUNDS) {
                    String question = boundary.substring(9).trim();
                    System.out.print(question + " ");
                    notes += "\n" + question + " " + input.nextLine().trim();
                    asked = true;
                }
            }
            if (asked) {
                continue;
            }

            System.out.println();
            System.out.println("Here is what the AI understood:");
            for (String boundary : summary) {
                System.out.println("  - " + boundary);
            }
            for (String boundary : summary) {
                if (boundary.startsWith("doctor:")) {
                    System.out.println("Important: " + boundary.substring(7).trim()); // UC01 5b: see a doctor first
                }
            }

            System.out.print("Is this right? (yes/no): ");
            if (input.nextLine().trim().toLowerCase().startsWith("y")) {
                app.confirmBoundaries(summary);
                System.out.println("Your profile is saved.");
                return;
            }
            // UC01 7a: the user explains again, and the AI reads the notes again
            System.out.print("What is wrong? ");
            notes += "\nCorrection from the user: " + input.nextLine().trim();
        }
        System.out.println("Your profile was not saved. Run \"fitagent profile setup\" to try again.");
    }

    // F02: fitagent plan generate --weeks 8
    private void generatePlan(String command) {
        if (app.getProfile() == null) {
            System.out.println("You need a profile first. Run \"fitagent profile setup\".");
            return;
        }

        int weeks = DEFAULT_WEEKS;
        if (command.contains("--weeks")) {
            try {
                weeks = Integer.parseInt(command.substring(command.indexOf("--weeks") + 7).trim());
            } catch (NumberFormatException error) {
                System.out.println("The number of weeks must be a whole number, for example: fitagent plan generate --weeks 8");
                return;
            }
        }
        if (weeks < 1 || weeks > FitnessAppFacade.MAX_WEEKS) {
            System.out.println("A plan can be 1 to " + FitnessAppFacade.MAX_WEEKS + " weeks long.");
            return;
        }

        while (true) {
            System.out.println("Building your plan. This can take a minute...");
            WorkoutPlan plan;
            try {
                plan = app.generatePlan(weeks);
            } catch (RuntimeException error) {
                System.out.println(error.getMessage());
                return;
            }
            showPlan(plan);

            System.out.println();
            if (app.getCurrentPlan() != null) {
                System.out.println("Accepting this plan replaces your current plan."); // UC02 8a
            }
            System.out.print("Accept, regenerate or cancel? (a/r/c): ");
            String choice = input.nextLine().trim().toLowerCase();
            if (choice.startsWith("a")) {
                app.acceptPlan(plan);
                System.out.println("Your plan is saved.");
                return;
            }
            if (!choice.startsWith("r")) {
                System.out.println("Nothing was saved.");
                return;
            }
        }
    }

    // prints the plan week by week
    private void showPlan(WorkoutPlan plan) {
        if (plan == null || plan.getDays().isEmpty()) {
            System.out.println("There is no plan yet. Run \"fitagent plan generate --weeks 8\" to make one.");
            return;
        }

        long week = 0;
        for (WorkoutDay day : plan.getDays()) {
            long dayWeek = ChronoUnit.DAYS.between(plan.getStartDate(), day.getDate()) / 7 + 1;
            if (dayWeek != week) {
                week = dayWeek;
                System.out.println();
                System.out.println("Week " + week);
            }
            System.out.print(describeDay(day));
        }
    }

    // F03: fitagent plan change "only 30 minutes on tuesday"
    private void changePlan(String request) {
        request = request.replace("\"", "").trim();
        if (app.getCurrentPlan() == null) {
            System.out.println("You don't have a plan yet. Run \"fitagent plan generate --weeks 8\" first.");
            return;
        }
        if (request.isEmpty()) {
            System.out.println("Type the change after the command, for example: fitagent plan change \"only 30 minutes on tuesday\"");
            return;
        }

        while (true) {
            System.out.println("The AI is changing your plan. This can take a minute...");
            WorkoutPlan newPlan;
            try {
                newPlan = app.requestChange(request);
            } catch (RuntimeException error) {
                System.out.println(error.getMessage());
                return;
            }
            if (newPlan == null) {
                System.out.println("The AI is offline, so your plan can't be changed right now. Try again when it is back.");
                return;
            }

            if (showChanges(app.getCurrentPlan(), newPlan) == 0) {
                // UC03 2a: the request was not clear enough to change anything
                System.out.println("The AI did not change anything. Try saying which days or exercises to change,");
                System.out.println("for example \"only 30 minutes on tuesday\" or \"no bench next week\".");
                System.out.print("New request (or press Enter to stop): ");
                request = input.nextLine().trim();
                if (request.isEmpty()) {
                    return;
                }
                continue;
            }

            System.out.print("Confirm, undo, regenerate or edit the request? (c/u/r/e): ");
            String choice = input.nextLine().trim().toLowerCase();
            if (choice.startsWith("c")) {
                app.acceptPlan(newPlan);
                System.out.println("Your plan is saved. Run \"fitagent plan undo\" if you want the old plan back.");
                return;
            } else if (choice.startsWith("e")) {
                System.out.print("New request: ");
                request = input.nextLine().trim();
            } else if (!choice.startsWith("r")) {
                app.undoChange();
                System.out.println("The change was undone, so your plan is the same as before.");
                return;
            }
        }
    }

    // F03: fitagent plan undo
    private void undoChange() {
        try {
            app.undoChange();
            System.out.println("Your plan is back to how it was before the last change.");
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
        }
    }

    // F04: fitagent exercise swap "Barbell Squat" --reason pain
    private void swapExercise(String text) {
        String reason = "";
        if (text.contains("--reason")) {
            reason = text.substring(text.indexOf("--reason") + 8).trim();
            text = text.substring(0, text.indexOf("--reason"));
        }
        String name = text.replace("\"", "").trim();
        if (name.isEmpty()) {
            System.out.println("Type the exercise after the command, for example: fitagent exercise swap \"Barbell Squat\" --reason pain");
            return;
        }
        if (app.getCurrentPlan() == null) {
            System.out.println("You don't have a plan yet. Run \"fitagent plan generate --weeks 8\" first.");
            return;
        }

        // the next time the exercise is in the plan, from today on
        WorkoutDay day = null;
        PlannedExercise exercise = null;
        for (WorkoutDay planDay : app.getCurrentPlan().getDays()) {
            for (PlannedExercise planned : planDay.getExercises()) {
                if (exercise == null && !planDay.getDate().isBefore(LocalDate.now()) && planned.getExercise().getName().equalsIgnoreCase(name)) {
                    day = planDay;
                    exercise = planned;
                }
            }
        }
        if (exercise == null) {
            System.out.println(name + " is not in your plan from today on. Run \"fitagent plan show\" to see your exercises.");
            return;
        }

        if (reason.isEmpty()) {
            System.out.print("Why do you want to swap it? (pain, don't enjoy it, machine in use, or something else): ");
            reason = input.nextLine().trim();
        }
        if (reason.toLowerCase().contains("sharp")) {
            // UC04 1a
            System.out.println("Sharp pain is a warning sign. Stop doing this exercise and see a doctor or physiotherapist.");
        }

        System.out.println("Finding other exercises...");
        List<String> options;
        try {
            options = app.findSwapOptions(exercise, reason);
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
            return;
        }
        if (options.isEmpty()) {
            System.out.println("No other exercise in the library works these muscles with your equipment and boundaries.");
            return;
        }

        System.out.println("Options for " + exercise.getExercise().getName() + ":");
        for (int i = 0; i < options.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + options.get(i));
        }
        System.out.print("Pick an option (1-" + options.size() + "), or press Enter to cancel: ");
        int pick = 0;
        try {
            pick = Integer.parseInt(input.nextLine().trim());
        } catch (NumberFormatException error) {
            pick = 0; // not a number, so nothing is picked
        }
        if (pick < 1 || pick > options.size()) {
            System.out.println("Nothing was changed.");
            return;
        }

        System.out.print("Only on " + dayLabel(day.getDate()) + ", or for the rest of the plan? (o/r): ");
        boolean restOfPlan = input.nextLine().trim().toLowerCase().startsWith("r");
        String oldName = exercise.getExercise().getName();
        app.swapExercise(exercise, options.get(pick - 1), restOfPlan);

        System.out.println("Done. Here is " + dayLabel(day.getDate()) + " now:");
        System.out.print(describeDay(day));
        if (restOfPlan) {
            System.out.println("The rest of your plan uses " + exercise.getExercise().getName() + " too.");
        }
        if (app.getProfile().getBoundaries().contains("avoid: " + oldName)) {
            System.out.println("Because of the pain, your profile now avoids " + oldName + ", so future plans skip it.");
        }
    }

    // F07: fitagent checkin --sleep 6 --energy 2 --stress 3. Anything left out is asked for
    private void checkIn(String command) {
        CheckIn checkIn;
        try {
            double sleepHours = readNumberFlag(command, "--sleep", "Hours of sleep last night: ");
            int energy = (int) readNumberFlag(command, "--energy", "Energy, from 1 (none) to 5 (lots): ");
            int stress = (int) readNumberFlag(command, "--stress", "Stress, from 1 (none) to 5 (lots): ");
            int soreness = (int) readNumberFlag(command, "--soreness", "Soreness, from 1 (none) to 5 (lots): ");
            String comments = readFlag(command, "--comments");
            if (comments == null) {
                System.out.print("Anything else about how you feel? (press Enter to skip): ");
                comments = input.nextLine().trim();
            }
            checkIn = new CheckIn(LocalDate.now(), sleepHours, energy, stress, soreness, comments);
        } catch (IllegalArgumentException error) {
            System.out.println(error.getMessage() + ". The check-in was not saved.");
            return;
        }

        WorkoutDay before = null;
        if (app.getCurrentPlan() != null) {
            before = app.getCurrentPlan().getTodaysWorkout();
        }
        System.out.println("Your check-in score is " + checkIn.calculateScore() + " out of 100. Checking today's workout...");
        WorkoutDay today;
        try {
            today = app.submitDailyCheckIn(checkIn);
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
            return;
        }

        if (today == null) {
            System.out.println("No workout is planned today, but your check-in is saved.");
        } else if (today == before) {
            System.out.println("That is a normal day, so today's workout stays the same.");
        } else {
            System.out.println("Today's workout was changed:");
            System.out.print(describeDay(today));
            System.out.print("Accept or reject the change? (a/r): ");
            if (input.nextLine().trim().toLowerCase().startsWith("r")) {
                app.undoChange();
                System.out.println("OK, you will do the normal workout.");
            } else {
                System.out.println("The changed workout is saved.");
            }
        }
    }

    // F05: fitagent workout start. The daily check-in comes first, unless the user skips it
    private void startWorkout() {
        if (app.getCurrentLog() == null) {
            System.out.print("Do your daily check-in first? (y/n): ");
            if (input.nextLine().trim().toLowerCase().startsWith("y")) {
                checkIn("checkin");
            }
        }

        WorkoutDay day = app.startWorkout();
        WorkoutLog log = app.getCurrentLog();
        if (log.getDate().isBefore(LocalDate.now())) {
            System.out.println("You still have a workout from " + log.getDate() + ". Finish it first with: fitagent workout finish");
            return;
        }
        if (day == null) {
            System.out.println("There are no more workouts in your plan, so this is a free workout. Log each set with --exercise, for example:");
            System.out.println("fitagent workout log --exercise \"Push Up\" --reps 10 --weight 0 --difficulty 6");
            return;
        }

        if (!log.getSets().isEmpty()) {
            System.out.println("Going on with your workout (" + log.getSets().size() + " sets logged so far):");
        } else if (!day.getDate().equals(LocalDate.now())) {
            System.out.println("No workout is planned today, so your next workout starts early:"); // UC05 3a
        } else {
            System.out.println("Today's workout:");
        }
        System.out.print(describeDay(day));
        showNextSet(day, log);
    }

    // F05: fitagent workout log --reps 8 --weight 60 --difficulty 8 (--exercise "Name" for another exercise)
    private void logSet(String command) {
        WorkoutLog log = app.getCurrentLog();
        if (log == null) {
            System.out.println("Start a workout first with: fitagent workout start");
            return;
        }

        String name = readFlag(command, "--exercise");
        if (name == null) {
            PlannedExercise current = null;
            if (log.getDay() != null) {
                current = findCurrentExercise(log.getDay(), log);
            }
            if (current == null) {
                System.out.println("Say which exercise the set was for, for example: --exercise \"Push Up\"");
                return;
            }
            name = current.getExercise().getName();
        } else if (ExerciseLibrary.getInstance().findExercise(name) != null) {
            name = ExerciseLibrary.getInstance().findExercise(name).getName(); // the library's spelling
        }

        SetLog set;
        try {
            int reps = (int) readNumberFlag(command, "--reps", "Reps: ");
            double weightKg = readNumberFlag(command, "--weight", "Weight in kg (0 for bodyweight): ");
            int difficulty = (int) readNumberFlag(command, "--difficulty", "Difficulty, from 1 (easy) to 10 (as hard as possible): ");
            set = new SetLog(name, reps, weightKg, difficulty);
        } catch (IllegalArgumentException error) {
            System.out.println(error.getMessage() + ". The set was not logged."); // UC05 4a
            return;
        }

        app.logSet(set);
        System.out.println("Logged " + name + ": " + set.getReps() + " reps at " + set.getWeightKg() + " kg. Rest about 90 seconds.");
        if (log.getDay() != null) {
            showNextSet(log.getDay(), log);
        }
    }

    // F05: fitagent workout skip
    private void skipExercise() {
        WorkoutLog log = app.getCurrentLog();
        if (log == null || log.getDay() == null) {
            System.out.println("There is no planned workout going on.");
            return;
        }
        PlannedExercise current = findCurrentExercise(log.getDay(), log);
        if (current == null) {
            System.out.println("Every exercise is done. Finish with: fitagent workout finish");
            return;
        }

        app.skipExercise(current);
        System.out.println("Skipped " + current.getExercise().getName() + ". It does not count as a failed attempt.");
        showNextSet(log.getDay(), log);
    }

    // F05 and F06: fitagent workout finish
    private void finishWorkout() {
        if (app.getCurrentLog() == null) {
            System.out.println("There is no workout to finish. Start one with: fitagent workout start");
            return;
        }

        System.out.println("Saving your workout and working out your next weights...");
        WorkoutLog log;
        try {
            log = app.finishWorkout();
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
            return;
        }

        System.out.println();
        System.out.println("Workout done: " + log.getSets().size() + " sets, " + log.getWeightLifted() + " kg lifted in total.");
        ArrayList<String> names = new ArrayList<>();
        for (SetLog set : log.getSets()) {
            if (!names.contains(set.getExerciseName())) {
                names.add(set.getExerciseName());
            }
        }
        for (String name : names) {
            showRecord(name, log);
        }

        if (log.getDay() != null && app.getCurrentPlan() != null) {
            System.out.println();
            System.out.println("Your next weights:");
            if (showNextWeights(log.getDay().getDate().plusDays(1)).isEmpty()) {
                System.out.println("  (no more workouts in your plan)");
            }
            System.out.println("To change one, run: fitagent plan progress");
        }
    }

    // F06: fitagent plan progress. Shows the next weight of each exercise, and the user can change one
    private void showProgression() {
        if (app.getCurrentPlan() == null) {
            System.out.println("You don't have a plan yet. Run \"fitagent plan generate --weeks 8\" first.");
            return;
        }

        System.out.println("Your weights for the next workouts:");
        ArrayList<PlannedExercise> upcoming = showNextWeights(LocalDate.now().plusDays(1));
        if (upcoming.isEmpty()) {
            System.out.println("  (no workouts planned after today)");
            return;
        }

        System.out.print("Type a number to change that weight, or press Enter to accept them: ");
        int pick = 0;
        try {
            pick = Integer.parseInt(input.nextLine().trim());
        } catch (NumberFormatException error) {
            pick = 0;
        }
        if (pick < 1 || pick > upcoming.size()) {
            System.out.println("The weights stay as they are.");
            return;
        }

        PlannedExercise chosen = upcoming.get(pick - 1);
        double weightKg = askNumber("New weight in kg for " + chosen.getExercise().getName() + ": ");
        try {
            app.overrideWeight(chosen, weightKg); // UC06 6a
            System.out.println("Saved. " + chosen.getExercise().getName() + " is now " + weightKg + " kg from then on, and the coach will remember it.");
        } catch (IllegalArgumentException error) {
            System.out.println(error.getMessage());
        }
    }

    // F08: fitagent progress --exercise "Barbell Squat" --range 12w (and --bodyweight 78.5 to add today's body weight)
    private void showDashboard(String command) {
        String bodyWeight = readFlag(command, "--bodyweight");
        if (bodyWeight != null) {
            try {
                app.addBodyWeight(Double.parseDouble(bodyWeight));
                System.out.println("Saved today's body weight: " + bodyWeight + " kg.");
            } catch (NumberFormatException error) {
                System.out.println("--bodyweight must be a number, for example: fitagent progress --bodyweight 78.5");
                return;
            } catch (RuntimeException error) {
                System.out.println(error.getMessage());
                return;
            }
        }

        int days = readRange(readFlag(command, "--range"));
        LocalDate from = LocalDate.now().minusDays(days);
        Map<String, Double> records = app.getPersonalRecords();
        ArrayList<Map.Entry<String, Integer>> muscles = new ArrayList<>(app.getMuscleSets(days).entrySet());
        muscles.sort((a, b) -> b.getValue() - a.getValue()); // most sets first
        if (records.isEmpty() && muscles.get(0).getValue() == 0) {
            // UC08 2a
            System.out.println("No workouts yet. Log one with \"fitagent workout start\", or import your history with \"fitagent import <file>\".");
        }

        String exercise = readFlag(command, "--exercise");
        if (exercise != null && ExerciseLibrary.getInstance().findExercise(exercise) == null) {
            System.out.println(exercise + " is not in the exercise library, so there is no chart for it. "
                    + "The names under \"Personal records\" below will work.");
        } else if (exercise != null) {
            exercise = ExerciseLibrary.getInstance().findExercise(exercise).getName();
            TreeMap<LocalDate, Double> points = new TreeMap<>();
            for (Map.Entry<LocalDate, Double> entry : app.getProgress(exercise).entrySet()) {
                if (entry.getKey().isAfter(from)) {
                    points.put(entry.getKey(), entry.getValue());
                }
            }
            System.out.println();
            System.out.println(exercise + ", heaviest set of each workout in the last " + days + " days:");
            if (points.size() < 2) {
                System.out.println("  Not enough data yet."); // UC08 3a
            }
            drawChart(points, "kg");
        }

        System.out.println();
        System.out.println("Sets for each muscle in the last " + days + " days:");
        for (Map.Entry<String, Integer> muscle : muscles) {
            System.out.println(String.format("  %-12s %s %d", muscle.getKey(), "#".repeat(muscle.getValue()), muscle.getValue()));
        }
        if (muscles.get(0).getValue() > 0) {
            ArrayList<String> notTrained = new ArrayList<>();
            for (Map.Entry<String, Integer> muscle : muscles) {
                if (muscle.getValue() == 0) {
                    notTrained.add(muscle.getKey());
                }
            }
            System.out.println("Trained most: " + muscles.get(0).getKey() + ".");
            if (notTrained.isEmpty()) {
                System.out.println("Trained least: " + muscles.get(muscles.size() - 1).getKey() + ".");
            } else {
                System.out.println("Not trained at all: " + String.join(", ", notTrained) + ".");
            }
        }

        System.out.println();
        System.out.println("Personal records:");
        if (records.isEmpty()) {
            System.out.println("  (none yet)");
        }
        for (Map.Entry<String, Double> record : records.entrySet()) {
            System.out.println("  " + record.getKey() + ": " + record.getValue() + " kg");
        }

        System.out.println();
        System.out.println("Body weight in the last " + days + " days:");
        TreeMap<LocalDate, Double> weights = new TreeMap<>();
        if (app.getProfile() != null) {
            for (Map.Entry<LocalDate, Double> entry : app.getProfile().getWeightHistory().entrySet()) {
                if (entry.getKey().isAfter(from)) {
                    weights.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (weights.isEmpty()) {
            System.out.println("  (none yet, add one with: fitagent progress --bodyweight 78.5)");
        }
        drawChart(weights, "kg");
    }

    // a text chart with one bar for each date. A value much bigger than the one before is flagged as a possible typo (UC08 3b)
    private void drawChart(Map<LocalDate, Double> points, String unit) {
        double max = 0;
        for (double value : points.values()) {
            max = Math.max(max, value);
        }

        Double previous = null;
        for (Map.Entry<LocalDate, Double> point : points.entrySet()) {
            int length = 1;
            if (max > 0) {
                length = Math.max(1, (int) Math.round(point.getValue() / max * 30));
            }
            String line = "  " + point.getKey() + "  " + "#".repeat(length) + " " + point.getValue() + " " + unit;
            if (previous != null && point.getValue() > previous * 1.5 && point.getValue() - previous >= 20) {
                line += "   <- much more than before, is this a typo?";
            }
            System.out.println(line);
            previous = point.getValue();
        }
    }

    // "12w" is 12 weeks, "30d" is 30 days and "6m" is 6 months. With no range the dashboard shows 12 weeks
    private int readRange(String range) {
        if (range == null || range.isEmpty()) {
            return 84;
        }
        try {
            int number = Integer.parseInt(range.substring(0, range.length() - 1));
            char unit = Character.toLowerCase(range.charAt(range.length() - 1));
            if (unit == 'd') {
                return number;
            } else if (unit == 'w') {
                return number * 7;
            } else if (unit == 'm') {
                return number * 30;
            }
        } catch (NumberFormatException error) {
            // not a range, so the default is used below
        }
        System.out.println("The range should look like 12w, 30d or 6m, so 12 weeks is shown.");
        return 84;
    }

    // F09: fitagent chat, or fitagent chat "a question"
    private void chat(String question) {
        question = question.replace("\"", "").trim();
        if (!question.isEmpty()) {
            askCoach(question);
            return;
        }

        System.out.println("Coach chat. Ask anything about your training, or press Enter to stop.");
        while (true) {
            System.out.print("You: ");
            if (!input.hasNextLine()) {
                return;
            }
            String line = input.nextLine().trim();
            if (line.isEmpty()) {
                return;
            }
            askCoach(line);
        }
    }

    private void askCoach(String question) {
        System.out.println("The coach is thinking...");
        try {
            System.out.println("Coach: " + app.askCoach(question));
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
        }
        System.out.println();
    }

    // F10: fitagent nutrition targets
    private void showTargets() {
        Map<String, Integer> targets;
        try {
            targets = app.calculateTargets();
        } catch (RuntimeException error) {
            System.out.println(error.getMessage() + " Run \"fitagent profile setup\".");
            return;
        }

        System.out.println("Your daily targets:");
        System.out.println("  Calories: " + targets.get("calories"));
        if (targets.get("calories") <= 1200) {
            System.out.println("  (your goal would go lower, so this is raised to 1200, the safe minimum)"); // UC10 2b
        }
        System.out.println("  Protein:  " + targets.get("protein") + " g");
        System.out.println("  Carbs:    " + targets.get("carbs") + " g");
        System.out.println("  Fat:      " + targets.get("fat") + " g");
    }

    // F10: fitagent nutrition meals
    private void showMealIdeas() {
        showTargets();
        if (app.getProfile() == null) {
            return;
        }

        System.out.println();
        System.out.print("Foods you like: ");
        String likes = input.nextLine().trim();
        System.out.print("Foods you don't like: ");
        String dislikes = input.nextLine().trim();
        System.out.print("Allergies, separated by commas (press Enter for none): ");
        String allergies = input.nextLine().trim();
        String preferences = "likes: " + likes + "\ndislikes: " + dislikes;
        if (!allergies.isEmpty()) {
            preferences += "\nallergies: " + allergies;
        }

        while (true) {
            System.out.println("The AI is thinking of meals...");
            List<String> ideas;
            try {
                ideas = app.getMealIdeas(preferences);
            } catch (RuntimeException error) {
                System.out.println(error.getMessage());
                return;
            }
            if (ideas == null) {
                System.out.println("The AI is offline, so meal ideas are not available right now. Your targets are above."); // UC10 4a
                return;
            }
            if (ideas.isEmpty()) {
                System.out.println("The AI could not find meals that fit your allergies this time.");
            }
            for (int i = 0; i < ideas.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + ideas.get(i));
            }

            System.out.print("Accept an idea (type its number), regenerate (r), or press Enter to stop: ");
            String choice = input.nextLine().trim().toLowerCase();
            if (!choice.startsWith("r")) {
                int pick = 0;
                try {
                    pick = Integer.parseInt(choice);
                } catch (NumberFormatException error) {
                    pick = 0; // Enter or not a number, so nothing is saved
                }
                if (pick >= 1 && pick <= ideas.size()) {
                    app.acceptMealIdea(ideas.get(pick - 1));
                    System.out.println("Saved. The coach will remember that you like this meal.");
                }
                return;
            }
        }
    }

    // F11: fitagent report weekly
    private void weeklyReport() {
        System.out.println("The AI is writing your weekly report...");
        String report;
        try {
            report = app.generateWeeklyReport();
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
            return;
        }
        System.out.println();
        System.out.print(report);

        // each suggestion can be applied to the plan the same way as "fitagent plan change" (F03)
        ArrayList<String> suggestions = new ArrayList<>();
        boolean inSuggestions = false;
        for (String line : report.split("\n")) {
            if (line.startsWith("Suggestions for next week:")) {
                inSuggestions = true;
            } else if (inSuggestions && line.matches("\\d+\\. .*")) {
                suggestions.add(line.substring(line.indexOf(". ") + 2));
            }
        }
        for (String suggestion : suggestions) {
            if (app.getCurrentPlan() == null) {
                break;
            }
            System.out.print("\nApply \"" + suggestion + "\" to your plan? (y/n): ");
            if (!input.nextLine().trim().toLowerCase().startsWith("y")) {
                continue; // UC11 6a: Deny
            }

            System.out.println("The AI is changing your plan...");
            WorkoutPlan newPlan;
            try {
                newPlan = app.requestChange(suggestion);
            } catch (RuntimeException error) {
                System.out.println(error.getMessage());
                continue;
            }
            if (newPlan == null) {
                System.out.println("The AI is offline, so the suggestions can't be applied right now.");
                break;
            }
            if (showChanges(app.getCurrentPlan(), newPlan) == 0) {
                app.undoChange();
                System.out.println("The AI did not find anything to change for this one.");
                continue;
            }
            System.out.print("Confirm this change? (y/n): ");
            if (input.nextLine().trim().toLowerCase().startsWith("y")) {
                app.acceptPlan(newPlan);
                System.out.println("Your plan is changed.");
            } else {
                app.undoChange();
                System.out.println("Your plan was not changed.");
            }
        }

        System.out.print("\nSave this report? (y/n): ");
        if (input.nextLine().trim().toLowerCase().startsWith("y")) {
            app.saveReport(report);
            System.out.println("Saved as data/report-" + LocalDate.now() + ".txt");
        } else {
            System.out.println("The report was not saved."); // UC11 7a
        }
    }

    // F12: fitagent import history.csv
    private void importHistory(String filePath) {
        filePath = filePath.replace("\"", "").trim();
        if (filePath.isEmpty()) {
            System.out.println("Type the file after the command, for example: fitagent import sample-data/workout-history.csv");
            return;
        }

        System.out.println("Reading " + filePath + "...");
        List<String> unmatched;
        try {
            unmatched = app.importWorkoutHistory(filePath);
        } catch (RuntimeException error) {
            System.out.println(error.getMessage());
            return;
        }
        for (String line : app.getImportPreview()) {
            System.out.println(line);
        }

        HashMap<String, String> matches = new HashMap<>();
        if (!unmatched.isEmpty()) {
            System.out.println();
            System.out.println("These exercises are not in the library. For each one, type the library exercise it is");
            System.out.println("(like \"Cable Fly\"), or press Enter to leave it out:");
            for (String name : unmatched) {
                System.out.print("  " + name + " = ");
                String answer = input.nextLine().trim();
                if (answer.isEmpty()) {
                    continue;
                }
                if (ExerciseLibrary.getInstance().findExercise(answer) == null) {
                    System.out.println("    " + answer + " is not in the library, so " + name + " is left out.");
                } else {
                    matches.put(name, answer);
                }
            }
        }

        System.out.print("Import these workouts? (y/n): ");
        if (!input.nextLine().trim().toLowerCase().startsWith("y")) {
            System.out.println("Nothing was imported."); // UC12 5a
            return;
        }
        System.out.println(app.confirmImport(matches));
    }

    // F06: prints the next planned copy of each exercise from the date on, next to the last weight lifted, and returns them
    private ArrayList<PlannedExercise> showNextWeights(LocalDate from) {
        ArrayList<PlannedExercise> upcoming = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (WorkoutDay day : app.getCurrentPlan().getDays()) {
            if (!day.getDate().isBefore(from)) {
                for (PlannedExercise planned : day.getExercises()) {
                    String name = planned.getExercise().getName();
                    if (!names.contains(name)) {
                        names.add(name);
                        upcoming.add(planned);
                        System.out.println("  " + upcoming.size() + ". " + name + ": " + describeWeight(planned));
                    }
                }
            }
        }
        return upcoming;
    }

    // like "22.5 kg, up from 20.0 kg last time (not heavier for 2 workouts)"
    private String describeWeight(PlannedExercise planned) {
        if (planned.getWeightKg() == 0) {
            return "bodyweight";
        }
        String text = planned.getWeightKg() + " kg";
        TreeMap<LocalDate, Double> progress = new TreeMap<>(app.getProgress(planned.getExercise().getName()));
        if (!progress.isEmpty()) {
            double last = progress.lastEntry().getValue();
            if (planned.getWeightKg() > last) {
                text += ", up from " + last + " kg last time";
            } else if (planned.getWeightKg() < last) {
                text += ", down from " + last + " kg last time";
            } else {
                text += ", the same as last time";
            }
        }
        if (planned.getStuckCount() > 0) {
            text += " (not heavier for " + planned.getStuckCount() + (planned.getStuckCount() == 1 ? " workout)" : " workouts)");
        }
        if (planned.getNote() != null && !planned.getNote().isEmpty()) {
            text += " (" + planned.getNote() + ")";
        }
        return text;
    }

    // UC05 8: says when this workout beat the heaviest weight from an earlier day
    private void showRecord(String exerciseName, WorkoutLog log) {
        double best = 0;
        for (SetLog set : log.getSets()) {
            if (set.getExerciseName().equals(exerciseName) && set.getWeightKg() > best) {
                best = set.getWeightKg();
            }
        }

        double earlierBest = -1;
        for (Map.Entry<LocalDate, Double> entry : app.getProgress(exerciseName).entrySet()) {
            if (entry.getKey().isBefore(log.getDate()) && entry.getValue() > earlierBest) {
                earlierBest = entry.getValue();
            }
        }
        if (earlierBest >= 0 && best > earlierBest) {
            System.out.println("New record: " + exerciseName + " at " + best + " kg (your best was " + earlierBest + " kg).");
        }
    }

    // shows the next set to do: the first exercise that still has sets left and was not skipped
    private void showNextSet(WorkoutDay day, WorkoutLog log) {
        PlannedExercise next = findCurrentExercise(day, log);
        if (next == null) {
            System.out.println("Every exercise is done. Finish with: fitagent workout finish");
            return;
        }

        String weight = "";
        if (next.getWeightKg() > 0) {
            weight = " at " + next.getWeightKg() + " kg";
        }
        int done = countSets(log, next.getExercise().getName());
        System.out.println("Next: " + next.getExercise().getName() + ", set " + (done + 1) + " of " + next.getSets()
                + ", " + next.getReps() + " reps" + weight + ".");
        System.out.println("Log it with: fitagent workout log --reps <reps> --weight <kg> --difficulty <1-10>");
    }

    private PlannedExercise findCurrentExercise(WorkoutDay day, WorkoutLog log) {
        for (PlannedExercise planned : day.getExercises()) {
            boolean skipped = "skipped".equals(planned.getNote());
            if (!skipped && countSets(log, planned.getExercise().getName()) < planned.getSets()) {
                return planned;
            }
        }
        return null;
    }

    private int countSets(WorkoutLog log, String exerciseName) {
        int count = 0;
        for (SetLog set : log.getSets()) {
            if (set.getExerciseName().equalsIgnoreCase(exerciseName)) {
                count++;
            }
        }
        return count;
    }

    // the text after a flag like --reps, up to the next flag, or null when the flag is not there
    private String readFlag(String command, String flag) {
        int start = command.indexOf(flag);
        if (start == -1) {
            return null;
        }
        String value = command.substring(start + flag.length());
        if (value.contains(" --")) {
            value = value.substring(0, value.indexOf(" --"));
        }
        return value.replace("\"", "").trim();
    }

    // a number from a flag, or asked for when the flag is left out or is not a number
    private double readNumberFlag(String command, String flag, String question) {
        String value = readFlag(command, flag);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException error) {
                System.out.println(flag + " must be a number.");
            }
        }
        return askNumber(question);
    }

    // prints the days that are different in the new plan, and returns how many there are
    private int showChanges(WorkoutPlan oldPlan, WorkoutPlan newPlan) {
        TreeSet<LocalDate> dates = new TreeSet<>(); // every date in either plan, in order
        for (WorkoutDay day : oldPlan.getDays()) {
            dates.add(day.getDate());
        }
        for (WorkoutDay day : newPlan.getDays()) {
            dates.add(day.getDate());
        }

        int changes = 0;
        for (LocalDate date : dates) {
            WorkoutDay before = findDay(oldPlan, date);
            WorkoutDay after = findDay(newPlan, date);
            if (!describeDay(before).equals(describeDay(after))) {
                if (changes == 0) {
                    System.out.println();
                    System.out.println("Changed workouts:");
                }
                changes++;
                if (after == null) {
                    System.out.println("  " + dayLabel(date) + "  removed, now a rest day");
                } else {
                    System.out.print(describeDay(after));
                }
            }
        }
        System.out.println();
        return changes;
    }

    // a day the way the plan is printed, or "" when there is no workout
    private String describeDay(WorkoutDay day) {
        if (day == null) {
            return "";
        }
        String text = "  " + dayLabel(day.getDate()) + "  " + day.getNote() + "\n";
        for (PlannedExercise planned : day.getExercises()) {
            text += String.format("      %-28s %d x %d", planned.getExercise().getName(), planned.getSets(), planned.getReps());
            if (planned.getWeightKg() > 0) {
                text += " at " + planned.getWeightKg() + " kg";
            }
            if (planned.getNote() != null && !planned.getNote().isEmpty()) {
                text += "  (" + planned.getNote() + ")";
            }
            text += "\n";
        }
        return text;
    }

    // like "Tue 2026-11-03"
    private String dayLabel(LocalDate date) {
        String dayName = date.getDayOfWeek().toString();
        return dayName.charAt(0) + dayName.substring(1, 3).toLowerCase() + " " + date;
    }

    private WorkoutDay findDay(WorkoutPlan plan, LocalDate date) {
        for (WorkoutDay day : plan.getDays()) {
            if (day.getDate().equals(date)) {
                return day;
            }
        }
        return null;
    }

    // asks again until the user types a number
    private double askNumber(String question) {
        while (true) {
            System.out.print(question);
            try {
                return Double.parseDouble(input.nextLine().trim());
            } catch (NumberFormatException error) {
                System.out.println("Please type a number.");
            }
        }
    }
}
