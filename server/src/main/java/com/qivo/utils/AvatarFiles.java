package com.qivo.utils;

import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** 头像专用文件白名单；商品上传保持原有协议。 */
public final class AvatarFiles {
    private AvatarFiles() {}
    public static final long MAX_BYTES = 2 * 1024 * 1024;

    public static String suffix(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String suffix = name.substring(name.lastIndexOf('.') + 1);
        boolean png = bytes.length > 24 && bytes[0] == (byte) 137 && bytes[1] == 80
                && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10
                && bytes[6] == 26 && bytes[7] == 10;
        boolean jpeg = bytes.length > 24 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216
                && bytes[2] == (byte) 255;
        boolean webp = bytes.length > 30 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")
                && (ascii(bytes, 12, "VP8 ") || ascii(bytes, 12, "VP8L") || ascii(bytes, 12, "VP8X"));
        if (bytes.length > MAX_BYTES || !(png && suffix.equals("png")
                || jpeg && (suffix.equals("jpg") || suffix.equals("jpeg"))
                || webp && suffix.equals("webp"))) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        if (!webp) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096 || reader.read(0) == null)
                        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
                } finally { reader.dispose(); }
            } catch (IOException | IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
            }
        } else {
            long size = 0;
            for (int i = 0; i < 4; i++) size |= (long) (bytes[4 + i] & 255) << (8 * i);
            if (size + 8 != bytes.length) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
            int width;
            int height;
            if (ascii(bytes, 12, "VP8X")) {
                if ((bytes[20] & 2) != 0) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
                width = 1 + (bytes[24] & 255) + ((bytes[25] & 255) << 8) + ((bytes[26] & 255) << 16);
                height = 1 + (bytes[27] & 255) + ((bytes[28] & 255) << 8) + ((bytes[29] & 255) << 16);
            } else if (ascii(bytes, 12, "VP8L")) {
                if (bytes[20] != 47) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
                width = 1 + (bytes[21] & 255) + ((bytes[22] & 63) << 8);
                height = 1 + ((bytes[22] & 192) >> 6) + ((bytes[23] & 255) << 2) + ((bytes[24] & 15) << 10);
            } else {
                if (bytes[23] != (byte) 157 || bytes[24] != 1 || bytes[25] != 42)
                    throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
                width = (bytes[26] & 255) + ((bytes[27] & 63) << 8);
                height = (bytes[28] & 255) + ((bytes[29] & 63) << 8);
            }
            if (width < 1 || height < 1 || width > 4096 || height > 4096)
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return suffix;
    }
    private static boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != value.charAt(i)) return false;
        return true;
    }
    public static void validatePath(String path) {
        if (path != null && !path.isEmpty() && !path.matches(
                "/upload/avatars/[0-9]{4}/[0-9]{2}/[0-9]{2}/[a-f0-9-]{36}\\.(png|jpe?g|webp)"))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
}
