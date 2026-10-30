package fitnesscoach.ui;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
        } else {
            System.out.println("Unknown command: " + command);
            System.out.println("Type \"fitagent help\" to see the commands.");
        }
    }

    private void printHelp() {
        System.out.println("AI Fitness Plan Coach");
        System.out.println("Usage: fitagent <command>");
        System.out.println();
        System.out.println("  (no command)               open the GUI");
        System.out.println("  help                       show this list");
        System.out.println("  profile setup              set up your profile and boundaries");
        System.out.println("  plan generate --weeks 8    make a new workout plan (1 to 12 weeks, 4 if no number is given)");
        System.out.println("  plan show                  show your current plan");
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

            String dayName = day.getDate().getDayOfWeek().toString();
            System.out.println("  " + dayName.charAt(0) + dayName.substring(1, 3).toLowerCase() + " " + day.getDate() + "  " + day.getNote());
            for (PlannedExercise planned : day.getExercises()) {
                String line = String.format("      %-28s %d x %d", planned.getExercise().getName(), planned.getSets(), planned.getReps());
                if (planned.getWeightKg() > 0) {
                    line += " at " + planned.getWeightKg() + " kg";
                }
                System.out.println(line);
            }
        }
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
