package com.example.emr.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public class FailResponse {

	private final int status;
	private final String message;
	private final Map<String, String> validation;
}
