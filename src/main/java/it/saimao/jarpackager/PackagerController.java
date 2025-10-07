package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class PackagerController {

    @FXML
    private TextField jarFileField;

    @FXML
    private ProgressBar analysisProgress;

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
    private TextArea outputArea;

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
        }
    }

    @FXML
    private void analyzeJarFile(ActionEvent event) {
        String jarFilePath = jarFileField.getText();
        if (jarFilePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "NoJAR File", "Please select a JAR file first.");
            return;
        }

        File jarFile = new File(jarFilePath);
        if (!jarFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "File Not Found", "Theselected JAR file does not exist.");
            return;
        }

        // Show progress indicator
        analysisProgress.setVisible(true);
        analysisProgress.setManaged(true);
        analysisProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

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
                mainJarField.setText(jarFile.getName());

                // Set input directory
                inputDirField.setText(jarFile.getParent());
                
                // Set destination directory to same as input by default
                destDirField.setText(jarFile.getParent());

                hideProgressIndicator();
                showAlert(Alert.AlertType.INFORMATION, "Analysis Complete", "JAR file analyzedsuccessfully. Fields populated where possible.");
            });

            jar.close();
        } catch (Exception e) {
            Platform.runLater(() -> {
                hideProgressIndicator();
                showAlert(Alert.AlertType.ERROR, "Analysis Error",
                        "Error analyzing JAR file: " + e.getMessage());
            });
        }
    }

    private MavenInfo getMavenInfoForJar(File jarFile) {
        try {
            // Look for pom.xml in the same directory as the JAR file
            File jarDirectory = jarFile.getParentFile();
            File pomFile = new File(jarDirectory, "pom.xml");

            if (!pomFile.exists()) {
                // Try to find pom.xml in parent directories
                pomFile = findPomFile(jarDirectory);
            }

            if (pomFile != null && pomFile.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(pomFile);
                document.getDocumentElement().normalize();

                String version = null;
                String groupId = null;
                String artifactId = null;
                String name = null;

                NodeList versionNodes = document.getElementsByTagName("version");
                if (versionNodes.getLength() > 0) {
                    Node versionNode = versionNodes.item(0);
                    if (versionNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element versionElement = (Element) versionNode;
                        version = versionElement.getTextContent();
                    }
                }

                NodeList groupIdNodes = document.getElementsByTagName("groupId");
                if (groupIdNodes.getLength() > 0) {
                    Node groupIdNode = groupIdNodes.item(0);
                    if (groupIdNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element groupIdElement = (Element) groupIdNode;
                        groupId = groupIdElement.getTextContent();
                    }
                }

                NodeList artifactIdNodes = document.getElementsByTagName("artifactId");
                if (artifactIdNodes.getLength() > 0) {
                    Node artifactIdNode = artifactIdNodes.item(0);
                    if (artifactIdNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element artifactIdElement = (Element) artifactIdNode;
                        artifactId = artifactIdElement.getTextContent();
                    }
                }

                NodeList nameNodes = document.getElementsByTagName("name");
                if (nameNodes.getLength() > 0) {
                    Node nameNode = nameNodes.item(0);
                    if (nameNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element nameElement = (Element) nameNode;
                        name = nameElement.getTextContent();
                    }
                }

                return new MavenInfo(version, groupId, artifactId, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private File findPomFile(File directory) {
        if (directory == null) {
            return null;
        }

        File pomFile = new File(directory, "pom.xml");
        if (pomFile.exists()) {
            return pomFile;
        }

        // Look in parent directory
        return findPomFile(directory.getParentFile());
    }

    private String getArtifactIdFromFileName(String fileName) {
        if (fileName.endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4); // Remove .jar extension
        }
        return fileName;
    }

    private void hideProgressIndicator() {
        analysisProgress.setVisible(false);
        analysisProgress.setManaged(false);
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
        fileChooser.setTitle("SelectMainJAR File");
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
            iconField.setText(selectedFile.getName());
        }
    }

    @FXML
    private void browseLicense(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select License File");
        File selectedFile = fileChooser.showOpenDialog(licenseField.getScene().getWindow());
        if (selectedFile != null) {
            licenseField.setText(selectedFile.getName());
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
                    "Please fill inall required fields (Input Directory,Application Name, Main Class, Main JAR)");
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
                    progressController.appendText("Please check the above output for more details about the error.\n");
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
        command.add("\"" + inputDirField.getText() + "\"");

        command.add("--name");
        command.add("\"" + appNameField.getText() + "\"");

        if (!appVersionField.getText().isEmpty()) {
            command.add("--app-version");
            command.add("\"" + appVersionField.getText() + "\"");
        }

        command.add("--main-class");
        command.add("\"" + mainClassField.getText() + "\"");

        command.add("--main-jar");
        command.add("\"" + mainJarField.getText() + "\"");

        command.add("--type");
        command.add(packageTypeCombo.getValue());

        if (!destDirField.getText().isEmpty()) {
            command.add("--dest");
            command.add("\"" + destDirField.getText() + "\"");
        }

        if (!iconField.getText().isEmpty()) {
            command.add("--icon");
            command.add("\"" + iconField.getText() + "\"");
        }

        if (!vendorField.getText().isEmpty()) {
            command.add("--vendor");
            command.add("\"" + vendorField.getText() + "\"");
        }

        if (!copyrightField.getText().isEmpty()) {
            command.add("--copyright");
            command.add("\"" + copyrightField.getText() + "\"");
        }

        if (!descriptionField.getText().isEmpty()) {
            command.add("--description");
            command.add("\"" + descriptionField.getText() + "\"");
        }

        if (!licenseField.getText().isEmpty()) {
            command.add("--license-file");
            command.add("\"" + licenseField.getText() + "\"");
        }

        command.add("--win-shortcut");
        command.add("--win-menu");
        command.add("--win-dir-chooser");

        if (!upgradeUuidField.getText().isEmpty()) {
            command.add("--win-upgrade-uuid");
            command.add("\"" + upgradeUuidField.getText() + "\"");
        }

        if (!menuGroupField.getText().isEmpty()) {
            command.add("--win-menu-group");
            command.add("\"" + menuGroupField.getText() + "\"");
        }

        if (!javaOptionsField.getText().isEmpty()) {
            String[] options = javaOptionsField.getText().split(",");
            for (String option : options) {
                command.add("--java-options");
                command.add("\"" + option.trim() + "\"");
            }
        }

        if (!addModulesField.getText().isEmpty()) {
            command.add("--add-modules");
            command.add("\"" + addModulesField.getText() + "\"");
        }

        System.out.println(command);

        return command;
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

    // Inner class to hold Maven information
    private static class MavenInfo {
        public final String version;
        public final String groupId;
        public final String artifactId;
        public final String name;

        public MavenInfo(String version, String groupId, String artifactId, String name) {
            this.version = version;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.name = name;
        }
    }

    // Inner class to handle progress dialog
    private static class ProgressController {
        private Stage dialog;
        private TextArea textArea;
        private ProgressBar progressBar;
        private Label statusLabel;

        public void showProgressDialog(Stage primaryStage) {
            dialog = new Stage();
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("Packaging Progress");
            
            statusLabel = new Label("Packaging in progress...");
            progressBar = new ProgressBar();
            progressBar.setPrefWidth(400);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            
            textArea = new TextArea();
            textArea.setPrefRowCount(15);
            textArea.setPrefColumnCount(50);
            textArea.setEditable(false);
            
            VBox vbox = new VBox(10);
            vbox.setPadding(new javafx.geometry.Insets(10));
            vbox.getChildren().addAll(statusLabel, progressBar, textArea);
            
            dialog.setScene(new javafx.scene.Scene(vbox));
            dialog.setResizable(true);
            dialog.show();
        }
        
        public void appendText(String text) {
            if (textArea != null) {
                textArea.appendText(text);
            }
        }
        
        public void setCompleted() {
            if (statusLabel != null) {
                statusLabel.setText("Packaging completed successfully!");
            }
            if (progressBar != null) {
                progressBar.setProgress(1.0);
            }
        }
        
        public void setFailed() {
            if (statusLabel != null) {
                statusLabel.setText("Packaging failed!");
            }
            if (progressBar != null) {
                progressBar.setProgress(0);
            }
        }
        
        public void close() {
            if (dialog != null) {
                dialog.close();
            }
        }
    }
}