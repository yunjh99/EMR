package com.example.emr.audit_logs.aspect;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.audit_logs.domain.AuditResult;
import com.example.emr.audit_logs.dto.AuditLogCommand;
import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.global.error.exception.DuplicateUserNameException;
import com.example.emr.global.error.exception.UserNotFoundException;
import com.example.emr.global.error.exception.PatientNotFoundException;
import com.example.emr.global.error.exception.DepartmentNotFoundException;
import com.example.emr.global.error.exception.DoctorNotFoundException;
import com.example.emr.global.error.exception.DuplicateDepartmentCodeException;
import com.example.emr.global.error.exception.DuplicateDoctorException;
import com.example.emr.global.error.exception.DuplicateAppointmentException;
import com.example.emr.global.error.exception.InvalidDoctorAssignmentException;
import com.example.emr.users.dto.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Aspect
@Order(0) // 기본 메서드 권한 검사와 트랜잭션보다 바깥에서 실행
@RequiredArgsConstructor
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around(value = "@annotation(audit)", argNames = "joinPoint,audit")
    public Object audit(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        // 1. JWT 필터가 SecurityContext에 넣어둔 작업자 정보 추출
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = authentication != null
                && authentication.getPrincipal() instanceof LoginUser loginUser
                ? loginUser.userId() : null;

        // 매개변수 이름과 실제 인자를 연결: #userId 또는 #p0 등으로 참조
        var signature = (MethodSignature) joinPoint.getSignature();
        var method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        var context = new MethodBasedEvaluationContext(joinPoint.getTarget(), method,
                joinPoint.getArgs(), new DefaultParameterNameDiscoverer());

        // 생성 결과(#result)는 아직 없으므로 실행 후에 해석
        boolean usesResult = audit.targetId().contains("#result");
        Long targetId = usesResult ? null : resolveTargetId(audit.targetId(), context);

        Object response;
        try {
            // 2. 실제 서비스 메서드 실행 (권한 검사와 트랜잭션 처리 포함)
            response = joinPoint.proceed();
        } catch (AccessDeniedException exception) {
            // 3-1. 권한 거부: 로그를 남기고 원래 예외를 다시 전달
            write(userId, audit, targetId, AuditResult.DENIED, "ACCESS_DENIED");
            throw exception;
        } catch (Throwable exception) {
            // 3-2. 실행 실패: 예외 원문 대신 안전한 코드 사용
            write(userId, audit, targetId, AuditResult.FAILURE, failureCode(exception));
            throw exception;
        }

        // 3-3. 반환값을 #result에 등록. 생성 성공이면 새로 생성된 ID 추출
        context.setVariable("result", response);
        if (usesResult) {
            targetId = resolveTargetId(audit.targetId(), context);
        }

        // 외부 트랜잭션에 참여했다면 실제 커밋 이후에 성공 로그를 기록한다.
        if (audit.logSuccess()) {
            writeSuccessAfterCommit(userId, audit, targetId);
        }
        return response;
    }

    private void writeSuccessAfterCommit(Long userId, Audit audit, Long targetId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // 현재 AOP 순서에서는 서비스 자체 트랜잭션이 이미 완료된 경우다.
            // 트랜잭션이 없는 메서드의 성공 로그도 기존처럼 즉시 기록한다.
            write(userId, audit, targetId, AuditResult.SUCCESS, null);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // write()가 호출하는 감사 저장 서비스는 REQUIRES_NEW를 유지한다.
                write(userId, audit, targetId, AuditResult.SUCCESS, null);
            }
        });
    }

    private void write(Long userId, Audit audit, Long targetId,
                       AuditResult result, String failureReason) {
        // 현재 테이블은 user_id가 필수이므로 로그인 사용자의 작업만 기록
        if (userId == null) {
            log.warn("감사 로그 생략: 로그인 사용자 없음, action={}", audit.action());
            return;
        }
        try {
            auditLogService.write(new AuditLogCommand(
                    userId, audit.action(), audit.targetTable(), targetId,
                    result, failureReason
            ));
        } catch (RuntimeException exception) {
            // 기록 실패로 원래 응답이나 업무 예외를 덮어쓰지 않음. 민감한 예외 원문은 출력하지 않음
            log.error("감사 로그 저장 실패: action={}, errorType={}",
                    audit.action(), exception.getClass().getSimpleName());
        }
    }

    private Long resolveTargetId(String expression, MethodBasedEvaluationContext context) {
        if (expression.isBlank()) {
            return null;
        }
        try {
            return new SpelExpressionParser().parseExpression(expression).getValue(context, Long.class);
        } catch (RuntimeException exception) {
            log.warn("감사 로그 대상 ID 해석 실패: expression={}", expression);
            return null;
        }
    }

    private String failureCode(Throwable exception) {
        if (exception instanceof DuplicateAppointmentException) return "DUPLICATE_APPOINTMENT";
        if (exception instanceof DepartmentNotFoundException) return "DEPARTMENT_NOT_FOUND";
        if (exception instanceof DoctorNotFoundException) return "DOCTOR_NOT_FOUND";
        if (exception instanceof DuplicateDepartmentCodeException) return "DUPLICATE_DEPARTMENT_CODE";
        if (exception instanceof DuplicateDoctorException) return "DUPLICATE_DOCTOR";
        if (exception instanceof InvalidDoctorAssignmentException) return "INVALID_DOCTOR_ASSIGNMENT";
        if (exception instanceof PatientNotFoundException) {
            return "PATIENT_NOT_FOUND";
        }
        if (exception instanceof UserNotFoundException) {
            return "USER_NOT_FOUND";
        }
        if (exception instanceof DuplicateUserNameException) {
            return "DUPLICATE_USERNAME";
        }
        return "INTERNAL_ERROR";
    }
}
