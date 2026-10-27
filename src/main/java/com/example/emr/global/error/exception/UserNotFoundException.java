package com.example.emr.global.error.exception;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.");
    }

    @Override
    public int getStatusCode() {
        return 404;
    }
}
