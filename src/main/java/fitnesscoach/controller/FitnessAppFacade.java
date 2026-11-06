package fitnesscoach.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import fitnesscoach.ai.AIModel;
import fitnesscoach.ai.ClaudeAdapter;
import fitnesscoach.ai.FitnessAgent;
import fitnesscoach.ai.LlamaAdapter;
import fitnesscoach.ai.OllamaClient;
import fitnesscoach.data.DataStore;
import fitnesscoach.data.UserProfile;
import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutHistory;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.history.WorkoutObserver;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;
import fitnesscoach.progression.BeginnerStrategy;
import fitnesscoach.progression.IntermediateStrategy;
import fitnesscoach.progression.ProgressionStrategy;
import fitnesscoach.progression.WeightProgression;

/**
 * Facade pattern: the one class that the GUI and the CLI call.
 * Each public method is one user action, and it passes the work on to the other classes.
 */
public class FitnessAppFacade {
    public static final int MAX_WEEKS = 12;
    private static final int MAX_FIX_TRIES = 3; // UC13: the agent gets 3 tries to fix an unsafe plan
    private static final int MAX_OLD_PLANS = 10; // how many plan changes can be undone

    private UserProfile profile;
    private UserProfile pendingProfile; // saved by saveProfile(), waiting for the user to confirm its boundaries
    private FitnessAgent agent;
    private WorkoutPlan currentPlan;
    private WorkoutPlan pendingPlan; // a new plan shown to the user, waiting for acceptPlan() or undoChange()
    private Stack<WorkoutPlan> oldPlans;
    private WorkoutHistory history;
    private WeightProgression progression;
    private DataStore dataStore;
    private String swapReason; // from findSwapOptions(), so swapExercise() knows if the reason was pain
    private List<WorkoutLog> pendingImport; // read by importWorkoutHistory(), waiting for confirmImport()

    /** Starts the app with the data saved in dataFolder and the AI settings from the .env file. */
    public FitnessAppFacade(String dataFolder, String envFile) {
        dataStore = new DataStore(dataFolder);
        Map<String, String> config = dataStore.loadConfig(envFile);
        AIModel claude = new ClaudeAdapter(config.get("ANTHROPIC_API_KEY"), config.get("CLAUDE_MODEL"));
        AIModel llama = new LlamaAdapter(new OllamaClient(config.get("OLLAMA_URL")), config.get("OLLAMA_MODEL"));
        loadSavedData(claude, llama);
    }

    /** Starts the app with the given AI models. The tests use this with fake models. */
    public FitnessAppFacade(DataStore dataStore, AIModel mainModel, AIModel backupModel) {
        this.dataStore = dataStore;
        loadSavedData(mainModel, backupModel);
    }

    /**
     * F01: checks the basic information, then asks the AI to turn the notes into boundaries.
     * The user confirms the boundaries with confirmBoundaries(). Returns null when the AI is offline.
     */
    public List<String> saveProfile(UserProfile profile, String notes) {
        if (!profile.checkBasicInfo()) {
            throw new IllegalArgumentException("Please check your information. Age must be 13 to 100, height 100 to 250 cm, "
                    + "weight 30 to 300 kg, and experience beginner or intermediate.");
        }
        if (notes == null) {
            notes = "";
        }

        List<String> boundaries = agent.readBoundaries(profile, notes);
        dataStore.saveMemory(agent.getMemory()); // the agent may have remembered something new

        if (boundaries == null) {
            // UC01 5c: the AI is offline, so the basic information is saved and the notes are kept as they are
            if (!notes.isBlank()) {
                profile.addBoundary("note: " + notes);
            }
            useProfile(profile);
            return null;
        }
        pendingProfile = profile;
        return boundaries;
    }

    /** F01: adds the boundaries the user confirmed to the profile, picks the progression rule and saves it. */
    public void confirmBoundaries(List<String> boundaries) {
        if (pendingProfile == null) {
            throw new IllegalStateException("Save the profile before confirming its boundaries.");
        }

        for (String boundary : boundaries) {
            pendingProfile.addBoundary(boundary);
        }
        useProfile(pendingProfile);
        pendingProfile = null;
    }

