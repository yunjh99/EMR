package com.example.emr.global.error.exception;

public class InvalidAppointmentTimeException extends ApiException {
    public InvalidAppointmentTimeException() {
        super("예약 시작 시간은 09:00부터 17:30까지 30분 단위로 지정해야 합니다.");
    }

    @Override
    public int getStatusCode() {
        return 400;
    }
}
