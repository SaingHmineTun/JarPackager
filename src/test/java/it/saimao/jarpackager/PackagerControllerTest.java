package it.saimao.jarpackager;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PackagerControllerTest {
    @Test void macCommandsAndFxml() throws Exception {
        if (!JdkLocator.isMac()) return;
        Platform.startup(() -> {});
        FutureTask<Void> check = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("stepped-packager-view.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1040, 820);
            root.applyCss();
            root.layout();
            if (Boolean.getBoolean("packager.snapshots")) snapshot(root, "application");
            var nodes = loader.getNamespace();
            assertFalse(nodes.containsKey("jdkHomeField"));
            assertEquals("Check", ((Button) nodes.get("checkButton")).getText());
            TextField destination = (TextField) nodes.get("destDirField");
            assertEquals(PackagerController.destinationFor(System.getProperty("user.dir")), destination.getText());
            ((TextField) nodes.get("inputDirField")).setText("/tmp/home");
            assertEquals("/tmp/home/dist", destination.getText());
            ((TextField) nodes.get("appImageField")).setText("/tmp/images/Example.app");
            ((RadioButton) nodes.get("fromAppImageRadioButton")).setSelected(true);
            assertEquals("/tmp/images/dist", destination.getText());
            ((RadioButton) nodes.get("fromJarRadioButton")).setSelected(true);
            assertEquals("/tmp/home/dist", destination.getText());
            destination.setText("/tmp/custom");
            ((TextField) nodes.get("inputDirField")).setText("/tmp/another");
            assertEquals("/tmp/custom", destination.getText());
            @SuppressWarnings("unchecked") ComboBox<String> types = (ComboBox<String>) nodes.get("packageTypeCombo");
            assertEquals(List.of("dmg", "pkg", "app-image"), types.getItems());
            ((TextField) nodes.get("iconField")).setText("/tmp/icon.icns");
            var method = PackagerController.class.getDeclaredMethod("buildJPackageCommand");
            method.setAccessible(true);
            for (String type : types.getItems()) {
                types.setValue(type);
                @SuppressWarnings("unchecked") List<String> command = (List<String>) method.invoke(loader.getController());
                assertTrue(command.contains(type));
                assertTrue(command.contains("/tmp/icon.icns"));
                assertFalse(command.stream().anyMatch(s -> s.startsWith("--win-")));
                if (!type.equals("app-image")) {
                    ((RadioButton) nodes.get("fromAppImageRadioButton")).setSelected(true);
                    @SuppressWarnings("unchecked") List<String> imageCommand = (List<String>) method.invoke(loader.getController());
                    assertTrue(imageCommand.contains("--app-image"));
                    assertFalse(imageCommand.contains("--main-jar"));
                    assertFalse(imageCommand.contains("--icon"));
                    ((RadioButton) nodes.get("fromJarRadioButton")).setSelected(true);
                }
            }
            if (Boolean.getBoolean("packager.snapshots")) {
                var activate = PackagerController.class.getDeclaredMethod("activateStep", int.class);
                activate.setAccessible(true);
                for (int step = 2; step <= 3; step++) {
                    activate.invoke(loader.getController(), step);
                    root.applyCss(); root.layout();
                    snapshot(root, "step-" + step);
                }
            }
            var results = List.of(
                    new ToolCheckService.Result("JDK", true, "Java application tools", "JDK 27", ToolCheckService.JDK_HELP, ToolCheckService.JDK_URL),
                    new ToolCheckService.Result("jpackage", true, "Application images & installers", "27", ToolCheckService.JDK_HELP, ToolCheckService.JDK_URL),
                    new ToolCheckService.Result("Xcode Command Line Tools", false, "Custom DMG icons & signing setup", "SetFile not found", "Run xcode-select --install in Terminal, then check again.", "https://developer.apple.com/documentation/xcode/installing-the-command-line-tools/"));
            var show = PackagerController.class.getDeclaredMethod("showToolResults", List.class);
            show.setAccessible(true);
            show.invoke(loader.getController(), results);
            javafx.stage.Stage tools = (javafx.stage.Stage) javafx.stage.Window.getWindows().getLast();
            var toolRoot = tools.getScene().getRoot();
            toolRoot.applyCss(); toolRoot.layout();
            assertEquals(2, toolRoot.lookupAll(".tool-ready").size());
            assertEquals(1, toolRoot.lookupAll(".tool-missing").size());
            if (Boolean.getBoolean("packager.snapshots")) snapshot(toolRoot, "check");
            FXMLLoader help = new FXMLLoader(getClass().getResource("custom-dialog.fxml"));
            javafx.scene.Parent helpRoot = help.load();
            CustomDialogController helpController = help.getController();
            helpController.setTitle("Xcode Command Line Tools needs setup");
            helpController.setContent(results.getLast().help());
            var clicked = new java.util.concurrent.atomic.AtomicReference<String>();
            helpController.setHelpLink(results.getLast().url(), clicked::set);
            Hyperlink link = (Hyperlink) help.getNamespace().get("helpLink");
            assertTrue(link.isVisible());
            link.fire();
            assertEquals(results.getLast().url(), clicked.get());
            if (Boolean.getBoolean("packager.snapshots")) {
                new javafx.scene.Scene(helpRoot, 580, 450);
                helpRoot.applyCss(); helpRoot.layout();
                snapshot(helpRoot, "setup");
            }
            tools.close();
            return null;
        });
        Platform.runLater(check);
        try { check.get(30, TimeUnit.SECONDS); } finally { Platform.exit(); }
    }
    private static void snapshot(javafx.scene.Parent root, String name) throws Exception {
        var image = root.snapshot(null, null);
        var buffer = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++) buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffer, "png", new java.io.File("target/preview-" + name + ".png"));
    }
}
