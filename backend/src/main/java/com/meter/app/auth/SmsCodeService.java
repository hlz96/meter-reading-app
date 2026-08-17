package com.meter.app.auth;

import com.meter.app.auth.entity.SmsCode;
import com.meter.app.auth.repository.SmsCodeRepository;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 验证码生成与校验(TRD §4.1)。存 DB,骨架阶段不发真短信、日志打印。
 */
@Service
public class SmsCodeService {

    public static final String SCENE_REGISTER = "REGISTER";
    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeRepository smsCodeRepository;
    private final SmsProperties smsProperties;

    public SmsCodeService(SmsCodeRepository smsCodeRepository, SmsProperties smsProperties) {
        this.smsCodeRepository = smsCodeRepository;
        this.smsProperties = smsProperties;
    }

    /** 生成验证码并写库。返回码本身,便于骨架阶段前端/测试拿到(生产接短信网关后应改为不返回)。 */
    @Transactional
    public String generate(String phone, String scene) {
        String s = (scene == null || scene.isBlank()) ? SCENE_REGISTER : scene;
        String code = randomCode(smsProperties.codeLength());
        SmsCode entity = new SmsCode();
        entity.setPhone(phone);
        entity.setCode(code);
        entity.setScene(s);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(smsProperties.ttlSeconds()));
        smsCodeRepository.save(entity);
        // 骨架阶段:不接短信网关,打印到日志便于联调
        log.info("【验证码】phone={} scene={} code={}", phone, s, code);
        return code;
    }

    /** 校验最新未用未过期的码,匹配则置为已用。失败抛 SMS_CODE_INVALID。 */
    @Transactional
    public void verifyAndConsume(String phone, String scene, String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "请输入验证码");
        }
        SmsCode latest = smsCodeRepository
                .findFirstByPhoneAndSceneAndUsedOrderByIdDesc(phone, scene, 0)
                .orElseThrow(() -> new BizException(ErrorCode.SMS_CODE_INVALID, "验证码无效或已使用"));
        if (latest.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "验证码已过期");
        }
        if (!latest.getCode().equals(code)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "验证码错误");
        }
        latest.setUsed(1);
        smsCodeRepository.save(latest);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
