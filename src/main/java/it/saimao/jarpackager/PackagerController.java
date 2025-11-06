package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
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

    @FXML
    private TextField jarFileField;

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
    private CheckBox winShortcutCheckBox;

    @FXML
    private CheckBox winMenuCheckBox;

    @FXML
    private CheckBox winDirChooserCheckBox;

    @FXML
    private CheckBox winConsoleCheckBox;

    private final List<TextField> javaOptionsFields = new ArrayList<>();
    private final List<HBox> javaOptionsContainers = new ArrayList<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    public void initialize() {
        packageTypeCombo.getItems().addAll("exe", "msi");
        packageTypeCombo.setValue("exe");
        javaOptionsFields.add(javaOptionsField);
    }

    //步骤导航方法
    @FXML
    private void showStep1() {
        step1.setVisible(true);
        step2.setVisible(false);
        step3.setVisible(false);
    }

    @FXML
    private void showStep2() {
        //验证步骤1中的必填字段
        if (!validateStep1Fields()) {
            showAlert("Missing Required Fields", "Please fill in all requiredfields (marked with *) before proceeding tothenext step.");
            return;
        }

        step1.setVisible(false);
        step2.setVisible(true);
        step3.setVisible(false);
    }

    @FXML
    private void showStep3() {
        step1.setVisible(false);
        step2.setVisible(false);
        step3.setVisible(true);
    }

    private boolean validateStep1Fields() {
        return !jarFileField.getText().isEmpty() && !inputDirField.getText().isEmpty() && !destDirField.getText().isEmpty() && !appNameField.getText().isEmpty() && !mainClassField.getText().isEmpty() && !mainJarField.getText().isEmpty();
    }

    @FXML
    private void checkJdkInfo(ActionEvent event) {
        executorService.submit(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("java", "-version");
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder versionInfo = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    versionInfo.append(line).append("\n");
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Platform.runLater(() -> showCustomDialog("JDK Information", "Installed JDK Information", versionInfo.toString()));
                } else {
                    Platform.runLater(() -> showCustomDialog("Error", "Failed to get JDK information", "Error code: " + exitCode));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showCustomDialog("Error", "Failed to execute java -version", e.getMessage()));
            }
        });
    }

    private void showCustomDialog(String title, String header, String content) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("custom-dialog.fxml"));
            VBox dialogRoot = loader.load();

            CustomDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            controller.setTitle(header);
            controller.setContent(content);

            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            javafx.scene.Scene scene = new javafx.scene.Scene(dialogRoot);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(title, content);
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

                if (finalMainClass != null && !finalMainClass.isEmpty()) {
                    mainClassField.setText(finalMainClass);
                }

                // Set appname to jar name
                appNameField.setText(jarFile.getName().substring(0, jarFile.getName().lastIndexOf(".jar")));

                // Set main jar file name
                mainJarField.setText(jarFile.getAbsolutePath());

                // Set input directory
                inputDirField.setText(jarFile.getParent());

                // Set destinationdirectory to same as input by default
                destDirField.setText(jarFile.getParent());
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
    private void browseIcon(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select IconFile");
        fileChooser.setInitialDirectory(new File(inputDirField.getText()));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Icon Files", "*.ico", "*.png", "*.jpg", "*.jpeg"), new FileChooser.ExtensionFilter("ICO Files", "*.ico"), new FileChooser.ExtensionFilter("PNG Files", "*.png"), new FileChooser.ExtensionFilter("JPG Files", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(iconField.getScene().getWindow());
        if (selectedFile != null) {
            String filePath = selectedFile.getAbsolutePath();
            String extension = getFileExtension(filePath).toLowerCase();

            // 如果不是ico文件，需要转换
            if (!extension.equals(".ico")) {
                iconField.setText(filePath + "(will be converted to ICO)");
                try {
                    IcoConverter.convertToIco(filePath, filePath + ".ico");
                    iconField.setText(filePath + ".ico");
                } catch (IOException e) {
                    showCustomDialog("Conversion Error", "Error converting toICO", "Try other file or use ico directly");
                }

            } else {
                iconField.setText(filePath);
            }
        }
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
        fileChooser.setInitialDirectory(new File(inputDirField.getText()));
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
        if (inputDirField.getText().isEmpty() || appNameField.getText().isEmpty() || mainClassField.getText().isEmpty() || mainJarField.getText().isEmpty()) {

            showCustomDialog("Missing Required Fields", "Missing Required Fields", "Please fill in all required fields(InputDirectory, Application Name, Main Class, MainJAR)");
            return;
        }

        // 在后台线程中执行打包命令
        executorService.submit(this::executePackagingTask);
    }

    private void executePackagingTask() {
//创建并显示进度对话框
        ProgressController progressController = new ProgressController();
        Platform.runLater(() -> {
            progressController.showProgressDialog(primaryStage);
            progressController.appendText("Executing packaging command...\n\n");
        });

        //Buildj package command
        List<String> command = buildJPackageCommand();

        try {
            //在进度对话框中显示执行的命令
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
                    showCustomDialog("Success", "Packaging Completed", "Application packaged successfully!\nOutput file: " + outputFile);
                });
            } else {
                Platform.runLater(() -> {
                    progressController.appendText("\nPackaging failed with exit code: " + exitCode + "\n");
                    // 添加更多错误信息
                    progressController.appendText("Please check the above output for more details about the error.\n");
                    progressController.appendText("Common issuesand solutions:\n");
                    progressController.appendText("1. Makesure thejpackage tool is installedand in your PATH\n");
                    progressController.appendText("2. Check that all filepaths arecorrect and accessible\n");
                    progressController.appendText("3. Ensure the main JAR file contains a proper manifest withMain-Classentry\n");
                    progressController.appendText("4. Verifythat theinput directory contains all necessaryfiles\n");
                    progressController.setFailed();
                    showCustomDialog("Packaging Failed", "Packaging Failed", "Packaging failed with exit code: " + exitCode + ". Please check the progress dialog formore details.");
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
                showCustomDialog("Execution Error", "ExecutionError", "Error executing command:" + e.getMessage());
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
            String iconPath = iconField.getText();

            if (iconPath.contains("(will be converted to ICO)")) {
                iconPath = iconPath.replace(" (will be converted to ICO)", "");
            }

            String extension = getFileExtension(iconPath).toLowerCase();

            if (!extension.equals(".ico")) {
                showCustomDialog("Icon Conversion", "Icon Conversion Needed", "The selected icon isnot in ICOformat. You need to convert it to ICOformat manually or implement automatic conversion.");
            }

            command.add("--icon");
            command.add(quoteIfHasSpace(iconPath));
        }

        if (winShortcutCheckBox.isSelected()) {
            command.add("--win-shortcut");
        }

        if (winMenuCheckBox.isSelected()) {
            command.add("--win-menu");
        }

        if (winDirChooserCheckBox.isSelected()) {
            command.add("--win-dir-chooser");
        }

        if (winConsoleCheckBox.isSelected()) {
            command.add("--win-console");
        }

        if (!upgradeUuidField.getText().isEmpty()) {
            command.add("--win-upgrade-uuid");
            command.add(quoteIfHasSpace(upgradeUuidField.getText()));
        }

        if (!menuGroupField.getText().isEmpty()) {
            command.add("--win-menu-group");
            command.add(quoteIfHasSpace(menuGroupField.getText()));
        }

        for (TextField field : javaOptionsFields) {
            if (!field.getText().isEmpty()) {
                command.add("--java-options");
                command.add(quoteAlways(field.getText().trim()));
            }
        }

        if (!addModulesField.getText().isEmpty()) {
            command.add("--add-modules");
            command.add(quoteIfHasSpace(addModulesField.getText()));
        }

        if (!modulePathField.getText().isEmpty()) {
            command.add("--module-path");
            command.add(quoteIfHasSpace(modulePathField.getText()));
        }

        if (!runtimeImageField.getText().isEmpty()) {
            command.add("--runtime-image");
            command.add(quoteIfHasSpace(runtimeImageField.getText()));
        }

        if (!licenseField.getText().isEmpty()) {
            command.add("--license-file");
            command.add(quoteIfHasSpace(licenseField.getText()));
        }

        if (!descriptionField.getText().isEmpty()) {
            command.add("--description");
            command.add(quoteIfHasSpace(descriptionField.getText()));
        }

        if (!copyrightField.getText().isEmpty()) {
            command.add("--copyright");
            command.add(quoteIfHasSpace(copyrightField.getText()));
        }

        if (!vendorField.getText().isEmpty()) {
            command.add("--vendor");
            command.add(quoteIfHasSpace(vendorField.getText()));
        }



        System.out.println(command);

        return command;
    }

    //Helper method to add double quotes only if the string contains spaces
    private String quoteIfHasSpace(String value) {
        if (value != null && value.contains(" ")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    // Helper method toalways add double quotes
    private String quoteAlways(String value) {
        if (value != null) {
            return "\"" + value + "\"";
        }
        return value;
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