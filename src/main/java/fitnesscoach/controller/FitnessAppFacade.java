package fitnesscoach.controller;

import java.time.LocalDate;
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

    private UserProfile profile;
    private UserProfile pendingProfile; // saved by saveProfile(), waiting for the user to confirm its boundaries
    private FitnessAgent agent;
    private WorkoutPlan currentPlan;
    private Stack<WorkoutPlan> oldPlans;
    private WorkoutHistory history;
    private WeightProgression progression;
    private DataStore dataStore;

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
        return plan;
    }

    /** F02: keeps the old plan for undo and saves the new one. */
    public void acceptPlan(WorkoutPlan plan) {
        if (currentPlan != null) {
            oldPlans.push(currentPlan);
        }
        currentPlan = plan;
        dataStore.savePlan(plan);
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
        List<String> problems = plan.checkSafety(profile);
        int tries = 0;

        while (!problems.isEmpty() && tries < MAX_FIX_TRIES) {
            WorkoutPlan fixed = agent.changePlan(plan, profile, "Fix these problems:\n- " + String.join("\n- ", problems));
            if (fixed == null) {
                break; // no AI model can be reached to fix it
            }
            plan = fixed;
            problems = plan.checkSafety(profile);
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
}
