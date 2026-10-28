package fitnesscoach.ai;

import java.util.ArrayList;
import java.util.List;

import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;

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
			+ "When you have what you need, reply with your final answer and do not write TOOL.\n";

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
		String conversation = TOOL_INSTRUCTIONS + "\n" + memoryText + "\n" + prompt;

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

			String toolLine = null;
			for (String line : reply.split("\n"))
			{
				if (line.trim().startsWith("TOOL ") && toolLine == null)
				{
					toolLine = line.trim();
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

			conversation += "\n\nYou asked: " + toolLine + "\nTool result:\n" + useTool(toolName, input);
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
				return "No exercises found for that muscle and equipment.";
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
}
