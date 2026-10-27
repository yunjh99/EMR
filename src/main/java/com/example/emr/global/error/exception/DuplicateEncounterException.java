package com.example.emr.global.error.exception;

public class DuplicateEncounterException extends ApiException {
    private static final String MESSAGE = "해당 방문 접수에 이미 진료 기록이 존재합니다.";

    public DuplicateEncounterException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}
