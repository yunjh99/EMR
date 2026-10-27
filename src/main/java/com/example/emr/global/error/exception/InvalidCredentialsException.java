package com.example.emr.global.error.exception;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() { super("로그인 정보가 올바르지 않거나 사용할 수 없는 계정입니다."); }
    @Override public int getStatusCode() { return 401; }
}
