package it.saimao.jarpackager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class IconConversionServiceTest {
    @TempDir Path temp;

    private Path png() throws IOException {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 200; x++) image.setRGB(x, y, 0xff365dd8);
        Path source = temp.resolve("My Icon.PNG");
        ImageIO.write(image, "png", source.toFile());
        return source;
    }

    private void writeTestIcns(Path iconset, Path output) throws IOException {
        try (var stream = new DataOutputStream(Files.newOutputStream(output))) {
            stream.writeInt(0x69636e73);
            stream.writeInt(20);
            stream.write(new byte[12]);
        }
    }

    @Test void generatesAllSizesAndDeletesOnlySelectedPngAfterSuccess() throws Exception {
        Path source = png();
        Path unrelated = Files.writeString(temp.resolve("keep.png"), "untouched");
        var result = IconConversionService.convert(source, true, (iconset, output) -> {
            assertTrue(Files.exists(source));
            try (var files = Files.list(iconset)) { assertEquals(10, files.count()); }
            var retina = ImageIO.read(iconset.resolve("icon_512x512@2x.png").toFile());
            assertEquals(1024, retina.getWidth());
            assertEquals(0, retina.getRGB(0, 0)); // transparent padding, no stretching
            assertEquals(0xff365dd8, retina.getRGB(512, 512));
            writeTestIcns(iconset, output);
        });
        assertTrue(result.sourceDeleted());
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(result.icon()));
        assertTrue(Files.exists(unrelated));
        try (var files = Files.list(temp)) { assertEquals(2, files.count()); }
    }

    @Test void converterFailurePreservesOriginalAndCleansTemporaryPngs() throws Exception {
        Path source = png();
        byte[] original = Files.readAllBytes(source);
        assertThrows(IOException.class, () -> IconConversionService.convert(source, true, (set, output) -> { throw new IOException("Tool failed"); }));
        assertArrayEquals(original, Files.readAllBytes(source));
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
    }

    @Test void invalidOutputNeverDeletesSource() throws Exception {
        Path source = png();
        assertThrows(IOException.class, () -> IconConversionService.convert(source, true, (set, output) -> Files.writeString(output, "broken")));
        assertTrue(Files.exists(source));
    }

    @Test void existingIconIsNotOverwritten() throws Exception {
        Path source = png();
        Path existing = Files.writeString(temp.resolve("My Icon.icns"), "keep existing icon");
        var result = IconConversionService.convert(source, true, this::writeTestIcns);
        assertEquals("My Icon-1.icns", result.icon().getFileName().toString());
        assertEquals("keep existing icon", Files.readString(existing));
    }

    @Test void sourceEditedDuringConversionIsKept() throws Exception {
        Path source = png();
        var result = IconConversionService.convert(source, true, (set, output) -> {
            writeTestIcns(set, output);
            Files.writeString(source, "new content");
        });
        assertFalse(result.sourceDeleted());
        assertEquals("new content", Files.readString(source));
    }

    @Test void windowsPngConversionAlsoRemovesSourceAfterSuccess() throws Exception {
        Path source = png();
        var result = IconConversionService.convert(source, false, this::writeTestIcns);
        assertTrue(result.sourceDeleted());
        assertTrue(result.icon().toString().endsWith(".ico"));
        byte[] icon = Files.readAllBytes(result.icon());
        assertEquals(1, icon[2]);
        assertTrue(icon.length > 22);
    }

    @Test @EnabledOnOs(OS.MAC)
    void realMacConversionRoundTripsThroughAppleTools() throws Exception {
        var result = IconConversionService.convert(png());
        IconConversionService.validateIcns(result.icon());
        Path extracted = temp.resolve("decoded.iconset");
        Process process = new ProcessBuilder("/usr/bin/iconutil", "-c", "iconset", "-o", extracted.toString(), result.icon().toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("decode.log").toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            var image = ImageIO.read(extracted.resolve("icon_512x512@2x.png").toFile());
            assertEquals(1024, image.getWidth());
            assertEquals(0, image.getRGB(0, 0));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
