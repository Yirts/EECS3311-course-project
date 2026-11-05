package fitnesscoach.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import fitnesscoach.controller.FitnessAppFacade;
import fitnesscoach.data.UserProfile;
import fitnesscoach.history.CheckIn;
import fitnesscoach.history.SetLog;
import fitnesscoach.history.WorkoutLog;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The JavaFX window, with one tab for each page (the View in MVC). Each button calls the facade method with the same name.
 * Calls to the AI run on a background thread, so the window does not freeze while the AI is thinking.
 */
public class Main extends Application {
    private static final int REST_SECONDS = 90;

    private FitnessAppFacade app;
    private TabPane tabs;
    private Label status;
    private ProgressIndicator busy;

    // Profile tab (F01)
    private TextField ageField;
    private TextField heightField;
    private TextField weightField;
    private ChoiceBox<String> experienceBox;
    private TextArea notesArea;
    private VBox questionsBox;
    private ArrayList<Label> questionLabels = new ArrayList<>();
    private ArrayList<TextField> answerFields = new ArrayList<>();
    private TextArea boundariesArea;
    private Label profileMessage;
    private Label doctorWarning;
    private Button confirmButton;

    // Workout Plan tab (F02, F03, F04, F06)
    private Spinner<Integer> weeksSpinner;
    private TextField requestField;
    private TableView<PlannedExercise> planTable;
    private HashMap<PlannedExercise, WorkoutDay> dayOf = new HashMap<>();
    private ArrayList<LocalDate> changedDates = new ArrayList<>();
    private HBox planButtons;
    private HBox rowButtons;
    private HBox pendingBar;
    private Label pendingLabel;
    private Button keepButton;
    private Button dropButton;
    private Label planMessage;
    private WorkoutPlan pendingPlan; // a new or changed plan that waits for the user
    private boolean pendingIsNewPlan;
    private Runnable regenerate;

    // Workout tab (F05, F07)
    private Label workoutTitle;
    private Label workoutMessage;
    private Button startButton;
    private Button finishButton;
    private ListView<String> exercisesList;
    private VBox setPanel;
    private Label currentLabel;
    private ComboBox<String> exerciseBox;
    private TextField repsInput;
    private TextField weightInput;
    private Slider difficultySlider;
    private Label restLabel;
    private ListView<String> setsList;
    private TextArea summaryArea;
    private PlannedExercise prefilled; // the exercise whose planned numbers are in the boxes
    private Timeline restTimer;
    private int restSecondsLeft;

    public Main() {
        // the facade is made in start(), when the window opens
    }

    // opens the window with a facade that was already made, for example one with test data
    public Main(FitnessAppFacade app) {
        this.app = app;
    }

