package com.example.emr.global.error.exception;

public class DuplicateUserNameException extends ApiException {

	private static final String MESSAGE = "이미 사용중인 아이디입니다";

	public DuplicateUserNameException() {
		super(MESSAGE);
	}

	public DuplicateUserNameException(String fieldName, String message) {
		super(MESSAGE);
		addValidation(fieldName, message);
	}

	@Override
	public int getStatusCode() {
		return 409;
	}
}
