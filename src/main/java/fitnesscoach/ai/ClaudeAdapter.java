package fitnesscoach.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;

public class ClaudeAdapter implements AIModel
{
	private AnthropicClient client;
	private String modelName;


	public ClaudeAdapter(String apiKey, String modelName)
	{
		if (modelName == null || modelName.isEmpty())
		{
			modelName = "claude-sonnet-5";
		}
		this.modelName = modelName;

		if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_LOCAL_API_KEY_HERE"))
		{
			client = null; //no key means claude can not be used
		}
		else
		{
			client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
		}
	}


	@Override
	public String send(String prompt)
	{
		if (client == null)
		{
			throw new IllegalStateException("There is no Anthropic API key in .env");
		}

		MessageCreateParams params = MessageCreateParams.builder()
				.model(modelName)
				.maxTokens(16000L)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
				.addUserMessage(prompt)
				.build();
		Message response = client.messages().create(params);

		String ans = "";
		for (ContentBlock block : response.content())
		{
			if (block.text().isPresent())
			{
				ans += block.text().get().text();
			}
		}
		return ans;
	}


	@Override
	public boolean isAvailable()
	{
		if (client == null)
		{
			return false;
		}

		try
		{
			client.models().list(); //free call that only works with a good key and an internet connection
			return true;
		}
		catch(Exception error)
		{
			return false;
		}
	}


	public String getModelName()
	{
		return modelName;
	}
}
