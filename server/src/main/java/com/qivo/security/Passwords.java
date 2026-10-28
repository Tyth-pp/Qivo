package com.qivo.security;

import com.qivo.constant.AccountMessageConstant;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Passwords {
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String raw) {
        if (raw == null || raw.length() < 8 || raw.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AccountMessageConstant.PASSWORD_REQUIREMENTS);
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null || raw.getBytes(StandardCharsets.UTF_8).length > 72)
            return false;
        return BCRYPT_HASH.matcher(stored).matches() && encoder.matches(raw, stored);
    }

    public static String stamp(String encoded) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(encoded.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
