package it.saimao.jarpackager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Converts into a temporary workspace; the source is deleted only after publication. */
final class IconConversionService {
    record Result(Path icon, boolean sourceDeleted, String note) { }
    @FunctionalInterface interface IcnsWriter { void write(Path iconset, Path output) throws IOException; }

    static boolean needsConversion(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return JdkLocator.isMac() ? name.endsWith(".png") : JdkLocator.isWindows()
                && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
    }

    static Result convert(Path source) throws IOException {
        return convert(source, JdkLocator.isMac(), IconConversionService::writeIcns);
    }

    static Result convert(Path source, boolean mac, IcnsWriter writer) throws IOException {
        source = source.toAbsolutePath().normalize();
        String filename = source.getFileName().toString();
        boolean png = filename.toLowerCase(Locale.ROOT).endsWith(".png");
        if (mac && !png) throw new IOException("Choose a PNG image to convert to ICNS.");
        byte[] original = Files.readAllBytes(source);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
        if (image == null) throw new IOException("This file is not a readable image. The original was kept.");
        Path workspace = Files.createTempDirectory(source.getParent(), ".jarpackager-icon-");
        String extension = mac ? ".icns" : ".ico";
        try {
            Path converted = workspace.resolve("converted" + extension);
            if (mac) {
                Path iconset = Files.createDirectory(workspace.resolve("Application.iconset"));
                for (int size : new int[]{16, 32, 128, 256, 512}) {
                    writeSize(image, size, iconset.resolve("icon_" + size + "x" + size + ".png"));
                    writeSize(image, size * 2, iconset.resolve("icon_" + size + "x" + size + "@2x.png"));
                }
                writer.write(iconset, converted);
                validateIcns(converted);
            } else {
                // Use the captured source, so conversion and deletion refer to the same image.
                Path captured = workspace.resolve("source.png");
                Files.write(captured, original);
                IcoConverter.convertToIco(captured.toString(), converted.toString());
                if (Files.size(converted) <= 22) throw new IOException("The ICO conversion produced an empty icon.");
            }
            String base = filename.substring(0, filename.lastIndexOf('.'));
            Path output;
            for (int number = 0; ; number++) {
                output = source.resolveSibling(base + (number == 0 ? "" : "-" + number) + extension);
                try { Files.move(converted, output); break; }
                catch (FileAlreadyExistsException ignored) { }
            }
            if (png) {
                try {
                    if (!java.util.Arrays.equals(original, Files.readAllBytes(source))) {
                        return new Result(output, false, "Icon saved. The PNG changed during conversion and was kept.");
                    }
                    Files.delete(source);
                    return new Result(output, true, "Icon saved. Original PNG removed.");
                } catch (IOException e) {
                    return new Result(output, false, "Icon saved, but the original PNG could not be removed: " + e.getMessage());
                }
            }
            return new Result(output, false, "Icon saved.");
        } finally {
            // Only remove files in the temporary workspace created by this conversion.
            try (var paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException ignored) { }
        }
    }

    private static void writeSize(BufferedImage original, int size, Path output) throws IOException {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min((double) size / original.getWidth(), (double) size / original.getHeight());
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            graphics.drawImage(original, (size - width) / 2, (size - height) / 2, width, height, null);
        } finally { graphics.dispose(); }
        if (!ImageIO.write(canvas, "png", output.toFile())) throw new IOException("PNG encoder is unavailable.");
    }

    static void validateIcns(Path file) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != 0x69636e73 || input.readInt() != Files.size(file) || Files.size(file) <= 16) {
                throw new IOException("The conversion did not produce a valid ICNS file. The PNG was kept.");
            }
        }
    }

    private static void writeIcns(Path iconset, Path output) throws IOException {
        Path log = iconset.getParent().resolve("conversion.log");
        Process process = new ProcessBuilder("/usr/bin/iconutil", "-c", "icns", "-o", output.toString(), iconset.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(45, TimeUnit.SECONDS)) throw new IOException("Icon conversion timed out. The PNG was kept.");
            if (process.exitValue() != 0) throw new IOException("ICNS conversion failed. The PNG was kept.\n" + Files.readString(log));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Icon conversion was interrupted. The PNG was kept.", e);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
