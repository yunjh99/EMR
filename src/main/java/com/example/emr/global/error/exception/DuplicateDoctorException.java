package com.example.emr.global.error.exception;

public class DuplicateDoctorException extends ApiException {
    public DuplicateDoctorException() { super("이미 등록된 의사 계정 또는 면허번호입니다."); }
    @Override public int getStatusCode() { return 409; }
}
