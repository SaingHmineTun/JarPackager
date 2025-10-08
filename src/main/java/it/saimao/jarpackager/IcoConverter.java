package it.saimao.jarpackager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * 简单的PNG/JPG到ICO转换器
 */
public class IcoConverter {

    /**
     * 将图像文件转换为ICO格式
     *
     * @param inputImagePath 输入图像路径 (PNG/JPG)
     * @param outputIcoPath  输出ICO路径
     * @throws IOException 如果读取或写入文件时出错
     */
    public static void convertToIco(String inputImagePath, String outputIcoPath) throws IOException {
        // 读取原始图像
        BufferedImage img = ImageIO.read(new File(inputImagePath));
        if (img == null) {
            throw new IOException("Could not read image: " + inputImagePath);
        }

        // 调整图像大小以适应ICO格式（通常为16x16, 32x32, 48x48等）
        BufferedImage scaledImg = createMultiResolutionImage(img);

        // 写入ICO文件
        writeIcoFile(scaledImg, outputIcoPath);
    }

    /**
     * 创建多分辨率图像
     */
    private static BufferedImage createMultiResolutionImage(BufferedImage originalImg) {
        // 对于简化实现，我们只创建一个适当大小的图像
        int size = Math.min(256, Math.max(originalImg.getWidth(), originalImg.getHeight()));
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        // 设置渲染提示以获得更好的图像质量
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 在新图像中居中绘制原始图像
        int x = (size - originalImg.getWidth()) / 2;
        int y = (size - originalImg.getHeight()) / 2;
        g2d.drawImage(originalImg, x, y, null);
        g2d.dispose();

        return img;
    }

    /**
     * 将图像写入ICO文件
     */
    private static void writeIcoFile(BufferedImage img, String outputIcoPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputIcoPath);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // ICO文件头
            dos.writeByte(0);  // Reserved, must be 0
            dos.writeByte(0);  // Reserved, must be 0
            dos.writeByte(1);  // Image type: 1 for ICO, 2 for CUR
            dos.writeByte(0);  // Image type: 1 for ICO, 2 for CUR

            // Number of images in the file (1 in our case)
            dos.writeByte(1);  // Number of images
            dos.writeByte(0);  // Number of images

            // 图像目录项
            int width = img.getWidth();
            int height = img.getHeight();

            dos.writeByte((byte) (width == 256 ? 0 : width));   // Width
            dos.writeByte((byte) (height == 256 ? 0 : height)); // Height
            dos.writeByte(0);  // Number of colors in palette (0 = no palette)
            dos.writeByte(0);  // Reserved

            dos.writeShort(1); // Color planes
            dos.writeShort(32); // Bits per pixel

            // 计算图像大小
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            int imageSize = imageBytes.length;

            dos.writeInt(Integer.reverseBytes(imageSize)); // Image size in bytes (little-endian)
            dos.writeInt(Integer.reverseBytes(22)); // Image offset (little-endian)

            // 写入实际图像数据 (PNG格式)
            dos.write(imageBytes);
        }
    }
}