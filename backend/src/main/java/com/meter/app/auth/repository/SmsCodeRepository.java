package com.meter.app.auth.repository;

import com.meter.app.auth.entity.SmsCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmsCodeRepository extends JpaRepository<SmsCode, Long> {

    // 取该手机号+用途的最新一条未用码
    Optional<SmsCode> findFirstByPhoneAndSceneAndUsedOrderByIdDesc(String phone, String scene, Integer used);
}
