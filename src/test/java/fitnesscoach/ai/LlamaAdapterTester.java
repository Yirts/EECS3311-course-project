package fitnesscoach.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fitnesscoach.history.WorkoutHistory;

class LlamaAdapterTester
{
	//send goes through ollama's chat and gives back its text
	@Test
	void test_send_1()
	{
		FakeOllama ollama = new FakeOllama();
		LlamaAdapter llama = new LlamaAdapter(ollama, "llama3.2");

		assertEquals("hello from llama", llama.send("Hi"), "Failed Test 1 send");
	}

	//the adapter tells ollama which model to use
	@Test
	void test_send_2()
	{
		FakeOllama ollama = new FakeOllama();
		LlamaAdapter llama = new LlamaAdapter(ollama, "llama3.2");

		llama.send("Hi");

		assertEquals("llama3.2", ollama.lastModel, "Failed Test 2 send");
	}

	//ollama is running and the model is downloaded
	@Test
	void test_isAvailable_1()
	{
		FakeOllama ollama = new FakeOllama();
		ollama.models.add("llama3.2:latest");

		assertTrue(new LlamaAdapter(ollama, "llama3.2").isAvailable(), "Failed Test 1 isAvailable");
	}

	//ollama is not running
	@Test
	void test_isAvailable_2()
	{
		assertFalse(new LlamaAdapter(new FakeOllama(), "llama3.2").isAvailable(), "Failed Test 2 isAvailable");
	}

	//ollama is running but only has a different model
	@Test
	void test_isAvailable_3()
	{
		FakeOllama ollama = new FakeOllama();
		ollama.models.add("mistral:latest");

		assertFalse(new LlamaAdapter(ollama, "llama3.2").isAvailable(), "Failed Test 3 isAvailable");
	}

	//nothing is listening at the address
	@Test
	void test_listModels_1()
	{
		OllamaClient nobody = new OllamaClient("http://localhost:1");

		assertEquals(0, nobody.listModels().size(), "Failed Test 1 listModels");
	}

	//no model in .env means llama3.2
	@Test
	void test_getModelName_1()
	{
		assertEquals("llama3.2", new LlamaAdapter(new FakeOllama(), "").getModelName(), "Failed Test 1 getModelName");
	}

	//claude has no key so the agent uses llama
	@Test
	void test_backup_1()
	{
		FakeOllama ollama = new FakeOllama();
		ollama.models.add("llama3.2:latest");
		FitnessAgent agent = new FitnessAgent(new ClaudeAdapter("", "claude-sonnet-5"), new LlamaAdapter(ollama, "llama3.2"), new WorkoutHistory());

		assertEquals("hello from llama", agent.answerQuestion("Hi"), "Failed Test 1 backup");
	}

	//real call to the local llama model (skipped when ollama is not running)
	@Test
	void test_send_3()
	{
		LlamaAdapter llama = new LlamaAdapter(new OllamaClient("http://localhost:11434"), "llama3.2");
		assumeTrue(llama.isAvailable(), "Ollama is not running so this test is skipped");

		String answer = llama.send("Reply with only the word ready.");

		assertTrue(answer.toLowerCase().contains("ready"), "Failed Test 3 send");
	}
}

class FakeOllama extends OllamaClient
{
	ArrayList<String> models = new ArrayList<>();
	String lastModel = null;


	public FakeOllama()
	{
		super("http://localhost:1");
	}


	@Override
	public String chat(String model, String prompt)
	{
		lastModel = model;
		return "hello from llama";
	}


	@Override
	public List<String> listModels()
	{
		return models;
	}
}
