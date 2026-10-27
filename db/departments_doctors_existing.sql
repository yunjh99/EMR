-- 제공된 DDL로 두 테이블을 이미 생성한 환경 전용.
-- departments_doctors.sql과 둘 중 하나만 사용한다.
-- 먼저 SHOW CREATE TABLE로 확인하고, 이미 존재하는 제약은 다시 추가하지 않는다.
-- 아래 사전 점검 결과에 행이 있으면 중복/잘못된 참조를 먼저 해결해야 한다.
SELECT code, COUNT(*) FROM departments GROUP BY code HAVING COUNT(*) > 1;
SELECT user_id, COUNT(*) FROM doctors GROUP BY user_id HAVING COUNT(*) > 1;
SELECT license_no, COUNT(*) FROM doctors GROUP BY license_no HAVING COUNT(*) > 1;
SELECT d.id FROM doctors d LEFT JOIN users u ON u.id = d.user_id WHERE u.id IS NULL;
SELECT d.id FROM doctors d LEFT JOIN departments dep ON dep.id = d.department_id WHERE dep.id IS NULL;

ALTER TABLE departments
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT,
    ADD CONSTRAINT uk_departments_code UNIQUE (code);

ALTER TABLE doctors
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT,
    ADD CONSTRAINT uk_doctors_user UNIQUE (user_id),
    ADD CONSTRAINT uk_doctors_license UNIQUE (license_no),
    ADD CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT;
