package it.saimao.jarpackager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Resolves a full installed JDK, independently of shell aliases and Oracle javapath. */
final class JdkLocator {
    static boolean isWindows() { return System.getProperty("os.name", "").startsWith("Windows"); }
    static boolean isMac() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"); }
    static String executable(String name) { return name + (isWindows() ? ".exe" : ""); }

    static Path resolve() throws IOException {
        Path home = resolveHome();
        Path tool = home.resolve("bin").resolve(executable("jpackage"));
        if (usable(tool)) return tool;
        throw new IOException("The installed JDK at " + home + " does not include jpackage. Install a full JDK 25 or newer, set JAVA_HOME to its home folder, and restart the app.");
    }

    static Path resolveHome() throws IOException {
        List<Path> homes = new ArrayList<>();
        add(homes, System.getenv("JAVA_HOME"));
        add(homes, System.getProperty("java.home"));
        String path = System.getenv("PATH");
        if (path != null) for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) continue;
            try {
                Path bin = Path.of(entry.replace("\"", ""));
                if (bin.getParent() != null) homes.add(bin.getParent());
                for (String tool : List.of("java", "javac", "jpackage")) {
                    try { homes.add(bin.resolve(executable(tool)).toRealPath().getParent().getParent()); }
                    catch (IOException ignored) { }
                }
            } catch (InvalidPathException ignored) { }
        }
        Path found = firstHome(homes);
        if (found != null) return found;

        List<Path> roots = new ArrayList<>();
        if (isWindows()) {
            for (String env : List.of("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")) {
                String base = System.getenv(env);
                if (base != null) for (String vendor : List.of("Java", "Zulu", "Eclipse Adoptium", "Microsoft", "BellSoft")) roots.add(Path.of(base, vendor));
            }
        } else if (isMac()) {
            roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
            roots.add(Path.of(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines"));
        } else roots.add(Path.of("/usr/lib/jvm"));
        for (Path root : roots) {
            try (Stream<Path> entries = Files.list(root)) {
                found = firstHome(entries.sorted(Comparator.reverseOrder()).toList());
                if (found != null) return found;
            } catch (IOException ignored) { }
        }
        throw new IOException("No full JDK was found. Install Oracle JDK or Zulu JDK 25 or newer, set JAVA_HOME to its home directory, and restart the app. Oracle's javapath folder and a JRE are not full JDKs.");
    }

    static Path firstHome(List<Path> homes) {
        for (Path home : homes) {
            for (Path candidate : List.of(home, home.resolve("Contents/Home"))) {
                if (usable(candidate.resolve("bin").resolve(executable("java")))
                        && usable(candidate.resolve("bin").resolve(executable("javac")))) return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static boolean usable(Path path) { return Files.isRegularFile(path) && Files.isExecutable(path); }

    private static void add(List<Path> paths, String value) {
        if (value != null && !value.isBlank()) {
            try { paths.add(Path.of(value.replace("\"", ""))); }
            catch (InvalidPathException ignored) { }
        }
    }
}
