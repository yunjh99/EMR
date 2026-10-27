package com.example.emr.global.error.exception;

public class EncounterHistoryNotFoundException extends ApiException {
    public EncounterHistoryNotFoundException() {
        super("진료 기록 수정 이력을 찾을 수 없습니다.");
    }

    @Override
    public int getStatusCode() {
        return 404;
    }
}
