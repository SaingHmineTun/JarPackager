package it.saimao.jarpackager;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ToolCheckServiceTest {
    private ToolCheckService.ProbeResult ok(String output) { return new ToolCheckService.ProbeResult(true, output); }
    private ToolCheckService.ProbeResult missing() { return new ToolCheckService.ProbeResult(false, "Not found"); }
    private ToolCheckService.Result named(List<ToolCheckService.Result> results, String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
    @Test void windowsNeedsWixForFullReadiness() {
        var results = ToolCheckService.check(ToolCheckService.Host.WINDOWS, Path.of("jdk"), "", command ->
                command[0].contains("jdk") ? ok("27") : missing());
        assertTrue(named(results, "JDK").ready());
        assertTrue(named(results, "jpackage").ready());
        assertFalse(named(results, "WiX Toolset").ready());
        assertTrue(named(results, "WiX Toolset").url().startsWith("https://"));
    }
    @Test void modernWixRequiresCompatibleJdk() {
        for (int version : List.of(21, 27)) {
            var results = ToolCheckService.check(ToolCheckService.Host.WINDOWS, Path.of("jdk"), "", command ->
                    command[0].equals("wix.exe") ? ok("5.0.2+123") : command[0].contains("jdk") ? ok("" + version) : missing());
            assertEquals(version >= 24, named(results, "WiX Toolset").ready());
        }
    }
    @Test void wixThreeNeedsBothTools() {
        for (boolean hasLight : List.of(false, true)) {
            var results = ToolCheckService.check(ToolCheckService.Host.WINDOWS, Path.of("jdk"), "", command -> {
                if (command[0].equals("candle.exe") || hasLight && command[0].equals("light.exe")) return ok("Windows Installer XML Toolset version 3.14.1");
                return command[0].contains("jdk") ? ok("21") : missing();
            });
            assertEquals(hasLight, named(results, "WiX Toolset").ready());
        }
    }
    @Test void missingJdkDoesNotHideOtherChecks() {
        var results = ToolCheckService.check(ToolCheckService.Host.MAC, null, "No full JDK", command -> ok("Available"));
        assertFalse(named(results, "JDK").ready());
        assertFalse(named(results, "jpackage").ready());
        assertTrue(named(results, "PKG tools").ready());
    }
    @Test void missingSetFileIsReportedEvenWithSelectedXcode() {
        var results = ToolCheckService.check(ToolCheckService.Host.MAC, Path.of("jdk"), "", command ->
                command[0].equals("/usr/bin/xcrun") ? missing() : ok("27"));
        assertFalse(named(results, "Xcode Command Line Tools").ready());
        assertTrue(named(results, "hdiutil").ready());
    }
    @Test void failedJpackageIsNotReady() {
        var results = ToolCheckService.check(ToolCheckService.Host.OTHER, Path.of("jdk"), "", command ->
                command[0].contains("jpackage") ? new ToolCheckService.ProbeResult(false, "27") : ok("27"));
        assertFalse(named(results, "jpackage").ready());
    }
    @Test void parsesVersionFormatsWithoutTreatingErrorsAsVersions() {
        assertEquals(27, ToolCheckService.majorVersion("27"));
        assertEquals(25, ToolCheckService.majorVersion("25.0.3"));
        assertEquals(3, ToolCheckService.majorVersion("Windows Installer XML Toolset version 3.14.1"));
        assertEquals(-1, ToolCheckService.majorVersion("tool missing"));
    }
}
