package com.example.emr.audit_logs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();

    String targetTable();

    // 인자 ID: "#userId" 또는 "#p0". 생성 후 반환된 ID: "#result"
    String targetId() default "";

    // 서비스가 업무 트랜잭션에서 성공 로그를 직접 저장하는 경우 false
    boolean logSuccess() default true;
}
