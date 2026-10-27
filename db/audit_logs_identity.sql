-- MySQL에서 기존 audit_logs 테이블의 ID 자동 생성을 설정하는 수동 실행 스크립트.
-- 실행 전 SHOW CREATE TABLE audit_logs; 로 현재 기본 키를 확인하세요.
-- 아래는 최초 제공된 DDL처럼 기본 키가 없는 테이블 기준입니다.
ALTER TABLE audit_logs
    ADD PRIMARY KEY (id),
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '감사 로그 ID';

-- 이미 id가 기본 키라면 위 구문 대신 다음 구문만 실행하세요.
-- ALTER TABLE audit_logs
--     MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '감사 로그 ID';
