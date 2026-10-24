# AI Fitness Plan Coach

EECS3311 course project, Stage 2 (implementation).

A Java desktop app with a JavaFX GUI and a command-line interface (CLI). An AI agent (Claude Sonnet 5, with a local Llama 3.2 model as the backup) builds and changes workout plans around the user's goal, schedule, equipment and injuries.

## Prerequisites

- JDK 17 or newer
- Maven 3.9, or an IDE with Maven built in, such as Eclipse IDE for Java Developers

## Run it in Eclipse

1. **File > Import > Maven > Existing Maven Projects**, choose this folder and press **Finish**. The first time, Eclipse downloads the libraries listed in `pom.xml`.
2. **GUI:** right-click `src/main/java/fitnesscoach/ui/CommandLineApp.java` > **Run As > Java Application**.
3. **CLI:** open **Run > Run Configurations > CommandLineApp > Arguments**, type a command such as `help` under **Program arguments**, and press **Run**.

## Run it with Maven

```
mvn javafx:run
```

## Local configuration

The AI features need an Anthropic API key. Copy `.env.example` to `.env` and replace the placeholder with your own key. `.env` is listed in `.gitignore`, so it is never committed.
