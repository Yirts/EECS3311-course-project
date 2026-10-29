package fitnesscoach.ai;

public class LlamaAdapter implements AIModel
{
	private OllamaClient client;
	private String modelName;


	public LlamaAdapter(OllamaClient client, String modelName)
	{
		if (modelName == null || modelName.isEmpty())
		{
			modelName = "llama3.2";
		}
		this.client = client;
		this.modelName = modelName;
	}


	@Override
	public String send(String prompt)
	{
		return client.chat(modelName, prompt);
	}


	@Override
	public boolean isAvailable()
	{
		for (String name : client.listModels())
		{
			//ollama lists models like llama3.2:latest
			if (name.equals(modelName) || name.startsWith(modelName + ":"))
			{
				return true;
			}
		}
		return false;
	}


	public String getModelName()
	{
		return modelName;
	}
}
