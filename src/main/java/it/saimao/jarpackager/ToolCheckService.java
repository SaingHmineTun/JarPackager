package it.saimao.jarpackager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Read-only checks. A present file alone is not proof that a tool can run. */
final class ToolCheckService {
    enum Host { WINDOWS, MAC, OTHER }
    record Result(String name, boolean ready, String scope, String detail, String help, String url) { }
    record ProbeResult(boolean ok, String output) { }
    @FunctionalInterface interface Probe { ProbeResult run(String... command); }

    static final String JDK_URL = "https://www.oracle.com/java/technologies/downloads/";
    static final String JDK_HELP = "Install a full JDK 25 or newer (Oracle or Zulu), not a JRE. Set JAVA_HOME to the JDK home folder and restart JAR Packager.\n\nWindows: use C:\\Program Files\\Java\\jdk-27, for example. Add %JAVA_HOME%\\bin to PATH to use the tools in Command Prompt. Oracle's javapath directory does not expose every JDK tool.\n\nmacOS: the JDK home is usually /Library/Java/JavaVirtualMachines/<jdk>.jdk/Contents/Home. The app also discovers installed JDKs automatically.";
    private static final String APPLE_URL = "https://developer.apple.com/documentation/xcode/installing-the-command-line-tools/";
    private static final String APPLE_HELP = "Open Terminal and run:\n\nxcode-select --install\n\nComplete the Apple installer, then click Check again. If Xcode is already installed, open it once and finish setup. To select full Xcode, run:\n\nsudo xcode-select --switch /Applications/Xcode.app/Contents/Developer\n\nCommand Line Tools are needed for custom DMG icons and signing; plain unsigned packages can work without them.";

    static Host host() { return JdkLocator.isWindows() ? Host.WINDOWS : JdkLocator.isMac() ? Host.MAC : Host.OTHER; }

    static List<Result> check() {
        Path home = null;
        String error = "";
        try { home = JdkLocator.resolveHome(); } catch (IOException | RuntimeException e) { error = e.getMessage(); }
        return check(host(), home, error, ToolCheckService::probe);
    }

    static List<Result> check(Host host, Path home, String discoveryError, Probe probe) {
        List<Result> results = new ArrayList<>();
        String suffix = host == Host.WINDOWS ? ".exe" : "";
        ProbeResult java = home == null ? new ProbeResult(false, discoveryError) : probe.run(home.resolve("bin/java" + suffix).toString(), "-version");
        ProbeResult javac = home == null ? java : probe.run(home.resolve("bin/javac" + suffix).toString(), "-version");
        results.add(new Result("JDK", java.ok && javac.ok, "Java application tools", java.output + "\n" + javac.output + (home == null ? "" : "\n" + home), JDK_HELP, JDK_URL));
        ProbeResult packaging = home == null ? java : probe.run(home.resolve("bin/jpackage" + suffix).toString(), "--version");
        int version = majorVersion(packaging.output);
        results.add(new Result("jpackage", packaging.ok && version >= 16, "Application images & installers", packaging.output + (home == null ? "" : "\n" + home.resolve("bin/jpackage" + suffix)), JDK_HELP, JDK_URL));
        ProbeResult jlink = home == null ? java : probe.run(home.resolve("bin/jlink" + suffix).toString(), "--version");
        results.add(new Result("jlink", jlink.ok, "Create a bundled Java runtime", jlink.output, JDK_HELP, JDK_URL));
        if (host == Host.WINDOWS) results.add(checkWix(version, probe));
        if (host == Host.MAC) {
            String systemHelp = "This tool ships with macOS. Install available macOS updates and restart. If the tool is missing or cannot run, repair your macOS installation using Apple's recovery instructions. JAR Packager does not install or modify system tools.";
            String systemUrl = "https://support.apple.com/guide/mac-help/reinstall-macos-mchlp1599/mac";
            results.add(result("hdiutil", "DMG disk images", probe.run("/usr/bin/hdiutil", "help"), systemHelp, systemUrl));
            ProbeResult pkgbuild = probe.run("/usr/bin/pkgbuild");
            ProbeResult productbuild = probe.run("/usr/bin/productbuild");
            results.add(result("PKG tools", "pkgbuild & productbuild", new ProbeResult(pkgbuild.ok && productbuild.ok, pkgbuild.output + "\n" + productbuild.output), systemHelp, systemUrl));
            ProbeResult selected = probe.run("/usr/bin/xcode-select", "-p");
            ProbeResult setfile = probe.run("/usr/bin/xcrun", "--find", "SetFile");
            results.add(result("Xcode Command Line Tools", "Custom DMG icons & signing setup", new ProbeResult(selected.ok && setfile.ok, selected.output + "\n" + setfile.output), APPLE_HELP, APPLE_URL));
        }
        return List.copyOf(results);
    }

