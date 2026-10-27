package com.example.emr.global.error.exception;

public class AppointmentNotFoundException extends ApiException {
    public AppointmentNotFoundException() { super("예약을 찾을 수 없습니다."); }
    @Override public int getStatusCode() { return 404; }
}
