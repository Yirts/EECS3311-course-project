package fitnesscoach.ui;

import javafx.application.Application;

public class CommandLineApp
{
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


	public void runCommand(String command)
	{
		command = command.trim();

		if (command.equals("help"))
		{
			System.out.println("AI Fitness Plan Coach");
			System.out.println("Usage: fitagent <command>");
			System.out.println();
			System.out.println("  (no command)   open the GUI");
			System.out.println("  help           show this list");
		}
		else
		{
			System.out.println("Unknown command: " + command);
			System.out.println("Type \"fitagent help\" to see the commands.");
		}
	}
}
