package com.example.emr.global.error.exception;

public class EncounterNotFoundException extends ApiException {
    private static final String MESSAGE = "진료 기록을 찾을 수 없습니다.";

    public EncounterNotFoundException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 404;
    }
}
