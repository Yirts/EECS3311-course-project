package fitnesscoach.ui;

import java.io.File;
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
import fitnesscoach.history.WorkoutObserver;
import fitnesscoach.plan.Exercise;
import fitnesscoach.plan.ExerciseLibrary;
import fitnesscoach.plan.PlannedExercise;
import fitnesscoach.plan.WorkoutDay;
import fitnesscoach.plan.WorkoutPlan;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The JavaFX window, with one tab for each page (the View in MVC). Each button calls the facade method with the same name.
 * The sidebar on the left opens the pages, and style.css gives the window its look.
 * Calls to the AI run on a background thread, so the window does not freeze while the AI is thinking.
 * Observer pattern: the window is an observer of the workout history, so the dashboard refreshes after every workout.
 */
public class Main extends Application implements WorkoutObserver {
    private static final int REST_SECONDS = 90;
    private static final String LEAVE_OUT = "(leave it out)";

    // the sidebar icons, simple shapes drawn on a 20 x 20 grid (SVG path format), in the same order as the pages
    private static final String[] ICONS = {
        "M10 2.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z M3 18a7 6 0 0 1 14 0z",
        "M4 3h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M4 8v8h12V8z M6 10h3v2H6z M11 10h3v2h-3z M6 13h3v2H6z",
        "M1 8h2v4H1z M3 6h3v8H3z M6 9h8v2H6z M14 6h3v8h-3z M17 8h2v4h-2z",
        "M3 17v-6h3v6z M8.5 17V5h3v12z M14 17V8h3v9z M2 18h16v1.2H2z",
        "M4 3h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 3v-3H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M6 8h2v2H6z M9 8h2v2H9z M12 8h2v2h-2z",
        "M10 6c-2-1.5-6-1-6 4 0 4 3 8 6 7 3 1 6-3 6-7 0-5-4-5.5-6-4z M10 5c0-2 1.5-3.5 3.5-3.5 0 2-1.5 3.5-3.5 3.5z",
        "M5 2h7l4 4v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z M12 3v3h3z M7 9h6v1.5H7z M7 12h6v1.5H7z M7 15h4v1.5H7z",
        "M9 2h2v8h3l-4 4-4-4h3z M3 13h2v3h10v-3h2v5H3z"
    };

    private FitnessAppFacade app;
    private TabPane tabs;
    private String stylesheet; // style.css, the look of the window and its dialogs
    private ArrayList<Button> navButtons = new ArrayList<>(); // the sidebar, one button for each page
    private StackPane busyOverlay; // covers the page while the AI is thinking
    private Label status;
    private ProgressIndicator busy;
    private Label toast; // a short message that pops up after an action
    private SequentialTransition toastAnimation;

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
    private Label progressLabel;
    private ProgressBar workoutProgress; // the sets done out of the sets planned
    private HBox workoutPanels; // the exercises and the set panel
    private VBox setPanel;
    private Label currentLabel;
    private ComboBox<String> exerciseBox;
    private TextField repsInput;
    private TextField weightInput;
    private Slider difficultySlider;
    private Label restLabel;
    private ProgressBar restBar; // the rest time that is left
    private ListView<String> setsList;
    private TextArea summaryArea;
    private PlannedExercise prefilled; // the exercise whose planned numbers are in the boxes
    private Timeline restTimer;
    private int restSecondsLeft;

    // Progress Dashboard tab (F08)
    private ComboBox<String> chartExerciseBox;
    private ChoiceBox<String> rangeBox;
    private LineChart<String, Number> strengthChart;
    private BarChart<String, Number> muscleChart;
    private LineChart<String, Number> bodyWeightChart;
    private Label strengthNote;
    private Label muscleNote;
    private Label bodyWeightNote;
    private ListView<String> recordsList;
    private TextField bodyWeightField;
    private Label dashboardMessage;
    private Label[] tileValues = new Label[4]; // the four numbers at the top of the dashboard
    private Label[] tileDetails = new Label[4];
    private boolean refreshingDashboard; // so filling the exercise box does not start another refresh

    // Coach Chatbox tab (F09)
    private VBox chatMessages; // the chat bubbles
    private ScrollPane chatScroll;
    private TextField questionField;

    // Nutrition tab (F10)
    private Label caloriesLabel;
    private VBox macroBox; // a bar for each of protein, carbs and fat
    private Label targetsLabel;
    private TextField likesField;
    private TextField dislikesField;
    private TextField allergiesField;
    private VBox ideasBox;
    private Label nutritionMessage;

    // Report tab (F11)
    private TextArea reportArea;
    private VBox suggestionsBox;
    private Button saveReportButton;
    private Label reportMessage;
    private String report;

    // Import tab (F12)
    private TextArea previewArea;
    private ScrollPane matchesPane;
    private VBox matchesBox;
    private HashMap<String, ComboBox<String>> matchBoxes = new HashMap<>();
    private HBox importButtons;
    private Label importMessage;

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
        stylesheet = getClass().getResource("/style.css").toExternalForm();

        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("pages"); // the tab headers are hidden, because the sidebar opens the pages
        tabs.getTabs().add(new Tab("Profile", buildProfileTab()));
        tabs.getTabs().add(new Tab("Workout Plan", buildPlanTab()));
        tabs.getTabs().add(new Tab("Workout", buildWorkoutTab()));
        tabs.getTabs().add(new Tab("Progress Dashboard", buildDashboardTab()));
        tabs.getTabs().add(new Tab("Coach Chatbox", buildChatTab()));
        tabs.getTabs().add(new Tab("Nutrition", buildNutritionTab()));
        tabs.getTabs().add(new Tab("Report", buildReportTab()));
        tabs.getTabs().add(new Tab("Import", buildImportTab()));
        app.addObserver(this); // the dashboard is told about every finished or imported workout
        refreshAll();
        refreshDashboard();

