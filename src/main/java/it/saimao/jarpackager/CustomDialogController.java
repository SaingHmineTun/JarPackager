package it.saimao.jarpackager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class CustomDialogController {

    @FXML
    private Label titleLabel;

    @FXML
    private TextArea contentTextArea;

    @FXML
    private Button okButton;

    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setContent(String content) {
        contentTextArea.setText(content);
    }

    @FXML
    private void onOkClicked() {
        dialogStage.close();
    }
}