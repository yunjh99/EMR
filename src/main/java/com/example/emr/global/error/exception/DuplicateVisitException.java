package com.example.emr.global.error.exception;

public class DuplicateVisitException extends ApiException {
    private static final String MESSAGE = "이미 방문 접수가 존재합니다.";

    public DuplicateVisitException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}