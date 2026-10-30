package fitnesscoach.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import fitnesscoach.ai.AIModel;
import fitnesscoach.ai.ClaudeAdapter;
import fitnesscoach.ai.FitnessAgent;
import fitnesscoach.ai.LlamaAdapter;
import fitnesscoach.ai.OllamaClient;
import fitnesscoach.data.DataStore;
import fitnesscoach.data.UserProfile;
import fitnesscoach.history.WorkoutHistory;
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

    public UserProfile getProfile() {
        return profile;
    }

    public WorkoutPlan getCurrentPlan() {
        return currentPlan;
    }

    // loads everything that was saved last time, when the app starts
    private void loadSavedData(AIModel mainModel, AIModel backupModel) {
        profile = dataStore.loadProfile();
        currentPlan = dataStore.loadPlan();
        oldPlans = new Stack<>();
        oldPlans.addAll(dataStore.loadOldPlans());
        history = dataStore.loadHistory();

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
        if (currentPlan == null) {
            return false;
        }
        for (WorkoutDay day : currentPlan.getDays()) {
            if (day.getExercises().contains(exercise)) {
                for (PlannedExercise planned : day.getExercises()) {
                    if (planned.getExercise().getName().equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAboutPain(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase();
        return lower.contains("pain") || lower.contains("hurt") || lower.contains("injur");
    }
}
