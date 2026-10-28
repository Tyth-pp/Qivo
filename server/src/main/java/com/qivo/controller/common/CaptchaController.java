package com.qivo.controller.common;

import com.qivo.cache.RedisStore;
import com.qivo.constant.SecurityConstant;
import com.qivo.utils.CaptchaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "公共-验证码")
@RestController
@RequestMapping("/api/common/captcha")
public class CaptchaController {

    private final RedisStore redisStore;

    public CaptchaController(RedisStore redisStore) {
        this.redisStore = redisStore;
    }

    @Operation(summary = "获取验证码")
    @GetMapping("/get")
    public Map<String, String> getCaptcha() {
        String code = CaptchaUtil.generateCode();
        String captchaKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        redisStore.set(SecurityConstant.CAPTCHA_KEY_PREFIX + captchaKey, code, 5, TimeUnit.MINUTES);

        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaImage", CaptchaUtil.image(code));
        return result;
    }
}
