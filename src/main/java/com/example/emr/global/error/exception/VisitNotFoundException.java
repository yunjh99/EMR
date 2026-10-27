package com.example.emr.global.error.exception;

public class VisitNotFoundException extends ApiException {
    private static final String MESSAGE = "방문 접수 정보를 찾을 수 없습니다.";

    public VisitNotFoundException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 404;
    }
}