    @Override
    public void start(Stage stage) {
        if (app == null) {
            app = new FitnessAppFacade("data", ".env"); // loads the saved data and the AI settings
        }

        status = new Label("Ready.");
        busy = new ProgressIndicator();
        busy.setPrefSize(18, 18);
        busy.setVisible(false);
        busy.managedProperty().bind(busy.visibleProperty()); // takes no space while it is hidden
        HBox statusBar = new HBox(8, busy, status);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 12, 6, 12));

        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Profile", buildProfileTab()));
        tabs.getTabs().add(new Tab("Workout Plan", buildPlanTab()));
        tabs.getTabs().add(new Tab("Workout", buildWorkoutTab()));
        for (String page : new String[] {"Progress Dashboard", "Coach Chatbox", "Nutrition", "Report", "Import"}) {
            Label later = new Label("The " + page + " page is not in the window yet. For now it works in the CLI (fitagent help).");
            later.setPadding(new Insets(16));
            tabs.getTabs().add(new Tab(page, later));
        }
        refreshAll();

        BorderPane root = new BorderPane(tabs);
        root.setBottom(statusBar);
        stage.setTitle("AI Fitness Plan Coach");
        stage.setScene(new Scene(root, 1100, 720));
        stage.show();
    }

    // ----- Profile tab (F01) -----

    private Node buildProfileTab() {
        ageField = new TextField();
        heightField = new TextField();
        weightField = new TextField();
        experienceBox = new ChoiceBox<>(FXCollections.observableArrayList("beginner", "intermediate"));
        notesArea = new TextArea();
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(8);
        notesArea.setPromptText("For example: I want to build muscle. I have dumbbells. I can train monday, wednesday and friday "
                + "for 45 minutes. My left knee hurts when I squat deep.");
        Button saveButton = new Button("Save Profile");
        saveButton.setOnAction(event -> saveProfile());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Age"), ageField);
        form.addRow(1, new Label("Height (cm)"), heightField);
        form.addRow(2, new Label("Weight (kg)"), weightField);
        form.addRow(3, new Label("Experience"), experienceBox);
        Label notesLabel = new Label("Anything the AI planner should know: injuries, your goal, your equipment, "
                + "which days you can work out and how long a workout can be.");
        notesLabel.setWrapText(true);
        VBox left = new VBox(10, title("Your information"), form, notesLabel, notesArea, saveButton);
        left.setMinWidth(430);
        left.setMaxWidth(430);

        profileMessage = new Label("Fill in your information and press Save Profile. The AI turns your notes into boundaries for you to check.");
        profileMessage.setWrapText(true);
        doctorWarning = new Label();
        doctorWarning.setWrapText(true);
        doctorWarning.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
        questionsBox = new VBox(6);
        boundariesArea = new TextArea();
        boundariesArea.setWrapText(true);
        boundariesArea.setPrefRowCount(12);
        boundariesArea.setEditable(false);
        confirmButton = new Button("Confirm");
        confirmButton.setDisable(true);
        confirmButton.setOnAction(event -> confirmBoundaries());
        VBox right = new VBox(10, title("Your boundaries"), profileMessage, doctorWarning, questionsBox, boundariesArea, confirmButton);
        HBox.setHgrow(right, Priority.ALWAYS);

        showSavedProfile();
        HBox page = new HBox(24, left, right);
        page.setPadding(new Insets(16));
        return page;
    }

    // fills the form with the profile that was saved last time
    private void showSavedProfile() {
        UserProfile profile = app.getProfile();
        if (profile == null) {
            return;
        }
        ageField.setText(String.valueOf(profile.getAge()));
        heightField.setText(String.valueOf(profile.getHeightCm()));
        weightField.setText(String.valueOf(profile.getWeightKg()));
        experienceBox.setValue(profile.getExperience());
        boundariesArea.setText(String.join("\n", profile.getBoundaries()));
        profileMessage.setText("These are your saved boundaries. To change them, write your notes and press Save Profile.");
    }

    private void saveProfile() {
        UserProfile profile = readProfileForm();
        if (profile == null) {
            return;
        }
        // UC01 5a: the answers to the AI's follow-up questions are added to the notes
        for (int i = 0; i < answerFields.size(); i++) {
            String answer = answerFields.get(i).getText().trim();
            if (!answer.isEmpty()) {
                notesArea.appendText("\n" + questionLabels.get(i).getText() + " " + answer);
            }
        }
        String notes = notesArea.getText();
        runInBackground("The AI is reading your notes...", () -> app.saveProfile(profile, notes), this::showBoundaries);
    }

    // reads the form. A field that is missing or not realistic is highlighted, and null is returned (UC01 4a)
    private UserProfile readProfileForm() {
        double age = readNumber(ageField);
        double height = readNumber(heightField);
        double weight = readNumber(weightField);
        String experience = experienceBox.getValue();

        // the profile checks each value on its own, with good values for the others, so the right field is highlighted
        boolean ageOk = new UserProfile((int) age, 170, 70, "beginner").checkBasicInfo();
        boolean heightOk = new UserProfile(30, height, 70, "beginner").checkBasicInfo();
        boolean weightOk = new UserProfile(30, 170, weight, "beginner").checkBasicInfo();
        highlight(ageField, !ageOk);
        highlight(heightField, !heightOk);
        highlight(weightField, !weightOk);
        highlight(experienceBox, experience == null);
        if (!ageOk || !heightOk || !weightOk || experience == null) {
            profileMessage.setText("Please check the highlighted fields. They are missing or not realistic.");
            return null;
        }
        return new UserProfile((int) age, height, weight, experience);
    }

    // shows the AI's follow-up questions, or its summary of the boundaries for the user to check and confirm
    private void showBoundaries(List<String> boundaries) {
        questionsBox.getChildren().clear();
        questionLabels.clear();
        answerFields.clear();
        doctorWarning.setText("");
        if (boundaries == null) {
            // UC01 5c
            profileMessage.setText("The AI is offline, so your basic information was saved and your notes were kept as they are. "
                    + "Press Save Profile again when the AI is back.");
            confirmButton.setDisable(true);
            return;
        }

        ArrayList<String> summary = new ArrayList<>();
        for (String boundary : boundaries) {
            if (boundary.startsWith("question:")) {
                Label question = new Label(boundary.substring(9).trim());
                question.setWrapText(true);
                TextField answer = new TextField();
                questionLabels.add(question);
                answerFields.add(answer);
                questionsBox.getChildren().addAll(question, answer);
            } else {
                summary.add(boundary);
                if (boundary.startsWith("doctor:")) {
                    doctorWarning.setText("Important: " + boundary.substring(7).trim()); // UC01 5b
                }
            }
        }

        boundariesArea.setText(String.join("\n", summary));
        boundariesArea.setEditable(true);
        if (!answerFields.isEmpty()) {
            profileMessage.setText("The AI has some questions. Answer them and press Save Profile again.");
            confirmButton.setDisable(true);
        } else {
            profileMessage.setText("This is what the AI understood. You can change any line, then press Confirm. "
                    + "If it is wrong, change your notes and press Save Profile again.");
            confirmButton.setDisable(false);
        }
    }

    private void confirmBoundaries() {
        ArrayList<String> boundaries = new ArrayList<>();
        for (String line : boundariesArea.getText().split("\n")) {
            if (!line.isBlank()) {
                boundaries.add(line.trim());
            }
        }
        app.confirmBoundaries(boundaries);
        confirmButton.setDisable(true);
        boundariesArea.setEditable(false);
        profileMessage.setText("Your profile is saved. Go to the Workout Plan page to make a plan.");
    }

    // ----- Workout Plan tab (F02, F03, F04, F06) -----

    private Node buildPlanTab() {
        weeksSpinner = new Spinner<>(1, FitnessAppFacade.MAX_WEEKS, 4);
        weeksSpinner.setPrefWidth(70);
        Button generateButton = new Button("Generate Plan");
        generateButton.setOnAction(event -> generatePlan());
        requestField = new TextField();
        requestField.setPromptText("Request a change, like: only 30 minutes on tuesday");
        HBox.setHgrow(requestField, Priority.ALWAYS);
        Button requestButton = new Button("Request a Change");
        requestButton.setOnAction(event -> requestChange());
        Button undoButton = new Button("Undo Last Change");
        undoButton.setOnAction(event -> undoLastChange());
        planButtons = new HBox(8, new Label("Weeks"), weeksSpinner, generateButton, requestField, requestButton, undoButton);
        planButtons.setAlignment(Pos.CENTER_LEFT);

        pendingLabel = new Label();
        pendingLabel.setWrapText(true);
        HBox.setHgrow(pendingLabel, Priority.ALWAYS);
        keepButton = new Button("Accept");
        keepButton.setOnAction(event -> keepPending());
        dropButton = new Button("Undo");
        dropButton.setOnAction(event -> dropPending());
        Button regenerateButton = new Button("Regenerate");
        regenerateButton.setOnAction(event -> regenerate.run());
        pendingBar = new HBox(8, pendingLabel, keepButton, dropButton, regenerateButton);
        pendingBar.setAlignment(Pos.CENTER_LEFT);
        pendingBar.setStyle("-fx-background-color: #fff4cc; -fx-padding: 8;");

        planTable = new TableView<>();
        planTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        planTable.getColumns().add(column("Date", 120, planned -> dateText(planned)));
        planTable.getColumns().add(column("Workout", 230, planned -> firstOfDay(planned) ? dayOf.get(planned).getNote() : ""));
        planTable.getColumns().add(column("Exercise", 190, planned -> planned.getExercise().getName()));
        planTable.getColumns().add(column("Sets x Reps", 85, planned -> planned.getSets() + " x " + planned.getReps()));
        planTable.getColumns().add(column("Weight", 110, planned -> weightText(planned)));
        planTable.getColumns().add(column("Note", 220, planned -> noteText(planned)));
        VBox.setVgrow(planTable, Priority.ALWAYS);

        Button swapButton = new Button("Swap Exercise");
        swapButton.setOnAction(event -> swapSelected());
        Button overrideButton = new Button("Override Weight");
        overrideButton.setOnAction(event -> overrideSelected());
        rowButtons = new HBox(8, swapButton, overrideButton, new Label("(select an exercise in the table first)"));
        rowButtons.setAlignment(Pos.CENTER_LEFT);
        planMessage = new Label();
        planMessage.setWrapText(true);
        showPendingBar(false);

        VBox page = new VBox(10, title("Your workout plan"), planButtons, pendingBar, planTable, rowButtons, planMessage);
        page.setPadding(new Insets(16));
        return page;
    }

    // F02: the AI builds a new plan, which waits for Accept
    private void generatePlan() {
        if (app.getProfile() == null) {
            // UC02 1a: the profile comes first
            planMessage.setText("Please set up your profile first.");
            tabs.getSelectionModel().select(0);
            return;
        }
        int weeks = weeksSpinner.getValue();
        runInBackground("The AI is building your plan. This can take a minute...", () -> app.generatePlan(weeks), plan -> {
            pendingIsNewPlan = true;
            showPending(plan, "This is your new " + weeks + "-week plan. Accept it to save it, or regenerate it.",
                    "Accept", "Cancel", this::generatePlan);
        });
    }

    // F03: the AI changes the plan for the typed request, and the change waits for Confirm
    private void requestChange() {
        String request = requestField.getText().trim();
        if (app.getCurrentPlan() == null) {
            planMessage.setText("You don't have a plan yet. Press Generate Plan first.");
            return;
        }
        if (request.isEmpty()) {
            planMessage.setText("Type the change you want first.");
            return;
        }
        runInBackground("The AI is changing your plan...", () -> app.requestChange(request), plan -> {
            if (plan == null) {
                planMessage.setText("The AI is offline, so your plan can't be changed right now.");
                return;
            }
            if (findChangedDates(app.getCurrentPlan(), plan).isEmpty()) {
                // UC03 2a: the request was not clear enough to change anything
                app.undoChange();
                planMessage.setText("The AI did not change anything. Try saying which days or exercises to change, "
                        + "like \"only 30 minutes on tuesday\" or \"no bench next week\".");
                return;
            }
            pendingIsNewPlan = false;
            showPending(plan, "The changed workouts are marked with a star. Confirm or undo the change, "
                    + "or edit your request and regenerate.", "Confirm", "Undo", this::requestChange);
        });
    }

    // shows a plan that waits for the user: Accept or Confirm keeps it, Cancel or Undo drops it, and Regenerate asks the AI again
    private void showPending(WorkoutPlan plan, String text, String keepText, String dropText, Runnable again) {
        pendingPlan = plan;
        regenerate = again;
        keepButton.setText(keepText);
        dropButton.setText(dropText);
        showPlan(plan, app.getCurrentPlan());

        String removed = "";
        for (LocalDate date : changedDates) {
            if (findDay(plan, date) == null) {
                removed += dayLabel(date) + ", ";
            }
        }
        if (!removed.isEmpty()) {
            text += " Removed workouts: " + removed.substring(0, removed.length() - 2) + ".";
        }
        pendingLabel.setText(text);
        planMessage.setText("");
        showPendingBar(true);
    }

    private void keepPending() {
        if (pendingIsNewPlan && app.getCurrentPlan() != null) {
            // UC02 8a: ask before replacing the current plan
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "The new plan replaces your current plan. You can bring the old one back with Undo Last Change.");
            alert.setHeaderText(null);
            Optional<ButtonType> answer = alert.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                return;
            }
        }
        app.acceptPlan(pendingPlan);
        pendingPlan = null;
        showPendingBar(false);
        planMessage.setText("Your plan is saved.");
        refreshAll();
    }

    private void dropPending() {
        app.undoChange(); // drops the plan that was waiting, so the current plan stays
        pendingPlan = null;
        showPendingBar(false);
        planMessage.setText("Nothing was changed.");
        refreshAll();
    }

    private void showPendingBar(boolean show) {
        pendingBar.setVisible(show);
        pendingBar.setManaged(show);
        planButtons.setDisable(show);
        rowButtons.setDisable(show);
    }

    // F03: puts the plan from before the last accepted change back, even after the app was closed
    private void undoLastChange() {
        try {
            app.undoChange();
            planMessage.setText("Your plan is back to how it was before the last change.");
        } catch (IllegalStateException error) {
            planMessage.setText(error.getMessage());
        }
        refreshAll();
    }

    private void swapSelected() {
        PlannedExercise exercise = planTable.getSelectionModel().getSelectedItem();
        if (exercise == null) {
            planMessage.setText("Select an exercise in the table first.");
            return;
        }
        swapExercise(exercise, dayOf.get(exercise), planMessage);
    }

    // F04: asks why, gets up to 3 options from the AI, and swaps the exercise for the one the user picks
    private void swapExercise(PlannedExercise exercise, WorkoutDay day, Label message) {
        String name = exercise.getExercise().getName();
        ChoiceDialog<String> reasonDialog = new ChoiceDialog<>("pain", "pain", "I don't enjoy it", "the machine is being used", "something else");
        reasonDialog.setTitle("Swap Exercise");
        reasonDialog.setHeaderText("Why do you want to swap " + name + "?");
        reasonDialog.setContentText("Reason:");
        Optional<String> choice = reasonDialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }

        String reason = choice.get();
        if (reason.equals("something else")) {
            TextInputDialog otherDialog = new TextInputDialog();
            otherDialog.setTitle("Swap Exercise");
            otherDialog.setHeaderText("Why do you want to swap " + name + "?");
            otherDialog.setContentText("Reason:");
            Optional<String> typed = otherDialog.showAndWait();
            if (typed.isEmpty() || typed.get().isBlank()) {
                return;
            }
            reason = typed.get().trim();
        }
        if (reason.equals("pain")) {
            message.setText("If the pain is sharp, stop doing this exercise and see a doctor or physiotherapist."); // UC04 1a
        }

        String swapReason = reason;
        runInBackground("Finding other exercises...", () -> app.findSwapOptions(exercise, swapReason),
                options -> pickSwapOption(exercise, day, options, message));
    }

    private void pickSwapOption(PlannedExercise exercise, WorkoutDay day, List<String> options, Label message) {
        if (options.isEmpty()) {
            message.setText("No other exercise in the library works these muscles with your equipment and boundaries.");
            return;
        }

        VBox box = new VBox(8);
        box.setPrefWidth(540);
        ToggleGroup optionGroup = new ToggleGroup();
        for (String option : options) {
            RadioButton button = new RadioButton(option);
            button.setWrapText(true);
            button.setUserData(option);
            button.setToggleGroup(optionGroup);
            box.getChildren().add(button);
        }
        optionGroup.selectToggle(optionGroup.getToggles().get(0));
        ToggleGroup whenGroup = new ToggleGroup();
        RadioButton oneDay = new RadioButton("Only on " + dayLabel(day.getDate()));
        RadioButton restOfPlan = new RadioButton("For the rest of the plan");
        oneDay.setToggleGroup(whenGroup);
        restOfPlan.setToggleGroup(whenGroup);
        oneDay.setSelected(true);
        box.getChildren().addAll(new Label("When:"), oneDay, restOfPlan);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Swap Exercise");
        dialog.setHeaderText("Pick the exercise to do instead of " + exercise.getExercise().getName());
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        String oldName = exercise.getExercise().getName();
        app.swapExercise(exercise, (String) optionGroup.getSelectedToggle().getUserData(), restOfPlan.isSelected());
        String text = "Swapped " + oldName + " for " + exercise.getExercise().getName();
        if (restOfPlan.isSelected()) {
            text += " for the rest of your plan.";
        } else {
            text += " on " + dayLabel(day.getDate()) + ".";
        }
        if (app.getProfile() != null && app.getProfile().getBoundaries().contains("avoid: " + oldName)) {
            text += " Because of the pain, your profile now avoids " + oldName + ".";
        }
        message.setText(text);
        refreshAll();
    }

    // F06: the user's own weight for the selected exercise, from its day on
    private void overrideSelected() {
        PlannedExercise exercise = planTable.getSelectionModel().getSelectedItem();
        if (exercise == null) {
            planMessage.setText("Select an exercise in the table first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(String.valueOf(exercise.getWeightKg()));
        dialog.setTitle("Override Weight");
        dialog.setHeaderText("Your own weight for " + exercise.getExercise().getName() + ", from " + dayLabel(dayOf.get(exercise).getDate()) + " on");
        dialog.setContentText("Weight in kg:");
        Optional<String> typed = dialog.showAndWait();
        if (typed.isEmpty()) {
            return;
        }
        try {
            app.overrideWeight(exercise, Double.parseDouble(typed.get().trim()));
            planMessage.setText("Saved. The coach will remember the weight you prefer.");
        } catch (NumberFormatException error) {
            planMessage.setText("The weight must be a number.");
        } catch (IllegalArgumentException error) {
            planMessage.setText(error.getMessage());
        }
        refreshAll();
    }

    // shows the current plan, or the plan that is waiting for the user
    private void refreshPlan() {
        if (pendingPlan != null) {
            showPlan(pendingPlan, app.getCurrentPlan());
        } else {
            showPlan(app.getCurrentPlan(), null);
        }
    }

    // fills the table with the plan from today on, one row for each exercise.
    // Days that are different from "before" are marked with a star
    private void showPlan(WorkoutPlan plan, WorkoutPlan before) {
        dayOf.clear();
        changedDates.clear();
        ArrayList<PlannedExercise> rows = new ArrayList<>();
        if (plan == null) {
            planTable.setPlaceholder(new Label("There is no plan yet. Choose the number of weeks and press Generate Plan."));
        } else {
            planTable.setPlaceholder(new Label("No workouts are planned from today on."));
            if (before != null) {
                changedDates.addAll(findChangedDates(before, plan));
            }
            for (WorkoutDay day : plan.getDays()) {
                if (!day.getDate().isBefore(LocalDate.now())) {
                    for (PlannedExercise planned : day.getExercises()) {
                        dayOf.put(planned, day);
                        rows.add(planned);
                    }
                }
            }
        }
        planTable.setItems(FXCollections.observableArrayList(rows));
        planTable.refresh();
    }

    // the dates from today on whose workout is different in the new plan, removed workouts too
    private List<LocalDate> findChangedDates(WorkoutPlan before, WorkoutPlan after) {
        TreeMap<LocalDate, String> oldDays = new TreeMap<>();
        TreeMap<LocalDate, String> newDays = new TreeMap<>();
        for (WorkoutDay day : before.getDays()) {
            oldDays.put(day.getDate(), dayText(day));
        }
        for (WorkoutDay day : after.getDays()) {
            newDays.put(day.getDate(), dayText(day));
        }

        TreeSet<LocalDate> dates = new TreeSet<>(oldDays.keySet());
        dates.addAll(newDays.keySet());
        ArrayList<LocalDate> changed = new ArrayList<>();
        for (LocalDate date : dates) {
            if (!date.isBefore(LocalDate.now()) && !String.valueOf(oldDays.get(date)).equals(String.valueOf(newDays.get(date)))) {
                changed.add(date);
            }
        }
        return changed;
    }

    private String dayText(WorkoutDay day) {
        String text = day.getNote();
        for (PlannedExercise planned : day.getExercises()) {
            text += ";" + planned.getExercise().getName() + " " + planned.getSets() + " " + planned.getReps() + " " + planned.getWeightKg();
        }
        return text;
    }

    // a table column that shows text worked out from each planned exercise
    private TableColumn<PlannedExercise, String> column(String name, double width, Function<PlannedExercise, String> text) {
        TableColumn<PlannedExercise, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return column;
    }

    // the date goes on the first exercise of each day, with a star when the day was changed
    private String dateText(PlannedExercise planned) {
        if (!firstOfDay(planned)) {
            return "";
        }
        LocalDate date = dayOf.get(planned).getDate();
        if (changedDates.contains(date)) {
            return "★ " + dayLabel(date);
        }
        return dayLabel(date);
    }

    private boolean firstOfDay(PlannedExercise planned) {
        WorkoutDay day = dayOf.get(planned);
        return day != null && day.getExercises().get(0) == planned;
    }

    // F06: like "22.5 kg ↑", with an arrow when it is heavier or lighter than the last time it was lifted
    private String weightText(PlannedExercise planned) {
        if (planned.getWeightKg() == 0) {
            return "bodyweight";
        }
        String text = planned.getWeightKg() + " kg";
        TreeMap<LocalDate, Double> progress = new TreeMap<>(app.getProgress(planned.getExercise().getName()));
        if (!progress.isEmpty()) {
            double last = progress.lastEntry().getValue();
            if (planned.getWeightKg() > last) {
                text += "  ↑";
            } else if (planned.getWeightKg() < last) {
                text += "  ↓";
            }
        }
        return text;
    }

    private String noteText(PlannedExercise planned) {
        String text = "";
        if (planned.getNote() != null) {
            text = planned.getNote();
        }
        if (planned.getStuckCount() > 0) {
            if (!text.isEmpty()) {
                text += "; ";
            }
            text += "not heavier for " + planned.getStuckCount() + (planned.getStuckCount() == 1 ? " workout" : " workouts");
        }
        return text;
    }

    // ----- Workout tab (F05, F07) -----

    private Node buildWorkoutTab() {
        workoutTitle = title("No workout is going on");
        startButton = new Button("Start Workout");
        startButton.setOnAction(event -> startWorkout());
        finishButton = new Button("Finish Workout");
        finishButton.setOnAction(event -> finishWorkout());
        workoutMessage = new Label();
        workoutMessage.setWrapText(true);

        exercisesList = new ListView<>();
        exercisesList.setPrefWidth(430);
        exercisesList.setPrefHeight(330);
        VBox exercisesBox = new VBox(6, new Label("The exercises:"), exercisesList);

        currentLabel = new Label();
        currentLabel.setWrapText(true);
        currentLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        exerciseBox = new ComboBox<>();
        for (Exercise exercise : ExerciseLibrary.getInstance().getExercises()) {
            exerciseBox.getItems().add(exercise.getName());
        }
        exerciseBox.getItems().sort(null);
        repsInput = new TextField();
        repsInput.setPrefWidth(80);
        weightInput = new TextField();
        weightInput.setPrefWidth(80);
        difficultySlider = slider(1, 10, 7, 1, 0);
        difficultySlider.setPrefWidth(260);
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Exercise"), exerciseBox);
        form.addRow(1, new Label("Reps"), repsInput);
        form.addRow(2, new Label("Weight (kg)"), weightInput);
        form.addRow(3, new Label("Difficulty"), difficultySlider);

        Button logButton = new Button("Log Set");
        logButton.setOnAction(event -> logSet());
        Button skipButton = new Button("Skip Exercise");
        skipButton.setOnAction(event -> skipExercise());
        Button swapButton = new Button("Swap Exercise");
        swapButton.setOnAction(event -> swapCurrent());
        restLabel = new Label();
        restLabel.setStyle("-fx-text-fill: #1a5fb4;");
        setsList = new ListView<>();
        setsList.setPrefHeight(150);
        setPanel = new VBox(10, currentLabel, form, new HBox(8, logButton, skipButton, swapButton), restLabel,
                new Label("Sets logged so far:"), setsList);
        HBox.setHgrow(setPanel, Priority.ALWAYS);

        summaryArea = new TextArea();
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        VBox.setVgrow(summaryArea, Priority.ALWAYS); // the summary gets the rest of the page
        showSummary("");

        HBox middle = new HBox(16, exercisesBox, setPanel);
        VBox page = new VBox(10, workoutTitle, new HBox(8, startButton, finishButton), workoutMessage, middle, summaryArea);
        page.setPadding(new Insets(16));
        return page;
    }

    // F07, then F05: the daily check-in comes first (unless the user skips it), and then the workout starts
    private void startWorkout() {
        showSummary("");
        if (app.getCurrentLog() != null) {
            beginWorkout();
            return;
        }
        Optional<CheckIn> checkIn = showCheckInDialog();
        if (checkIn.isEmpty()) {
            beginWorkout();
            return;
        }

        WorkoutDay before = null;
        if (app.getCurrentPlan() != null) {
            before = app.getCurrentPlan().getTodaysWorkout();
        }
        WorkoutDay normalDay = before;
        String score = "Your check-in score is " + checkIn.get().calculateScore() + " out of 100.";
        runInBackground("Checking today's workout...", () -> app.submitDailyCheckIn(checkIn.get()), today -> {
            String text = score;
            if (today != null && today != normalDay) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Today's Workout");
                alert.setHeaderText("Your workout was changed for how you feel today");
                alert.setContentText(describeDay(today));
                ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE);
                ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(accept, reject);
                Optional<ButtonType> answer = alert.showAndWait();
                if (answer.isPresent() && answer.get() == accept) {
                    text += " Today's workout was changed.";
                } else {
                    app.undoChange(); // UC07 6a: back to the normal workout
                    text += " You chose the normal workout.";
                }
            } else if (today != null) {
                text += " That is a normal day, so the workout stays the same."; // UC07 3a
            }
            beginWorkout();
            workoutMessage.setText(text + " " + workoutMessage.getText());
        });
    }

    private void beginWorkout() {
        WorkoutDay day = app.startWorkout();
        WorkoutLog log = app.getCurrentLog();
        if (log.getDate().isBefore(LocalDate.now())) {
            workoutMessage.setText("You still have a workout from " + log.getDate() + ". Log the rest of it, or press Finish Workout.");
        } else if (day == null) {
            workoutMessage.setText("There are no more workouts in your plan, so this is a free workout. Choose the exercise for each set.");
        } else if (!log.getSets().isEmpty()) {
            workoutMessage.setText("Going on with your workout. " + log.getSets().size() + " sets were logged before.");
        } else if (!day.getDate().equals(LocalDate.now())) {
            workoutMessage.setText("No workout is planned today, so your next workout starts early."); // UC05 3a
        } else {
            workoutMessage.setText("");
        }
        prefilled = null;
        refreshAll();
    }

    // F07: the check-in window with sliders and a comment box. Submit gives the check-in, and Skip gives nothing
    private Optional<CheckIn> showCheckInDialog() {
        Slider sleep = slider(0, 12, 7, 2, 3);
        Slider energy = slider(1, 5, 3, 1, 0);
        Slider stress = slider(1, 5, 3, 1, 0);
        Slider soreness = slider(1, 5, 3, 1, 0);
        TextArea comments = new TextArea();
        comments.setPrefRowCount(3);
        comments.setWrapText(true);
        comments.setPromptText("Anything else about how you feel (optional)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Hours of sleep"), sleep, valueLabel(sleep, "%.1f"));
        grid.addRow(1, new Label("Energy (1 = none, 5 = lots)"), energy, valueLabel(energy, "%.0f"));
        grid.addRow(2, new Label("Stress (1 = none, 5 = lots)"), stress, valueLabel(stress, "%.0f"));
        grid.addRow(3, new Label("Soreness (1 = none, 5 = lots)"), soreness, valueLabel(soreness, "%.0f"));
        grid.add(comments, 0, 4, 3, 1);

        Dialog<CheckIn> dialog = new Dialog<>();
        dialog.setTitle("Daily Check-in");
        dialog.setHeaderText("How do you feel today?");
        dialog.getDialogPane().setContent(grid);
        ButtonType submit = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        ButtonType skip = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, skip);
        dialog.setResultConverter(button -> {
            if (button != submit) {
                return null;
            }
            return new CheckIn(LocalDate.now(), sleep.getValue(), (int) energy.getValue(), (int) stress.getValue(),
                    (int) soreness.getValue(), comments.getText().trim());
        });
        return dialog.showAndWait();
    }

    private void logSet() {
        String exercise = exerciseBox.getValue();
        if (exercise == null) {
            workoutMessage.setText("Choose the exercise first.");
            return;
        }
        try {
            int reps = Integer.parseInt(repsInput.getText().trim());
            double weightKg = Double.parseDouble(weightInput.getText().trim());
            app.logSet(new SetLog(exercise, reps, weightKg, (int) difficultySlider.getValue()));
        } catch (NumberFormatException error) {
            workoutMessage.setText("The reps and the weight must be numbers. The set was not logged.");
            return;
        } catch (IllegalArgumentException | IllegalStateException error) {
            workoutMessage.setText(error.getMessage() + ". The set was not logged."); // UC05 4a
            return;
        }
        workoutMessage.setText("");
        startRestTimer();
        refreshWorkout();
    }

    private void skipExercise() {
        WorkoutLog log = app.getCurrentLog();
        PlannedExercise current = null;
        if (log != null && log.getDay() != null) {
            current = findCurrentExercise(log.getDay(), log);
        }
        if (current == null) {
            workoutMessage.setText("There is no planned exercise left to skip.");
            return;
        }
        app.skipExercise(current);
        workoutMessage.setText("Skipped " + current.getExercise().getName() + ". It does not count as a failed attempt.");
        refreshWorkout();
    }

    // F04 during a workout: the swap happens right away, and the sets already logged are kept (UC04 6b)
    private void swapCurrent() {
        WorkoutLog log = app.getCurrentLog();
        PlannedExercise current = null;
        if (log != null && log.getDay() != null) {
            current = findCurrentExercise(log.getDay(), log);
        }
        if (current == null) {
            workoutMessage.setText("There is no planned exercise left to swap.");
            return;
        }
        swapExercise(current, log.getDay(), workoutMessage);
    }

    // F05 and F06: saves the workout, and shows the total, any new records and the weights for next time
    private void finishWorkout() {
        if (restTimer != null) {
            restTimer.stop();
            restLabel.setText("");
        }
        runInBackground("Saving your workout and working out your next weights...", () -> app.finishWorkout(), log -> {
            workoutMessage.setText("Your workout is saved.");
            showSummary(workoutSummary(log));
            refreshAll();
        });
    }

    private String workoutSummary(WorkoutLog log) {
        String text = "Workout done: " + log.getSets().size() + " sets, " + log.getWeightLifted() + " kg lifted in total.\n";
        ArrayList<String> names = new ArrayList<>();
        for (SetLog set : log.getSets()) {
            if (!names.contains(set.getExerciseName())) {
                names.add(set.getExerciseName());
            }
        }
        for (String name : names) {
            // UC05 8: a record beats the heaviest weight from every earlier day
            double best = 0;
            for (SetLog set : log.getSets()) {
                if (set.getExerciseName().equals(name)) {
                    best = Math.max(best, set.getWeightKg());
                }
            }
            double earlierBest = -1;
            for (Map.Entry<LocalDate, Double> entry : app.getProgress(name).entrySet()) {
                if (entry.getKey().isBefore(log.getDate())) {
                    earlierBest = Math.max(earlierBest, entry.getValue());
                }
            }
            if (earlierBest >= 0 && best > earlierBest) {
                text += "New record: " + name + " at " + best + " kg (your best was " + earlierBest + " kg).\n";
            }
        }

        if (log.getDay() != null && app.getCurrentPlan() != null) {
            text += "\nYour next weights (they are on the Workout Plan page too):\n";
            ArrayList<String> shown = new ArrayList<>();
            for (WorkoutDay day : app.getCurrentPlan().getDays()) {
                if (day.getDate().isAfter(log.getDay().getDate())) {
                    for (PlannedExercise planned : day.getExercises()) {
                        if (!shown.contains(planned.getExercise().getName())) {
                            shown.add(planned.getExercise().getName());
                            String note = noteText(planned);
                            text += "- " + planned.getExercise().getName() + ": " + weightText(planned) + (note.isEmpty() ? "" : "  (" + note + ")") + "\n";
                        }
                    }
                }
            }
        }
        return text;
    }

    // shows the workout that is going on: its exercises, the next set to do, and the sets logged so far
    private void refreshWorkout() {
        WorkoutLog log = app.getCurrentLog();
        boolean going = log != null;
        startButton.setDisable(going);
        finishButton.setDisable(!going);
        setPanel.setDisable(!going);
        exercisesList.getItems().clear();
        setsList.getItems().clear();
        if (!going) {
            workoutTitle.setText("No workout is going on");
            currentLabel.setText("Press Start Workout when you are ready.");
            repsInput.clear();
            weightInput.clear();
            prefilled = null;
            return;
        }

        WorkoutDay day = log.getDay();
        if (day == null) {
            workoutTitle.setText("Free workout, " + dayLabel(log.getDate()));
        } else {
            workoutTitle.setText(dayLabel(day.getDate()) + ": " + day.getNote());
            for (PlannedExercise planned : day.getExercises()) {
                String state = countSets(log, planned.getExercise().getName()) + " of " + planned.getSets() + " sets done";
                if ("skipped".equals(planned.getNote())) {
                    state = "skipped";
                }
                exercisesList.getItems().add(planned.getExercise().getName() + ": " + planned.getSets() + " x " + planned.getReps()
                        + ", " + weightText(planned) + "  (" + state + ")");
            }
        }
        for (SetLog set : log.getSets()) {
            setsList.getItems().add(set.getExerciseName() + ": " + set.getReps() + " reps at " + set.getWeightKg() + " kg, difficulty " + set.getDifficulty());
        }

        PlannedExercise next = null;
        if (day != null) {
            next = findCurrentExercise(day, log);
        }
        if (next == null) {
            if (day == null) {
                currentLabel.setText("Choose the exercise and log your set.");
            } else {
                currentLabel.setText("Every exercise is done. Press Finish Workout, or log more sets.");
            }
            return;
        }
        String weight = "";
        if (next.getWeightKg() > 0) {
            weight = " at " + next.getWeightKg() + " kg";
        }
        int done = countSets(log, next.getExercise().getName());
        currentLabel.setText("Next: " + next.getExercise().getName() + ", set " + (done + 1) + " of " + next.getSets()
                + ", " + next.getReps() + " reps" + weight);
        if (next != prefilled) {
            // the planned numbers go in the boxes when a new exercise comes up, and what the user typed stays for the same one
            exerciseBox.setValue(next.getExercise().getName());
            repsInput.setText(String.valueOf(next.getReps()));
            weightInput.setText(String.valueOf(next.getWeightKg()));
            prefilled = next;
        }
    }

    // the first planned exercise that still has sets to do and was not skipped
    private PlannedExercise findCurrentExercise(WorkoutDay day, WorkoutLog log) {
        for (PlannedExercise planned : day.getExercises()) {
            if (!"skipped".equals(planned.getNote()) && countSets(log, planned.getExercise().getName()) < planned.getSets()) {
                return planned;
            }
        }
        return null;
    }

    private int countSets(WorkoutLog log, String exerciseName) {
        int count = 0;
        for (SetLog set : log.getSets()) {
            if (set.getExerciseName().equalsIgnoreCase(exerciseName)) {
                count++;
            }
        }
        return count;
    }

    // UC05 5: the rest timer counts down after each set
    private void startRestTimer() {
        if (restTimer != null) {
            restTimer.stop();
        }
        restSecondsLeft = REST_SECONDS;
        restLabel.setText("Rest: " + restSecondsLeft + " seconds");
        restTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            restSecondsLeft--;
            if (restSecondsLeft > 0) {
                restLabel.setText("Rest: " + restSecondsLeft + " seconds");
            } else {
                restLabel.setText("Rest is over. Time for the next set!");
            }
        }));
        restTimer.setCycleCount(REST_SECONDS);
        restTimer.play();
    }

    private void showSummary(String text) {
        summaryArea.setText(text);
        summaryArea.setVisible(!text.isEmpty());
        summaryArea.setManaged(!text.isEmpty());
    }

    // ----- shared helpers -----

    // runs slow work, like a call to the AI, on a background thread so the window does not freeze.
    // The tabs are locked until it is done, and then "done" gets the result on the JavaFX thread
    private <T> void runInBackground(String message, Callable<T> work, Consumer<T> done) {
        status.setText(message);
        busy.setVisible(true);
        tabs.setDisable(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            finishBackground();
            done.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finishBackground();
            Throwable error = task.getException();
            String text = error.getMessage();
            if (text == null) {
                text = error.toString();
            }
            showError(text);
        });
        Thread thread = new Thread(task);
        thread.setDaemon(true); // the thread does not keep the app running after the window is closed
        thread.start();
    }

    private void finishBackground() {
        status.setText("Ready.");
        busy.setVisible(false);
        tabs.setDisable(false);
    }

    private void refreshAll() {
        refreshPlan();
        refreshWorkout();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        return label;
    }

    private Slider slider(double min, double max, double value, double tickUnit, int minorTicks) {
        Slider slider = new Slider(min, max, value);
        slider.setMajorTickUnit(tickUnit);
        slider.setMinorTickCount(minorTicks);
        slider.setSnapToTicks(true);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setPrefWidth(240);
        return slider;
    }

    private Label valueLabel(Slider slider, String format) {
        Label label = new Label();
        label.textProperty().bind(slider.valueProperty().asString(format));
        return label;
    }

    // the number in a text field, or -1 when it is empty or not a number
    private double readNumber(TextField field) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private void highlight(Node field, boolean wrong) {
        if (wrong) {
            field.setStyle("-fx-border-color: #b00020; -fx-border-width: 2;");
        } else {
            field.setStyle("");
        }
    }

    // like "Tue 2026-11-03"
    private String dayLabel(LocalDate date) {
        String dayName = date.getDayOfWeek().toString();
        return dayName.charAt(0) + dayName.substring(1, 3).toLowerCase() + " " + date;
    }

    private WorkoutDay findDay(WorkoutPlan plan, LocalDate date) {
        for (WorkoutDay day : plan.getDays()) {
            if (day.getDate().equals(date)) {
                return day;
            }
        }
        return null;
    }

    // a day as a few lines of text, for the check-in window
    private String describeDay(WorkoutDay day) {
        String text = day.getNote() + "\n";
        for (PlannedExercise planned : day.getExercises()) {
            text += "\n" + planned.getExercise().getName() + ": " + planned.getSets() + " x " + planned.getReps();
            if (planned.getWeightKg() > 0) {
                text += " at " + planned.getWeightKg() + " kg";
            }
        }
        return text;
    }
}
