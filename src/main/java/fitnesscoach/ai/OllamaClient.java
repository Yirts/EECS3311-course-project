package fitnesscoach.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OllamaClient
{
	private String url;
	private HttpClient http;


	public OllamaClient(String url)
	{
		if (url == null || url.isEmpty())
		{
			url = "http://localhost:11434"; //where ollama runs by default
		}
		this.url = url;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	}


	public String chat(String model, String prompt)
	{
		JsonObject message = new JsonObject();
		message.addProperty("role", "user");
		message.addProperty("content", prompt);
		JsonArray messages = new JsonArray();
		messages.add(message);

		JsonObject options = new JsonObject();
		options.addProperty("num_ctx", 8192); //room for the tools, the memory and a whole plan

		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.add("messages", messages);
		body.addProperty("stream", false);
		body.add("options", options);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url + "/api/chat"))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofMinutes(5))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();

		try
		{
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				throw new IllegalStateException("Ollama answered with error " + response.statusCode() + ": " + response.body());
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return json.getAsJsonObject("message").get("content").getAsString();
		}
		catch(IOException | InterruptedException error)
		{
			throw new IllegalStateException("Could not reach Ollama at " + url);
		}
	}


	public List<String> listModels()
	{
		ArrayList<String> names = new ArrayList<>();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url + "/api/tags"))
				.timeout(Duration.ofSeconds(2))
				.GET()
				.build();

		try
		{
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			for (JsonElement model : json.getAsJsonArray("models"))
			{
				names.add(model.getAsJsonObject().get("name").getAsString());
			}
		}
		catch(Exception error)
		{
			return names; //ollama is not running so there are no models
		}
		return names;
	}
}
