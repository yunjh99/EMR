package com.example.emr.global.error.exception;

public class AppointmentCancellationNotAllowedException extends ApiException {
    public AppointmentCancellationNotAllowedException() { super("예정된 예약만 취소할 수 있습니다."); }
    @Override public int getStatusCode() { return 409; }
}
