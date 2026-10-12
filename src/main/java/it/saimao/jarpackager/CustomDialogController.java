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

    @FXML private javafx.scene.control.Hyperlink helpLink;

    public void setHelpLink(String url, java.util.function.Consumer<String> openLink) {
        boolean visible = url != null && !url.isBlank();
        helpLink.setVisible(visible);
        helpLink.setManaged(visible);
        if (visible) {
            helpLink.setText("Open official installation guide ↗");
            helpLink.setOnAction(event -> openLink.accept(url));
        }
    }

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