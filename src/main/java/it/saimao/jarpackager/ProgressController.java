package it.saimao.jarpackager;


import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

// Inner class to handle progress dialog
public class ProgressController {
    private Stage dialog;
    private TextArea textArea;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;

    public void showProgressDialog(Stage primaryStage) {
        dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle("Packaging Progress");

        statusLabel = new Label("Packaging in progress...");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(50);
        textArea.setEditable(false);

        VBox vbox = new VBox(20);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(10));
        vbox.getChildren().addAll(statusLabel, progressIndicator, textArea);

        // 加载CSS样式
        try {
            Scene scene = new Scene(vbox, 500, 500);
            scene.getStylesheets().add(getClass().getResource("dialog.css").toExternalForm());
            dialog.setScene(scene);
        } catch (Exception e) {
            // 如果加载CSS失败，使用内联样式作为备选
            vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #f5f7fa, #e4edf9);");
            statusLabel.setStyle("-fx-font-weight:bold; -fx-text-fill: #3498db;");
            progressIndicator.setStyle("-fx-accent: #2ecc71;");
            textArea.setStyle("-fx-border-color: #bdc3c7; -fx-background-color: white; -fx-background-radius: 4;");
            dialog.setScene(new Scene(vbox, 500, 500));
        }

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
        if (progressIndicator != null) {
            progressIndicator.setProgress(1.0);
        }
    }

    public void setFailed() {
        if (statusLabel != null) {
            statusLabel.setText("Packaging failed!");
        }
        if (progressIndicator != null) {
            progressIndicator.setProgress(0);
        }
    }

    public void close() {
        if (dialog != null) {
            dialog.close();
        }
    }
}