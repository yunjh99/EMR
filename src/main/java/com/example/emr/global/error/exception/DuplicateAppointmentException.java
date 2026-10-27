package com.example.emr.global.error.exception;

public class DuplicateAppointmentException extends ApiException {
    public DuplicateAppointmentException() {
        super("해당 의사의 같은 시간에 이미 예약이 존재합니다.");
    }

    public DuplicateAppointmentException(Throwable cause) {
        super("해당 의사의 같은 시간에 이미 예약이 존재합니다.", cause);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}
