package fitnesscoach.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fitnesscoach.ai.AIModel;
import fitnesscoach.data.DataStore;
import fitnesscoach.data.UserProfile;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;

class FitnessAppFacadeTest {
    private static final String MONDAY_PUSH_UPS = "DAY monday: Upper body\nPush Up | 3 | 10 | 0";
    private static final String MONDAY_SQUATS = "DAY monday: Legs\nBodyweight Squat | 3 | 10 | 0";
    private static final String MONDAY_BRIDGES = "DAY monday: Legs\nGlute Bridge | 3 | 12 | 0";

    @TempDir
    Path folder;

    // ----- F01: saveProfile() and confirmBoundaries() -----

    @Test
    void saveProfileWithAnUnrealisticHeightSavesNothing() {
        FitnessAppFacade app = makeApp(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.saveProfile(new UserProfile(25, 5, 80, "beginner"), "notes"));
        assertNull(savedData().loadProfile());
    }

    @Test
    void saveProfileReturnsTheBoundariesFromTheAi() {
        FitnessAppFacade app = makeApp(new FakeModel("goal: build muscle\navoid: quads"));

        List<String> boundaries = app.saveProfile(newProfile(), "I want to build muscle but my knee hurts");

        assertEquals(List.of("goal: build muscle", "avoid: quads"), boundaries);
    }

    @Test
    void saveProfileWaitsForTheUserToConfirm() {
        FitnessAppFacade app = makeApp(new FakeModel("goal: build muscle"));

        app.saveProfile(newProfile(), "I want to build muscle");

        assertNull(savedData().loadProfile());
    }

    @Test
    void saveProfileWhenTheAiIsOfflineSavesTheBasicInfoAndTheNotes() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeApp(model);

        List<String> boundaries = app.saveProfile(newProfile(), "my knee hurts");

