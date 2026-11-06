# AI Fitness Plan Coach

EECS3311 course project, Stage 2 (implementation).

A Java desktop app with a JavaFX window (GUI) and a command-line interface (CLI). An AI agent builds and changes workout plans around the user's goal, schedule, equipment and injuries, adjusts each workout to how the user feels, and explains their progress. Claude (Sonnet 5) is the main AI model, and a local Llama 3.2 model is the backup.

## Prerequisites

- JDK 17 or newer. `java -version` should show 17 or higher.
- Maven 3.9 (`mvn -version`), or an IDE with Maven built in, such as Eclipse IDE for Java Developers.
- An Anthropic API key, for the AI features.
- Optional: [Ollama](https://ollama.com), for the local backup model.

## Install the dependencies

Open a terminal in this folder (the one with `pom.xml`) and run:

```
mvn package
```

The first time, Maven downloads every library listed in `pom.xml`: JavaFX, Gson, the Anthropic Java SDK and JUnit 5. Then it compiles the code, runs the tests and makes `target/fitagent.jar`, one jar with every library in it. The `fitagent` scripts below run that jar.

## Local configuration

1. Copy the example configuration to `.env`:
   - Windows: `copy .env.example .env`
   - macOS or Linux: `cp .env.example .env`
2. Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your own Anthropic API key.

`.env` is listed in `.gitignore`, so it is never committed. Never put a real key in `.env.example`, the code, screenshots or videos.

| Setting in `.env` | What it is |
|---|---|
| `ANTHROPIC_API_KEY` | Your Anthropic API key |
| `CLAUDE_MODEL` | The Claude model (`claude-sonnet-5`) |
| `OLLAMA_MODEL` | The local backup model (`llama3.2`) |
| `OLLAMA_URL` | Where Ollama runs (`http://localhost:11434`) |

When Claude can't be reached, the app uses the local Llama model. For that, install Ollama and run `ollama pull llama3.2`. With no AI at all, the app still runs: the pages that need the AI say it is offline, and a new plan is a simple starter plan.

Run the app from this folder, where `.env` is. The app saves its data (profile, plan, workout history, coach memory and reports) in the `data` folder here, which is not committed.

## Run the app

After `mvn package`:

| | Windows (PowerShell or Command Prompt) | macOS or Linux |
|---|---|---|
| GUI | `.\fitagent` | `sh fitagent` |
| CLI | `.\fitagent help` | `sh fitagent help` |

With no command, `fitagent` opens the window. With a command, it runs that command in the terminal.

Maven can also run the app without the jar: `mvn javafx:run` opens the window, and `mvn javafx:run "-Djavafx.args=plan show"` runs one CLI command.

In Eclipse: **File > Import > Maven > Existing Maven Projects**, choose this folder and press **Finish**. Right-click `src/main/java/fitnesscoach/ui/CommandLineApp.java` > **Run As > Java Application** to open the window. For the CLI, type a command such as `help` in **Run > Run Configurations > CommandLineApp > Arguments > Program arguments**.

## Example usage

A first week in the CLI (on Windows, type `.\fitagent` instead of `sh fitagent`):

```
sh fitagent profile setup
sh fitagent plan generate --weeks 4
sh fitagent import sample-data/workout-history.csv
sh fitagent workout start
sh fitagent workout log --reps 10 --weight 20 --difficulty 7
sh fitagent workout finish
sh fitagent progress --exercise "Barbell Squat" --range 12w
sh fitagent chat "How is my squat going?"
sh fitagent nutrition meals
sh fitagent report weekly
```

`profile setup` asks for your age, height, weight and experience, and for notes such as: *I want to build muscle. I have dumbbells. I can train monday, wednesday and friday for 45 minutes. My left knee hurts when I squat deep.* The AI turns the notes into boundaries for you to confirm, and every plan follows them.

The same in the window: fill in the **Profile** page, press **Save Profile** and then **Confirm**. On the **Workout Plan** page, press **Generate Plan** and then **Accept**. On the **Workout** page, **Start Workout** asks how you feel today and then guides you set by set.

## The window

| Page | What it does |
|---|---|
| Profile | Your information and notes. The AI turns the notes into boundaries (goal, days, minutes, equipment, exercises to avoid), asks follow-up questions if something is missing, and warns you to see a doctor about a serious injury. You can edit the boundaries before you confirm them (F01) |
| Workout Plan | Generate a plan, request a change in your own words, undo the last change, swap an exercise for one of 3 options, and override a weight. A new or changed plan waits for Accept or Confirm, and changed days are marked with a star (F02, F03, F04, F06) |
| Workout | The daily check-in (sleep, energy, stress, soreness) can make today's workout easier or harder. Then you log each set with the planned numbers filled in and a rest timer. Finish Workout shows the total, new records and your next weights (F07, F05, F06) |
| Progress Dashboard | Strength over time for an exercise, sets for each muscle, body weight and personal records, for the last 4 weeks up to all time. A point that looks like a typo is red. The page refreshes by itself after every finished or imported workout (F08) |
| Coach Chatbox | Ask the AI coach about your own workouts, plan and goals (F09) |
| Nutrition | Daily calorie, protein, carb and fat targets, and meal ideas from the AI that fit your likes, dislikes and allergies (F10) |
| Report | The AI's review of this week, with the numbers and up to 3 suggestions. Apply a suggestion to change your plan, or deny it. Save Report keeps it in the `data` folder (F11) |
| Import | Past workouts from another app's CSV file, with a preview and a box to match each exercise the library doesn't know (F12) |

While the AI is thinking, a spinner shows at the bottom of the window and the tabs wait.

## CLI commands

| Command | What it does |
|---|---|
| `help` | Lists the commands |
| `profile setup` | Asks for your age, height, weight, experience and notes (injuries, goal, equipment, free days, workout length). The AI turns the notes into boundaries, and you confirm them (F01) |
| `plan generate --weeks 8` | The AI builds a plan of 1 to 12 weeks (4 if no number is given) that passes the safety check. You accept it, regenerate it or cancel (F02) |
| `plan show` | Shows your saved plan |
| `plan change "only 30 minutes on tuesday"` | The AI changes only the workouts your request is about. You see the changed days, then confirm, undo, regenerate or edit the request (F03) |
| `plan undo` | Puts back your plan from before the last change, even after the app was closed (F03) |
| `exercise swap "Barbell Squat" --reason pain` | Gives 3 other exercises for the same muscles with your equipment, each with a reason. You pick one for that day or the rest of the plan. If the reason is pain, future plans avoid the old exercise (F04) |
| `checkin --sleep 6 --energy 2 --stress 3` | Daily check-in, also with `--soreness` and `--comments` (anything left out is asked). On a bad or great day the AI makes today's workout easier or harder and says why, and you accept or reject it (F07) |
| `workout start` | Offers the check-in, then shows today's workout (or starts the next one early) and the first set (F05) |
| `workout log --reps 8 --weight 60 --difficulty 8` | Logs the next set and saves it right away. Add `--exercise "Name"` for a different exercise (F05) |
| `workout skip` | Skips the current exercise without counting it as a failed attempt (F05) |
| `workout finish` | Saves the workout and shows the total, any new records and your next weights. Exercises that have been stuck for 3 workouts go to the AI for a lighter week or a new exercise (F05, F06) |
| `plan progress` | Shows the weights for your next workouts, and lets you change one (F06) |
| `progress --exercise "Barbell Squat" --range 12w` | Your dashboard: strength over time for the exercise, sets for each muscle, personal records and body weight. Ranges look like `12w`, `30d` or `6m`. Add `--bodyweight 78.5` to save today's body weight (F08) |
| `chat` | Talk to the AI coach about your own workouts and plan, or ask one question with `chat "How is my squat going?"` (F09) |
| `nutrition targets` | Your daily calorie, protein, carb and fat targets (F10) |
| `nutrition meals` | Meal ideas from the AI that fit your targets and food preferences. Allergies are always checked (F10) |
| `report weekly` | The AI's review of this week, with the numbers and up to 3 suggestions. You can apply each suggestion to your plan, and save the report in the `data` folder (F11) |
| `import sample-data/workout-history.csv` | Imports past workouts from another app's CSV export. You see a preview, match any exercises the app doesn't know, and confirm (F12) |

### Importing workouts

Import reads CSV files with one row for each set. It finds the columns by their names: the date (`Date` or `start_time`), the exercise (`Exercise Name`, `exercise_title` or `Exercise`), `Reps`, the weight (`Weight`, `weight_kg`, or a column with "lb" in its name for pounds) and an optional `RPE`. Exports laid out like Strong's and Hevy's use these names. `sample-data/workout-history.csv` is a small example in pounds, with a few exercise names to match by hand and two broken rows.

## Tests

```
mvn test
```

This runs all 397 JUnit 5 tests. They use a fake AI model, so they need no key and no internet. When `.env` has a key, 2 of them also make one small real call to Claude. Without a key, those 2 are skipped.

## Design

The code is in `src/main/java/fitnesscoach`:

| Package | Classes |
|---|---|
| `ui` | `Main` (the window), `CommandLineApp` (the CLI) |
| `controller` | `FitnessAppFacade` |
| `ai` | `FitnessAgent`, `AIModel`, `ClaudeAdapter`, `LlamaAdapter`, `OllamaClient` |
| `data` | `UserProfile`, `DataStore` |
| `plan` | `WorkoutPlan`, `WorkoutDay`, `PlannedExercise`, `Exercise`, `ExerciseLibrary` |
| `history` | `WorkoutHistory`, `WorkoutLog`, `SetLog`, `CheckIn`, `WorkoutObserver` |
| `progression` | `WeightProgression`, `ProgressionStrategy`, `BeginnerStrategy`, `IntermediateStrategy` |

Design patterns:

- **Facade:** the window and the CLI only call `FitnessAppFacade`, which works with the agent, the plan, the history and the saved data for them.
- **Adapter:** `ClaudeAdapter` and `LlamaAdapter` make the Anthropic SDK and the Ollama server look the same, as the `AIModel` interface. `FitnessAgent` tries Claude first and the local model second.
- **Observer:** `WorkoutHistory` tells its `WorkoutObserver`s about every finished or imported workout. `WeightProgression` works out the next weights, and the window's Progress Dashboard refreshes.
- **Strategy:** `BeginnerStrategy` and `IntermediateStrategy` are two ways to raise the weights. `WeightProgression` uses the one that fits the user's experience.
- **Singleton:** `ExerciseLibrary` loads the exercise list once, and every class uses that one copy.
