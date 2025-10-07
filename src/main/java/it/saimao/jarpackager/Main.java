package it.saimao.jarpackager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("packager-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 700);
        stage.setTitle("JAR Packager Tool");
        stage.setScene(scene);
        stage.show();
        
        // 将primaryStage传递给控制器
        PackagerController controller = fxmlLoader.getController();
        controller.setPrimaryStage(stage);
    }
}