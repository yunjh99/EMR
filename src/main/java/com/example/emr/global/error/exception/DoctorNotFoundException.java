package com.example.emr.global.error.exception;

public class DoctorNotFoundException extends ApiException {
    public DoctorNotFoundException() { super("의사를 찾을 수 없습니다."); }
    @Override public int getStatusCode() { return 404; }
}
