package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class PackagerController {
    private static final String PACKAGE_TYPE_EXE = "exe";
    private static final String PACKAGE_TYPE_MSI = "msi";
    private static final String PACKAGE_TYPE_APP_IMAGE = "app-image";

    @FXML private Button checkButton;
    @FXML private VBox modulesSection;
    @FXML private Button detectModulesButton;
    @FXML private Button moduleDetailsButton;
    @FXML private javafx.scene.control.Label modulesHint;
    private final javafx.animation.PauseTransition moduleDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(650));
    private final ExecutorService moduleExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "module-detector");
        thread.setDaemon(true);
        return thread;
    });
    private java.util.concurrent.Future<?> moduleTask;
    private long moduleGeneration;
    private List<String> detectedModules = List.of();
    private String moduleDetails = "";
    private boolean moduleAnalysisBusy;
    private boolean iconConversionBusy;
    @FXML private Button convertIconButton;
    @FXML private Button browseIconButton;
    @FXML private javafx.scene.control.Label iconHint;
    @FXML private javafx.scene.control.Label summaryName;
    @FXML private javafx.scene.control.Label summaryFormat;
    @FXML private javafx.scene.control.Label summaryDestination;
    @FXML private VBox iconSection;
    @FXML private javafx.scene.control.Label checkSummary;
    @FXML private Button applicationNav;
    @FXML private Button runtimeNav;
    @FXML private Button optionsNav;
    @FXML private Button backButton;
    @FXML private Button continueButton;
    @FXML private Button buildButton;
    private int currentStep = 1;
    private String automaticDestination;
    private java.util.function.Consumer<String> openLink = url -> {};

    public void setOpenLink(java.util.function.Consumer<String> openLink) { this.openLink = openLink; }
    @FXML private Node windowsOptionsPane;

    @FXML
    private TextField jarFileField;

    @FXML
    private TextField appImageField;

    @FXML
    private TextField inputDirField;

    @FXML
    private TextField destDirField;

    @FXML
    private TextField appNameField;

    @FXML
    private TextField appVersionField;

    @FXML
    private TextField mainClassField;

    @FXML
    private TextField mainJarField;

    @FXML
    private ComboBox<String> packageTypeCombo;

    @FXML
    private TextField iconField;

    @FXML
    private TextField vendorField;

    @FXML
    private TextField copyrightField;

    @FXML
    private TextField descriptionField;

    @FXML
    private TextField licenseField;

    @FXML
    private TextField upgradeUuidField;

    @FXML
    private TextField menuGroupField;

    @FXML
    private TextField winHelpUrlField;

    @FXML
    private TextField winUpdateUrlField;

    @FXML
    private TextField javaOptionsField;

    @FXML
    private TextField addModulesField;

    @FXML
    private TextField modulePathField;

    @FXML
    private TextField runtimeImageField;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private VBox javaOptionsContainer;

    @FXML
    private HBox sourceModeRow;

    @FXML
    private HBox appImageRow;

    @FXML
    private Node jarFileLabel;

    @FXML
    private HBox jarFileRow;

    @FXML
    private Node inputDirLabel;

    @FXML
    private HBox inputDirRow;

    @FXML
    private Node mainClassLabel;

    @FXML
    private Node mainJarLabel;

    @FXML
    private Node iconLabel;

    @FXML
    private HBox iconRow;

    @FXML
    private Node addModulesLabel;

    @FXML
    private Node modulePathLabel;

    @FXML
    private HBox modulePathRow;

    @FXML
    private Node runtimeImageLabel;

    @FXML
    private HBox runtimeImageRow;

    @FXML
    private Node javaOptionsLabel;

    @FXML
    private RadioButton fromJarRadioButton;

    @FXML
    private RadioButton fromAppImageRadioButton;

    @FXML
    private GridPane windowsInstallerOptionsGrid;

    @FXML
    private CheckBox winShortcutCheckBox;

    @FXML
    private CheckBox winMenuCheckBox;

    @FXML
    private CheckBox winDirChooserCheckBox;

    @FXML
    private CheckBox winPerUserInstallCheckBox;

    @FXML
    private CheckBox winShortcutPromptCheckBox;

    @FXML
    private CheckBox winConsoleCheckBox;

    private final List<TextField> javaOptionsFields = new ArrayList<>();
    private final List<HBox> javaOptionsContainers = new ArrayList<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "packager-worker");
        thread.setDaemon(true);
        return thread;
    });
    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    public void initialize() {
        packageTypeCombo.getItems().addAll(JdkLocator.isMac() ? List.of("dmg", "pkg", PACKAGE_TYPE_APP_IMAGE)
                : JdkLocator.isWindows() ? List.of(PACKAGE_TYPE_EXE, PACKAGE_TYPE_MSI, PACKAGE_TYPE_APP_IMAGE)
                : List.of(PACKAGE_TYPE_APP_IMAGE));
        packageTypeCombo.setValue(packageTypeCombo.getItems().getFirst());
        packageTypeCombo.valueProperty().addListener((observable, oldValue, newValue) -> updatePackageTypeOptions());

        ToggleGroup sourceToggleGroup = new ToggleGroup();
        fromJarRadioButton.setToggleGroup(sourceToggleGroup);
        fromAppImageRadioButton.setToggleGroup(sourceToggleGroup);
        sourceToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> updateSourceModeOptions());

        iconHint.setText(JdkLocator.isMac() ? "PNG → ICNS, automatically. Original PNG removed after conversion."
                : JdkLocator.isWindows() ? "PNG / JPG → ICO. Original PNG removed after conversion." : "Choose a PNG icon.");
        setManagedAndVisible(convertIconButton, JdkLocator.isMac() || JdkLocator.isWindows());
        summaryName.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> appNameField.getText().isBlank() ? "Your application" : appNameField.getText(), appNameField.textProperty()));
        summaryFormat.textProperty().bind(packageTypeCombo.valueProperty());
        summaryDestination.textProperty().bind(destDirField.textProperty());
        automaticDestination = destinationFor(System.getProperty("user.dir"));
        destDirField.setText(automaticDestination);
        inputDirField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.isBlank() && isJarSourceSelected()) updateDefaultDestination(newValue);
        });
        appImageField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.isBlank() && isImageSourceSelected()) {
                String parent = getParentDirectoryPath(newValue);
                if (!parent.isBlank()) updateDefaultDestination(parent);
            }
        });
        moduleDebounce.setOnFinished(event -> detectModules(null));
        for (TextField field : List.of(jarFileField, mainJarField, inputDirField, modulePathField, runtimeImageField, destDirField)) {
            field.textProperty().addListener((obs, before, after) -> scheduleModuleDetection());
        }
        javaOptionsFields.add(javaOptionsField);
        updatePackageTypeOptions();
        activateStep(1);
    }

    private void updatePackageTypeOptions() {
        boolean appImageSelected = isAppImageType(packageTypeCombo.getValue());

        if (appImageSelected) {
            fromJarRadioButton.setSelected(true);
        }

        setManagedAndVisible(sourceModeRow, !appImageSelected);
        setManagedAndVisible(windowsInstallerOptionsGrid, JdkLocator.isWindows() && !appImageSelected);
        setManagedAndVisible(windowsOptionsPane, JdkLocator.isWindows());
        setManagedAndVisible(winShortcutCheckBox, !appImageSelected);
        setManagedAndVisible(winMenuCheckBox, !appImageSelected);
        setManagedAndVisible(winDirChooserCheckBox, !appImageSelected);
        setManagedAndVisible(winPerUserInstallCheckBox, !appImageSelected);
        setManagedAndVisible(winShortcutPromptCheckBox, !appImageSelected);
        updateSourceModeOptions();
    }

    private void updateSourceModeOptions() {
        boolean jarSourceSelected = isJarSourceSelected();
        boolean imageSourceSelected = isImageSourceSelected();

        setManagedAndVisible(appImageRow, imageSourceSelected);
        setManagedAndVisible(jarFileLabel, jarSourceSelected);
        setManagedAndVisible(jarFileRow, jarSourceSelected);
        setManagedAndVisible(inputDirLabel, jarSourceSelected);
        setManagedAndVisible(inputDirRow, jarSourceSelected);
        setManagedAndVisible(mainClassLabel, jarSourceSelected);
        setManagedAndVisible(mainClassField, jarSourceSelected);
        setManagedAndVisible(mainJarLabel, jarSourceSelected);
        setManagedAndVisible(mainJarField, jarSourceSelected);

        setManagedAndVisible(iconSection, jarSourceSelected);
        setManagedAndVisible(iconLabel, jarSourceSelected);
        setManagedAndVisible(iconRow, jarSourceSelected);
        setManagedAndVisible(modulesSection, jarSourceSelected);
        setManagedAndVisible(addModulesLabel, jarSourceSelected);
        setManagedAndVisible(addModulesField, jarSourceSelected);
        setManagedAndVisible(modulePathLabel, jarSourceSelected);
        setManagedAndVisible(modulePathRow, jarSourceSelected);
        setManagedAndVisible(runtimeImageLabel, jarSourceSelected);
        setManagedAndVisible(runtimeImageRow, jarSourceSelected);
        setManagedAndVisible(javaOptionsLabel, jarSourceSelected);
        setManagedAndVisible(javaOptionsContainer, jarSourceSelected);
        setManagedAndVisible(winConsoleCheckBox, JdkLocator.isWindows() && jarSourceSelected);
        if (jarSourceSelected && !inputDirField.getText().isBlank()) {
            updateDefaultDestination(inputDirField.getText());
        } else if (imageSourceSelected && !appImageField.getText().isBlank()) {
            String parent = getParentDirectoryPath(appImageField.getText());
            if (!parent.isBlank()) updateDefaultDestination(parent);
        }
        scheduleModuleDetection();
    }

    private void setManagedAndVisible(Node node, boolean visible) {
        if (node != null) {
            node.setManaged(visible);
            node.setVisible(visible);
        }
    }

    //步骤导航方法
    @FXML
    private void showStep1() {
        activateStep(1);
    }

    @FXML
    private void showStep2() {
        //验证步骤1中的必填字段
        if (!validateStep1Fields()) {
            showAlert("Missing Required Fields", getRequiredFieldsMessage());
            return;
        }

        activateStep(2);
    }

    @FXML
    private void showStep3() {
        if (!validateStep1Fields()) {
            showAlert("Missing Required Fields", getRequiredFieldsMessage());
            return;
        }
        activateStep(3);
    }

    private boolean validateStep1Fields() {
        if (appNameField.getText().isEmpty() || destDirField.getText().isEmpty()) {
            return false;
        }

        if (isImageSourceSelected()) {
            return !appImageField.getText().isEmpty();
        }

        return !jarFileField.getText().isEmpty() && !inputDirField.getText().isEmpty() && !mainClassField.getText().isEmpty() && !mainJarField.getText().isEmpty();
    }

    @FXML private void nextStep() { if (currentStep == 1) showStep2(); else showStep3(); }
    @FXML private void previousStep() { activateStep(Math.max(1, currentStep - 1)); }

    private void activateStep(int step) {
        currentStep = step;
        setManagedAndVisible(backButton, step > 1);
        setManagedAndVisible(continueButton, step < 3);
        setManagedAndVisible(buildButton, step == 3);
        List<VBox> panes = List.of(step1, step2, step3);
        List<Button> navigation = List.of(applicationNav, runtimeNav, optionsNav);
        for (int i = 0; i < panes.size(); i++) {
            setManagedAndVisible(panes.get(i), i == step - 1);
            navigation.get(i).getStyleClass().remove("nav-active");
            if (i == step - 1) navigation.get(i).getStyleClass().add("nav-active");
        }
    }

    static String destinationFor(String folder) {
        return java.nio.file.Path.of(folder).toAbsolutePath().normalize().resolve("dist").toString();
    }

    private void updateDefaultDestination(String folder) {
        try {
            String next = destinationFor(folder);
            if (destDirField.getText().isBlank() || destDirField.getText().equals(automaticDestination)) {
                destDirField.setText(next);
            }
            automaticDestination = next;
        } catch (java.nio.file.InvalidPathException ignored) { }
    }

    @FXML
    private void checkEnvironment(ActionEvent event) {
        checkButton.setDisable(true);
        checkButton.setText("Checking…");
        checkSummary.setText("Checking installed tools…");
        checkButton.getStyleClass().removeAll("check-ready", "check-missing");
        executorService.submit(() -> {
            try {
                List<ToolCheckService.Result> results = ToolCheckService.check();
                Platform.runLater(() -> {
                    boolean ready = results.stream().allMatch(ToolCheckService.Result::ready);
                    checkButton.setDisable(false);
                    checkButton.setText("Check");
                    checkButton.getStyleClass().add(ready ? "check-ready" : "check-missing");
                    checkSummary.setText(ready ? "All tools ready" : "Setup needs attention");
                    showToolResults(results);
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    checkButton.setDisable(false);
                    checkButton.setText("Check");
                    checkButton.getStyleClass().add("check-missing");
                    checkSummary.setText("Could not complete check");
                    showAlert("Check failed", "Could not inspect installed tools. " + e.getMessage());
                });
            }
        });
    }

    private void showToolResults(List<ToolCheckService.Result> results) {
        javafx.scene.control.Label title = new javafx.scene.control.Label("Your packaging toolkit");
        title.getStyleClass().add("section-title");
        javafx.scene.control.Label description = new javafx.scene.control.Label(
                "Green means ready. Select a red tool for installation steps. Checks cover tool availability; your application is validated when packaging.");
        description.setWrapText(true);
        description.getStyleClass().add("muted");
        VBox rows = new VBox(10);
        for (ToolCheckService.Result result : results) {
            Button row = new Button((result.ready() ? "✓   " : "!   ") + result.name() + "    ·    " + (result.ready() ? "Ready" : "Needs setup")
                    + "\n" + result.scope());
            row.setMaxWidth(Double.MAX_VALUE);
            row.setWrapText(true);
            row.getStyleClass().addAll("tool-row", result.ready() ? "tool-ready" : "tool-missing");
            row.setOnAction(e -> showCustomDialog(result.name(), result.name() + (result.ready() ? " is ready" : " needs setup"),
                    (result.ready() ? "Detected tool:\n" : result.help() + "\n\nCheck details:\n") + result.detail(), result.url()));
            rows.getChildren().add(row);
        }
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(440, results.size() * 77));
        Button done = new Button("Done");
        done.getStyleClass().add("primary-button");
        HBox actions = new HBox(done);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        VBox content = new VBox(16, title, description, scroll, actions);
        content.getStyleClass().add("dialog-root");
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Environment check");
        javafx.scene.Scene scene = new javafx.scene.Scene(content, 580, Math.min(690, 210 + results.size() * 77));
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dialog.setScene(scene);
        done.setOnAction(e -> dialog.close());
        dialog.show();
    }

    private void showCustomDialog(String title, String header, String content) {
        showCustomDialog(title, header, content, null);
    }

    private void showCustomDialog(String title, String header, String content, String url) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("custom-dialog.fxml"));
            VBox dialogRoot = loader.load();

            CustomDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            controller.setTitle(header);
            controller.setContent(content);
            controller.setHelpLink(url, openLink);

            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            javafx.scene.Scene scene = new javafx.scene.Scene(dialogRoot);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, content).showAndWait();
        }
    }

    @FXML
    private void browseJarFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select JAR File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JARFiles", "*.jar"));
        File selectedFile = fileChooser.showOpenDialog(jarFileField.getScene().getWindow());
        if (selectedFile != null) {
            jarFileField.setText(selectedFile.getAbsolutePath());
            analyzeJarFile();
        }
    }

    private void analyzeJarFile() {
        String jarFilePath = jarFileField.getText();
        if (jarFilePath.isEmpty()) {
            showCustomDialog("Error", "No JAR File", "Please select a JAR file first.");
            return;
        }

        File jarFile = new File(jarFilePath);
        if (!jarFile.exists()) {
            showCustomDialog("Error", "FileNotFound", "The selected JAR file does not exist.");
            return;
        }

        // Run analysis in background thread
        executorService.submit(() -> analyzeJarInBackground(jarFile));
    }

    private void analyzeJarInBackground(File jarFile) {
        try {
            JarFile jar = new JarFile(jarFile);
            Manifest manifest = jar.getManifest();
// Extract information from manifest
            String mainClass = null;

            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                mainClass = attributes.getValue("Main-Class");
            }
            final String finalMainClass = mainClass;

            //Update UIonJavaFXApplication Thread
            Platform.runLater(() -> {
                if (!jarFileField.getText().equals(jarFile.getAbsolutePath())) return;
                if (finalMainClass != null && !finalMainClass.isEmpty()) {
                    mainClassField.setText(finalMainClass);
                }

                // Set appname to jar name
                appNameField.setText(jarFile.getName().substring(0, jarFile.getName().lastIndexOf(".jar")));

                // Set main jar file name
                mainJarField.setText(jarFile.getName());

                // Set input directory
                inputDirField.setText(jarFile.getParent());

                updateDefaultDestination(jarFile.getParent());
            });

            jar.close();
        } catch (Exception e) {
            Platform.runLater(() -> {
                showCustomDialog("Analysis Error", "Error analyzing JAR file", e.getMessage());
            });
        }
    }

    @FXML
    private void browseInputDir(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Input Directory");
        File selectedDirectory = directoryChooser.showDialog(inputDirField.getScene().getWindow());
        if (selectedDirectory != null) {
            inputDirField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void browseDestDir(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Destination Directory");
        File selectedDirectory = directoryChooser.showDialog(destDirField.getScene().getWindow());
        if (selectedDirectory != null) {
            destDirField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void browseAppImageDir(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select App Image Directory");
        File selectedDirectory = directoryChooser.showDialog(appImageField.getScene().getWindow());
        if (selectedDirectory != null) {
            if (!isAppImageType(packageTypeCombo.getValue())) {
                fromAppImageRadioButton.setSelected(true);
            }
            appImageField.setText(selectedDirectory.getAbsolutePath());

            if (appNameField.getText().isEmpty()) {
                appNameField.setText(getAppNameFromAppImageDirectory(selectedDirectory));
            }

            if (selectedDirectory.getParentFile() != null) {
                updateDefaultDestination(selectedDirectory.getParentFile().getAbsolutePath());
            }
        }
    }

    @FXML
    private void browseIcon(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an application icon");
        if (new File(inputDirField.getText()).isDirectory()) chooser.setInitialDirectory(new File(inputDirField.getText()));
        chooser.getExtensionFilters().add(JdkLocator.isMac()
                ? new FileChooser.ExtensionFilter("PNG images & macOS icons", "*.png", "*.PNG", "*.icns")
                : JdkLocator.isWindows() ? new FileChooser.ExtensionFilter("Images & Windows icons", "*.png", "*.PNG", "*.jpg", "*.jpeg", "*.ico")
                : new FileChooser.ExtensionFilter("PNG images", "*.png", "*.PNG"));
        File selected = chooser.showOpenDialog(iconField.getScene().getWindow());
        if (selected != null) {
            iconField.setText(selected.getAbsolutePath());
            if (IconConversionService.needsConversion(selected.toPath())) convertIcon(null);
        }
    }

    @FXML
    private void convertIcon(ActionEvent event) {
        java.nio.file.Path source;
        try {
            source = java.nio.file.Path.of(iconField.getText());
            if (iconField.getText().isBlank() || !IconConversionService.needsConversion(source)) {
                showAlert("Choose an image", JdkLocator.isMac() ? "Choose a PNG file to convert to ICNS." : "Choose a PNG or JPG file to convert to ICO.");
                return;
            }
        } catch (java.nio.file.InvalidPathException e) { showAlert("Invalid icon path", e.getMessage()); return; }
        setIconConverting(true);
        executorService.submit(() -> {
            try {
                var result = IconConversionService.convert(source);
                Platform.runLater(() -> {
                    iconField.setText(result.icon().toString());
                    iconHint.setText(result.note());
                    setIconConverting(false);
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    setIconConverting(false);
                    iconHint.setText("Conversion failed. Your original image was kept.");
                    showAlert("Icon conversion failed", e.getMessage());
                });
            }
        });
    }

    private void setIconConverting(boolean busy) {
        iconConversionBusy = busy;
        iconField.setDisable(busy);
        browseIconButton.setDisable(busy);
        convertIconButton.setDisable(busy);
        updateBuildAvailability();
        convertIconButton.setText(busy ? "Converting…" : "Convert");
        if (busy) iconHint.setText("Creating your native icon…");
    }

    private void updateBuildAvailability() {
        buildButton.setDisable(iconConversionBusy || moduleAnalysisBusy);
    }

    private void scheduleModuleDetection() {
        moduleDebounce.stop();
        moduleGeneration++;
        if (moduleTask != null) moduleTask.cancel(true);
        addModulesField.setText(ModuleDetector.merge(addModulesField.getText(), detectedModules, List.of()));
        detectedModules = List.of();
        moduleDetails = "";
        setManagedAndVisible(moduleDetailsButton, false);
        moduleAnalysisBusy = false;
        detectModulesButton.setDisable(!isJarSourceSelected() || !runtimeImageField.getText().isBlank());
        detectModulesButton.setText("Detect");
        if (!isJarSourceSelected() || !runtimeImageField.getText().isBlank()) {
            modulesHint.setText("An existing image supplies its own runtime; module detection is not needed.");
        } else if (jarFileField.getText().isBlank()) {
            modulesHint.setText("Modules are detected automatically after you choose a JAR.");
        } else {
            modulesHint.setText("Waiting to analyze dependencies…");
            moduleAnalysisBusy = true;
            moduleDebounce.playFromStart();
        }
        updateBuildAvailability();
    }

    @FXML
    private void detectModules(ActionEvent event) {
        moduleDebounce.stop();
        long generation = ++moduleGeneration;
        if (moduleTask != null) moduleTask.cancel(true);
        if (!isJarSourceSelected() || !runtimeImageField.getText().isBlank()) {
            scheduleModuleDetection();
            return;
        }
        ModuleDetector.Request request;
        try {
            if (jarFileField.getText().isBlank()) throw new IllegalArgumentException("Choose a JAR first.");
            java.nio.file.Path jar = java.nio.file.Path.of(jarFileField.getText()).toAbsolutePath();
            java.nio.file.Path input = inputDirField.getText().isBlank() ? jar.getParent() : java.nio.file.Path.of(inputDirField.getText()).toAbsolutePath();
            if (!mainJarField.getText().isBlank()) jar = input.resolve(mainJarField.getText()).normalize();
            request = new ModuleDetector.Request(jar, input, modulePathField.getText(),
                    destDirField.getText().isBlank() ? null : java.nio.file.Path.of(destDirField.getText()));
        } catch (IllegalArgumentException e) {
            finishModuleDetection(generation, null, e.getMessage(), addModulesField.getText());
            return;
        }
        String originalModules = addModulesField.getText();
        moduleAnalysisBusy = true;
        detectModulesButton.setDisable(true);
        detectModulesButton.setText("Detecting…");
        modulesHint.setText("Analyzing the JAR and its libraries…");
        updateBuildAvailability();
        moduleTask = moduleExecutor.submit(() -> {
            try {
                var result = ModuleDetector.detect(request);
                Platform.runLater(() -> finishModuleDetection(generation, result, null, originalModules));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> finishModuleDetection(generation, null, e.getMessage(), originalModules));
            }
        });
    }

    private void finishModuleDetection(long generation, ModuleDetector.Result result, String error, String originalModules) {
        if (generation != moduleGeneration) return;
        moduleAnalysisBusy = false;
        detectModulesButton.setDisable(false);
        detectModulesButton.setText("Detect");
        updateBuildAvailability();
        if (result == null) {
            modulesHint.setText("Could not detect all modules. See details, fix dependencies, then Detect again.");
            moduleDetails = error;
        } else if (!addModulesField.getText().equals(originalModules)) {
            modulesHint.setText("Your edits were kept. Click Detect to merge the detected modules.");
            moduleDetails = result.details();
        } else {
            addModulesField.setText(ModuleDetector.merge(originalModules, detectedModules, result.modules()));
            detectedModules = result.modules();
            modulesHint.setText("Detected automatically. Add modules loaded through reflection or services if needed.");
            moduleDetails = result.details();
        }
        setManagedAndVisible(moduleDetailsButton, true);
    }

    @FXML private void showModuleDetails(ActionEvent event) {
        showCustomDialog("Module detection", "Module analysis", moduleDetails);
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }

    @FXML
    private void browseLicense(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (new File(inputDirField.getText()).isDirectory()) fileChooser.setInitialDirectory(new File(inputDirField.getText()));
        fileChooser.setTitle("Select License File");
        File selectedFile = fileChooser.showOpenDialog(licenseField.getScene().getWindow());
        if (selectedFile != null) {
            licenseField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void browseModulePath(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("SelectModulePath Directory");
        directoryChooser.setInitialDirectory(new File(inputDirField.getText()));
        File selectedDirectory = directoryChooser.showDialog(modulePathField.getScene().getWindow());
        if (selectedDirectory != null) {
            modulePathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void browseRuntimeImage(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select RuntimeImage Directory");
        directoryChooser.setInitialDirectory(new File(inputDirField.getText()));
        File selectedDirectory = directoryChooser.showDialog(runtimeImageField.getScene().getWindow());
        if (selectedDirectory != null) {
            runtimeImageField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void packageApp(ActionEvent event) {
        //Validate inputs
        if (!validateStep1Fields()) {

            showCustomDialog("Missing Required Fields", "Missing Required Fields", getRequiredFieldsMessage());
            return;
        }

        // 在后台线程中执行打包命令
        List<String> command = buildJPackageCommand();
        executorService.submit(() -> executePackagingTask(command));
    }

    private void executePackagingTask(List<String> command) {
//创建并显示进度对话框
        ProgressController progressController = new ProgressController();
        Platform.runLater(() -> {
            progressController.showProgressDialog(primaryStage);
            progressController.appendText("Executing packaging command...\n\n");
        });

        //Buildj package command
        String packageType = getCommandOptionValue(command, "--type");
        String appName = getCommandOptionValue(command, "--name");
        String destDir = getCommandOptionValue(command, "--dest");
        if (destDir.isEmpty()) {
            String inputDir = getCommandOptionValue(command, "--input");
            String appImageDir = getCommandOptionValue(command, "--app-image");
            destDir = !inputDir.isEmpty() ? inputDir : getParentDirectoryPath(appImageDir);
        }
        boolean appImageSelected = isAppImageType(packageType);
        boolean prebuiltAppImageSelected = isCommandUsingPrebuiltAppImage(command);

        try {
            command.set(0, JdkLocator.resolve().toString());
            String iconPath = getCommandOptionValue(command, "--icon");
            if (!iconPath.isBlank() && IconConversionService.needsConversion(java.nio.file.Path.of(iconPath))) {
                var converted = IconConversionService.convert(java.nio.file.Path.of(iconPath));
                command.set(command.indexOf("--icon") + 1, converted.icon().toString());
                Platform.runLater(() -> {
                    if (iconField.getText().equals(iconPath)) iconField.setText(converted.icon().toString());
                    iconHint.setText(converted.note());
                    progressController.appendText(converted.note() + "\n");
                });
            }
            //在进度对话框中显示执行的命令
            String commandStr = formatCommandForDisplay(command);
            Platform.runLater(() -> {
                if (appImageSelected) {
                    progressController.appendText("Creating an application image. Installer-only options will be ignored.\n\n");
                } else if (prebuiltAppImageSelected) {
                    progressController.appendText("Creating an installer from an existing application image. JAR and launcher creation options will be ignored.\n\n");
                }
                progressController.appendText("Command: ");
                progressController.appendText(commandStr + "\n\n");
            });

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            ToolCheckService.configurePackagingEnvironment(processBuilder);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                final String logLine = line;
                Platform.runLater(() -> progressController.appendText(logLine + "\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String outputPath = appImageSelected ? getExpectedOutputPath(destDir, packageType, appName) : destDir;
                String outputType = appImageSelected ? "image" : "directory";

                Platform.runLater(() -> {
                    progressController.appendText("\nPackaging completed successfully!\n");
                    progressController.appendText("Output " + outputType + " saved to: " + outputPath + "\n");
                    progressController.setCompleted();
                    showCustomDialog("Success", "Packaging Completed", "Application packaged successfully!\nOutput " + outputType + ": " + outputPath);
                });
            } else {
                Platform.runLater(() -> {
                    progressController.appendText("\nPackaging failed with exit code: " + exitCode + "\n");
                    // 添加更多错误信息
                    progressController.appendText("Please check the above output for more details about the error.\n");
                    progressController.appendText("Common issuesand solutions:\n");
                    progressController.appendText("1. Use Check to verify the installed packaging tools\n");
                    progressController.appendText("2. Check that all filepaths arecorrect and accessible\n");
                    progressController.appendText("3. Ensure the main JAR file contains a proper manifest withMain-Classentry\n");
                    progressController.appendText("4. Verifythat theinput directory contains all necessaryfiles\n");
                    progressController.setFailed();
                    showCustomDialog("Packaging Failed", "Packaging Failed", "Packaging failed with exit code: " + exitCode + ". Please check the progress dialog formore details.");
                });
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            Platform.runLater(() -> {
                progressController.appendText("Error executing command: " + e.getMessage() + "\n");
                progressController.appendText("Stack trace:\n");
                progressController.appendText(sw.toString());
                progressController.setFailed();
                showCustomDialog("Execution Error", "ExecutionError", "Error executing command:" + e.getMessage());
            });
        }
    }

    private List<String> buildJPackageCommand() {
        List<String> command = new ArrayList<>();
        String packageType = packageTypeCombo.getValue();
        boolean appImageSelected = isAppImageType(packageType);
        boolean prebuiltAppImageSelected = isUsingPrebuiltAppImage(packageType);

        command.add("jpackage");

        command.add("--name");
        command.add(appNameField.getText());

        if (!appVersionField.getText().isEmpty()) {
            command.add("--app-version");
            command.add(appVersionField.getText());
        }

        command.add("--type");
        command.add(packageType);

        if (!destDirField.getText().isEmpty()) {
            command.add("--dest");
            command.add(destDirField.getText());
        }

        if (prebuiltAppImageSelected) {
            command.add("--app-image");
            command.add(appImageField.getText());
        } else {
            command.add("--input");
            command.add(inputDirField.getText());

            command.add("--main-class");
            command.add(mainClassField.getText());

            command.add("--main-jar");
            command.add(mainJarField.getText());
        }

        if (!prebuiltAppImageSelected && !iconField.getText().isEmpty()) {
            String iconPath = iconField.getText();

            command.add("--icon");
            command.add(iconPath);
        }

        if (JdkLocator.isWindows() && !appImageSelected) {
            if (winShortcutCheckBox.isSelected()) {
                command.add("--win-shortcut");
            }

            if (winMenuCheckBox.isSelected()) {
                command.add("--win-menu");
            }

            if (winDirChooserCheckBox.isSelected()) {
                command.add("--win-dir-chooser");
            }

            if (winPerUserInstallCheckBox.isSelected()) {
                command.add("--win-per-user-install");
            }

            if (winShortcutPromptCheckBox.isSelected()) {
                command.add("--win-shortcut-prompt");
            }
        }

        if (JdkLocator.isWindows() && !prebuiltAppImageSelected && winConsoleCheckBox.isSelected()) {
            command.add("--win-console");
        }

        if (JdkLocator.isWindows() && !appImageSelected) {
            if (!upgradeUuidField.getText().isEmpty()) {
                command.add("--win-upgrade-uuid");
                command.add(upgradeUuidField.getText());
            }

            if (!menuGroupField.getText().isEmpty()) {
                command.add("--win-menu-group");
                command.add(menuGroupField.getText());
            }

            if (!winHelpUrlField.getText().isEmpty()) {
                command.add("--win-help-url");
                command.add(winHelpUrlField.getText());
            }

            if (!winUpdateUrlField.getText().isEmpty()) {
                command.add("--win-update-url");
                command.add(winUpdateUrlField.getText());
            }
        }

        if (!prebuiltAppImageSelected) {
            for (TextField field : javaOptionsFields) {
                if (!field.getText().isEmpty()) {
                    command.add("--java-options");
                    command.add(field.getText().trim());
                }
            }

            if (!addModulesField.getText().isEmpty()) {
                command.add("--add-modules");
                command.add(addModulesField.getText());
            }

            if (!modulePathField.getText().isEmpty()) {
                command.add("--module-path");
                command.add(modulePathField.getText());
            }

            if (!runtimeImageField.getText().isEmpty()) {
                command.add("--runtime-image");
                command.add(runtimeImageField.getText());
            }
        }

        if (!appImageSelected && !licenseField.getText().isEmpty()) {
            command.add("--license-file");
            command.add(licenseField.getText());
        }

        if (!descriptionField.getText().isEmpty()) {
            command.add("--description");
            command.add(descriptionField.getText());
        }

        if (!copyrightField.getText().isEmpty()) {
            command.add("--copyright");
            command.add(copyrightField.getText());
        }

        if (!vendorField.getText().isEmpty()) {
            command.add("--vendor");
            command.add(vendorField.getText());
        }


        return command;
    }

    private String formatCommandForDisplay(List<String> command) {
        StringBuilder commandStr = new StringBuilder();
        for (String part : command) {
            if (commandStr.length() > 0) {
                commandStr.append(" ");
            }
            commandStr.append(quoteForDisplay(part));
        }
        return commandStr.toString();
    }

    private String quoteForDisplay(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuoting = value.isEmpty() || value.chars().anyMatch(Character::isWhitespace) || value.contains("\"");
        String escapedValue = value.replace("\"", "\\\"");
        return needsQuoting ? "\"" + escapedValue + "\"" : escapedValue;
    }

    private String getCommandOptionValue(List<String> command, String option) {
        for (int i = 0; i < command.size() - 1; i++) {
            if (option.equals(command.get(i))) {
                return command.get(i + 1);
            }
        }
        return "";
    }

    private boolean isCommandUsingPrebuiltAppImage(List<String> command) {
        return !getCommandOptionValue(command, "--app-image").isEmpty();
    }

    private String getExpectedOutputPath(String destDir, String packageType, String appName) {
        if (isAppImageType(packageType)) {
            return destDir + File.separator + getAppImageDirectoryName(appName);
        }
        return destDir + File.separator + appName + "." + packageType;
    }

    private String getAppNameFromAppImageDirectory(File directory) {
        String directoryName = directory.getName();
        if (directoryName.endsWith(".app")) {
            return directoryName.substring(0, directoryName.length() - 4);
        }
        return directoryName;
    }

    private String getParentDirectoryPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        File file = new File(path);
        File parentFile = file.getParentFile();
        return parentFile == null ? "" : parentFile.getAbsolutePath();
    }

    private String getAppImageDirectoryName(String appName) {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return appName + ".app";
        }
        return appName;
    }

    private boolean isAppImageType(String packageType) {
        return PACKAGE_TYPE_APP_IMAGE.equals(packageType);
    }

    private boolean isJarSourceSelected() {
        return isAppImageType(packageTypeCombo.getValue()) || fromJarRadioButton.isSelected();
    }

    private boolean isImageSourceSelected() {
        return !isAppImageType(packageTypeCombo.getValue()) && fromAppImageRadioButton.isSelected();
    }

    private boolean isUsingPrebuiltAppImage(String packageType) {
        return !isAppImageType(packageType) && fromAppImageRadioButton.isSelected();
    }

    private String getRequiredFieldsMessage() {
        if (isImageSourceSelected()) {
            return "Please fill in Destination Directory, Application Name, and Existing App Image.";
        }
        return "Please fill in Destination Directory, Application Name, and the JAR fields (JAR File, Input Directory, Main Class, Main JAR).";
    }

    @FXML
    private void addJavaOption(ActionEvent event) {
        //Create a new HBox container for the TextField and Button
        HBox container = new HBox(5);
        container.getStyleClass().add("hbox");
        // Create a new TextField
        TextField newJavaOptionField = new TextField();
        newJavaOptionField.setPrefHeight(javaOptionsField.getPrefHeight());
        newJavaOptionField.getStyleClass().add("text-field");
        HBox.setHgrow(newJavaOptionField, javafx.scene.layout.Priority.ALWAYS);

        // Create a remove button
        Button removeButton = new Button("Remove");
        removeButton.getStyleClass().add("button");
        removeButton.getStyleClass().add("secondary-button");
        removeButton.setOnAction(e -> {
            javaOptionsFields.remove(newJavaOptionField);
            javaOptionsContainers.remove(container);
            javaOptionsContainer.getChildren().remove(container);
        });

        //Add components to the container
        container.getChildren().addAll(newJavaOptionField, removeButton);

        // Add the new field toour lists
        javaOptionsFields.add(newJavaOptionField);
        javaOptionsContainers.add(container);

// Add the container to the java options container
        javaOptionsContainer.getChildren().add(container);
    }

    private void showAlert(String title, String message) {
        showCustomDialog(title, title, message);
    }
}