    /** F02: asks the agent for a new plan and runs the safety check (UC13) on it. The plan is saved by acceptPlan(). */
    public WorkoutPlan generatePlan(int weeks) {
        if (profile == null || !profile.checkBasicInfo()) {
            throw new IllegalStateException("Please finish your profile before making a plan.");
        }
        if (weeks < 1 || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("A plan can be 1 to " + MAX_WEEKS + " weeks long.");
        }

        WorkoutPlan plan = agent.buildPlan(profile, weeks);
        plan = checkPlanSafety(plan);
        dataStore.saveMemory(agent.getMemory());
        pendingPlan = plan;
        return plan;
    }

    /** F02 and F03: makes the plan the current plan and saves it. The old plan is kept so the change can be undone. */
    public void acceptPlan(WorkoutPlan plan) {
        if (currentPlan != null) {
            oldPlans.push(currentPlan);
            if (oldPlans.size() > MAX_OLD_PLANS) {
                oldPlans.remove(0); // forget the oldest plan
            }
        }
        currentPlan = plan;
        pendingPlan = null;
        dataStore.savePlan(currentPlan);
        dataStore.saveOldPlans(oldPlans);
    }

    /**
     * F03: asks the agent to change the plan for the user's request and runs the safety check (UC13) on the new plan.
     * The change is saved by acceptPlan(). Returns null when the AI is offline.
     */
    public WorkoutPlan requestChange(String request) {
        if (currentPlan == null || profile == null) {
            throw new IllegalStateException("Please make a plan before changing it.");
        }
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("Please type the change you want.");
        }