        assertNull(boundaries);
        assertEquals(List.of("note: my knee hurts"), savedData().loadProfile().getBoundaries());
    }

    @Test
    void confirmBoundariesSavesTheProfileWithItsBoundaries() {
        FitnessAppFacade app = makeApp(new FakeModel("goal: build muscle\navoid: quads"));
        List<String> boundaries = app.saveProfile(newProfile(), "notes");

        app.confirmBoundaries(boundaries);

        assertEquals(List.of("goal: build muscle", "avoid: quads"), savedData().loadProfile().getBoundaries());
    }

    @Test
    void confirmBoundariesFillsInTheProfileFields() {
        FitnessAppFacade app = makeApp(new FakeModel("goal: build muscle"));
        List<String> boundaries = app.saveProfile(newProfile(), "notes");

        app.confirmBoundaries(boundaries);

        assertEquals("build muscle", app.getProfile().getGoal());
    }

    @Test
    void confirmBoundariesUsesTheListTheUserEdited() {
        FitnessAppFacade app = makeApp(new FakeModel("avoid: quads"));
        app.saveProfile(newProfile(), "notes");

        app.confirmBoundaries(List.of("avoid: back"));

        assertEquals(List.of("avoid: back"), app.getProfile().getBoundaries());
    }

    @Test
    void confirmBoundariesBeforeSaveProfileThrows() {
        FitnessAppFacade app = makeApp(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.confirmBoundaries(List.of("goal: build muscle")));
    }

    @Test
    void newProfileKeepsTheOldBodyWeights() {
        UserProfile oldProfile = newProfile();
        oldProfile.addBodyWeight(79);
        savedData().saveProfile(oldProfile);
        FitnessAppFacade app = makeApp(new FakeModel("goal: lose weight"));

        app.confirmBoundaries(app.saveProfile(newProfile(), "notes"));

        assertEquals(79.0, app.getProfile().getWeightHistory().get(LocalDate.now()));
    }

    // ----- loading everything at startup -----

    @Test
    void startupLoadsTheSavedProfile() {
        savedData().saveProfile(new UserProfile(30, 170, 65, "intermediate"));

        FitnessAppFacade app = makeApp(new FakeModel());

        assertEquals(30, app.getProfile().getAge());
    }

    @Test
    void startupLoadsTheSavedPlan() {
        FitnessAppFacade first = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));
        first.acceptPlan(first.generatePlan(2));

        FitnessAppFacade second = makeApp(new FakeModel());

        assertEquals(2, second.getCurrentPlan().getDays().size());
    }

    @Test
    void startupLoadsTheAgentMemory() {
        savedData().saveMemory(List.of("the user trains at home"));
        FakeModel model = new FakeModel("goal: build muscle");
        FitnessAppFacade app = makeApp(model);

        app.saveProfile(newProfile(), "notes");

        assertTrue(model.prompts.get(0).contains("the user trains at home"));
    }

    // ----- F02: generatePlan() and acceptPlan() -----

    @Test
    void generatePlanWithoutAProfileThrows() {
        FitnessAppFacade app = makeApp(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.generatePlan(4));
    }

    @Test
    void generatePlanRejectsZeroWeeks() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.generatePlan(0));
    }

    @Test
    void generatePlanAcceptsOneWeek() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));

        assertEquals(1, app.generatePlan(1).getDays().size());
    }

    @Test
    void generatePlanAcceptsTwelveWeeks() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));

        assertEquals(12, app.generatePlan(12).getDays().size());
    }

    @Test
    void generatePlanRejectsThirteenWeeks() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.generatePlan(13));
    }

    @Test
    void generatePlanSendsSafetyProblemsBackToTheAgent() {
        FakeModel model = new FakeModel(MONDAY_SQUATS, MONDAY_PUSH_UPS);
        FitnessAppFacade app = makeAppWithProfile(model, "avoid: quads");

        app.generatePlan(4);

        assertTrue(model.prompts.get(1).contains("Fix these problems"));
    }

    @Test
    void generatePlanReturnsTheFixedPlan() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_SQUATS, MONDAY_PUSH_UPS), "avoid: quads");

        WorkoutPlan plan = app.generatePlan(4);

        assertTrue(plan.checkSafety(app.getProfile()).isEmpty());
    }

    @Test
    void generatePlanStopsFixingAfterThreeTries() {
        FakeModel model = new FakeModel(MONDAY_SQUATS, MONDAY_SQUATS, MONDAY_SQUATS, MONDAY_SQUATS);
        FitnessAppFacade app = makeAppWithProfile(model, "avoid: quads");

        app.generatePlan(4);

        assertEquals(4, model.prompts.size()); // the plan and 3 tries to fix it
    }

    @Test
    void generatePlanShowsTheProblemsLeftAsWarnings() {
        FakeModel model = new FakeModel(MONDAY_SQUATS, MONDAY_SQUATS, MONDAY_SQUATS, MONDAY_SQUATS);
        FitnessAppFacade app = makeAppWithProfile(model, "avoid: quads");

        WorkoutPlan plan = app.generatePlan(4);

        assertTrue(plan.getDays().get(0).getNote().contains("Warning"));
    }

    @Test
    void generatePlanWorksWhenTheAiIsOffline() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeAppWithProfile(model);

        WorkoutPlan plan = app.generatePlan(4);

        assertEquals(12, plan.getDays().size()); // the starter plan has 3 workouts a week
    }

    @Test
    void generatePlanDoesNotSaveThePlan() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));

        app.generatePlan(4);

        assertNull(savedData().loadPlan());
    }

    @Test
    void acceptPlanSavesThePlan() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));

        app.acceptPlan(app.generatePlan(4));

        assertEquals(4, savedData().loadPlan().getDays().size());
    }

    @Test
    void acceptPlanMakesItTheCurrentPlan() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));
        WorkoutPlan plan = app.generatePlan(4);

        app.acceptPlan(plan);

        assertSame(plan, app.getCurrentPlan());
    }

    @Test
    void undoAfterGeneratingDropsTheNewPlan() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(MONDAY_PUSH_UPS));
        app.generatePlan(2);

        app.undoChange();

        assertNull(app.getCurrentPlan());
    }

    // ----- F03: requestChange() and undoChange() -----

    @Test
    void requestChangeWithoutAPlanThrows() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.requestChange("only 30 minutes on tuesday"));
    }

    @Test
    void requestChangeWithAnEmptyRequestThrows() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.requestChange(" "));
    }

    @Test
    void requestChangeReturnsTheChangedPlan() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));

        WorkoutPlan newPlan = app.requestChange("legs on mondays");

        assertEquals("Glute Bridge", exerciseOn(newPlan, 0));
    }

    @Test
    void requestChangeKeepsTheSavedPlanUntilTheUserConfirms() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));

        app.requestChange("legs on mondays");

        assertEquals("Push Up", exerciseOn(savedData().loadPlan(), 0));
    }

    @Test
    void requestChangeRunsTheSafetyCheck() {
        FakeModel model = new FakeModel(MONDAY_SQUATS, MONDAY_BRIDGES);
        FitnessAppFacade app = makeAppWithPlan(model, "avoid: quads");

        app.requestChange("legs on mondays");

        assertTrue(model.prompts.get(1).contains("Fix these problems"));
    }

    @Test
    void requestChangeDoesNotTryToFixDaysThatAlreadyHappened() {
        // last week's squats break the "avoid: quads" boundary, but that day can't change any more
        UserProfile profile = newProfile();
        profile.addBoundary("avoid: quads");
        savedData().saveProfile(profile);
        WorkoutPlan plan = new WorkoutPlan(LocalDate.now().minusWeeks(1));
        plan.getDays().add(makeDay(LocalDate.now().minusDays(7), "Bodyweight Squat"));
        plan.getDays().add(makeDay(LocalDate.now().plusDays(7), "Push Up"));
        savedData().savePlan(plan);
        FakeModel model = new FakeModel("DAY " + LocalDate.now().plusDays(7) + ": Upper body\nKnee Push Up | 3 | 10 | 0");
        FitnessAppFacade app = makeApp(model);

        app.requestChange("easier push ups");

        assertEquals(1, model.prompts.size());
    }

    @Test
    void requestChangeWhenTheAiIsOfflineReturnsNull() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeAppWithPlan(model);

        assertNull(app.requestChange("legs on mondays"));
    }

    @Test
    void confirmingAChangeSavesTheNewPlan() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));

        app.acceptPlan(app.requestChange("legs on mondays"));

        assertEquals("Glute Bridge", exerciseOn(savedData().loadPlan(), 0));
    }

    @Test
    void undoBeforeConfirmingKeepsTheCurrentPlan() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));
        WorkoutPlan before = app.getCurrentPlan();
        app.requestChange("legs on mondays");

        app.undoChange();

        assertSame(before, app.getCurrentPlan());
    }

    @Test
    void undoAfterConfirmingPutsTheOldPlanBack() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));
        WorkoutPlan before = app.getCurrentPlan();
        app.acceptPlan(app.requestChange("legs on mondays"));

        app.undoChange();

        assertSame(before, app.getCurrentPlan());
    }

    @Test
    void undoSavesTheOldPlan() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));
        app.acceptPlan(app.requestChange("legs on mondays"));

        app.undoChange();

        assertEquals("Push Up", exerciseOn(savedData().loadPlan(), 0));
    }

    @Test
    void undoStillWorksAfterTheAppRestarts() {
        FitnessAppFacade first = makeAppWithPlan(new FakeModel(MONDAY_BRIDGES));
        first.acceptPlan(first.requestChange("legs on mondays"));

        FitnessAppFacade second = makeApp(new FakeModel());
        second.undoChange();

        assertEquals("Push Up", exerciseOn(second.getCurrentPlan(), 0));
    }

    @Test
    void undoWithNothingToUndoThrows() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.undoChange());
    }

    @Test
    void acceptPlanKeepsAtMostTenOldPlans() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        for (int i = 0; i < 12; i++) {
            app.acceptPlan(new WorkoutPlan(LocalDate.now()));
        }

        assertEquals(10, savedData().loadOldPlans().size());
    }

    // ----- F04: findSwapOptions() and swapExercise() -----

    @Test
    void findSwapOptionsReturnsTheOptionsFromTheAgent() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel("Knee Push Up | easier on the wrists"));

        List<String> options = app.findSwapOptions(firstPushUp(app), "my wrists hurt");

        assertEquals("Knee Push Up: easier on the wrists", options.get(0));
    }

    @Test
    void findSwapOptionsLeavesOutExercisesAlreadyInThatWorkout() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel("Knee Push Up | easier\nPike Push Up | more shoulders"));
        WorkoutDay firstDay = app.getCurrentPlan().getDays().get(0);
        firstDay.getExercises().add(new PlannedExercise(ExerciseLibrary.getInstance().findExercise("Knee Push Up"), 3, 10, 0));

        List<String> options = app.findSwapOptions(firstPushUp(app), "bored");

        assertEquals("Pike Push Up: more shoulders", options.get(0));
    }

    @Test
    void swapExerciseChangesTheChosenDay() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        app.swapExercise(firstPushUp(app), "Knee Push Up", false);

        assertEquals("Knee Push Up", exerciseOn(app.getCurrentPlan(), 0));
    }

    @Test
    void swapExerciseForOneDayLeavesTheOtherDays() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        app.swapExercise(firstPushUp(app), "Knee Push Up", false);

        assertEquals("Push Up", exerciseOn(app.getCurrentPlan(), 1));
    }

    @Test
    void swapExerciseForTheRestOfThePlanChangesEveryDay() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        app.swapExercise(firstPushUp(app), "Knee Push Up", true);

        assertEquals("Knee Push Up", exerciseOn(app.getCurrentPlan(), 1));
    }

    @Test
    void swapExerciseSavesThePlan() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        app.swapExercise(firstPushUp(app), "Knee Push Up", false);

        assertEquals("Knee Push Up", exerciseOn(savedData().loadPlan(), 0));
    }

    @Test
    void swapExerciseTakesAWholeOption() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        app.swapExercise(firstPushUp(app), "Knee Push Up: easier on the wrists", false);

        assertEquals("Knee Push Up", exerciseOn(app.getCurrentPlan(), 0));
    }

    @Test
    void swapExerciseWithAnUnknownExerciseThrows() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.swapExercise(firstPushUp(app), "Flying Kick", false));
    }

    @Test
    void swapExerciseKeepsTheSetsAndReps() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());
        PlannedExercise pushUp = firstPushUp(app);

        app.swapExercise(pushUp, "Knee Push Up", false);

        assertEquals(10, pushUp.getReps());
    }

    @Test
    void swapToOtherEquipmentStartsWithALightWeight() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel());
        PlannedExercise pushUp = firstPushUp(app);

        app.swapExercise(pushUp, "Dumbbell Bench Press", false);

        assertEquals(5.0, pushUp.getWeightKg());
    }

    @Test
    void swapBecauseOfPainAddsABoundary() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel("Knee Push Up | less weight on the shoulder"));
        PlannedExercise pushUp = firstPushUp(app);
        app.findSwapOptions(pushUp, "my shoulder hurts");

        app.swapExercise(pushUp, "Knee Push Up", true);

        assertTrue(savedData().loadProfile().getBoundaries().contains("avoid: Push Up"));
    }

    @Test
    void swapForAnotherReasonAddsNoBoundary() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel("Knee Push Up | something new"));
        PlannedExercise pushUp = firstPushUp(app);
        app.findSwapOptions(pushUp, "I am bored of it");

        app.swapExercise(pushUp, "Knee Push Up", true);

        assertTrue(savedData().loadProfile().getBoundaries().isEmpty());
    }

    private UserProfile newProfile() {
        return new UserProfile(25, 180, 80, "beginner");
    }

    // a facade that starts with a saved profile and a saved plan with push ups on the next two mondays
    private FitnessAppFacade makeAppWithPlan(FakeModel model, String... boundaries) {
        WorkoutPlan plan = new WorkoutPlan(LocalDate.now());
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        plan.getDays().add(makeDay(monday, "Push Up"));
        plan.getDays().add(makeDay(monday.plusWeeks(1), "Push Up"));
        savedData().savePlan(plan);
        return makeAppWithProfile(model, boundaries);
    }

    private WorkoutDay makeDay(LocalDate date, String exerciseName) {
        WorkoutDay day = new WorkoutDay(date, "Workout");
        day.getExercises().add(new PlannedExercise(ExerciseLibrary.getInstance().findExercise(exerciseName), 3, 10, 0));
        return day;
    }

    private PlannedExercise firstPushUp(FitnessAppFacade app) {
        return app.getCurrentPlan().getDays().get(0).getExercises().get(0);
    }

    // the name of the first exercise on one day of the plan
    private String exerciseOn(WorkoutPlan plan, int dayIndex) {
        return plan.getDays().get(dayIndex).getExercises().get(0).getExercise().getName();
    }

    // the data store the facade saves to, read back again
    private DataStore savedData() {
        return new DataStore(folder.toString());
    }

    // a facade with its data in the temp folder and a fake AI model
    private FitnessAppFacade makeApp(FakeModel model) {
        return new FitnessAppFacade(new DataStore(folder.toString()), model, null);
    }

    // a facade that starts with a saved profile that has these boundaries
    private FitnessAppFacade makeAppWithProfile(FakeModel model, String... boundaries) {
        UserProfile profile = newProfile();
        for (String boundary : boundaries) {
            profile.addBoundary(boundary);
        }
        savedData().saveProfile(profile);
        return makeApp(model);
    }
}

// an AI model that gives the replies it was made with and remembers the prompts it got
class FakeModel implements AIModel {
    ArrayList<String> replies = new ArrayList<>();
    ArrayList<String> prompts = new ArrayList<>();
    boolean available = true;

    FakeModel(String... replies) {
        this.replies.addAll(List.of(replies));
    }

    @Override
    public String send(String prompt) {
        prompts.add(prompt);
        return replies.remove(0);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
