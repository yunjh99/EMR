package com.example.emr.global.common;


import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SuccessResponse<T> {

	private final int status;
	private final String message;
	private final T data;

	@Builder
	public static <T> SuccessResponse<T> of(
			int status, String message, T data) {

		return new SuccessResponse<>(status, message, data);
	}
}
