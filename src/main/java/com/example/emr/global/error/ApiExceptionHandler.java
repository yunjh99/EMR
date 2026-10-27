package com.example.emr.global.error;

import com.example.emr.global.common.FailResponse;
import com.example.emr.global.error.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<FailResponse> handleUnsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405).body(new FailResponse(405, "지원하지 않는 요청 방식입니다.", Map.of()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<FailResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(new FailResponse(409,
                "데이터 참조 또는 중복 제약조건을 확인해 주세요.", Map.of()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<FailResponse> handleApiException(ApiException exception) {
        int status = exception.getStatusCode();
        return ResponseEntity.status(status).body(
                new FailResponse(status, exception.getMessage(), exception.getValidation()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<FailResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> validation = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                validation.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new FailResponse(400, "입력값을 확인해 주세요.", validation));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<FailResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new FailResponse(400,
                "요청 본문이 올바르지 않습니다. 값의 형식, null, 수정 불가 필드를 확인해 주세요.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<FailResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new FailResponse(400, "요청 값의 형식이 올바르지 않습니다.",
                Map.of(exception.getName(), "올바른 형식으로 입력해 주세요.")));
    }
}
