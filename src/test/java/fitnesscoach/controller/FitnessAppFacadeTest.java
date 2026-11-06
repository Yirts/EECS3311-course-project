package fitnesscoach.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fitnesscoach.ai.AIModel;
import fitnesscoach.data.DataStore;
import fitnesscoach.data.UserProfile;
import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutLog;
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

    // ----- F07: submitDailyCheckIn() -----

    @Test
    void submitDailyCheckInSavesTheCheckIn() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        app.submitDailyCheckIn(normalCheckIn());

        assertEquals(1, savedData().loadHistory().getCheckIns().size());
    }

    @Test
    void aNormalCheckInKeepsTheWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel()); // no replies, so the AI must not be asked
        WorkoutDay today = app.getCurrentPlan().getDays().get(0);

        assertSame(today, app.submitDailyCheckIn(normalCheckIn()));
    }

    @Test
    void aLowCheckInChangesTodaysWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel(LIGHTER_ROWS));

        app.submitDailyCheckIn(lowCheckIn(""));

        assertEquals(2, rowOn(app, 0).getSets());
    }

    @Test
    void rejectingTheChangeBringsBackTheNormalWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel(LIGHTER_ROWS));
        app.submitDailyCheckIn(lowCheckIn(""));

        app.undoChange();

        assertEquals(3, rowOn(app, 0).getSets());
    }

    @Test
    void checkInCommentsAreReadByTheAi() {
        FakeModel model = new FakeModel(LIGHTER_ROWS);
        FitnessAppFacade app = makeAppWithTodaysWorkout(model);
        CheckIn checkIn = new CheckIn(LocalDate.now(), 7.5, 4, 2, 2, "my lower back is a bit sore");

        app.submitDailyCheckIn(checkIn);

        assertEquals(1, model.prompts.size());
    }

    @Test
    void aCheckInWithNoWorkoutTodayReturnsNull() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertNull(app.submitDailyCheckIn(lowCheckIn("")));
    }

    // ----- F05: startWorkout(), logSet(), skipExercise() and finishWorkout() -----

    @Test
    void startWorkoutGivesTodaysWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        assertEquals(LocalDate.now(), app.startWorkout().getDate());
    }

    @Test
    void startWorkoutWithNothingTodayStartsTheNextWorkoutEarly() {
        WorkoutPlan plan = new WorkoutPlan(LocalDate.now());
        plan.getDays().add(rowDay(LocalDate.now().plusDays(2)));
        savedData().savePlan(plan);
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertEquals(LocalDate.now().plusDays(2), app.startWorkout().getDate());
    }

    @Test
    void startWorkoutWithNoWorkoutsLeftStartsAFreeWorkout() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertNull(app.startWorkout());
        assertNotNull(app.getCurrentLog());
    }

    @Test
    void startWorkoutAgainGoesOnWithTheSameWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        app.logSet(new SetLog("Dumbbell Row", 10, 20, 7));

        app.startWorkout();

        assertEquals(1, app.getCurrentLog().getSets().size());
    }

    @Test
    void aWorkoutGoesOnAfterTheAppRestarts() {
        FitnessAppFacade first = makeAppWithTodaysWorkout(new FakeModel());
        first.startWorkout();
        first.logSet(new SetLog("Dumbbell Row", 10, 20, 7));

        FitnessAppFacade second = makeApp(new FakeModel());
        second.startWorkout();

        assertEquals(1, second.getCurrentLog().getSets().size());
    }

    @Test
    void aWorkoutAfterARestartUsesThePlansOwnDay() {
        FitnessAppFacade first = makeAppWithTodaysWorkout(new FakeModel());
        first.startWorkout();

        FitnessAppFacade second = makeApp(new FakeModel());

        assertSame(second.getCurrentPlan().getDays().get(0), second.getCurrentLog().getDay());
    }

    @Test
    void logSetSavesRightAway() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();

        app.logSet(new SetLog("Dumbbell Row", 10, 20, 7));

        assertEquals(1, savedData().loadHistory().getCurrentLog().getSets().size());
    }

    @Test
    void logSetBeforeStartingThrows() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.logSet(new SetLog("Dumbbell Row", 10, 20, 7)));
    }

    @Test
    void finishWorkoutSavesTheWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);

        app.finishWorkout();

        assertEquals(1, savedData().loadHistory().getLogs().size());
    }

    @Test
    void skipExerciseMarksItAsSkipped() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();

        app.skipExercise(rowOn(app, 0));

        assertEquals("skipped", savedData().loadPlan().getDays().get(0).getExercises().get(0).getNote());
    }

    @Test
    void aSkippedExerciseDoesNotCountAsAFailedAttempt() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        app.skipExercise(rowOn(app, 0));

        app.finishWorkout();

        assertEquals(0, rowOn(app, 1).getStuckCount());
    }

    @Test
    void getProgressHasTheHeaviestWeightOfTheWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);

        app.finishWorkout();

        assertEquals(20.0, app.getProgress("Dumbbell Row").get(LocalDate.now()));
    }

    // ----- F06: the next weights after finishWorkout(), and overrideWeight() -----

    @Test
    void finishWorkoutRaisesTheWeightForNextTime() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);

        app.finishWorkout();

        assertEquals(22.5, rowOn(app, 1).getWeightKg());
    }

    @Test
    void finishWorkoutSavesTheNewWeights() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);

        app.finishWorkout();

        assertEquals(22.5, savedData().loadPlan().getDays().get(1).getExercises().get(0).getWeightKg());
    }

    @Test
    void finishWorkoutAfterARestartStillRaisesTheWeight() {
        // each CLI command is a new run of the app, so the workout is started and finished in different runs
        FitnessAppFacade first = makeAppWithTodaysWorkout(new FakeModel());
        first.startWorkout();
        logRows(first, 10);

        FitnessAppFacade second = makeApp(new FakeModel());
        second.finishWorkout();

        assertEquals(22.5, rowOn(second, 1).getWeightKg());
    }

    @Test
    void finishWorkoutKeepsTheWeightWhenRepsAreMissed() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 8);

        app.finishWorkout();

        assertEquals(20.0, rowOn(app, 1).getWeightKg());
    }

    @Test
    void finishWorkoutCountsAWorkoutWithoutProgress() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 8);

        app.finishWorkout();

        assertEquals(1, rowOn(app, 1).getStuckCount());
    }

    @Test
    void finishWorkoutSendsAStuckExerciseToTheAgent() {
        // not heavier for 2 workouts, and today makes 3
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel("DELOAD | 17.5 | a lighter week so your back can recover"));
        rowOn(app, 0).setStuckCount(2);
        rowOn(app, 1).setStuckCount(2);
        app.startWorkout();
        logRows(app, 8);

        app.finishWorkout();

        assertEquals(17.5, rowOn(app, 1).getWeightKg());
    }

    @Test
    void aStuckExerciseWaitsWhenTheAiIsOffline() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeAppWithTodaysWorkout(model);
        rowOn(app, 0).setStuckCount(2);
        rowOn(app, 1).setStuckCount(2);
        app.startWorkout();
        logRows(app, 8);

        app.finishWorkout();

        assertEquals(3, rowOn(app, 1).getStuckCount());
    }

    @Test
    void overrideWeightChangesTheRestOfThePlan() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        app.overrideWeight(rowOn(app, 0), 25);

        assertEquals(25.0, rowOn(app, 1).getWeightKg());
    }

    @Test
    void overrideWeightIsSaved() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        app.overrideWeight(rowOn(app, 0), 25);

        assertEquals(25.0, savedData().loadPlan().getDays().get(1).getExercises().get(0).getWeightKg());
    }

    @Test
    void overrideWeightIsRememberedByTheCoach() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        app.overrideWeight(rowOn(app, 0), 25);

        assertTrue(savedData().loadMemory().get(0).contains("Dumbbell Row"));
    }

    @Test
    void overrideWeightRejectsANegativeWeight() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.overrideWeight(rowOn(app, 0), -5));
    }

    // ----- F08: addBodyWeight(), getMuscleSets() and getPersonalRecords() -----

    @Test
    void addBodyWeightIsSaved() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        app.addBodyWeight(78.5);

        assertEquals(78.5, savedData().loadProfile().getWeightHistory().get(LocalDate.now()));
    }

    @Test
    void addBodyWeightRejectsAnUnrealisticWeight() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.addBodyWeight(5));
    }

    @Test
    void addBodyWeightWithoutAProfileThrows() {
        FitnessAppFacade app = makeApp(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.addBodyWeight(78.5));
    }

    @Test
    void getMuscleSetsCountsTheFinishedWorkout() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);
        app.finishWorkout();

        assertEquals(3, app.getMuscleSets(7).get("back"));
    }

    @Test
    void getPersonalRecordsHasTheHeaviestSet() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.startWorkout();
        logRows(app, 10);
        app.finishWorkout();

        assertEquals(20.0, app.getPersonalRecords().get("Dumbbell Row"));
    }

    @Test
    void anObserverIsToldWhenAWorkoutIsFinished() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        ArrayList<WorkoutLog> told = new ArrayList<>();
        app.addObserver(log -> told.add(log));
        app.startWorkout();
        logRows(app, 10);

        app.finishWorkout();

        assertEquals(1, told.size());
    }

    @Test
    void anObserverIsToldAboutEachImportedWorkout() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        ArrayList<WorkoutLog> told = new ArrayList<>();
        app.addObserver(log -> told.add(log));
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        app.confirmImport(Map.of());

        assertEquals(2, told.size());
    }

    // ----- F09: askCoach() -----

    @Test
    void askCoachReturnsTheAnswer() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel("Rest days help your muscles grow."));

        assertEquals("Rest days help your muscles grow.", app.askCoach("Why do I need rest days?"));
    }

    @Test
    void askCoachSavesTheConversation() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel("Rest days help your muscles grow."));

        app.askCoach("Why do I need rest days?");

        assertTrue(savedData().loadMemory().contains("The user asked: Why do I need rest days?"));
    }

    @Test
    void askCoachGivesTheAiThePlan() {
        FakeModel model = new FakeModel("Rows are good for your back.");
        FitnessAppFacade app = makeAppWithTodaysWorkout(model);

        app.askCoach("Why am I doing rows today?");

        assertTrue(model.prompts.get(0).contains("Dumbbell Row | 3 | 10 | 20.0"));
    }

    @Test
    void askCoachWithAnEmptyQuestionThrows() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.askCoach(" "));
    }

    @Test
    void askCoachWhenTheAiIsOfflineSaysSo() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeAppWithProfile(model);

        assertTrue(app.askCoach("Why do I need rest days?").contains("offline"));
    }

    // ----- F10: calculateTargets(), getMealIdeas() and acceptMealIdea() -----

    @Test
    void calculateTargetsUsesTheProfile() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertEquals(newProfile().calculateTargets(), app.calculateTargets());
    }

    @Test
    void calculateTargetsWithoutAProfileThrows() {
        FitnessAppFacade app = makeApp(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.calculateTargets());
    }

    @Test
    void getMealIdeasReturnsTheIdeas() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(THREE_MEALS));

        List<String> ideas = app.getMealIdeas("likes: chicken");

        assertEquals("Chicken rice bowl: chicken, rice, broccoli (about 650 calories, 45 g protein)", ideas.get(0));
    }

    @Test
    void getMealIdeasSavesTypedAllergiesInTheProfile() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel(THREE_MEALS));

        app.getMealIdeas("likes: chicken\nallergies: Peanuts, shellfish");

        assertTrue(savedData().loadProfile().getBoundaries().contains("allergy: shellfish"));
    }

    @Test
    void getMealIdeasWhenTheAiIsOfflineReturnsNull() {
        FakeModel model = new FakeModel();
        model.available = false;
        FitnessAppFacade app = makeAppWithProfile(model);

        assertNull(app.getMealIdeas("likes: chicken"));
    }

    @Test
    void acceptMealIdeaIsRemembered() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        app.acceptMealIdea("Chicken rice bowl: chicken, rice, broccoli");

        assertTrue(savedData().loadMemory().get(0).contains("Chicken rice bowl"));
    }

    // ----- F11: generateWeeklyReport() and saveReport() -----

    @Test
    void generateWeeklyReportGivesTheReport() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel("REVIEW: Good week.\nSUGGESTION: only 30 minutes on friday"));

        assertTrue(app.generateWeeklyReport().contains("1. only 30 minutes on friday"));
    }

    @Test
    void generateWeeklyReportRemembersTheSuggestions() {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel("REVIEW: Good week.\nSUGGESTION: only 30 minutes on friday"));

        app.generateWeeklyReport();

        assertTrue(savedData().loadMemory().get(0).startsWith("Suggestions on"));
    }

    @Test
    void aSuggestionIsAppliedLikeAPlanChange() {
        FitnessAppFacade app = makeAppWithPlan(new FakeModel("REVIEW: Good week.\nSUGGESTION: legs on mondays", MONDAY_BRIDGES));
        app.generateWeeklyReport();

        app.acceptPlan(app.requestChange("legs on mondays"));

        assertEquals("Glute Bridge", exerciseOn(app.getCurrentPlan(), 0));
    }

    @Test
    void saveReportWritesTheReportFile() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        app.saveReport("A good week.");

        assertTrue(Files.exists(folder.resolve("report-" + LocalDate.now() + ".txt")));
    }

    // ----- F12: importWorkoutHistory(), getImportPreview() and confirmImport() -----

    @Test
    void importWorkoutHistoryOfAFileThatIsNotCsvThrows() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalArgumentException.class, () -> app.importWorkoutHistory("history.txt"));
    }

    @Test
    void importWorkoutHistoryListsTheNamesItCanNotMatch() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        List<String> unmatched = app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        assertEquals(List.of("Cable Crossover"), unmatched);
    }

    @Test
    void importWorkoutHistoryDoesNotSaveAnythingYet() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        assertTrue(savedData().loadHistory().getLogs().isEmpty());
    }

    @Test
    void getImportPreviewCountsTheWorkoutsAndSets() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        assertTrue(app.getImportPreview().contains("2 workouts with 3 sets, from 2026-10-01 to 2026-10-03."));
    }

    @Test
    void confirmImportAddsTheWorkouts() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        app.confirmImport(Map.of());

        assertEquals(2, savedData().loadHistory().getLogs().size());
    }

    @Test
    void confirmImportUsesTheLibraryName() throws IOException {
        // "Bench Press (Barbell)" is the library's "Barbell Bench Press"
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        app.confirmImport(Map.of());

        assertEquals(60.0, app.getPersonalRecords().get("Barbell Bench Press"));
    }

    @Test
    void confirmImportUsesTheUsersMatch() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        app.confirmImport(Map.of("Cable Crossover", "Cable Fly"));

        assertEquals(15.0, app.getPersonalRecords().get("Cable Fly"));
    }

    @Test
    void confirmImportLeavesOutSetsWithNoMatch() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        app.importWorkoutHistory(writeCsv(TWO_WORKOUTS));

        assertTrue(app.confirmImport(Map.of()).contains("Left out 1 set of exercises that are not in the library."));
    }

    @Test
    void confirmImportSkipsWorkoutsThatAreAlreadyInTheHistory() throws IOException {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());
        String csv = writeCsv(TWO_WORKOUTS);
        app.importWorkoutHistory(csv);
        app.confirmImport(Map.of());

        app.importWorkoutHistory(csv);
        app.confirmImport(Map.of());

        assertEquals(2, savedData().loadHistory().getLogs().size());
    }

    @Test
    void confirmImportRaisesALowerPlannedWeight() throws IOException {
        // the plan has Dumbbell Row at 20 kg, and the latest imported workout averages 31 kg
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.importWorkoutHistory(writeCsv("Date,Exercise,Reps,Weight\n2026-10-20,Dumbbell Row,10,30\n2026-10-20,Dumbbell Row,8,32\n"));

        app.confirmImport(Map.of());

        assertEquals(31.0, rowOn(app, 1).getWeightKg());
    }

    @Test
    void confirmImportKeepsAHigherPlannedWeight() throws IOException {
        FitnessAppFacade app = makeAppWithTodaysWorkout(new FakeModel());
        app.importWorkoutHistory(writeCsv("Date,Exercise,Reps,Weight\n2026-10-20,Dumbbell Row,10,12\n"));

        app.confirmImport(Map.of());

        assertEquals(20.0, rowOn(app, 1).getWeightKg());
    }

    @Test
    void confirmImportBeforeChoosingAFileThrows() {
        FitnessAppFacade app = makeAppWithProfile(new FakeModel());

        assertThrows(IllegalStateException.class, () -> app.confirmImport(Map.of()));
    }

    private static final String TWO_WORKOUTS = "Date,Exercise Name,Set Order,Weight,Reps\n"
            + "2026-10-01 18:00:00,Bench Press (Barbell),1,60,8\n"
            + "2026-10-01 18:00:00,Cable Crossover,1,15,12\n"
            + "2026-10-03 18:00:00,Lat Pulldown (Cable),1,45,10\n";

    // writes a CSV file into the temp folder and returns its path
    private String writeCsv(String text) throws IOException {
        Path csv = folder.resolve("history.csv");
        Files.writeString(csv, text);
        return csv.toString();
    }

    private static final String THREE_MEALS = "Chicken rice bowl | chicken, rice, broccoli | 650 | 45\n"
            + "Greek yogurt bowl | greek yogurt, berries, oats | 450 | 30\n"
            + "Salmon and potatoes | salmon, potatoes, green beans | 700 | 42";
    private static final String LIGHTER_ROWS = "DAY " + LocalDate.now() + ": Back, lighter (you slept badly)\nDumbbell Row | 2 | 10 | 20";

    private UserProfile newProfile() {
        return new UserProfile(25, 180, 80, "beginner");
    }

    // 7.5 hours of sleep, good energy, little stress and soreness: a score of 80
    private CheckIn normalCheckIn() {
        return new CheckIn(LocalDate.now(), 7.5, 4, 2, 2, "");
    }

    // 5 hours of sleep, low energy, high stress and soreness: a score of 34
    private CheckIn lowCheckIn(String comments) {
        return new CheckIn(LocalDate.now(), 5, 2, 4, 4, comments);
    }

    // a facade with a saved plan that has Dumbbell Row (3 sets of 10 at 20 kg) today and a week from today
    private FitnessAppFacade makeAppWithTodaysWorkout(FakeModel model, String... boundaries) {
        WorkoutPlan plan = new WorkoutPlan(LocalDate.now());
        plan.getDays().add(rowDay(LocalDate.now()));
        plan.getDays().add(rowDay(LocalDate.now().plusWeeks(1)));
        savedData().savePlan(plan);
        return makeAppWithProfile(model, boundaries);
    }

    private WorkoutDay rowDay(LocalDate date) {
        WorkoutDay day = new WorkoutDay(date, "Back");
        day.getExercises().add(new PlannedExercise(ExerciseLibrary.getInstance().findExercise("Dumbbell Row"), 3, 10, 20));
        return day;
    }

    // logs the 3 planned sets of Dumbbell Row at 20 kg, with this many reps each
    private void logRows(FitnessAppFacade app, int reps) {
        for (int i = 0; i < 3; i++) {
            app.logSet(new SetLog("Dumbbell Row", reps, 20, 7));
        }
    }

    // the Dumbbell Row on one day of the current plan
    private PlannedExercise rowOn(FitnessAppFacade app, int dayIndex) {
        return app.getCurrentPlan().getDays().get(dayIndex).getExercises().get(0);
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