        // while the AI is thinking, a card with a spinner covers the page
        status = new Label();
        status.getStyleClass().add("busy-message");
        busy = new ProgressIndicator();
        busy.setPrefSize(44, 44);
        VBox busyCard = new VBox(14, busy, status);
        busyCard.setAlignment(Pos.CENTER);
        busyCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        busyCard.getStyleClass().add("busy-card");
        busyOverlay = new StackPane(busyCard);
        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);

        toast = new Label();
        toast.getStyleClass().add("toast");
        toast.setVisible(false);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.TOP_RIGHT); // the top corner of a page is empty, so the message covers nothing
        StackPane.setMargin(toast, new Insets(16, 16, 0, 0));

        BorderPane root = new BorderPane(new StackPane(tabs, busyOverlay, toast));
        root.setLeft(buildSidebar());
        Scene scene = new Scene(root, 1280, 780);
        scene.getStylesheets().add(stylesheet);
        stage.setTitle("AI Fitness Plan Coach");
        stage.getIcons().add(makeIcon());
        stage.setScene(scene);
        stage.show();
    }

    // the sidebar: the app's name, a button with an icon for each page, and today's date
    private Node buildSidebar() {
        Label logo = new Label("AI");
        logo.getStyleClass().add("app-logo");
        Label name = new Label("Fitness Plan Coach");
        name.getStyleClass().add("brand-name");
        Label tagline = new Label("Your AI training partner");
        tagline.getStyleClass().add("brand-tagline");
        HBox brand = new HBox(10, logo, new VBox(1, name, tagline));
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox nav = new VBox(4);
        for (int i = 0; i < tabs.getTabs().size(); i++) {
            int page = i;
            SVGPath icon = new SVGPath();
            icon.setContent(ICONS[i]);
            icon.setFillRule(FillRule.EVEN_ODD); // shapes inside other shapes are holes, like the lines on the page icon
            icon.getStyleClass().add("nav-icon");
            StackPane iconBox = new StackPane(icon);
            iconBox.setMinWidth(20); // every icon takes the same space, so the page names line up
            iconBox.setPrefWidth(20);
            Button button = new Button(tabs.getTabs().get(i).getText(), iconBox);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(event -> tabs.getSelectionModel().select(page));
            navButtons.add(button);
            nav.getChildren().add(button);
        }
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, oldPage, newPage) -> showPage(newPage.intValue()));
        showPage(tabs.getSelectionModel().getSelectedIndex());

        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        Label today = new Label("Today is " + dayLabel(LocalDate.now()));
        today.getStyleClass().add("sidebar-footer");

        VBox sidebar = new VBox(28, brand, nav, space, today);
        sidebar.setMinWidth(220);
        sidebar.setPrefWidth(220);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    // marks the page's button in the sidebar, and fades the page in
    private void showPage(int page) {
        for (Button button : navButtons) {
            button.getStyleClass().remove("selected");
        }
        navButtons.get(page).getStyleClass().add("selected");
        FadeTransition fade = new FadeTransition(Duration.millis(200), tabs.getTabs().get(page).getContent());
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // the icon in the title bar and the taskbar: "AI" on a blue square
    private Image makeIcon() {
        Canvas canvas = new Canvas(64, 64);
        GraphicsContext pen = canvas.getGraphicsContext2D();
        pen.setFill(Color.web("#2f6fed"));
        pen.fillRoundRect(0, 0, 64, 64, 20, 20);
        pen.setFill(Color.WHITE);
        pen.setFont(Font.font("System", FontWeight.BOLD, 28));
        pen.setTextAlign(TextAlignment.CENTER);
        pen.setTextBaseline(VPos.CENTER);
        pen.fillText("AI", 32, 33);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT); // round corners
        return canvas.snapshot(parameters, null);
    }

    // a short message that pops up at the top of the page and fades away
    private void showToast(String text) {
        if (toastAnimation != null) {
            toastAnimation.stop();
        }
        toast.setText("✓  " + text);
        toast.setVisible(true);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.seconds(2.5));
        toastAnimation = new SequentialTransition(fadeIn, fadeOut);
        toastAnimation.setOnFinished(event -> toast.setVisible(false));
        toastAnimation.play();
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
        saveButton.getStyleClass().add("primary");

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
        notesLabel.getStyleClass().add("hint");
        VBox left = new VBox(10, title("Your information"), form, notesLabel, notesArea, saveButton);
        left.setMinWidth(430);
        left.setMaxWidth(430);
        left.getStyleClass().add("card");

        profileMessage = new Label("Fill in your information and press Save Profile. The AI turns your notes into boundaries for you to check.");
        profileMessage.setWrapText(true);
        doctorWarning = new Label();
        doctorWarning.setWrapText(true);
        doctorWarning.getStyleClass().add("warning");
        questionsBox = new VBox(6);
        boundariesArea = new TextArea();
        boundariesArea.setWrapText(true);
        boundariesArea.setPrefRowCount(12);
        boundariesArea.setEditable(false);
        confirmButton = new Button("Confirm");
        confirmButton.getStyleClass().add("primary");
        confirmButton.setDisable(true);
        confirmButton.setOnAction(event -> confirmBoundaries());
        VBox right = new VBox(10, title("Your boundaries"), profileMessage, doctorWarning, questionsBox, boundariesArea, confirmButton);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.getStyleClass().add("card");

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
        showAllergies();
        showTargets(); // the targets on the Nutrition page come from the profile
        refreshDashboard(); // the body weight chart has the new weight
        showToast("Profile saved");
    }

    // ----- Workout Plan tab (F02, F03, F04, F06) -----

    private Node buildPlanTab() {
        weeksSpinner = new Spinner<>(1, FitnessAppFacade.MAX_WEEKS, 4);
        weeksSpinner.setPrefWidth(70);
        Button generateButton = new Button("Generate Plan");
        generateButton.setOnAction(event -> generatePlan());
        generateButton.getStyleClass().add("primary");
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
        keepButton.getStyleClass().add("primary");
        dropButton = new Button("Undo");
        dropButton.setOnAction(event -> dropPending());
        Button regenerateButton = new Button("Regenerate");
        regenerateButton.setOnAction(event -> regenerate.run());
        for (Button button : new Button[] {keepButton, dropButton, regenerateButton}) {
            button.setMinWidth(Region.USE_PREF_SIZE); // a long message wraps instead of squeezing the buttons
        }
        pendingBar = new HBox(8, pendingLabel, keepButton, dropButton, regenerateButton);
        pendingBar.setAlignment(Pos.CENTER_LEFT);
        pendingBar.getStyleClass().add("pending-bar");

        planTable = new TableView<>();
        planTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // the rows of a changed workout are yellow, like the bar above the table
        planTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(PlannedExercise planned, boolean empty) {
                super.updateItem(planned, empty);
                getStyleClass().remove("changed-day");
                WorkoutDay day = empty ? null : dayOf.get(planned);
                if (day != null && changedDates.contains(day.getDate())) {
                    getStyleClass().add("changed-day");
                }
            }
        });
        planTable.getColumns().add(column("Date", 145, planned -> dateText(planned)));
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
        Label rowHint = new Label("(select an exercise in the table first)");
        rowHint.getStyleClass().add("hint");
        rowButtons = new HBox(8, swapButton, overrideButton, rowHint);
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
        changePlan(request, "The changed workouts are marked with a star. Confirm or undo the change, "
                + "or edit your request and regenerate.", this::requestChange);
    }

    // asks the AI to change the plan, and shows the change on this page, where it waits for Confirm or Undo.
    // The weekly report's suggestions use it too (F11)
    private void changePlan(String request, String text, Runnable again) {
        tabs.getSelectionModel().select(1); // the Workout Plan page
        runInBackground("The AI is changing your plan...", () -> app.requestChange(request), plan -> {
            if (plan == null) {
                planMessage.setText("The AI is offline, so your plan can't be changed right now.");
                return;
            }
            if (findChangedDates(app.getCurrentPlan(), plan).isEmpty()) {
                // UC03 2a: the request was not clear enough to change anything
                // after Regenerate, the change that was already waiting stays, so Undo still drops only that change
                if (pendingPlan == null) {
                    app.undoChange();
                }
                planMessage.setText("The AI did not change anything for \"" + request + "\". Try saying which days or "
                        + "exercises to change, like \"only 30 minutes on tuesday\" or \"no bench next week\".");
                return;
            }
            pendingIsNewPlan = false;
            showPending(plan, text, "Confirm", "Undo", again);
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
            style(alert);
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
        showToast("Plan saved");
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
            showToast("Change undone");
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
        style(reasonDialog);
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
            style(otherDialog);
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
        box.setPrefWidth(640); // wide enough for each reason on one line
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
        style(dialog);
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
        showToast("Exercise swapped");
    }

    // F06: the user's own weight for the selected exercise, from its day on
    private void overrideSelected() {
        PlannedExercise exercise = planTable.getSelectionModel().getSelectedItem();
        if (exercise == null) {
            planMessage.setText("Select an exercise in the table first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(String.valueOf(exercise.getWeightKg()));
        style(dialog);
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
            showToast("Weight saved");
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
        startButton.getStyleClass().add("primary");
        finishButton = new Button("Finish Workout");
        finishButton.setOnAction(event -> finishWorkout());
        workoutMessage = new Label();
        workoutMessage.setWrapText(true);

        exercisesList = new ListView<>();
        exercisesList.setPrefWidth(430);
        exercisesList.setPrefHeight(330);
        VBox.setVgrow(exercisesList, Priority.ALWAYS);
        progressLabel = new Label();
        progressLabel.getStyleClass().add("hint");
        workoutProgress = new ProgressBar(0);
        workoutProgress.setMaxWidth(Double.MAX_VALUE);
        VBox exercisesBox = new VBox(8, new Label("The exercises:"), exercisesList, progressLabel, workoutProgress);
        exercisesBox.getStyleClass().add("card");

        currentLabel = new Label();
        currentLabel.setWrapText(true);
        currentLabel.getStyleClass().add("current-set");
        exerciseBox = new ComboBox<>();
        for (Exercise exercise : ExerciseLibrary.getInstance().getExercises()) {
            exerciseBox.getItems().add(exercise.getName());
        }
        exerciseBox.getItems().sort(null);
        repsInput = new TextField();
        repsInput.setPrefWidth(80);
        repsInput.setOnAction(event -> logSet()); // Enter logs the set
        weightInput = new TextField();
        weightInput.setPrefWidth(80);
        weightInput.setOnAction(event -> logSet());
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
        logButton.getStyleClass().add("primary");
        Button skipButton = new Button("Skip Exercise");
        skipButton.setOnAction(event -> skipExercise());
        Button swapButton = new Button("Swap Exercise");
        swapButton.setOnAction(event -> swapCurrent());
        restLabel = new Label();
        restLabel.getStyleClass().add("rest-timer");
        restBar = new ProgressBar(0);
        restBar.setPrefWidth(220);
        restBar.getStyleClass().add("rest-bar");
        restBar.setVisible(false);
        HBox restRow = new HBox(12, restLabel, restBar);
        restRow.setAlignment(Pos.CENTER_LEFT);
        setsList = new ListView<>();
        setsList.setPrefHeight(150);
        setPanel = new VBox(10, currentLabel, form, new HBox(8, logButton, skipButton, swapButton), restRow,
                new Label("Sets logged so far:"), setsList);
        HBox.setHgrow(setPanel, Priority.ALWAYS);
        setPanel.getStyleClass().add("card");
        workoutPanels = new HBox(16, exercisesBox, setPanel);

        summaryArea = new TextArea();
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        VBox.setVgrow(summaryArea, Priority.ALWAYS); // the summary gets the rest of the page
        showSummary("");

        VBox page = new VBox(10, workoutTitle, new HBox(8, startButton, finishButton), workoutMessage, workoutPanels, summaryArea);
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
                style(alert);
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
        style(dialog);
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
        showToast("Set logged");
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
            restBar.setVisible(false);
        }
        runInBackground("Saving your workout and working out your next weights...", () -> app.finishWorkout(), log -> {
            workoutMessage.setText("Your workout is saved.");
            showSummary(workoutSummary(log));
            refreshAll();
            showToast("Workout saved");
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
            progressLabel.setText("");
            workoutProgress.setProgress(0);
            prefilled = null;
            return;
        }

        WorkoutDay day = log.getDay();
        if (day == null) {
            workoutTitle.setText("Free workout, " + dayLabel(log.getDate()));
            progressLabel.setText(log.getSets().size() + (log.getSets().size() == 1 ? " set" : " sets") + " logged");
            workoutProgress.setProgress(0);
        } else {
            workoutTitle.setText(dayLabel(day.getDate()) + ": " + day.getNote());
            int setsPlanned = 0;
            int setsDone = 0;
            for (PlannedExercise planned : day.getExercises()) {
                int done = countSets(log, planned.getExercise().getName());
                String state = done + " of " + planned.getSets() + " sets done";
                String mark = done >= planned.getSets() ? "✓  " : ""; // a tick on a finished exercise
                if ("skipped".equals(planned.getNote())) {
                    state = "skipped";
                    mark = "";
                } else {
                    setsPlanned += planned.getSets();
                    setsDone += Math.min(done, planned.getSets());
                }
                exercisesList.getItems().add(mark + planned.getExercise().getName() + ": " + planned.getSets() + " x " + planned.getReps()
                        + ", " + weightText(planned) + "  (" + state + ")");
            }
            progressLabel.setText(setsDone + " of " + setsPlanned + " sets done");
            workoutProgress.setProgress(setsPlanned == 0 ? 0 : (double) setsDone / setsPlanned);
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
        restBar.setProgress(1);
        restBar.setVisible(true);
        restTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            restSecondsLeft--;
            restBar.setProgress((double) restSecondsLeft / REST_SECONDS); // the bar gets shorter as the rest runs out
            if (restSecondsLeft > 0) {
                restLabel.setText("Rest: " + restSecondsLeft + " seconds");
            } else {
                restLabel.setText("Rest is over. Time for the next set!");
                restBar.setVisible(false);
            }
        }));
        restTimer.setCycleCount(REST_SECONDS);
        restTimer.play();
    }

    // the summary takes the place of the exercises and the set panel, which are empty after a workout
    private void showSummary(String text) {
        summaryArea.setText(text);
        summaryArea.setVisible(!text.isEmpty());
        summaryArea.setManaged(!text.isEmpty());
        workoutPanels.setVisible(text.isEmpty());
        workoutPanels.setManaged(text.isEmpty());
    }

    // ----- Progress Dashboard tab (F08) -----

    private Node buildDashboardTab() {
        chartExerciseBox = new ComboBox<>();
        chartExerciseBox.setPrefWidth(200);
        rangeBox = new ChoiceBox<>(FXCollections.observableArrayList("4 weeks", "12 weeks", "6 months", "1 year", "All time"));
        rangeBox.setValue("12 weeks");
        bodyWeightField = new TextField();
        bodyWeightField.setPrefWidth(70);
        bodyWeightField.setOnAction(event -> addBodyWeight());
        Button bodyWeightButton = new Button("Add Today's Weight");
        bodyWeightButton.setOnAction(event -> addBodyWeight());
        HBox controls = new HBox(8, new Label("Exercise"), chartExerciseBox, new Label("Range"), rangeBox,
                new Label("     Body weight (kg)"), bodyWeightField, bodyWeightButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        dashboardMessage = new Label();
        dashboardMessage.setWrapText(true);
        dashboardMessage.managedProperty().bind(dashboardMessage.textProperty().isNotEmpty()); // no empty gap without a message
        HBox tiles = new HBox(16, tile(0, "Sets"), tile(1, "Most trained"), tile(2, "Personal records"), tile(3, "Body weight"));

        strengthChart = lineChart("Strength: the heaviest set of each workout (kg)");
        muscleChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        muscleChart.setTitle("Sets for each muscle");
        muscleChart.setLegendVisible(false);
        muscleChart.setAnimated(false);
        bodyWeightChart = lineChart("Body weight (kg)");
        bodyWeightChart.getStyleClass().add("body-weight-chart"); // a different colour from the strength chart
        strengthNote = new Label();
        muscleNote = new Label();
        bodyWeightNote = new Label();
        recordsList = new ListView<>();
        recordsList.setPlaceholder(new Label("No records yet."));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(withNote(strengthChart, strengthNote), 0, 0);
        grid.add(withNote(bodyWeightChart, bodyWeightNote), 1, 0);
        grid.add(withNote(muscleChart, muscleNote), 0, 1);
        grid.add(withNote(recordsList, new Label("Personal records: the heaviest weight for each exercise.")), 1, 1);
        // the left column is wider, so the muscle names fit under the bars
        ColumnConstraints wide = new ColumnConstraints();
        wide.setPercentWidth(60);
        ColumnConstraints narrow = new ColumnConstraints();
        narrow.setPercentWidth(40);
        grid.getColumnConstraints().addAll(wide, narrow);
        for (int i = 0; i < 2; i++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(50);
            grid.getRowConstraints().add(row);
        }
        VBox.setVgrow(grid, Priority.ALWAYS);

        // added last, so the boxes do not refresh the charts before the charts are made
        chartExerciseBox.setOnAction(event -> refreshDashboard());
        rangeBox.setOnAction(event -> refreshDashboard());

        VBox page = new VBox(12, title("Your progress"), controls, dashboardMessage, tiles, grid);
        page.setPadding(new Insets(16));
        return page;
    }

    // a small card at the top of the dashboard, with a caption, a big number and a detail under it
    private VBox tile(int index, String caption) {
        Label name = new Label(caption);
        name.getStyleClass().add("tile-caption");
        tileValues[index] = new Label("-");
        tileValues[index].getStyleClass().add("tile-value");
        tileDetails[index] = new Label();
        tileDetails[index].getStyleClass().add("tile-detail");
        VBox tile = new VBox(2, name, tileValues[index], tileDetails[index]);
        tile.getStyleClass().addAll("card", "tile");
        tile.setPrefWidth(200); // the same width for each, so the four tiles share the row equally
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private void setTile(int index, String value, String detail) {
        tileValues[index].setText(value);
        tileDetails[index].setText(detail);
    }

    // a small box with the exact numbers, shown when the mouse is over a point or a bar
    private void tip(Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(100));
        Tooltip.install(node, tooltip);
    }

    private LineChart<String, Number> lineChart(String title) {
        NumberAxis weightAxis = new NumberAxis();
        weightAxis.setForceZeroInRange(false); // so small changes can be seen
        LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), weightAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        return chart;
    }

    // a card with a chart (or the records list) and a short note under it
    private VBox withNote(Node chart, Label note) {
        note.setWrapText(true);
        note.getStyleClass().add("note");
        VBox.setVgrow(chart, Priority.ALWAYS);
        VBox card = new VBox(4, chart, note);
        card.getStyleClass().add("card");
        return card;
    }

    // Observer pattern: the workout history calls this for each finished or imported workout. The call can come from
    // a background thread, so the dashboard is refreshed on the JavaFX thread
    @Override
    public void onWorkoutLogged(WorkoutLog log) {
        Platform.runLater(this::refreshDashboard);
    }

    // fills the charts for the chosen exercise and range
    private void refreshDashboard() {
        if (refreshingDashboard) {
            return;
        }
        int days = rangeDays();
        LocalDate from = LocalDate.now().minusDays(days);
        Map<String, Double> records = app.getPersonalRecords();

        // the exercise box lists the exercises lifted with a weight, and keeps the one that was chosen
        String chosen = chartExerciseBox.getValue();
        if (chosen == null || !records.containsKey(chosen)) {
            chosen = null;
            if (!records.isEmpty()) {
                chosen = records.keySet().iterator().next();
            }
        }
        refreshingDashboard = true; // changing the box calls refreshDashboard() again
        chartExerciseBox.getItems().setAll(records.keySet());
        chartExerciseBox.setValue(chosen);
        refreshingDashboard = false;

        int allSets = 0;
        for (int sets : app.getMuscleSets(36500).values()) {
            allSets += sets;
        }
        if (allSets == 0) {
            // UC08 2a
            dashboardMessage.setText("No workouts yet. Do one on the Workout page, or import your history on the Import page.");
        } else {
            dashboardMessage.setText("");
        }

        TreeMap<LocalDate, Double> points = new TreeMap<>();
        if (chosen != null) {
            for (Map.Entry<LocalDate, Double> entry : app.getProgress(chosen).entrySet()) {
                if (entry.getKey().isAfter(from)) {
                    points.put(entry.getKey(), entry.getValue());
                }
            }
        }
        String typos = drawLine(strengthChart, points);
        if (chosen == null) {
            strengthNote.setText("Lift a weight in a workout to see your strength here.");
        } else if (points.size() < 2) {
            strengthNote.setText("Not enough data yet. " + chosen + " needs 2 workouts in this range to show a line."); // UC08 3a
        } else {
            strengthNote.setText(typos);
        }

        showMuscleSets(app.getMuscleSets(days));

        TreeMap<LocalDate, Double> weights = new TreeMap<>();
        if (app.getProfile() != null) {
            for (Map.Entry<LocalDate, Double> entry : app.getProfile().getWeightHistory().entrySet()) {
                if (entry.getKey().isAfter(from)) {
                    weights.put(entry.getKey(), entry.getValue());
                }
            }
        }
        typos = drawLine(bodyWeightChart, weights);
        if (weights.isEmpty()) {
            bodyWeightNote.setText("No body weights in this range. Add today's weight above.");
            setTile(3, "-", "add today's weight above");
        } else {
            bodyWeightNote.setText(typos);
            double change = weights.lastEntry().getValue() - weights.firstEntry().getValue();
            setTile(3, weights.lastEntry().getValue() + " kg", String.format("%+.1f kg since %s", change, weights.firstKey()));
        }

        recordsList.getItems().clear();
        String best = "nothing lifted with a weight yet";
        double heaviest = 0;
        for (Map.Entry<String, Double> record : records.entrySet()) {
            recordsList.getItems().add(record.getKey() + ": " + record.getValue() + " kg");
            if (record.getValue() > heaviest) {
                heaviest = record.getValue();
                best = "heaviest: " + record.getKey() + ", " + record.getValue() + " kg";
            }
        }
        setTile(2, String.valueOf(records.size()), best);
    }

    private int rangeDays() {
        switch (rangeBox.getValue()) {
            case "4 weeks":
                return 28;
            case "6 months":
                return 182;
            case "1 year":
                return 365;
            case "All time":
                return 36500;
            default:
                return 84; // 12 weeks
        }
    }

    // puts one point on the chart for each date. A point much bigger than the one before is red, because it could be
    // a typo (UC08 3b), and the returned text says which ones
    private String drawLine(LineChart<String, Number> chart, Map<LocalDate, Double> points) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        ArrayList<XYChart.Data<String, Number>> typos = new ArrayList<>();
        String text = "";
        Double previous = null;
        for (Map.Entry<LocalDate, Double> point : points.entrySet()) {
            XYChart.Data<String, Number> data = new XYChart.Data<>(point.getKey().toString(), point.getValue());
            series.getData().add(data);
            if (previous != null && point.getValue() > previous * 1.5 && point.getValue() - previous >= 20) {
                typos.add(data);
                text += point.getKey() + " (" + point.getValue() + " kg) is much more than before. Is it a typo? ";
            }
            previous = point.getValue();
        }
        chart.getData().clear();
        chart.getData().add(series);
        // the points are made when the series is added
        for (XYChart.Data<String, Number> point : series.getData()) {
            tip(point.getNode(), point.getXValue() + ": " + point.getYValue() + " kg");
        }
        for (XYChart.Data<String, Number> typo : typos) {
            typo.getNode().getStyleClass().add("typo-point");
        }
        return text.trim();
    }

    // the bars go A to Z, and the note and the tiles say which muscles were trained most and least
    private void showMuscleSets(Map<String, Integer> muscles) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        String most = null;
        String least = null;
        ArrayList<String> notTrained = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> muscle : muscles.entrySet()) {
            series.getData().add(new XYChart.Data<>(muscle.getKey(), muscle.getValue()));
            total += muscle.getValue();
            if (most == null || muscle.getValue() > muscles.get(most)) {
                most = muscle.getKey();
            }
            if (least == null || muscle.getValue() < muscles.get(least)) {
                least = muscle.getKey();
            }
            if (muscle.getValue() == 0) {
                notTrained.add(muscle.getKey());
            }
        }
        muscleChart.getData().clear();
        muscleChart.getData().add(series);
        for (XYChart.Data<String, Number> bar : series.getData()) {
            tip(bar.getNode(), bar.getXValue() + ": " + bar.getYValue() + " sets");
        }

        String range = "in the last " + rangeBox.getValue();
        if (rangeBox.getValue().equals("All time")) {
            range = "in all your workouts";
        }
        setTile(0, String.valueOf(total), range);
        if (most == null || muscles.get(most) == 0) {
            muscleNote.setText("No sets in this range.");
            setTile(1, "-", "no sets in this range");
        } else {
            if (notTrained.isEmpty()) {
                muscleNote.setText("Trained most: " + most + ". Trained least: " + least + ".");
            } else {
                muscleNote.setText("Trained most: " + most + ". Not trained at all: " + String.join(", ", notTrained) + ".");
            }
            setTile(1, most.substring(0, 1).toUpperCase() + most.substring(1), muscles.get(most) + " sets");
        }
    }

    private void addBodyWeight() {
        try {
            double weightKg = Double.parseDouble(bodyWeightField.getText().trim());
            app.addBodyWeight(weightKg);
            weightField.setText(String.valueOf(weightKg)); // the Profile page shows the new weight too
            bodyWeightField.clear();
            refreshDashboard();
            dashboardMessage.setText("Saved today's body weight: " + weightKg + " kg.");
            showToast("Body weight saved");
        } catch (NumberFormatException error) {
            dashboardMessage.setText("The body weight must be a number, like 78.5.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            dashboardMessage.setText(error.getMessage());
        }
    }

    // ----- Coach Chatbox tab (F09) -----

    private Node buildChatTab() {
        chatMessages = new VBox(12);
        chatMessages.setPadding(new Insets(16));
        chatScroll = new ScrollPane(chatMessages);
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("chat-list");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);
        chatMessages.heightProperty().addListener((observable, oldHeight, newHeight) -> chatScroll.setVvalue(1)); // shows the newest message
        addMessage("Hi! Ask me anything about your training, like \"Why is my bench press not going up?\" or "
                + "\"How much protein should I eat?\". I can see your profile, your plan and your workouts.", false);

        questionField = new TextField();
        questionField.setPromptText("Type your question");
        questionField.setOnAction(event -> askCoach()); // Enter sends it too
        HBox.setHgrow(questionField, Priority.ALWAYS);
        Button sendButton = new Button("Send");
        sendButton.setOnAction(event -> askCoach());
        sendButton.getStyleClass().add("primary");

        VBox page = new VBox(10, title("Coach chat"), chatScroll, new HBox(8, questionField, sendButton));
        page.setPadding(new Insets(16));
        return page;
    }

    private void askCoach() {
        String question = questionField.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        questionField.clear();
        addMessage(question, true);
        runInBackground("The coach is thinking...", () -> app.askCoach(question), answer -> {
            addMessage(answer, false);
            questionField.requestFocus();
        });
    }

    // a chat bubble: the user's are blue on the right, and the coach's are grey on the left
    private void addMessage(String text, boolean fromUser) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.maxWidthProperty().bind(chatScroll.widthProperty().multiply(0.7));
        HBox row = new HBox(10);
        if (fromUser) {
            bubble.getStyleClass().add("bubble-user");
            row.setAlignment(Pos.TOP_RIGHT);
            row.getChildren().add(bubble);
        } else {
            bubble.getStyleClass().add("bubble-coach");
            Label avatar = new Label("AI");
            avatar.getStyleClass().add("avatar");
            avatar.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().addAll(avatar, bubble);
        }
        chatMessages.getChildren().add(row);
    }

    // ----- Nutrition tab (F10) -----

    private Node buildNutritionTab() {
        Button targetsButton = new Button("Calculate Targets");
        targetsButton.setOnAction(event -> showTargets());
        caloriesLabel = new Label();
        caloriesLabel.getStyleClass().add("big-number");
        caloriesLabel.managedProperty().bind(caloriesLabel.textProperty().isNotEmpty());
        macroBox = new VBox(6);
        targetsLabel = new Label("Press Calculate Targets to see how much to eat each day for your goal.");
        targetsLabel.setWrapText(true);
        targetsLabel.getStyleClass().add("hint");
        VBox left = new VBox(10, title("Daily targets"), targetsButton, caloriesLabel, macroBox, targetsLabel);
        left.setMinWidth(320);
        left.setMaxWidth(320);
        left.getStyleClass().add("card");

        likesField = new TextField();
        likesField.setPromptText("For example: chicken, rice, eggs");
        likesField.setPrefWidth(380);
        dislikesField = new TextField();
        dislikesField.setPromptText("For example: fish, mushrooms");
        allergiesField = new TextField();
        allergiesField.setPromptText("Separated by commas, or leave it empty");
        showAllergies();
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Foods you like"), likesField);
        form.addRow(1, new Label("Foods you don't like"), dislikesField);
        form.addRow(2, new Label("Allergies"), allergiesField);
        Button ideasButton = new Button("Get Meal Ideas");
        ideasButton.setOnAction(event -> getMealIdeas());
        ideasButton.getStyleClass().add("primary");
        nutritionMessage = new Label();
        nutritionMessage.setWrapText(true);
        ideasBox = new VBox(8);
        VBox right = new VBox(10, title("Meal ideas"), form, ideasButton, nutritionMessage, ideasBox);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.getStyleClass().add("card");

        if (app.getProfile() != null) {
            showTargets(); // the targets are ready when the page opens
        }
        HBox page = new HBox(24, left, right);
        page.setPadding(new Insets(16));
        return page;
    }

    // the allergies box starts with the allergies saved in the profile
    private void showAllergies() {
        if (app.getProfile() == null) {
            return;
        }
        ArrayList<String> allergies = new ArrayList<>();
        for (String boundary : app.getProfile().getBoundaries()) {
            if (boundary.toLowerCase().startsWith("allergy:")) {
                allergies.add(boundary.substring(8).trim());
            }
        }
        allergiesField.setText(String.join(", ", allergies));
    }

    // F10: the daily calories and macros, worked out from the profile without the AI. Returns false with no profile
    private boolean showTargets() {
        Map<String, Integer> targets;
        try {
            targets = app.calculateTargets();
        } catch (IllegalStateException error) {
            targetsLabel.setText(error.getMessage());
            return false;
        }
        caloriesLabel.setText(targets.get("calories") + " calories a day");
        macroBox.getChildren().clear();
        double calories = targets.get("protein") * 4 + targets.get("carbs") * 4 + targets.get("fat") * 9;
        addMacro("Protein", targets.get("protein"), 4, calories, "protein-bar");
        addMacro("Carbs", targets.get("carbs"), 4, calories, "carbs-bar");
        addMacro("Fat", targets.get("fat"), 9, calories, "fat-bar");
        targetsLabel.setText("Protein and carbs have 4 calories in each gram, and fat has 9.");
        if (targets.get("calories") <= 1200) {
            targetsLabel.setText("Your goal would go lower, so the calories are raised to 1200, the safe minimum."); // UC10 2b
        }
        return true;
    }

    // one macro: its grams and its share of the calories, with a bar
    private void addMacro(String name, int grams, int caloriesPerGram, double calories, String barStyle) {
        double share = grams * caloriesPerGram / calories;
        Label text = new Label(name + ": " + grams + " g  (" + Math.round(share * 100) + "% of calories)");
        ProgressBar bar = new ProgressBar(share);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(barStyle);
        macroBox.getChildren().addAll(text, bar);
    }

    private void getMealIdeas() {
        if (!showTargets()) {
            nutritionMessage.setText("Please set up your profile first.");
            return;
        }
        String preferences = "likes: " + likesField.getText().trim() + "\ndislikes: " + dislikesField.getText().trim();
        if (!allergiesField.getText().isBlank()) {
            preferences += "\nallergies: " + allergiesField.getText().trim(); // these become boundaries in the profile
        }
        String request = preferences;
        runInBackground("The AI is thinking of meals...", () -> app.getMealIdeas(request), this::showMealIdeas);
    }

    private void showMealIdeas(List<String> ideas) {
        ideasBox.getChildren().clear();
        if (ideas == null) {
            // UC10 4a
            nutritionMessage.setText("The AI is offline, so meal ideas are not available right now. Your daily targets still work.");
            return;
        }
        if (ideas.isEmpty()) {
            nutritionMessage.setText("The AI could not find meals that fit your allergies this time. Press Get Meal Ideas to try again.");
            return;
        }

        nutritionMessage.setText("Accept the meals you like, so the coach remembers them, or press Regenerate for new ideas.");
        for (String idea : ideas) {
            Label text = new Label(idea);
            text.setWrapText(true);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button acceptButton = new Button("Accept");
            acceptButton.setMinWidth(Region.USE_PREF_SIZE); // a long idea wraps instead of squeezing the button
            acceptButton.setOnAction(event -> {
                app.acceptMealIdea(idea);
                acceptButton.setText("Saved");
                acceptButton.setDisable(true);
                showToast("Meal saved");
            });
            HBox row = new HBox(8, text, acceptButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("item-row");
            ideasBox.getChildren().add(row);
        }
        Button regenerateButton = new Button("Regenerate");
        regenerateButton.setOnAction(event -> getMealIdeas());
        ideasBox.getChildren().add(regenerateButton);
    }

    // ----- Report tab (F11) -----

    private Node buildReportTab() {
        Button generateButton = new Button("Generate Report");
        generateButton.getStyleClass().add("primary");
        generateButton.setOnAction(event -> runInBackground("The AI is writing your weekly report...",
                () -> app.generateWeeklyReport(), this::showReport));
        saveReportButton = new Button("Save Report");
        saveReportButton.setDisable(true);
        saveReportButton.setOnAction(event -> {
            app.saveReport(report);
            reportMessage.setText("Saved as report-" + LocalDate.now() + ".txt in the data folder.");
            showToast("Report saved");
        });
        reportMessage = new Label("The report looks at this week from Monday: your workouts, check-ins and records, "
                + "with the AI's review and suggestions for next week.");
        reportMessage.setWrapText(true);
        reportArea = new TextArea();
        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        VBox.setVgrow(reportArea, Priority.ALWAYS);
        suggestionsBox = new VBox(8);

        VBox page = new VBox(10, title("Weekly report"), new HBox(8, generateButton, saveReportButton), reportMessage,
                reportArea, suggestionsBox);
        page.setPadding(new Insets(16));
        return page;
    }

    private void showReport(String text) {
        report = text;
        reportArea.setText(text);
        saveReportButton.setDisable(false);
        suggestionsBox.getChildren().clear();

        boolean inSuggestions = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("Suggestions for next week:")) {
                inSuggestions = true;
            } else if (inSuggestions && line.matches("\\d+\\. .*")) {
                addSuggestion(line.substring(line.indexOf(". ") + 2));
            }
        }
        if (suggestionsBox.getChildren().isEmpty()) {
            reportMessage.setText("Press Save Report to keep it. Nothing is saved until you do.");
        } else {
            reportMessage.setText("Apply a suggestion to change your plan, or deny it. Press Save Report to keep the report.");
        }
    }

    // a suggestion with Apply and Deny. Apply changes the plan like Request a Change (F03)
    private void addSuggestion(String suggestion) {
        Label text = new Label(suggestion);
        text.setWrapText(true);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button applyButton = new Button("Apply");
        Button denyButton = new Button("Deny");
        applyButton.setMinWidth(Region.USE_PREF_SIZE); // a long suggestion wraps instead of squeezing the buttons
        denyButton.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(8, text, applyButton, denyButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");

        applyButton.setOnAction(event -> {
            if (app.getCurrentPlan() == null) {
                reportMessage.setText("You don't have a plan yet, so there is nothing to change.");
                return;
            }
            if (pendingPlan != null) {
                reportMessage.setText("First confirm or undo the change that is waiting on the Workout Plan page.");
                return;
            }
            row.setDisable(true);
            text.setText(suggestion + "  (applied)");
            applySuggestion(suggestion);
        });
        denyButton.setOnAction(event -> {
            row.setDisable(true); // UC11 6a: the plan stays the same
            text.setText(suggestion + "  (denied)");
        });
        suggestionsBox.getChildren().add(row);
    }

    private void applySuggestion(String suggestion) {
        changePlan(suggestion, "This change is from your weekly report: \"" + suggestion + "\". The changed workouts "
                + "are marked with a star. Confirm or undo it.", () -> applySuggestion(suggestion));
    }

    // ----- Import tab (F12) -----

    private Node buildImportTab() {
        Label about = new Label("Import your past workouts from a .csv file, like an export from Strong or Hevy. The file needs "
                + "columns for the date, the exercise, the reps and the weight. Nothing is saved until you press Import.");
        about.setWrapText(true);
        about.getStyleClass().add("hint");
        Button chooseButton = new Button("Choose File...");
        chooseButton.getStyleClass().add("primary");
        chooseButton.setOnAction(event -> chooseImportFile());

        // a file can also be dragged onto this box
        SVGPath icon = new SVGPath();
        icon.setContent(ICONS[7]);
        icon.setFillRule(FillRule.EVEN_ODD);
        icon.getStyleClass().add("drop-icon");
        icon.setScaleX(1.8);
        icon.setScaleY(1.8);
        VBox dropZone = new VBox(10, new Group(icon), new Label("Drag a .csv file here, or"), chooseButton);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        dropZone.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                dropZone.getStyleClass().add("drag-over"); // the box turns blue while a file is over it
            }
        });
        dropZone.setOnDragExited(event -> dropZone.getStyleClass().remove("drag-over"));
        dropZone.setOnDragDropped(event -> {
            boolean hasFiles = event.getDragboard().hasFiles();
            if (hasFiles) {
                readImportFile(event.getDragboard().getFiles().get(0).getPath());
            }
            event.setDropCompleted(hasFiles);
            event.consume();
        });

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(6);
        previewArea.setPromptText("Choose a file to see what is in it.");
        matchesBox = new VBox(6);
        matchesBox.setPadding(new Insets(8));
        matchesPane = new ScrollPane(matchesBox);
        matchesPane.setFitToWidth(true);
        matchesPane.getStyleClass().add("match-list");
        VBox.setVgrow(matchesPane, Priority.ALWAYS); // a long list scrolls
        Button importButton = new Button("Import");
        importButton.setOnAction(event -> confirmImport());
        importButton.getStyleClass().add("primary");
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            clearImport();
            importMessage.setText("Nothing was imported."); // UC12 5a
        });
        importButtons = new HBox(8, importButton, cancelButton);
        importMessage = new Label();
        importMessage.setWrapText(true);
        clearImport();

        VBox page = new VBox(10, title("Import workout history"), about, dropZone, previewArea, matchesPane,
                importButtons, importMessage);
        page.setPadding(new Insets(16));
        return page;
    }

    private void chooseImportFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a workout file");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File sampleData = new File("sample-data");
        if (sampleData.isDirectory()) {
            chooser.setInitialDirectory(sampleData); // the example file that comes with the project
        }
        File file = chooser.showOpenDialog(tabs.getScene().getWindow());
        if (file != null) {
            readImportFile(file.getPath());
        }
    }

    // F12: reads the file and shows the preview. Each exercise that is not in the library gets a box to match it by hand
    private void readImportFile(String filePath) {
        clearImport();
        List<String> unmatched;
        try {
            unmatched = app.importWorkoutHistory(filePath);
        } catch (IllegalArgumentException error) {
            importMessage.setText(error.getMessage()); // UC12 2a
            return;
        }
        previewArea.setText(new File(filePath).getName() + "\n" + String.join("\n", app.getImportPreview()));

        if (!unmatched.isEmpty()) {
            Label explain = new Label("These exercises are not in the library. Pick the library exercise for each one, or leave it out:");
            explain.setWrapText(true);
            matchesBox.getChildren().add(explain);
            ArrayList<String> names = new ArrayList<>();
            for (Exercise exercise : ExerciseLibrary.getInstance().getExercises()) {
                names.add(exercise.getName());
            }
            names.sort(null);
            for (String name : unmatched) {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().add(LEAVE_OUT);
                box.getItems().addAll(names);
                box.setValue(LEAVE_OUT);
                matchBoxes.put(name, box);
                Label label = new Label(name);
                label.setMinWidth(240);
                HBox row = new HBox(8, label, box);
                row.setAlignment(Pos.CENTER_LEFT);
                matchesBox.getChildren().add(row);
            }
        }
        matchesPane.setVisible(!unmatched.isEmpty());
        matchesPane.setManaged(!unmatched.isEmpty());
        importButtons.setVisible(true);
        importButtons.setManaged(true);
        importMessage.setText("Check the preview, then press Import. Cancel imports nothing.");
    }

    private void confirmImport() {
        HashMap<String, String> matches = new HashMap<>();
        for (Map.Entry<String, ComboBox<String>> entry : matchBoxes.entrySet()) {
            if (!entry.getValue().getValue().equals(LEAVE_OUT)) {
                matches.put(entry.getKey(), entry.getValue().getValue());
            }
        }
        String summary = app.confirmImport(matches); // each new workout tells the dashboard (Observer pattern)
        clearImport();
        previewArea.setText(summary);
        importMessage.setText("Your workouts are on the Progress Dashboard page now.");
        showToast("Workouts imported");
        refreshAll(); // the plan can start from the imported weights
    }

    private void clearImport() {
        previewArea.clear();
        matchesBox.getChildren().clear();
        matchBoxes.clear();
        matchesPane.setVisible(false);
        matchesPane.setManaged(false);
        importButtons.setVisible(false);
        importButtons.setManaged(false);
    }

    // ----- shared helpers -----

    // runs slow work, like a call to the AI, on a background thread so the window does not freeze.
    // The pages are locked under a card with a spinner until it is done, and then "done" gets the result on the JavaFX thread
    private <T> void runInBackground(String message, Callable<T> work, Consumer<T> done) {
        status.setText(message);
        busyOverlay.setVisible(true);
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
        busyOverlay.setVisible(false);
        tabs.setDisable(false);
    }

    // the dashboard is not here, because the workout history tells it when to refresh (Observer pattern)
    private void refreshAll() {
        refreshPlan();
        refreshWorkout();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }

    // a dialog is a separate window, so it gets the same stylesheet and opens over the main window
    private void style(Dialog<?> dialog) {
        dialog.initOwner(tabs.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().add(stylesheet);
    }

    private Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("page-title");
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

    // a red border on a field that is missing or not realistic
    private void highlight(Node field, boolean wrong) {
        field.getStyleClass().remove("invalid");
        if (wrong) {
            field.getStyleClass().add("invalid");
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
