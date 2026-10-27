package com.example.emr.global.error.exception;

public class DoctorAlreadyInProgressException extends ApiException {
    private static final String MESSAGE = "해당 의사는 이미 다른 환자를 진료 중입니다.";

    public DoctorAlreadyInProgressException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}
