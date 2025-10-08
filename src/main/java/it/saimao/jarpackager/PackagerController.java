package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
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

    // 添加对步骤界面的引用
    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;
    // Windows选项复选框
    @FXML
    private CheckBox winShortcutCheckBox;

    @FXML
    private CheckBox winMenuCheckBox;

    @FXML
    private CheckBox winDirChooserCheckBox;

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

    // 步骤导航方法
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
            showAlert(Alert.AlertType.WARNING, "Missing Required Fields",
                    "Please fill in all required fields (marked with *) before proceeding tothenext step.");
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

    //验证步骤1中的必填字段
    private boolean validateStep1Fields() {
        return !jarFileField.getText().isEmpty() &&
                !inputDirField.getText().isEmpty() &&
                !destDirField.getText().isEmpty() &&
                !appNameField.getText().isEmpty() &&
                !mainClassField.getText().isEmpty() &&
                !mainJarField.getText().isEmpty();
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
            showAlert(Alert.AlertType.INFORMATION, title, content);
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
// Extract information frommanifest
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

// Set main jar file name
                mainJarField.setText(jarFile.getAbsolutePath());

                // Set input directory
                inputDirField.setText(jarFile.getParent());

                // Set destination directoryto same as input by default
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
    private void browseMainJar(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Main JAR File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JARFiles", "*.jar"));
        File selectedFile = fileChooser.showOpenDialog(mainJarField.getScene().getWindow());
        if (selectedFile != null) {
            mainJarField.setText(selectedFile.getName());
        }
    }

    @FXML
    private void browseIcon(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Icon File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Icon Files", "*.ico", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("ICO Files", "*.ico"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPG Files", "*.jpg", "*.jpeg")
        );
        File selectedFile = fileChooser.showOpenDialog(iconField.getScene().getWindow());
        if (selectedFile != null) {
            String filePath = selectedFile.getAbsolutePath();
            String extension = getFileExtension(filePath).toLowerCase();

            // 如果不是ico文件，需要转换
            if (!extension.equals(".ico")) {
                iconField.setText(filePath + " (will be converted to ICO)");
                try {
                    IcoConverter.convertToIco(filePath, filePath + ".ico");
                    iconField.setText(filePath + ".ico");
                } catch (IOException e) {
                    showCustomDialog("Conversion Error", "Error converting to ICO", "Try other file or use ico directly");
                }

            } else {
                iconField.setText(filePath);
            }
        }
    }

    // 辅助方法：获取文件扩展名
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
        File selectedDirectory = directoryChooser.showDialog(modulePathField.getScene().getWindow());
        if (selectedDirectory != null) {
            modulePathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void packageApp(ActionEvent event) {
        //Validate inputs
        if (inputDirField.getText().isEmpty() ||
                appNameField.getText().isEmpty() ||
                mainClassField.getText().isEmpty() ||
                mainJarField.getText().isEmpty()) {

            showCustomDialog("Missing Required Fields", "Missing Required Fields",
                    "Please fill in all required fields (InputDirectory, Application Name, Main Class, MainJAR)");
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

        // Buildjpackage command
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
                    progressController.appendText("Common issues and solutions:\n");
                    progressController.appendText("1. Make sure the jpackage tool is installedand in your PATH\n");
                    progressController.appendText("2. Check that all filepaths arecorrect and accessible\n");
                    progressController.appendText("3. Ensure the main JAR file contains a proper manifest with Main-Classentry\n");
                    progressController.appendText("4. Verify that the input directory contains all necessaryfiles\n");
                    progressController.setFailed();
                    showCustomDialog("Packaging Failed", "Packaging Failed", "Packaging failed with exit code: " + exitCode +
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
                showCustomDialog("Execution Error", "Execution Error", "Error executing command:" + e.getMessage());
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

            // 检查是否需要转换图标格式
            if (iconPath.contains(" (will be converted to ICO)")) {
                // 移除标记文本，获取原始路径
                iconPath = iconPath.replace(" (will be converted to ICO)", "");
            }

            String extension = getFileExtension(iconPath).toLowerCase();

            // 如果不是ico文件，需要进行转换
            if (!extension.equals(".ico")) {
                //这里应该实现PNG/JPG到ICO的转换逻辑
                // 为简化示例，我们在这里只是给出提示
                // 在实际应用中，你可能需要使用图像处理库来完成转换
                showCustomDialog("Icon Conversion", "Icon Conversion Needed",
                        "The selected icon isnot in ICO format. You need to convert it to ICO format manually or implement automatic conversion.");
            }

            command.add("--icon");
            command.add(quoteIfHasSpace(iconPath));
        }

        // 添加Windows选项（根据复选框状态）
        if (winShortcutCheckBox.isSelected()) {
            command.add("--win-shortcut");
        }

        if (winMenuCheckBox.isSelected()) {
            command.add("--win-menu");
        }

        if (winDirChooserCheckBox.isSelected()) {
            command.add("--win-dir-chooser");
        }

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
                if (!option.trim().isEmpty()) {
                    command.add("--java-options");
                    command.add(quoteIfHasSpace(option.trim()));
                }
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

        System.out.println(command);

        return command;
    }

    // Helper method to add doublequotes onlyif the string contains spaces
    private String quoteIfHasSpace(String value) {
        if (value != null && value.contains(" ")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    //将PNG/JPG图像转换为ICO格式
    private String convertToIco(String imagePath) {
        try {
            // 获取临时目录
            String tempDir = System.getProperty("java.io.tmpdir");
            String fileName = new File(imagePath).getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String icoPath = tempDir + File.separator + baseName + ".ico";

            // 检查是否已存在转换后的ICO文件
            File icoFile = new File(icoPath);
            if (icoFile.exists()) {
                return icoPath;
            }

            // 使用IcoConverter进行转换
            IcoConverter.convertToIco(imagePath, icoPath);

            showCustomDialog("Success", "Icon Conversion",
                    "Image successfully converted to ICO format.\n\n" +
                            "Converted ICO saved to: " + icoPath);

            return icoPath;
        } catch (Exception e) {
            e.printStackTrace();
            showCustomDialog("Conversion Error", "Icon Conversion Failed",
                    "Error converting image to ICO format: " + e.getMessage() +
                            "\n\nUsing original image file instead.");
            return imagePath; // 出错时返回原始路径
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        showCustomDialog(title, title, message);
    }
}