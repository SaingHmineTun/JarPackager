package it.saimao.jarpackager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Static dependency analysis with the same JDK used for packaging. */
final class ModuleDetector {
    record Request(Path jar, Path input, String modulePath, Path destination) { }
    record Result(List<String> modules, String details) { }

    static Result detect(Request request) throws IOException, InterruptedException {
        return detect(request, JdkLocator.resolveHome());
    }

    static Result detect(Request request, Path jdkHome) throws IOException, InterruptedException {
        Path jar = request.jar.toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) throw new IOException("The main JAR does not exist: " + jar);
        try (JarFile archive = new JarFile(jar.toFile())) {
            if (archive.stream().anyMatch(entry -> entry.getName().endsWith(".jar"))) {
                throw new IOException("This archive contains nested JARs. Extract its libraries into the input folder (for example lib/) before detecting modules. Nested application launchers may also need special packaging configuration.");
            }
        }
        Path tool = jdkHome.resolve("bin").resolve(JdkLocator.executable("jdeps"));
        if (!JdkLocator.usable(tool)) throw new IOException("jdeps is missing from " + jdkHome + ". Install a full JDK, then use Check.");
        Properties release = new Properties();
        try (Reader reader = Files.newBufferedReader(jdkHome.resolve("release"))) { release.load(reader); }
        String version = release.getProperty("JAVA_VERSION", "").replace("\"", "");
        var matcher = Pattern.compile("^(\\d+)").matcher(version);
        if (!matcher.find()) throw new IOException("Cannot determine the packaging JDK version for multi-release JAR analysis.");
        List<String> command = new ArrayList<>(List.of(tool.toString(), "-J-Duser.language=en", "-J-Dfile.encoding=UTF-8",
                "--multi-release", matcher.group(1), "--recursive", "--print-module-deps"));
        List<Path> libraries = libraries(request);
        if (!libraries.isEmpty()) {
            command.add("--class-path");
            command.add(String.join(File.pathSeparator, libraries.stream().map(Path::toString).toList()));
        }
        if (!request.modulePath.isBlank()) {
            command.add("--module-path");
            command.add(request.modulePath.trim());
        }
        command.add(jar.toString());
        // Analyze every packaged library as a root too: jdeps' module-list mode
        // filters same-package references, which can span separate JARs.
        libraries.stream().filter(path -> Files.isRegularFile(path)).forEach(path -> command.add(path.toString()));
        List<String> missingCommand = new ArrayList<>(command);
        int analysisOption = missingCommand.indexOf("--print-module-deps");
        missingCommand.set(analysisOption, "--missing-deps");
        missingCommand.add(analysisOption + 1, "-filter:none");
        String missing = run(missingCommand);
        if (!missing.isBlank()) throw new IOException("jdeps reported unresolved dependencies or analysis warnings. Put dependency JARs in the input folder or lib/, or set Module path, then click Detect.\n\n" + missing);
        String output = run(command);
        List<String> modules = parseModules(output);
        if (modules.isEmpty()) throw new IOException("jdeps returned no module list. No defaults were added.\n\n" + output);
        return new Result(modules, "JDK: " + jdkHome + "\nJAR: " + jar + "\nPackaged libraries: " + (libraries.size() - 1) + "\n\n" + output);
    }

    private static String run(List<String> command) throws IOException, InterruptedException {
        Path log = Files.createTempFile("jarpackager-jdeps-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IOException("Module detection timed out. You can retry or enter modules manually.");
            String output;
            try (InputStream stream = Files.newInputStream(log)) { output = new String(stream.readNBytes(65536), StandardCharsets.UTF_8).strip(); }
            if (process.exitValue() != 0) throw new IOException("jdeps could not resolve all dependencies. Put dependency JARs in the input folder or lib/, or set Module path for named modules, then click Detect.\n\n" + output);
            return output;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try { Files.deleteIfExists(log); } catch (IOException ignored) { }
        }
    }

    static List<Path> libraries(Request request) throws IOException {
        Set<Path> libraries = new LinkedHashSet<>();
        Path input = request.input.toAbsolutePath().normalize();
        Path jar = request.jar.toAbsolutePath().normalize();
        Path destination = request.destination == null ? null : request.destination.toAbsolutePath().normalize();
        if (!Files.isDirectory(input)) throw new IOException("The input folder does not exist: " + input);
        libraries.add(input);
        Files.walkFileTree(input, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (!dir.equals(input) && (dir.equals(destination) || dir.getFileName().toString().startsWith(".")
                        || dir.getFileName().toString().endsWith(".app"))) return FileVisitResult.SKIP_SUBTREE;
                return Thread.currentThread().isInterrupted() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (!file.equals(jar) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) libraries.add(file);
                return Thread.currentThread().isInterrupted() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        return List.copyOf(libraries);
    }

    static List<String> parseModules(String output) {
        String identifier = "[A-Za-z_$][A-Za-z0-9_$]*";
        String module = identifier + "(?:\\." + identifier + ")*";
        return output.lines().map(String::strip).filter(line -> line.matches(module + "(?:," + module + ")*"))
                .reduce((a, b) -> b).map(line -> List.of(line.split(","))).orElse(List.of());
    }

    /** Replace earlier inferred defaults while preserving modules the user added. */
    static String merge(String current, List<String> previous, List<String> detected) {
        Set<String> modules = new LinkedHashSet<>();
        for (String name : current.split(",")) if (!name.isBlank()) modules.add(name.trim());
        modules.removeAll(previous);
        modules.addAll(detected);
        return String.join(",", modules);
    }
}
