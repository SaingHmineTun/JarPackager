package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
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

    @FXML
    private TextField jarFileField;


    @FXML
    private TextField inputDirField;

    @FXML
    private
    TextField destDirField;

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
    private TextField javaOptionsField;

    @FXML
    private TextField addModulesField;

    // 添加对TabPane的引用
    @FXML
    private TabPane mainTabPane;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    public void initialize() {
        packageTypeCombo.getItems().addAll("exe", "msi");
        packageTypeCombo.setValue("exe");
    }

    @FXML
    private void browseJarFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select JAR File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        File selectedFile = fileChooser.showOpenDialog(jarFileField.getScene().getWindow());
        if (selectedFile != null) {
            jarFileField.setText(selectedFile.getAbsolutePath());
            analyzeJarFile();
        }
    }

    private void analyzeJarFile() {
        String jarFilePath = jarFileField.getText();
        if (jarFilePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "No JAR File", "Please select a JAR file first.");
            return;
        }

        File jarFile = new File(jarFilePath);
        if (!jarFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "File Not Found", "The selected JAR file does not exist.");
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

            // Update UI on JavaFX Application Thread
            Platform.runLater(() -> {

                if (finalMainClass != null && !finalMainClass.isEmpty()) {
                    mainClassField.setText(finalMainClass);
                }

                // Set main jar file name
                mainJarField.setText(jarFile.getAbsolutePath());

                // Set input directory
                inputDirField.setText(jarFile.getParent());

                // Set destination directory to same as input by default
                destDirField.setText(jarFile.getParent());
            });

            jar.close();
        } catch (Exception e) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "AnalysisError",
                        "Error analyzing JAR file: " + e.getMessage());
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
    private void browseMainJar(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Main JAR File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        File selectedFile = fileChooser.showOpenDialog(mainJarField.getScene().getWindow());
        if (selectedFile != null) {
            mainJarField.setText(selectedFile.getName());
        }
    }

    @FXML
    private void browseIcon(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Icon File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Icon Files", "*.ico"));
        File selectedFile = fileChooser.showOpenDialog(iconField.getScene().getWindow());
        if (selectedFile != null) {
            iconField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void browseLicense(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select License File");
        File selectedFile = fileChooser.showOpenDialog(licenseField.getScene().getWindow());
        if (selectedFile != null) {
            licenseField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void packageApp(ActionEvent event) {
//Validate inputs
        if (inputDirField.getText().isEmpty() ||
                appNameField.getText().isEmpty() ||
                mainClassField.getText().isEmpty() ||
                mainJarField.getText().isEmpty()) {

            showAlert(Alert.AlertType.ERROR, "Missing Required Fields",
                    "Please fill in all required fields (InputDirectory, Application Name, Main Class, Main JAR)");
            return;
        }

        // 在后台线程中执行打包命令
        executorService.submit(this::executePackagingTask);
    }

    private void executePackagingTask() {
        // 创建并显示进度对话框
        ProgressController progressController = new ProgressController();
        Platform.runLater(() -> {
            progressController.showProgressDialog(primaryStage);
            progressController.appendText("Executing packaging command...\n\n");
        });

        // Build jpackage command
        List<String> command = buildJPackageCommand();

        try {
            // 在进度对话框中显示执行的命令
            StringBuilder commandStr = new StringBuilder();
            for (String part : command) {
                commandStr.append(part).append(" ");
            }
            Platform.runLater(() -> {
                progressController.appendText("Command: ");
                progressController.appendText(commandStr.toString().trim() + "\n\n");
            });

            ProcessBuilder processBuilder = new ProcessBuilder(command);
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
                String destDir = destDirField.getText().isEmpty() ? inputDirField.getText() : destDirField.getText();
                String packageType = packageTypeCombo.getValue();
                String appName = appNameField.getText();
                String outputFile = destDir + File.separator + appName + "." + packageType;

                Platform.runLater(() -> {
                    progressController.appendText("\nPackaging completed successfully!\n");
                    progressController.appendText("Output file saved to: " + outputFile + "\n");
                    progressController.setCompleted();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Application packaged successfully!\nOutput file: " + outputFile);
                });
            } else {
                Platform.runLater(() -> {
                    progressController.appendText("\nPackaging failed with exit code: " + exitCode + "\n");
// 添加更多错误信息
                    progressController.appendText("Please checkthe above output for more details about the error.\n");
                    progressController.appendText("Common issues and solutions:\n");
                    progressController.appendText("1. Make sure the jpackage tool is installed and in your PATH\n");
                    progressController.appendText("2. Check that all file paths are correct and accessible\n");
                    progressController.appendText("3. Ensure the main JAR file contains a proper manifest with Main-Class entry\n");
                    progressController.appendText("4. Verify that the input directory contains all necessary files\n");
                    progressController.setFailed();
                    showAlert(Alert.AlertType.ERROR, "Packaging Failed", "Packaging failed with exit code: " + exitCode +
                            ". Please check the progress dialog for more details.");
                });
            }
        } catch (IOException | InterruptedException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            Platform.runLater(() -> {
                progressController.appendText("Error executing command: " + e.getMessage() + "\n");
                progressController.appendText("Stack trace:\n");
                progressController.appendText(sw.toString());
                progressController.setFailed();
                showAlert(Alert.AlertType.ERROR, "Execution Error", "Error executing command: " + e.getMessage());
            });
        }
    }

    private List<String> buildJPackageCommand() {
        List<String> command = new ArrayList<>();
        command.add("jpackage");

        command.add("--input");
        command.add(quoteIfHasSpace(inputDirField.getText()));

        command.add("--name");
        command.add(quoteIfHasSpace(appNameField.getText()));

        if (!appVersionField.getText().isEmpty()) {
            command.add("--app-version");
            command.add(quoteIfHasSpace(appVersionField.getText()));
        }

        command.add("--main-class");
        command.add(quoteIfHasSpace(mainClassField.getText()));

        command.add("--main-jar");
        command.add(quoteIfHasSpace(mainJarField.getText()));

        command.add("--type");
        command.add(packageTypeCombo.getValue());

        if (!destDirField.getText().isEmpty()) {
            command.add("--dest");
            command.add(quoteIfHasSpace(destDirField.getText()));
        }

        if (!iconField.getText().isEmpty()) {
            command.add("--icon");
            command.add(quoteIfHasSpace(iconField.getText()));
        }

        if (!vendorField.getText().isEmpty()) {
            command.add("--vendor");
            command.add(quoteIfHasSpace(vendorField.getText()));
        }

        if (!copyrightField.getText().isEmpty()) {
            command.add("--copyright");
            command.add(quoteIfHasSpace(copyrightField.getText()));
        }

        if (!descriptionField.getText().isEmpty()) {
            command.add("--description");
            command.add(quoteIfHasSpace(descriptionField.getText()));
        }

        if (!licenseField.getText().isEmpty()) {
            command.add("--license-file");
            command.add(quoteIfHasSpace(licenseField.getText()));
        }

        command.add("--win-shortcut");
        command.add("--win-menu");
        command.add("--win-dir-chooser");

        if (!upgradeUuidField.getText().isEmpty()) {
            command.add("--win-upgrade-uuid");
            command.add(quoteIfHasSpace(upgradeUuidField.getText()));
        }

        if (!menuGroupField.getText().isEmpty()) {
            command.add("--win-menu-group");
            command.add(quoteIfHasSpace(menuGroupField.getText()));
        }

        if (!javaOptionsField.getText().isEmpty()) {
            String[] options = javaOptionsField.getText().split(",");
            for (String option : options) {
                command.add("--java-options");
                command.add(quoteIfHasSpace(option.trim()));
            }
        }

        if (!addModulesField.getText().isEmpty()) {
            command.add("--add-modules");
            command.add(quoteIfHasSpace(addModulesField.getText()));
        }

        System.out.println(command);

        return command;
    }

    // Helper method to add double quotes onlyif the string contains spaces
    private String quoteIfHasSpace(String value) {
        if (value != null && value.contains(" ")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
}