    private static Result checkWix(int jdkVersion, Probe probe) {
        ProbeResult modern = probe.run("wix.exe", "--version");
        boolean modernReady = jdkVersion >= 24 && modern.ok && majorVersion(modern.output) >= 4;
        ProbeResult candle = probe.run("candle.exe", "-?");
        ProbeResult light = probe.run("light.exe", "-?");
        boolean legacyReady = candle.ok && light.ok && majorVersion(candle.output) == 3 && majorVersion(light.output) == 3;
        String detail = modernReady ? "WiX " + modern.output : legacyReady ? candle.output + "\n" + light.output
                : "No compatible, runnable WiX toolset found.\n" + modern.output + "\n" + candle.output + "\n" + light.output;
        String help = "WiX builds Windows EXE/MSI installers. It is not needed for app-image output.\n\nWith JDK 24 or newer, install WiX using the official instructions below (requires the .NET SDK). Ensure the folder containing wix.exe is on PATH; global .NET tools usually live in %USERPROFILE%\\.dotnet\\tools.\n\nFor older JDKs, install WiX 3 and add its bin folder to PATH. Both candle.exe and light.exe must run successfully.\n\nRestart JAR Packager after installation or PATH changes, then click Check again.";
        return new Result("WiX Toolset", modernReady || legacyReady, "Windows EXE & MSI installers", detail, help, "https://wixtoolset.org/docs/intro/");
    }

    private static Result result(String name, String scope, ProbeResult probe, String help, String url) {
        return new Result(name, probe.ok, scope, probe.output, help, url);
    }

    static int majorVersion(String output) {
        var matcher = Pattern.compile("(?<![\\d.])(\\d+)(?:\\.\\d+)+(?:[+\\-\\w.]*)|^\\s*(\\d+)\\s*$").matcher(output);
        if (!matcher.find()) return -1;
        try { return Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2)); }
        catch (NumberFormatException e) { return -1; }
    }

    static void configurePackagingEnvironment(ProcessBuilder builder) {
        if (!JdkLocator.isWindows()) return;
        // Windows environment keys are case-insensitive; keep the original key.
        String key = builder.environment().keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        builder.environment().put(key, String.join(java.io.File.pathSeparator,
                windowsToolDirectories().stream().map(Path::toString).toList()));
    }

    private static List<Path> windowsToolDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        String path = System.getenv("PATH");
        if (path != null) for (String entry : path.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) try { directories.add(Path.of(entry.replace("\"", ""))); }
            catch (InvalidPathException ignored) { }
        }
        directories.add(Path.of(System.getProperty("user.home"), ".dotnet", "tools"));
        String wixHome = System.getenv("WIX");
        if (wixHome != null && !wixHome.isBlank()) {
            try { directories.add(Path.of(wixHome, "bin")); } catch (InvalidPathException ignored) { }
        }
        for (String env : List.of("ProgramFiles", "ProgramFiles(x86)")) {
            String base = System.getenv(env);
            if (base == null) continue;
            try (var children = Files.list(Path.of(base))) {
                children.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("wix toolset"))
                        .sorted(Comparator.reverseOrder()).forEach(p -> directories.add(p.resolve("bin")));
            } catch (IOException | InvalidPathException ignored) { }
        }
        return directories.stream().filter(Files::isDirectory).toList();
    }

    static ProbeResult probe(String... command) {
        Path log = null;
        Process process = null;
        try {
            log = Files.createTempFile("jarpackager-check-", ".log");
            String[] resolved = command.clone();
            if (JdkLocator.isWindows() && Set.of("wix.exe", "candle.exe", "light.exe").contains(command[0])) {
                for (Path directory : windowsToolDirectories()) {
                    Path candidate = directory.resolve(command[0]);
                    if (JdkLocator.usable(candidate)) { resolved[0] = candidate.toString(); break; }
                }
            }
            ProcessBuilder builder = new ProcessBuilder(resolved).redirectErrorStream(true).redirectOutput(log.toFile());
            configurePackagingEnvironment(builder);
            process = builder.start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) return new ProbeResult(false, command[0] + " did not respond within 8 seconds.");
            String output;
            try (InputStream input = Files.newInputStream(log)) { output = new String(input.readNBytes(16000), StandardCharsets.UTF_8).strip(); }
            // Apple package builders have no version/help query; no arguments prints
            // their usage with exit 1. Recognize only those two known system tools.
            boolean appleUsage = command.length == 1 && process.exitValue() == 1
                    && ((command[0].equals("/usr/bin/pkgbuild") && output.startsWith("Usage: pkgbuild "))
                    || (command[0].equals("/usr/bin/productbuild") && output.startsWith("Usage: productbuild ")));
            return new ProbeResult(process.exitValue() == 0 || appleUsage, output.isBlank() ? command[0] + " (exit " + process.exitValue() + ")" : output);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new ProbeResult(false, command[0] + ": " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (log != null) try { Files.deleteIfExists(log); } catch (IOException ignored) { }
        }
    }
}
