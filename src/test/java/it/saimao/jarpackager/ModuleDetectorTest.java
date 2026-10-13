package it.saimao.jarpackager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleDetectorTest {
    @TempDir Path temp;

    private Path fixture(String name, String source, Path dependency, Path output) throws Exception {
        return fixture(name, source, dependency, output, null);
    }

    private Path fixture(String name, String source, Path dependency, Path output, String descriptor) throws Exception {
        Path project = Files.createDirectories(temp.resolve("source-" + name));
        Path java = Files.writeString(project.resolve(name + ".java"), source);
        Path classes = Files.createDirectories(project.resolve("classes"));
        List<String> command = new ArrayList<>(List.of(JdkLocator.resolveHome().resolve("bin").resolve(JdkLocator.executable("javac")).toString(),
                "-d", classes.toString()));
        if (dependency != null) command.addAll(List.of("-cp", dependency.toString()));
        command.add(java.toString());
        if (descriptor != null) command.add(Files.writeString(project.resolve("module-info.java"), descriptor).toString());
        Process compiler = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(project.resolve("compile.log").toFile()).start();
        try {
            assertTrue(compiler.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, compiler.exitValue(), Files.readString(project.resolve("compile.log")));
        } finally { if (compiler.isAlive()) compiler.destroyForcibly(); }
        Files.createDirectories(output.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output)); var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
        return output;
    }

    @Test void detectsDirectJdkModulesInPathWithSpaces() throws Exception {
        Path input = Files.createDirectories(temp.resolve("My Application"));
        Path jar = fixture("App", "public class App { java.sql.Connection connection; javax.swing.JButton button; }", null, input.resolve("app.jar"));
        var result = ModuleDetector.detect(new ModuleDetector.Request(jar, input, "", input.resolve("dist")));
        assertTrue(result.modules().contains("java.sql"), result.details());
        assertTrue(result.modules().contains("java.desktop"));
    }

    @Test void followsTransitiveLibrariesInLibFolder() throws Exception {
        Path input = Files.createDirectories(temp.resolve("input"));
        Path dependency = fixture("Library", "public class Library { public static String value() { return java.sql.DriverManager.class.getName(); } }", null, input.resolve("lib/library.jar"));
        Path app = fixture("App", "public class App { public static void main(String[] args) { System.out.println(Library.value()); } }", dependency, input.resolve("app.jar"));
        var result = ModuleDetector.detect(new ModuleDetector.Request(app, input, "", input.resolve("dist")));
        assertTrue(result.modules().contains("java.sql"), result.details());
        Files.delete(dependency);
        IOException error = assertThrows(IOException.class, () -> ModuleDetector.detect(new ModuleDetector.Request(app, input, "", null)));
        assertTrue(error.getMessage().contains("dependencies"), error.getMessage());
    }

    @Test void resolvesNamedDependenciesFromModulePath() throws Exception {
        Path input = Files.createDirectories(temp.resolve("input"));
        Path library = fixture("Library", "package example; public class Library { public static String value() { return java.sql.DriverManager.class.getName(); } }",
                null, temp.resolve("modules/library.jar"), "module example.library { requires java.sql; exports example; }");
        Path app = fixture("App", "public class App { public static void main(String[] args) { System.out.println(example.Library.value()); } }", library, input.resolve("app.jar"));
        var result = ModuleDetector.detect(new ModuleDetector.Request(app, input, library.getParent().toString(), input.resolve("dist")));
        assertTrue(result.modules().contains("example.library"), result.details());
    }

    @Test void doesNotUseOldPackagesOrHiddenJarsAsDependencies() throws Exception {
        Path input = Files.createDirectories(temp.resolve("input"));
        Path source = Files.writeString(input.resolve("app.jar"), "main");
        Path lib = Files.createDirectories(input.resolve("lib")).resolve("dependency.jar");
        Files.writeString(lib, "dependency");
        Files.writeString(Files.createDirectories(input.resolve("dist")).resolve("old.jar"), "old");
        Files.writeString(Files.createDirectories(input.resolve(".cache")).resolve("hidden.jar"), "hidden");
        var libraries = ModuleDetector.libraries(new ModuleDetector.Request(source, input, "", input.resolve("dist")));
        assertEquals(List.of(input, lib), libraries);
    }

    @Test void rejectsNestedArchivesInsteadOfInventingCompleteDefaults() throws Exception {
        Path jar = temp.resolve("nested.jar");
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(jar))) {
            archive.putNextEntry(new JarEntry("BOOT-INF/lib/dependency.jar"));
            archive.write(new byte[]{0});
            archive.closeEntry();
        }
        IOException error = assertThrows(IOException.class, () -> ModuleDetector.detect(new ModuleDetector.Request(jar, temp, "", null)));
        assertTrue(error.getMessage().contains("nested JARs"));
    }

    @Test void replacesOnlyPreviousDefaultsAndKeepsManualModules() {
        assertEquals("jdk.crypto.ec,java.base,java.sql", ModuleDetector.merge("java.base, java.desktop, jdk.crypto.ec", List.of("java.base", "java.desktop"), List.of("java.base", "java.sql")));
        assertEquals("java.sql", ModuleDetector.merge("java.sql", List.of(), List.of("java.sql")));
    }

    @Test void ignoresWarningLinesWhenParsingOutput() {
        assertEquals(List.of("java.base", "java.sql"), ModuleDetector.parseModules("Warning: split package example\njava.base,java.sql\n"));
        assertTrue(ModuleDetector.parseModules("Error: missing dependencies").isEmpty());
    }
}
