package fitnesscoach.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;

class FitnessAgentTester
{
	//model answers straight away without a tool
	@Test
	void test_answerQuestion_1()
	{
		FakeModel claude = new FakeModel("Rest days help your muscles recover.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		assertEquals("Rest days help your muscles recover.", agent.answerQuestion("Why do I need rest days?"), "Failed Test 1 answerQuestion");
	}

	//search tool result goes back to the model
	@Test
	void test_answerQuestion_2()
	{
		FakeModel claude = new FakeModel("TOOL search: chest | dumbbell", "Try the Dumbbell Bench Press.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("What chest exercise can I do with dumbbells?");

		assertTrue(claude.prompts.get(1).contains("Dumbbell Bench Press"), "Failed Test 2 answerQuestion");
	}

	//final answer comes after the tool
	@Test
	void test_answerQuestion_3()
	{
		FakeModel claude = new FakeModel("TOOL search: chest | dumbbell", "Try the Dumbbell Bench Press.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		assertEquals("Try the Dumbbell Bench Press.", agent.answerQuestion("What chest exercise can I do with dumbbells?"), "Failed Test 3 answerQuestion");
	}

	//history tool gives the model the user's workouts
	@Test
	void test_answerQuestion_4()
	{
		WorkoutHistory history = new WorkoutHistory();
		WorkoutLog log = new WorkoutLog(LocalDate.now().minusDays(2), null);
		log.addSet(new SetLog("Bench Press", 8, 60, 7));
		history.addWorkout(log);
		FakeModel claude = new FakeModel("TOOL history: 7", "You benched 60 kg two days ago.");
		FitnessAgent agent = new FitnessAgent(claude, null, history);

		agent.answerQuestion("How is my bench press going?");

		assertTrue(claude.prompts.get(1).contains("Bench Press 8 x 60.0 kg"), "Failed Test 4 answerQuestion");
	}

	//remember tool saves a fact
	@Test
	void test_answerQuestion_5()
	{
		FakeModel claude = new FakeModel("TOOL remember: the user prefers morning workouts", "Got it, mornings it is!");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("I like to work out in the morning.");

		assertTrue(agent.getMemory().contains("the user prefers morning workouts"), "Failed Test 5 answerQuestion");
	}

	//saved memory is put in the prompt
	@Test
	void test_answerQuestion_6()
	{
		FakeModel claude = new FakeModel("Here is a peanut free snack.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
		agent.getMemory().add("the user is allergic to peanuts");

		agent.answerQuestion("What is a good snack after a workout?");

		assertTrue(claude.prompts.get(0).contains("the user is allergic to peanuts"), "Failed Test 6 answerQuestion");
	}

	//question is remembered for next time
	@Test
	void test_answerQuestion_7()
	{
		FakeModel claude = new FakeModel("Keep going!");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("Am I getting stronger?");

		assertTrue(agent.getMemory().contains("The user asked: Am I getting stronger?"), "Failed Test 7 answerQuestion");
	}

	//unknown tool is reported back to the model
	@Test
	void test_answerQuestion_8()
	{
		FakeModel claude = new FakeModel("TOOL dance: now", "Sorry about that.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("Hi");

		assertTrue(claude.prompts.get(1).contains("Unknown tool"), "Failed Test 8 answerQuestion");
	}

	//claude can not be reached so the backup model answers
	@Test
	void test_answerQuestion_9()
	{
		FakeModel claude = new FakeModel("from claude");
		claude.available = false;
		FakeModel llama = new FakeModel("from llama");
		FitnessAgent agent = new FitnessAgent(claude, llama, new WorkoutHistory());

		assertEquals("from llama", agent.answerQuestion("Hi"), "Failed Test 9 answerQuestion");
	}

	//no model can be reached
	@Test
	void test_answerQuestion_10()
	{
		FakeModel claude = new FakeModel("from claude");
		claude.available = false;
		FakeModel llama = new FakeModel("from llama");
		llama.available = false;
		FitnessAgent agent = new FitnessAgent(claude, llama, new WorkoutHistory());

		assertTrue(agent.answerQuestion("Hi").contains("offline"), "Failed Test 10 answerQuestion");
	}

	//model that keeps asking for tools is stopped and asked for an answer (6 tools + 1 final)
	@Test
	void test_answerQuestion_11()
	{
		FakeModel claude = new FakeModel("TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "Here is my answer.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("How am I doing?");

		assertEquals(7, claude.prompts.size(), "Failed Test 11 answerQuestion");
	}

	//memory never grows past 50 facts
	@Test
	void test_answerQuestion_12()
	{
		FakeModel claude = new FakeModel("ok");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
		for (int i = 0; i < 50; i++)
		{
			agent.getMemory().add("fact " + i);
		}

		agent.answerQuestion("Hi");

		assertEquals(50, agent.getMemory().size(), "Failed Test 12 answerQuestion");
	}

	//tool asked for in the middle of a sentence is still used (the local llama model does this)
	@Test
	void test_answerQuestion_13()
	{
		FakeModel llama = new FakeModel("Let me check your workout history (TOOL history: 14) first.", "You did great.");
		FitnessAgent agent = new FitnessAgent(llama, null, new WorkoutHistory());

		agent.answerQuestion("How did my bench press go this week?");

		assertTrue(llama.prompts.get(1).contains("Workouts in the last 14 days"), "Failed Test 13 answerQuestion");
	}

	//full stop after the tool is ignored
	@Test
	void test_answerQuestion_14()
	{
		FakeModel llama = new FakeModel("TOOL history: 14.", "You did great.");
		FitnessAgent agent = new FitnessAgent(llama, null, new WorkoutHistory());

		agent.answerQuestion("How did my bench press go this week?");

		assertTrue(llama.prompts.get(1).contains("Workouts in the last 14 days"), "Failed Test 14 answerQuestion");
	}

	//memory loaded from the data store is used
	@Test
	void test_setMemory_1()
	{
		FitnessAgent agent = new FitnessAgent(new FakeModel(), null, new WorkoutHistory());
		ArrayList<String> loaded = new ArrayList<>();
		loaded.add("the user trains at home");

		agent.setMemory(loaded);

		assertEquals("the user trains at home", agent.getMemory().get(0), "Failed Test 1 setMemory");
	}
}

class FakeModel implements AIModel
{
	ArrayList<String> replies = new ArrayList<>();
	ArrayList<String> prompts = new ArrayList<>();
	boolean available = true;


	public FakeModel(String... replies)
	{
		for (String reply : replies)
		{
			this.replies.add(reply);
		}
	}


	@Override
	public String send(String prompt)
	{
		prompts.add(prompt);
		return replies.remove(0);
	}


	@Override
	public boolean isAvailable()
	{
		return available;
	}
}
