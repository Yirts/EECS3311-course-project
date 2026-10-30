package fitnesscoach.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fitnesscoach.data.UserProfile;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

class FitnessAgentTester
{
	//model answers straight away without a tool
	@Test
	void test_answerQuestion_1()
	{
		FakeModel claude = new FakeModel("Rest days help your muscles recover.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		assertEquals("Rest days help your muscles recover.", agent.answerQuestion("Why do I need rest days?"), "Failed Test 1 answerQuestion");
	}

	//search tool result goes back to the model
	@Test
	void test_answerQuestion_2()
	{
		FakeModel claude = new FakeModel("TOOL search: chest | dumbbell", "Try the Dumbbell Bench Press.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("What chest exercise can I do with dumbbells?");

		assertTrue(claude.prompts.get(1).contains("Dumbbell Bench Press"), "Failed Test 2 answerQuestion");
	}

	//final answer comes after the tool
	@Test
	void test_answerQuestion_3()
	{
		FakeModel claude = new FakeModel("TOOL search: chest | dumbbell", "Try the Dumbbell Bench Press.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		assertEquals("Try the Dumbbell Bench Press.", agent.answerQuestion("What chest exercise can I do with dumbbells?"), "Failed Test 3 answerQuestion");
	}

	//history tool gives the model the user's workouts
	@Test
	void test_answerQuestion_4()
	{
		WorkoutHistory history = new WorkoutHistory();
		WorkoutLog log = new WorkoutLog(LocalDate.now().minusDays(2), null);
		log.addSet(new SetLog("Bench Press", 8, 60, 7));
		history.addWorkout(log);
		FakeModel claude = new FakeModel("TOOL history: 7", "You benched 60 kg two days ago.");
		FitnessAgent agent = new FitnessAgent(claude, null, history);

		agent.answerQuestion("How is my bench press going?");

		assertTrue(claude.prompts.get(1).contains("Bench Press 8 x 60.0 kg"), "Failed Test 4 answerQuestion");
	}

	//remember tool saves a fact
	@Test
	void test_answerQuestion_5()
	{
		FakeModel claude = new FakeModel("TOOL remember: the user prefers morning workouts", "Got it, mornings it is!");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("I like to work out in the morning.");

		assertTrue(agent.getMemory().contains("the user prefers morning workouts"), "Failed Test 5 answerQuestion");
	}

	//saved memory is put in the prompt
	@Test
	void test_answerQuestion_6()
	{
		FakeModel claude = new FakeModel("Here is a peanut free snack.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
		agent.getMemory().add("the user is allergic to peanuts");

		agent.answerQuestion("What is a good snack after a workout?");

		assertTrue(claude.prompts.get(0).contains("the user is allergic to peanuts"), "Failed Test 6 answerQuestion");
	}

	//question is remembered for next time
	@Test
	void test_answerQuestion_7()
	{
		FakeModel claude = new FakeModel("Keep going!");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("Am I getting stronger?");

		assertTrue(agent.getMemory().contains("The user asked: Am I getting stronger?"), "Failed Test 7 answerQuestion");
	}

	//unknown tool is reported back to the model
	@Test
	void test_answerQuestion_8()
	{
		FakeModel claude = new FakeModel("TOOL dance: now", "Sorry about that.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("Hi");

		assertTrue(claude.prompts.get(1).contains("Unknown tool"), "Failed Test 8 answerQuestion");
	}

	//claude can not be reached so the backup model answers
	@Test
	void test_answerQuestion_9()
	{
		FakeModel claude = new FakeModel("from claude");
		claude.available = false;
		FakeModel llama = new FakeModel("from llama");
		FitnessAgent agent = new FitnessAgent(claude, llama, new WorkoutHistory());

		assertEquals("from llama", agent.answerQuestion("Hi"), "Failed Test 9 answerQuestion");
	}

	//no model can be reached
	@Test
	void test_answerQuestion_10()
	{
		FakeModel claude = new FakeModel("from claude");
		claude.available = false;
		FakeModel llama = new FakeModel("from llama");
		llama.available = false;
		FitnessAgent agent = new FitnessAgent(claude, llama, new WorkoutHistory());

		assertTrue(agent.answerQuestion("Hi").contains("offline"), "Failed Test 10 answerQuestion");
	}

	//model that keeps asking for tools is stopped and asked for an answer (6 tools + 1 final)
	@Test
	void test_answerQuestion_11()
	{
		FakeModel claude = new FakeModel("TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "TOOL history: 7", "Here is my answer.");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

		agent.answerQuestion("How am I doing?");

		assertEquals(7, claude.prompts.size(), "Failed Test 11 answerQuestion");
	}

	//memory never grows past 50 facts
	@Test
	void test_answerQuestion_12()
	{
		FakeModel claude = new FakeModel("ok");
		FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
		for (int i = 0; i < 50; i++)
		{
			agent.getMemory().add("fact " + i);
		}

		agent.answerQuestion("Hi");

		assertEquals(50, agent.getMemory().size(), "Failed Test 12 answerQuestion");
	}

	//tool asked for in the middle of a sentence is still used (the local llama model does this)
	@Test
	void test_answerQuestion_13()
	{
		FakeModel llama = new FakeModel("Let me check your workout history (TOOL history: 14) first.", "You did great.");
		FitnessAgent agent = new FitnessAgent(llama, null, new WorkoutHistory());

		agent.answerQuestion("How did my bench press go this week?");

		assertTrue(llama.prompts.get(1).contains("Workouts in the last 14 days"), "Failed Test 13 answerQuestion");
	}

	//full stop after the tool is ignored
	@Test
	void test_answerQuestion_14()
	{
		FakeModel llama = new FakeModel("TOOL history: 14.", "You did great.");
		FitnessAgent agent = new FitnessAgent(llama, null, new WorkoutHistory());

		agent.answerQuestion("How did my bench press go this week?");

		assertTrue(llama.prompts.get(1).contains("Workouts in the last 14 days"), "Failed Test 14 answerQuestion");
	}

	//memory loaded from the data store is used
	@Test
	void test_setMemory_1()
	{
		FitnessAgent agent = new FitnessAgent(new FakeModel(), null, new WorkoutHistory());
		ArrayList<String> loaded = new ArrayList<>();
		loaded.add("the user trains at home");

		agent.setMemory(loaded);

		assertEquals("the user trains at home", agent.getMemory().get(0), "Failed Test 1 setMemory");
	}


    // ----- F01: readBoundaries() -----

    @Test
    void readBoundariesKeepsOnlyTheBoundaryLines() {
        FakeModel claude = new FakeModel("Here are the boundaries:\n- goal: build muscle\n**avoid: quads**\nGood luck!");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> boundaries = agent.readBoundaries(newProfile(), "I want to build muscle but my knee hurts");

        assertEquals(List.of("goal: build muscle", "avoid: quads"), boundaries);
    }

    @Test
    void readBoundariesSplitsAnAvoidListIntoOneBoundaryEach() {
        FitnessAgent agent = new FitnessAgent(new FakeModel("avoid: knees, quads"), null, new WorkoutHistory());

        List<String> boundaries = agent.readBoundaries(newProfile(), "my knee hurts");

        assertEquals(List.of("avoid: knees", "avoid: quads"), boundaries);
    }

    @Test
    void readBoundariesSendsTheNotesToTheAi() {
        FakeModel claude = new FakeModel("equipment: dumbbell");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        agent.readBoundaries(newProfile(), "I only have dumbbells");

        assertTrue(claude.prompts.get(0).contains("I only have dumbbells"));
    }

    @Test
    void readBoundariesKeepsFollowUpQuestions() {
        FakeModel claude = new FakeModel("question: How bad is your knee injury?");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> boundaries = agent.readBoundaries(newProfile(), "my knee hurts");

        assertEquals(List.of("question: How bad is your knee injury?"), boundaries);
    }

    @Test
    void readBoundariesReturnsNullWhenNoModelCanBeReached() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        assertNull(agent.readBoundaries(newProfile(), "my knee hurts"));
    }

    // ----- F02: buildPlan() -----

    @Test
    void buildPlanRepeatsTheWeekForEveryWeek() {
        FitnessAgent agent = new FitnessAgent(new FakeModel(MONDAY_PUSH_UPS), null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 4);

        assertEquals(4, plan.getDays().size());
    }

    @Test
    void buildPlanPutsEachWorkoutOnItsDay() {
        FitnessAgent agent = new FitnessAgent(new FakeModel(MONDAY_PUSH_UPS), null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 4);

        for (WorkoutDay day : plan.getDays()) {
            assertEquals(DayOfWeek.MONDAY, day.getDate().getDayOfWeek());
        }
    }

    @Test
    void buildPlanReadsTheSetsRepsAndWeight() {
        FakeModel claude = new FakeModel("DAY monday: Upper body\nDumbbell Bench Press | 4 | 8 | 12.5");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        PlannedExercise first = agent.buildPlan(newProfile(), 1).getDays().get(0).getExercises().get(0);

        assertEquals("Dumbbell Bench Press", first.getExercise().getName());
        assertEquals(4, first.getSets());
        assertEquals(8, first.getReps());
        assertEquals(12.5, first.getWeightKg());
    }

    @Test
    void buildPlanUsesTheFirstNumberOfARepRange() {
        FakeModel claude = new FakeModel("DAY monday: Upper body\nPush Up | 3 | 8-12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        PlannedExercise first = agent.buildPlan(newProfile(), 1).getDays().get(0).getExercises().get(0);

        assertEquals(8, first.getReps());
    }

    @Test
    void buildPlanSkipsExercisesThatAreNotInTheLibrary() {
        FakeModel claude = new FakeModel("DAY monday: Upper body\nFlying Kick | 3 | 10 | 0\nPush Up | 3 | 10 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 1);

        assertEquals(1, plan.getDays().get(0).getExercises().size());
    }

    @Test
    void buildPlanReadsAMarkdownTableRow() {
        FakeModel claude = new FakeModel("**DAY monday: Upper body**\n| Push Up | 3 | 10 | 0 |");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 1);

        assertEquals(1, plan.getDays().get(0).getExercises().size());
    }

    @Test
    void buildPlanGivesEachDayItsOwnExercises() {
        FitnessAgent agent = new FitnessAgent(new FakeModel(MONDAY_PUSH_UPS), null, new WorkoutHistory());
        WorkoutPlan plan = agent.buildPlan(newProfile(), 2);

        plan.getDays().get(0).getExercises().get(0).setWeightKg(5);

        assertEquals(0, plan.getDays().get(1).getExercises().get(0).getWeightKg());
    }

    @Test
    void buildPlanUsesAStarterWeekWhenNoModelCanBeReached() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 4);

        assertEquals(12, plan.getDays().size()); // monday, wednesday and friday for 4 weeks
    }

    @Test
    void buildPlanUsesAStarterWeekWhenTheAnswerHasNoWorkouts() {
        FitnessAgent agent = new FitnessAgent(new FakeModel("Sorry, I can not make a plan."), null, new WorkoutHistory());

        WorkoutPlan plan = agent.buildPlan(newProfile(), 4);

        assertEquals(12, plan.getDays().size());
    }

    @Test
    void starterWeekPassesTheSafetyCheck() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
        UserProfile profile = newProfile();
        profile.addBoundary("days: tuesday, thursday");
        profile.addBoundary("minutes: 30");
        profile.addBoundary("equipment: dumbbell");
        profile.addBoundary("avoid: quads");

        WorkoutPlan plan = agent.buildPlan(profile, 2);

        assertTrue(plan.checkSafety(profile).isEmpty());
    }

    // ----- F02 and F03: changePlan() -----

    @Test
    void changePlanSendsTheCurrentPlanToTheAi() {
        WorkoutPlan plan = mondayPlan(1);
        FakeModel claude = new FakeModel("DAY monday: Legs\nGlute Bridge | 3 | 12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        agent.changePlan(plan, newProfile(), "legs instead");

        assertTrue(claude.prompts.get(0).contains("Push Up | 3 | 10 | 0.0"));
    }

    @Test
    void changePlanChangesOnlyTheDateItIsGiven() {
        WorkoutPlan plan = mondayPlan(2);
        LocalDate secondMonday = plan.getDays().get(1).getDate();
        FakeModel claude = new FakeModel("DAY " + secondMonday + ": Legs\nGlute Bridge | 3 | 12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "legs next monday");

        assertEquals("Push Up", firstExerciseName(changed.getDays().get(0)));
        assertEquals("Glute Bridge", firstExerciseName(changed.getDays().get(1)));
    }

    @Test
    void changePlanWithADayNameChangesEveryMatchingDay() {
        WorkoutPlan plan = mondayPlan(3);
        FakeModel claude = new FakeModel("DAY monday: Legs\nGlute Bridge | 3 | 12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "legs on mondays");

        for (WorkoutDay day : changed.getDays()) {
            assertEquals("Glute Bridge", firstExerciseName(day));
        }
    }

    @Test
    void changePlanCanAddWorkoutsOnANewDay() {
        WorkoutPlan plan = mondayPlan(2);
        FakeModel claude = new FakeModel("DAY wednesday: Core\nPlank | 3 | 30 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "add a core day on wednesdays");

        assertEquals(4, changed.getDays().size());
    }

    @Test
    void changePlanWithARestDayRemovesThatWorkout() {
        WorkoutPlan plan = mondayPlan(2);
        LocalDate firstMonday = plan.getDays().get(0).getDate();
        FitnessAgent agent = new FitnessAgent(new FakeModel("DAY " + firstMonday + ": Rest"), null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "I am away on monday");

        assertEquals(1, changed.getDays().size());
    }

    @Test
    void changePlanSkipsAnEmptyDayThatIsNotARestDay() {
        // the local Llama model sometimes writes a DAY line without its exercises
        WorkoutPlan plan = mondayPlan(2);
        FitnessAgent agent = new FitnessAgent(new FakeModel("DAY monday: Upper body (30 minutes, as you asked)"), null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "only 30 minutes on mondays");

        assertEquals(2, changed.getDays().size());
    }

    @Test
    void changePlanDoesNotChangeTheOldPlan() {
        WorkoutPlan plan = mondayPlan(2);
        FakeModel claude = new FakeModel("DAY monday: Legs\nGlute Bridge | 3 | 12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        agent.changePlan(plan, newProfile(), "legs on mondays");

        assertEquals("Push Up", firstExerciseName(plan.getDays().get(0)));
    }

    @Test
    void changePlanNeverChangesDaysThatAlreadyHappened() {
        WorkoutPlan plan = new WorkoutPlan(LocalDate.now().minusWeeks(2));
        WorkoutDay lastWeek = new WorkoutDay(LocalDate.now().minusDays(7), "Upper body");
        lastWeek.getExercises().add(new PlannedExercise(ExerciseLibrary.getInstance().findExercise("Push Up"), 3, 10, 0));
        plan.getDays().add(lastWeek);
        FakeModel claude = new FakeModel("DAY " + lastWeek.getDate() + ": Legs\nGlute Bridge | 3 | 12 | 0");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        WorkoutPlan changed = agent.changePlan(plan, newProfile(), "legs last week");

        assertEquals("Push Up", firstExerciseName(changed.getDays().get(0)));
    }

    @Test
    void changePlanReturnsNullWhenNoModelCanBeReached() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        assertNull(agent.changePlan(mondayPlan(1), newProfile(), "legs instead"));
    }

    // ----- F04: pickSwapOptions() -----

    @Test
    void pickSwapOptionsGivesTheAisThreePicksWithReasons() {
        FakeModel claude = new FakeModel("Goblet Squat | easier on the lower back\nLeg Press | the machine keeps you steady\n"
                + "Bodyweight Squat | no weight on the spine");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Barbell Squat"), "my lower back hurts", profileWith("equipment: dumbbell, machine"));

        assertEquals(List.of("Goblet Squat: easier on the lower back", "Leg Press: the machine keeps you steady",
                "Bodyweight Squat: no weight on the spine"), options);
    }

    @Test
    void pickSwapOptionsSendsTheReasonAndTheMatchesToTheAi() {
        FakeModel claude = new FakeModel("Goblet Squat | easier");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        agent.pickSwapOptions(planned("Barbell Squat"), "the squat rack is busy", profileWith("equipment: dumbbell"));

        assertTrue(claude.prompts.get(0).contains("the squat rack is busy"));
        assertTrue(claude.prompts.get(0).contains("Bulgarian Split Squat"));
    }

    @Test
    void pickSwapOptionsReadsANumberedList() {
        FakeModel claude = new FakeModel("1. Goblet Squat | easier\n2. Bodyweight Squat | no equipment\n3. Walking Lunge | one leg at a time");
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Barbell Squat"), "busy", profileWith("equipment: dumbbell"));

        assertEquals("Goblet Squat: easier", options.get(0));
    }

    @Test
    void pickSwapOptionsOnlyOffersExercisesForTheUsersEquipment() {
        // the AI picks the Leg Press, but the user has no machine, so the first matches are used instead
        FitnessAgent agent = new FitnessAgent(new FakeModel("Leg Press | easy to learn"), null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Barbell Squat"), "busy", profileWith("equipment: dumbbell"));

        assertEquals(List.of("Goblet Squat", "Bodyweight Squat", "Bulgarian Split Squat"), options);
    }

    @Test
    void pickSwapOptionsWithoutAnAiGivesNamesOnly() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Barbell Squat"), "busy", profileWith("equipment: dumbbell"));

        assertEquals(List.of("Goblet Squat", "Bodyweight Squat", "Bulgarian Split Squat"), options);
    }

    @Test
    void pickSwapOptionsSkipsAvoidedExercises() {
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());
        UserProfile profile = profileWith("equipment: dumbbell");
        profile.addBoundary("avoid: Goblet Squat");

        List<String> options = agent.pickSwapOptions(planned("Barbell Squat"), "busy", profile);

        assertEquals(List.of("Bodyweight Squat", "Bulgarian Split Squat", "Walking Lunge"), options);
    }

    @Test
    void pickSwapOptionsWidensTheSearchWhenFewExercisesMatch() {
        // without equipment only the Glute Bridge works the hamstrings, so the glutes and back are searched too (UC04 2a)
        FakeModel claude = new FakeModel();
        claude.available = false;
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Romanian Deadlift"), "no barbell", newProfile());

        assertEquals(List.of("Glute Bridge", "Bodyweight Squat", "Walking Lunge"), options);
    }

    @Test
    void pickSwapOptionsWithNoMatchesIsEmptyAndDoesNotAskTheAi() {
        // the standing calf raise is the only calf exercise without equipment
        FakeModel claude = new FakeModel();
        FitnessAgent agent = new FitnessAgent(claude, null, new WorkoutHistory());

        List<String> options = agent.pickSwapOptions(planned("Standing Calf Raise"), "boring", newProfile());

        assertTrue(options.isEmpty());
        assertTrue(claude.prompts.isEmpty());
    }

    private static final String MONDAY_PUSH_UPS = "DAY monday: Upper body\nPush Up | 3 | 10 | 0";

    private UserProfile newProfile() {
        return new UserProfile(25, 180, 80, "beginner");
    }

    private UserProfile profileWith(String boundary) {
        UserProfile profile = newProfile();
        profile.addBoundary(boundary);
        return profile;
    }

    private PlannedExercise planned(String exerciseName) {
        return new PlannedExercise(ExerciseLibrary.getInstance().findExercise(exerciseName), 3, 8, 60);
    }

    // a plan with push ups on mondays, made by an agent with its own fake model
    private WorkoutPlan mondayPlan(int weeks) {
        FitnessAgent agent = new FitnessAgent(new FakeModel(MONDAY_PUSH_UPS), null, new WorkoutHistory());
        return agent.buildPlan(newProfile(), weeks);
    }

    private String firstExerciseName(WorkoutDay day) {
        return day.getExercises().get(0).getExercise().getName();
    }
}

class FakeModel implements AIModel
{
	ArrayList<String> replies = new ArrayList<>();
	ArrayList<String> prompts = new ArrayList<>();
	boolean available = true;


	public FakeModel(String... replies)
	{
		for (String reply : replies)
		{
			this.replies.add(reply);
		}
	}


	@Override
	public String send(String prompt)
	{
		prompts.add(prompt);
		return replies.remove(0);
	}


	@Override
	public boolean isAvailable()
	{
		return available;
	}
}
