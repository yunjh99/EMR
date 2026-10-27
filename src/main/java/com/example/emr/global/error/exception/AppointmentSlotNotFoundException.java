package com.example.emr.global.error.exception;

public class AppointmentSlotNotFoundException extends ApiException {
    public AppointmentSlotNotFoundException() { super("예약 슬롯이 존재하지 않아 예약 상태를 변경할 수 없습니다."); }
    @Override public int getStatusCode() { return 409; }
}
