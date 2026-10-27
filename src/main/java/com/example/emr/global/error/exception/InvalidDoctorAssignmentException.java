package com.example.emr.global.error.exception;

public class InvalidDoctorAssignmentException extends ApiException {
    public InvalidDoctorAssignmentException() { super("활성 진료과와 활성 DOCTOR 계정만 배정할 수 있습니다."); }
    @Override public int getStatusCode() { return 409; }
}
