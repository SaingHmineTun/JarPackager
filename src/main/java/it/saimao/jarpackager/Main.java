package it.saimao.jarpackager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("stepped-packager-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("JAR Packager Tool");
        stage.getIcons().add(new Image(Main.class.getResourceAsStream("app_icon.png")));
        stage.setScene(scene);
        stage.show();
        
        // 将primaryStage传递给控制器
        PackagerController controller = fxmlLoader.getController();
        controller.setPrimaryStage(stage);
    }
}