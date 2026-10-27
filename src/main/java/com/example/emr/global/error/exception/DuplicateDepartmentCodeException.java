package com.example.emr.global.error.exception;

public class DuplicateDepartmentCodeException extends ApiException {
    public DuplicateDepartmentCodeException() { super("이미 사용 중인 진료과 코드입니다."); }
    @Override public int getStatusCode() { return 409; }
}
