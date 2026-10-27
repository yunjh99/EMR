package com.example.emr.global.error.exception;

public class DepartmentNotFoundException extends ApiException {
    public DepartmentNotFoundException() { super("진료과를 찾을 수 없습니다."); }
    @Override public int getStatusCode() { return 404; }
}