        WorkoutPlan newPlan = agent.changePlan(currentPlan, profile, request);
        if (newPlan != null) {
            newPlan = checkPlanSafety(newPlan);
            pendingPlan = newPlan;
        }
        dataStore.saveMemory(agent.getMemory());
        return newPlan;
    }

    /**
     * F03: Undo. A new plan the user has not accepted yet is dropped, so the current plan stays.
     * Otherwise the plan from before the last accepted change is put back, even after the app restarts.
     */
    public void undoChange() {
        if (pendingPlan != null) {
            pendingPlan = null;
        } else if (!oldPlans.isEmpty()) {
            currentPlan = oldPlans.pop();
            dataStore.saveOldPlans(oldPlans);
        } else {
            throw new IllegalStateException("There is no change to undo.");
        }

        if (currentPlan != null) {
            dataStore.savePlan(currentPlan);
        }
    }

    /** F04: asks the agent for up to 3 exercises that could replace this one, each with a short reason. */
    public List<String> findSwapOptions(PlannedExercise exercise, String reason) {
        if (profile == null) {
            throw new IllegalStateException("Please finish your profile first.");
        }

        swapReason = reason;
        ArrayList<String> options = new ArrayList<>();
        for (String option : agent.pickSwapOptions(exercise, reason, profile)) {
            // an exercise that is already in the same workout would be there twice
            if (!isInSameWorkout(exercise, optionName(option))) {
                options.add(option);
            }
        }
        dataStore.saveMemory(agent.getMemory());
        return options;
    }

    /**
     * F04: puts the chosen exercise in place of this one, only on its own day or for the rest of the plan, and saves the plan.
     * If the reason was pain, the old exercise becomes an "avoid:" boundary so future plans skip it (UC04 6a).
     */
    public void swapExercise(PlannedExercise exercise, String choice, boolean restOfPlan) {
        if (currentPlan == null) {
            throw new IllegalStateException("Please make a plan first.");
        }

        String name = optionName(choice);
        Exercise newExercise = ExerciseLibrary.getInstance().findExercise(name);
        if (newExercise == null) {
            throw new IllegalArgumentException("There is no exercise called " + name + " in the library.");
        }

        String oldName = exercise.getExercise().getName();
        if (restOfPlan) {
            for (WorkoutDay day : currentPlan.getDays()) {
                if (!day.getDate().isBefore(LocalDate.now())) {
                    for (PlannedExercise planned : day.getExercises()) {
                        if (planned.getExercise().getName().equalsIgnoreCase(oldName)) {
                            replaceExercise(planned, newExercise);
                        }
                    }
                }
            }
        } else {
            replaceExercise(exercise, newExercise);
        }

        if (profile != null && isAboutPain(swapReason)) {
            profile.addBoundary("avoid: " + oldName);
            dataStore.saveProfile(profile);
        }
        swapReason = null;
        dataStore.savePlan(currentPlan);
    }

    /**
     * F05: starts today's workout and returns its day. With no workout today, the next planned one starts early,
     * and with none left it is a free workout with no planned day, so null is returned (UC05 3a).
     * A workout that was already started goes on where it stopped, with its logged sets (UC05 4c).
     */
    public WorkoutDay startWorkout() {
        if (history.getCurrentLog() != null) {
            return history.getCurrentLog().getDay();
        }

        WorkoutDay day = null;
        if (currentPlan != null) {
            day = currentPlan.getTodaysWorkout();
            if (day == null) {
                day = findNextWorkout();
            }
        }
        history.startWorkout(day);
        dataStore.saveHistory(history);
        return day;
    }

    /** F05: adds one set to the workout and saves it right away, so nothing is lost if the app closes. */
    public void logSet(SetLog set) {
        history.logSet(set);
        dataStore.saveHistory(history);
    }

    /** F05: marks an exercise of the workout as skipped, so it does not count as a failed attempt (UC05 4b). */
    public void skipExercise(PlannedExercise exercise) {
        exercise.setNote("skipped");
        if (currentPlan != null) {
            dataStore.savePlan(currentPlan);
        }
    }

    /**
     * F05 and F06: finishes and saves the workout. WorkoutHistory tells its observers (Observer pattern), so
     * WeightProgression works out the next weights, and they go to the rest of the plan.
     * Exercises that have been stuck for a while go to the agent for a deload or a new exercise.
     */
    public WorkoutLog finishWorkout() {
        WorkoutLog log = history.finishWorkout();
        if (log.getDay() != null && currentPlan != null) {
            carryWeightsForward(log);
            if (profile != null) {
                for (PlannedExercise stuck : currentPlan.getStuckExercises()) {
                    PlannedExercise decision = agent.analyzeStuckExercise(stuck, profile);
                    if (decision != null) {
                        useFromNowOn(stuck.getExercise().getName(), decision, log.getDay().getDate());
                    }
                }
                dataStore.saveMemory(agent.getMemory());
            }
            dataStore.savePlan(currentPlan);
        }
        dataStore.saveHistory(history);
        return log;
    }

    /** F06: the user's own weight for an exercise, used from its day on. The agent remembers it (UC06 6a). */
    public void overrideWeight(PlannedExercise exercise, double weightKg) {
        if (weightKg < 0 || weightKg > 500) {
            throw new IllegalArgumentException("The weight must be between 0 and 500 kg.");
        }

        String name = exercise.getExercise().getName();
        double oldWeight = exercise.getWeightKg();
        exercise.setWeightKg(weightKg);
        exercise.setStuckCount(0);
        WorkoutDay day = findDayOf(exercise);
        if (day != null) {
            for (WorkoutDay later : currentPlan.getDays()) {
                if (later.getDate().isAfter(day.getDate())) {
                    for (PlannedExercise planned : later.getExercises()) {
                        if (planned.getExercise().getName().equalsIgnoreCase(name)) {
                            planned.setWeightKg(weightKg);
                            planned.setStuckCount(0);
                        }
                    }
                }
            }
            dataStore.savePlan(currentPlan);
        }
        agent.getMemory().add("The user changed the planned weight for " + name + " from " + oldWeight + " kg to " + weightKg + " kg");
        dataStore.saveMemory(agent.getMemory());
    }

    /**
     * F07: saves the check-in and returns today's workout. When the score is low or high, or the user wrote comments,
     * the agent changes the workout first. The change is accepted like a plan change, so Reject is undoChange().
     * Returns null when no workout is planned today.
     */
    public WorkoutDay submitDailyCheckIn(CheckIn checkIn) {
        history.addCheckIn(checkIn);
        dataStore.saveHistory(history);
        if (currentPlan == null || currentPlan.getTodaysWorkout() == null) {
            return null;
        }

        WorkoutDay today = currentPlan.getTodaysWorkout();
        int score = checkIn.calculateScore();
        boolean hasComments = checkIn.getComments() != null && !checkIn.getComments().isBlank();
        if (score >= CheckIn.LOW_SCORE && score <= CheckIn.HIGH_SCORE && !hasComments) {
            return today; // UC07 3a: a normal day, so the workout stays the same
        }

        WorkoutDay adjusted = agent.adjustIntensity(today, checkIn);
        dataStore.saveMemory(agent.getMemory());
        WorkoutPlan newPlan = currentPlan.copy();
        newPlan.getDays().set(currentPlan.getDays().indexOf(today), adjusted);
        acceptPlan(newPlan);
        return adjusted;
    }

    /** F08: the heaviest weight lifted for the exercise on each date (the workout summary also uses it for records). */
    public Map<LocalDate, Double> getProgress(String exerciseName) {
        return history.getProgress(exerciseName);
    }

    /** F08: Observer pattern. The observer (the window's dashboard) is told each time a workout is finished or imported. */
    public void addObserver(WorkoutObserver observer) {
        history.addObserver(observer);
    }

    /** F08: adds today's body weight to the profile and saves it, for the body weight chart. */
    public void addBodyWeight(double weightKg) {
        if (profile == null) {
            throw new IllegalStateException("Please set up your profile first.");
        }
        profile.addBodyWeight(weightKg); // throws when the weight is not realistic
        dataStore.saveProfile(profile);
    }

    /** F08: how many sets each main muscle got in the last few days, for the muscles trained most and least. */
    public Map<String, Integer> getMuscleSets(int days) {
        return history.getMuscleSets(days);
    }

    /** F08: the heaviest weight ever lifted for each exercise. */
    public Map<String, Double> getPersonalRecords() {
        return history.getPersonalRecords();
    }

    /** F09: the coach answers a question about the user's own data, and the conversation is saved for next time. */
    public String askCoach(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Please type a question.");
        }
        String answer = agent.answerQuestion(question, profile, currentPlan);
        dataStore.saveMemory(agent.getMemory());
        return answer;
    }

    /** F10: the daily calorie and macro targets from the profile. No AI is used. */
    public Map<String, Integer> calculateTargets() {
        if (profile == null) {
            throw new IllegalStateException("Please set up your profile first.");
        }
        return profile.calculateTargets();
    }

    /**
     * F10: asks the agent for meal ideas that fit the targets and the food preferences. A line like
     * "allergies: peanuts, shellfish" in the preferences adds strict "allergy:" boundaries to the profile, so every
     * later meal idea follows them too. Returns null when the AI is offline (UC10 4a).
     */
    public List<String> getMealIdeas(String preferences) {
        if (profile == null) {
            throw new IllegalStateException("Please set up your profile first.");
        }
        if (preferences == null) {
            preferences = "";
        }

        for (String line : preferences.split("\n")) {
            if (line.trim().toLowerCase().startsWith("allergies:")) {
                for (String allergy : line.trim().substring(10).split(",")) {
                    String food = allergy.trim().toLowerCase();
                    if (!food.isEmpty() && !food.equals("none")) {
                        profile.addBoundary("allergy: " + food);
                    }
                }
                dataStore.saveProfile(profile);
            }
        }

        List<String> ideas = agent.createMealIdeas(profile, preferences);
        dataStore.saveMemory(agent.getMemory());
        return ideas;
    }

    /** F10: remembers a meal idea the user accepted, so the coach knows what they like (UC10 7). */
    public void acceptMealIdea(String idea) {
        agent.getMemory().add("The user liked this meal idea: " + idea);
        dataStore.saveMemory(agent.getMemory());
    }

    /**
     * F11: the agent's report on this week, with the numbers, a review and suggestions for next week.
     * Applying a suggestion is requestChange(suggestion), as in F03. The report is only saved by saveReport().
     */
    public String generateWeeklyReport() {
        String report = agent.writeWeeklyReport(profile, currentPlan);
        dataStore.saveMemory(agent.getMemory()); // the suggestions are remembered for next week
        return report;
    }

    /** F11: saves the report as report-(date).txt in the data folder. */
    public void saveReport(String report) {
        dataStore.saveReport(report);
    }

    /**
     * F12: reads a CSV file of past workouts and matches each exercise name to the library. Returns the names that
     * could not be matched, so the user can match them by hand. Nothing is saved until confirmImport() (UC12 5a).
     */
    public List<String> importWorkoutHistory(String filePath) {
        if (!filePath.toLowerCase().endsWith(".csv")) {
            // UC12 2a
            throw new IllegalArgumentException("Only .csv files can be imported. Workout apps like Strong and Hevy can export one.");
        }

        pendingImport = dataStore.readCsv(filePath);
        ArrayList<String> unmatched = new ArrayList<>();
        for (WorkoutLog log : pendingImport) {
            for (SetLog set : log.getSets()) {
                if (matchExercise(set.getExerciseName()) == null && !unmatched.contains(set.getExerciseName())) {
                    unmatched.add(set.getExerciseName());
                }
            }
        }
        return unmatched;
    }

    /** F12: the preview of the file: the app it came from, the units, how many workouts and sets, and any skipped rows. */
    public List<String> getImportPreview() {
        ArrayList<String> preview = new ArrayList<>(dataStore.getImportNotes());
        if (pendingImport != null && !pendingImport.isEmpty()) {
            int sets = 0;
            for (WorkoutLog log : pendingImport) {
                sets += log.getSets().size();
            }
            preview.add(plural(pendingImport.size(), "workout") + " with " + plural(sets, "set") + ", from "
                    + pendingImport.get(0).getDate() + " to " + pendingImport.get(pendingImport.size() - 1).getDate() + ".");
        }
        return preview;
    }

    /**
     * F12: adds the workouts read by importWorkoutHistory() to the history. matches gives the library exercise for each
     * name the user matched by hand; sets of names with no match are left out. Workouts that are already in the history
     * are skipped (UC12 6a). Each new workout tells the observers (Observer pattern). Returns a summary.
     */
    public String confirmImport(Map<String, String> matches) {
        if (pendingImport == null) {
            throw new IllegalStateException("Choose a file to import first.");
        }

        ArrayList<WorkoutLog> added = new ArrayList<>();
        int leftOut = 0;
        int duplicates = 0;
        for (WorkoutLog log : pendingImport) {
            WorkoutLog matched = new WorkoutLog(log.getDate(), null); // imported workouts are not planned days
            for (SetLog set : log.getSets()) {
                Exercise exercise = matchExercise(set.getExerciseName());
                if (exercise == null && matches.get(set.getExerciseName()) != null) {
                    exercise = ExerciseLibrary.getInstance().findExercise(matches.get(set.getExerciseName()));
                }
                if (exercise == null) {
                    leftOut++;
                } else {
                    matched.addSet(new SetLog(exercise.getName(), set.getReps(), set.getWeightKg(), set.getDifficulty()));
                }
            }

            if (matched.getSets().isEmpty()) {
                continue;
            }
            if (isAlreadyInHistory(matched)) {
                duplicates++;
            } else {
                history.addWorkout(matched); // calls notifyObservers()
                added.add(matched);
            }
        }

        List<String> raised = useImportedWeights(added);
        dataStore.saveHistory(history);
        if (currentPlan != null) {
            dataStore.savePlan(currentPlan);
        }
        pendingImport = null;

        int sets = 0;
        for (WorkoutLog log : added) {
            sets += log.getSets().size();
        }
        String summary = "Imported " + plural(added.size(), "workout") + " with " + plural(sets, "set") + ".";
        if (leftOut > 0) {
            summary += "\nLeft out " + plural(leftOut, "set") + " of exercises that are not in the library.";
        }
        if (duplicates > 0) {
            summary += "\nSkipped " + plural(duplicates, "workout") + " that you already had in your history.";
        }
        if (!raised.isEmpty()) {
            summary += "\nYour plan now starts from your imported weights: " + String.join(", ", raised) + ".";
        }
        return summary;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public WorkoutPlan getCurrentPlan() {
        return currentPlan;
    }

    // the workout that was started and not finished yet, or null
    public WorkoutLog getCurrentLog() {
        return history.getCurrentLog();
    }

    // loads everything that was saved last time, when the app starts
    private void loadSavedData(AIModel mainModel, AIModel backupModel) {
        profile = dataStore.loadProfile();
        currentPlan = dataStore.loadPlan();
        oldPlans = new Stack<>();
        oldPlans.addAll(dataStore.loadOldPlans());
        history = dataStore.loadHistory();

        // a saved workout in progress has its own copy of its day, so it is pointed back at the plan's day
        WorkoutLog current = history.getCurrentLog();
        if (current != null && current.getDay() != null && currentPlan != null) {
            for (WorkoutDay day : currentPlan.getDays()) {
                if (day.getDate().equals(current.getDay().getDate())) {
                    current.setDay(day);
                }
            }
        }

        agent = new FitnessAgent(mainModel, backupModel, history);
        agent.setMemory(dataStore.loadMemory());

        progression = new WeightProgression(pickStrategy(profile));
        history.addObserver(progression);
    }

    // makes newProfile the user's profile, picks its progression rule and saves it
    private void useProfile(UserProfile newProfile) {
        if (profile != null) {
            newProfile.getWeightHistory().putAll(profile.getWeightHistory()); // keep the old body weights for the dashboard
        }
        profile = newProfile;
        progression.setStrategy(pickStrategy(profile));
        dataStore.saveProfile(profile);
    }

    // Strategy pattern: the weight progression rule depends on the user's experience
    private ProgressionStrategy pickStrategy(UserProfile profile) {
        if (profile != null && "intermediate".equalsIgnoreCase(profile.getExperience())) {
            return new IntermediateStrategy();
        }
        return new BeginnerStrategy();
    }

    // UC13: any problems go back to the agent to fix, up to 3 times, and the ones left are shown as warnings
    private WorkoutPlan checkPlanSafety(WorkoutPlan plan) {
        List<String> problems = findProblems(plan);
        int tries = 0;

        while (!problems.isEmpty() && tries < MAX_FIX_TRIES) {
            WorkoutPlan fixed = agent.changePlan(plan, profile, "Fix these problems:\n- " + String.join("\n- ", problems));
            if (fixed == null) {
                break; // no AI model can be reached to fix it
            }
            plan = fixed;
            problems = findProblems(plan);
            tries++;
        }

        for (String problem : problems) {
            // every problem starts with its date, like "2026-11-02: ..."
            LocalDate date = LocalDate.parse(problem.substring(0, 10));
            for (WorkoutDay day : plan.getDays()) {
                if (day.getDate().equals(date)) {
                    day.setNote(day.getNote() + " (Warning: " + problem.substring(12) + ")");
                }
            }
        }
        return plan;
    }

    // the safety problems from today on. Days that already happened can not be changed any more
    private List<String> findProblems(WorkoutPlan plan) {
        ArrayList<String> problems = new ArrayList<>();
        for (String problem : plan.checkSafety(profile)) {
            if (!LocalDate.parse(problem.substring(0, 10)).isBefore(LocalDate.now())) {
                problems.add(problem);
            }
        }
        return problems;
    }

    // the planned exercise keeps its sets and reps. The weight stays with the same equipment, otherwise it starts light
    private void replaceExercise(PlannedExercise planned, Exercise newExercise) {
        if (!planned.getExercise().getEquipment().equals(newExercise.getEquipment())) {
            planned.setWeightKg(newExercise.getStartingWeight());
        }
        planned.setNote("swapped in for " + planned.getExercise().getName());
        planned.setExercise(newExercise);
        planned.setStuckCount(0);
    }

    // the exercise name in an option like "Goblet Squat: easier on the knees"
    private String optionName(String option) {
        if (option.contains(":")) {
            return option.substring(0, option.indexOf(":")).trim();
        }
        return option.trim();
    }

    // true when the exercise called name is already in the workout that has this planned exercise
    private boolean isInSameWorkout(PlannedExercise exercise, String name) {
        WorkoutDay day = findDayOf(exercise);
        if (day == null) {
            return false;
        }
        for (PlannedExercise planned : day.getExercises()) {
            if (planned.getExercise().getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // the day of the current plan that has this planned exercise, or null
    private WorkoutDay findDayOf(PlannedExercise exercise) {
        if (currentPlan == null) {
            return null;
        }
        for (WorkoutDay day : currentPlan.getDays()) {
            if (day.getExercises().contains(exercise)) {
                return day;
            }
        }
        return null;
    }

    // UC05 3a: the first workout after today
    private WorkoutDay findNextWorkout() {
        for (WorkoutDay day : currentPlan.getDays()) {
            if (day.getDate().isAfter(LocalDate.now())) {
                return day;
            }
        }
        return null;
    }

    // F06: the new weights from the finished workout go to every later workout with the same exercise.
    // Exercises with no logged sets were skipped, so they stay the same
    private void carryWeightsForward(WorkoutLog log) {
        for (PlannedExercise done : log.getDay().getExercises()) {
            boolean logged = false;
            for (SetLog set : log.getSets()) {
                if (set.getExerciseName().equalsIgnoreCase(done.getExercise().getName())) {
                    logged = true;
                }
            }
            if (logged) {
                for (WorkoutDay day : currentPlan.getDays()) {
                    if (day.getDate().isAfter(log.getDay().getDate())) {
                        for (PlannedExercise planned : day.getExercises()) {
                            if (planned.getExercise().getName().equalsIgnoreCase(done.getExercise().getName())) {
                                planned.setWeightKg(done.getWeightKg());
                                planned.setStuckCount(done.getStuckCount());
                            }
                        }
                    }
                }
            }
        }
    }

    // F06: the agent's decision for a stuck exercise is used in every workout after the finished one
    private void useFromNowOn(String exerciseName, PlannedExercise decision, LocalDate finishedDate) {
        for (WorkoutDay day : currentPlan.getDays()) {
            if (day.getDate().isAfter(finishedDate)) {
                for (PlannedExercise planned : day.getExercises()) {
                    if (planned.getExercise().getName().equalsIgnoreCase(exerciseName)) {
                        planned.setExercise(decision.getExercise());
                        planned.setWeightKg(decision.getWeightKg());
                        planned.setStuckCount(0);
                        planned.setNote(decision.getNote());
                    }
                }
            }
        }
    }

    // F12: finds the library exercise for a name from another app, like "Barbell Bench Press",
    // "Bench Press (Barbell)" (becomes Barbell Bench Press) or "Lat Pulldown (Cable)" (becomes Lat Pulldown)
    private Exercise matchExercise(String name) {
        ExerciseLibrary library = ExerciseLibrary.getInstance();
        Exercise exercise = library.findExercise(name.trim());
        if (exercise == null && name.contains("(") && name.contains(")")) {
            String main = name.substring(0, name.indexOf("(")).trim();
            String kind = name.substring(name.indexOf("(") + 1, name.indexOf(")")).trim();
            exercise = library.findExercise(kind + " " + main);
            if (exercise == null) {
                exercise = library.findExercise(main);
            }
        }
        return exercise;
    }

    // UC12 6a: a workout on the same date with the same sets is already in the history
    private boolean isAlreadyInHistory(WorkoutLog log) {
        for (WorkoutLog old : history.getLogs()) {
            if (old.getDate().equals(log.getDate()) && describeSets(old).equals(describeSets(log))) {
                return true;
            }
        }
        return false;
    }

    private String describeSets(WorkoutLog log) {
        String text = "";
        for (SetLog set : log.getSets()) {
            text += set.getExerciseName() + " " + set.getReps() + " " + set.getWeightKg() + ";";
        }
        return text;
    }

    // UC12 7: imported workouts are not planned days, so WeightProgression leaves them alone. Instead, a planned weight
    // that is lower than the user's latest imported working weight (the average of its sets) is raised to it
    private List<String> useImportedWeights(List<WorkoutLog> imported) {
        ArrayList<String> raised = new ArrayList<>();
        if (currentPlan == null) {
            return raised;
        }

        TreeMap<String, Double> latest = new TreeMap<>();
        for (WorkoutLog log : imported) { // in date order, so a later workout replaces an earlier one
            TreeMap<String, Double> totals = new TreeMap<>();
            TreeMap<String, Integer> counts = new TreeMap<>();
            for (SetLog set : log.getSets()) {
                if (set.getWeightKg() > 0) {
                    totals.put(set.getExerciseName(), totals.getOrDefault(set.getExerciseName(), 0.0) + set.getWeightKg());
                    counts.put(set.getExerciseName(), counts.getOrDefault(set.getExerciseName(), 0) + 1);
                }
            }
            for (String name : totals.keySet()) {
                double average = totals.get(name) / counts.get(name);
                latest.put(name, Math.round(average * 2) / 2.0); // to the nearest 0.5 kg
            }
        }

        for (String name : latest.keySet()) {
            boolean changed = false;
            for (WorkoutDay day : currentPlan.getDays()) {
                if (!day.getDate().isBefore(LocalDate.now())) {
                    for (PlannedExercise planned : day.getExercises()) {
                        if (planned.getExercise().getName().equalsIgnoreCase(name) && planned.getWeightKg() < latest.get(name)) {
                            planned.setWeightKg(latest.get(name));
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                raised.add(name + " " + latest.get(name) + " kg");
            }
        }
        return raised;
    }

    // like "1 set" or "3 sets"
    private String plural(int count, String word) {
        if (count == 1) {
            return count + " " + word;
        }
        return count + " " + word + "s";
    }

    private boolean isAboutPain(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase();
        return lower.contains("pain") || lower.contains("hurt") || lower.contains("injur");
    }
}
