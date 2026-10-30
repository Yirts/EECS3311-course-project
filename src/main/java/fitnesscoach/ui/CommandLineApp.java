package fitnesscoach.ui;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.TreeSet;

import fitnesscoach.controller.FitnessAppFacade;
import fitnesscoach.data.UserProfile;
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
    }

    private void printCommand(String command, String meaning) {
        System.out.println(String.format("  %-46s %s", command, meaning));
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
