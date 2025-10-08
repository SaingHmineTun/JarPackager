module it.saimao.jarpackager {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires javafx.graphics;
    requires java.desktop;

    opens it.saimao.jarpackager to javafx.fxml;
    exports it.saimao.jarpackager;
}