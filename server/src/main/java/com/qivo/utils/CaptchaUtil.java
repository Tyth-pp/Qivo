package com.qivo.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * 验证码生成工具 —— 生成 4 位数字验证码
 */
public final class CaptchaUtil {
    private CaptchaUtil() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 4 位数字验证码
     */
    public static String generateCode() {
        int code = RANDOM.nextInt(9000) + 1000;
        return String.valueOf(code);
    }

    public static String image(String code) {
        BufferedImage image = new BufferedImage(128, 42, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(242, 246, 244));
            g.fillRect(0, 0, 128, 42);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(
                        new Color(
                                20 + RANDOM.nextInt(90),
                                50 + RANDOM.nextInt(80),
                                30 + RANDOM.nextInt(90)));
                g.drawString(code.substring(i, i + 1), 10 + i * 28, 30 + RANDOM.nextInt(5));
            }
            for (int i = 0; i < 8; i++) {
                g.setColor(
                        new Color(
                                100 + RANDOM.nextInt(100),
                                120 + RANDOM.nextInt(100),
                                120 + RANDOM.nextInt(100)));
                g.drawLine(
                        RANDOM.nextInt(128),
                        RANDOM.nextInt(42),
                        RANDOM.nextInt(128),
                        RANDOM.nextInt(42));
            }
        } finally {
            g.dispose();
        }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图像生成失败", e);
        }
    }
}
