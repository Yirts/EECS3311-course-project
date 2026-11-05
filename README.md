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
| `progress --exercise "Bench Press" --range 12w` | Your dashboard: strength over time for the exercise, sets for each muscle, personal records and body weight. Ranges look like `12w`, `30d` or `6m`. Add `--bodyweight 78.5` to save today's body weight (F08) |
| `chat` | Talk to the AI coach about your own workouts and plan, or ask one question with `chat "How is my bench press going?"` (F09) |
| `nutrition targets` | Your daily calorie, protein, carb and fat targets (F10) |
| `nutrition meals` | Meal ideas from the AI that fit your targets and food preferences. Allergies are always checked (F10) |

## Run it with Maven

```
mvn javafx:run
```

## Local configuration

The AI features need an Anthropic API key. Copy `.env.example` to `.env` and replace the placeholder with your own key. `.env` is listed in `.gitignore`, so it is never committed.

`CLAUDE_MODEL` in `.env` picks the Claude model (`claude-sonnet-5` by default). When `.env` has a key, two of the tests make one small real call to Claude. Without a key they are skipped.

When Claude can't be reached, the app uses the local Llama model instead. For that, install [Ollama](https://ollama.com) and run `ollama pull llama3.2`. If neither model is available, plan generation still works with a simple starter plan.

The app saves its data (profile, plan, workout history, coach memory and reports) in the `data` folder, which is not committed.
