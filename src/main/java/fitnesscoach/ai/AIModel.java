package fitnesscoach.ai;

public interface AIModel
{
	String send(String prompt);

	boolean isAvailable();
}
