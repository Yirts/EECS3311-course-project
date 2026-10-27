package fitnesscoach.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fitnesscoach.data.DataStore;

class ClaudeAdapterTester
{
	@TempDir
	Path folder;

	//no key means claude is not available
	@Test
	void test_isAvailable_1()
	{
		assertFalse(new ClaudeAdapter("", "claude-sonnet-5").isAvailable(), "Failed Test 1 isAvailable");
	}

	//the placeholder from .env.example is not a real key
	@Test
	void test_isAvailable_2()
	{
		assertFalse(new ClaudeAdapter("YOUR_LOCAL_API_KEY_HERE", "claude-sonnet-5").isAvailable(), "Failed Test 2 isAvailable");
	}

	//real key in .env (skipped when there is no key)
	@Test
	void test_isAvailable_3()
	{
		Map<String, String> config = new DataStore(folder.toString()).loadConfig(".env");
		assumeTrue(hasRealKey(config), "No API key in .env so this test is skipped");

		assertTrue(new ClaudeAdapter(config.get("ANTHROPIC_API_KEY"), config.get("CLAUDE_MODEL")).isAvailable(), "Failed Test 3 isAvailable");
	}

	//sending without a key
	@Test
	void test_send_1()
	{
		ClaudeAdapter claude = new ClaudeAdapter(null, "claude-sonnet-5");

		assertThrows(IllegalStateException.class, () -> claude.send("hi"), "Failed Test 1 send");
	}

	//real call to claude, costs less than a cent (skipped when there is no key)
	@Test
	void test_send_2()
	{
		Map<String, String> config = new DataStore(folder.toString()).loadConfig(".env");
		assumeTrue(hasRealKey(config), "No API key in .env so this test is skipped");
		ClaudeAdapter claude = new ClaudeAdapter(config.get("ANTHROPIC_API_KEY"), config.get("CLAUDE_MODEL"));

		String answer = claude.send("Reply with only the word ready.");

		assertTrue(answer.toLowerCase().contains("ready"), "Failed Test 2 send");
	}

	//no model in .env means Claude Sonnet 5
	@Test
	void test_getModelName_1()
	{
		assertEquals("claude-sonnet-5", new ClaudeAdapter("", "").getModelName(), "Failed Test 1 getModelName");
	}


	private boolean hasRealKey(Map<String, String> config)
	{
		String key = config.get("ANTHROPIC_API_KEY");
		if (key == null || key.isEmpty() || key.equals("YOUR_LOCAL_API_KEY_HERE"))
		{
			return false;
		}
		return true;
	}
}
