package com.example.emr.global.error.exception;

public class InvalidVisitStatusException extends ApiException {
    private static final String MESSAGE = "현재 방문 접수 상태에서는 해당 작업을 수행할 수 없습니다.";

    public InvalidVisitStatusException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}
