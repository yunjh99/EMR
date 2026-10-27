package com.example.emr.visits.domain;

public enum VisitStatus {
    WAITING,       // 진료 대기
    IN_PROGRESS,   // 진료 중
    COMPLETED,     // 진료 완료
    CANCELLED      // 접수 취소
}
