package com.example.emr.global.error.exception;

public class PatientNotFoundException extends ApiException {
    public PatientNotFoundException() {
        super("환자를 찾을 수 없습니다.");
    }

    @Override
    public int getStatusCode() {
        return 404;
    }
}
