package it.saimao.jarpackager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Enhanced PNG/JPG to ICO converter with proper scaling
 */
public class IcoConverter {

    public static void convertToIco(String inputImagePath, String outputIcoPath) throws IOException {
        BufferedImage img = ImageIO.read(new File(inputImagePath));
        if (img == null) {
            throw new IOException("Could not read image: " + inputImagePath);
        }

        // ICO supports up to 256x256
        BufferedImage scaledImg = scaleImage(img, 256);

        writeIcoFile(scaledImg, outputIcoPath);
    }

    /**
     * Scales the image to fit within the target size while maintaining aspect ratio
     */
    private static BufferedImage scaleImage(BufferedImage originalImg, int targetSize) {
        // Create the canvas at target size (256x256)
        BufferedImage scaledImg = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImg.createGraphics();

        // High-quality rendering settings
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Calculate aspect ratio to prevent stretching
        double ratio = Math.min((double) targetSize / originalImg.getWidth(), (double) targetSize / originalImg.getHeight());
        int width = (int) (originalImg.getWidth() * ratio);
        int height = (int) (originalImg.getHeight() * ratio);

        // Center the scaled image
        int x = (targetSize - width) / 2;
        int y = (targetSize - height) / 2;

        // DRAW WITH WIDTH AND HEIGHT to force scaling
        g2d.drawImage(originalImg, x, y, width, height, null);
        g2d.dispose();

        return scaledImg;
    }

    private static void writeIcoFile(BufferedImage img, String outputIcoPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputIcoPath);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // Header
            dos.writeByte(0);
            dos.writeByte(0);
            dos.writeShort(Short.reverseBytes((short) 1)); // Type 1 = ICO
            dos.writeShort(Short.reverseBytes((short) 1)); // 1 image

            // Directory Entry
            int width = img.getWidth();
            int height = img.getHeight();

            // 0 means 256px in ICO format
            dos.writeByte((byte) (width >= 256 ? 0 : width));
            dos.writeByte((byte) (height >= 256 ? 0 : height));
            dos.writeByte(0); // Palette
            dos.writeByte(0); // Reserved

            dos.writeShort(Short.reverseBytes((short) 1));  // Planes
            dos.writeShort(Short.reverseBytes((short) 32)); // Bit depth

            // Write PNG data into buffer to get size
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            dos.writeInt(Integer.reverseBytes(imageBytes.length));
            dos.writeInt(Integer.reverseBytes(22)); // Header (6) + Entry (16) = 22

            dos.write(imageBytes);
        }
    }
}