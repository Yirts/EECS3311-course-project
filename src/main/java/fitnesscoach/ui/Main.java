package fitnesscoach.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class Main extends Application
{
	private String[] pages = {"Profile", "Workout Plan", "Workout", "Progress Dashboard", "Coach Chatbox", "Nutrition", "Report", "Import"};


	@Override
	public void start(Stage stage)
	{
		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		for (String page : pages)
		{
			tabs.getTabs().add(new Tab(page, new Label(page)));
		}

		stage.setTitle("AI Fitness Plan Coach");
		stage.setScene(new Scene(tabs, 1000, 700));
		stage.show();
	}
}
