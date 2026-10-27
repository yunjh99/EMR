package com.example.emr.global.error.exception;

public class InvalidVisitRegistrationException extends ApiException {
    public InvalidVisitRegistrationException() { super("활성 환자와 담당 의사, 예약 상태 및 예약 환자 정보를 확인해 주세요."); }
    @Override public int getStatusCode() { return 409; }
}
