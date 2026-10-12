package it.saimao.jarpackager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JdkLocatorTest {
    @TempDir Path temp;
    private Path jdk(Path home) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        for (String name : List.of("java", "javac")) {
            Path tool = Files.createFile(home.resolve("bin").resolve(JdkLocator.executable(name)));
            assertTrue(tool.toFile().setExecutable(true));
        }
        return home;
    }
    @Test void discoversJdkWithSpacesWithoutJpackageOnPath() throws Exception {
        Path home = jdk(temp.resolve("Oracle JDK 27"));
        assertEquals(home, JdkLocator.firstHome(List.of(home)));
    }
    @Test void ignoresStaleHomeAndFindsInstalledJdk() throws Exception {
        Path home = jdk(temp.resolve("installed"));
        assertEquals(home, JdkLocator.firstHome(List.of(temp.resolve("removed"), home)));
    }
    @Test void acceptsMacBundle() throws Exception {
        Path bundle = temp.resolve("jdk-27.jdk");
        Path home = jdk(bundle.resolve("Contents/Home"));
        assertEquals(home, JdkLocator.firstHome(List.of(bundle)));
    }
    @Test void ignoresRuntimeWithoutCompiler() {
        assertNull(JdkLocator.firstHome(List.of(temp)));
    }
}
