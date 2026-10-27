# MySQL 예약 동시성 테스트

활성 예약 UNIQUE 적용과 분리된 정확성·성능 비교의 최신 결과는
[`docs/appointment-unique-benchmark.md`](../../docs/appointment-unique-benchmark.md)를 참고한다.
아래 날짜별 표는 이전 구현의 기록이다.

`AppointmentConcurrencyTest`는 실제 서비스 프록시를 호출한다. 운영 코드는 변경하지 않는다.
두 테스트는 각각 새 의사/환자/직원/진료과를 커밋한 뒤 같은 의사·환자·다음 날 09:00으로
100번 예약한다. 활성 예약은 현재 enum에서 `SCHEDULED`로 정의한다.

## 실행

프로젝트 루트에서 실행한다. 기본 연결은 `src/main/resources/application.properties`의
MySQL 설정이다. 별도 테스트 MySQL을 사용하려면 아래 환경변수를 설정한다.
DB에는 **현재 엔티티와 기존 프로젝트 DDL에 맞는 전체 스키마**가 준비되어 있어야 한다.
테스트는 `ddl-auto=validate`, SQL 자동 초기화 비활성화로 실행하며 테이블이나 제약조건을 만들거나 바꾸지 않는다.
애플리케이션 설정에서 요구하는 JWT 등 환경변수도 기존 실행 환경과 동일하게 제공한다.

```powershell
$env:APPOINTMENT_TEST_MYSQL_URL = 'jdbc:mysql://localhost:3306/emr_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:APPOINTMENT_TEST_MYSQL_USERNAME = 'test_user'
# APPOINTMENT_TEST_MYSQL_PASSWORD는 로컬 환경에서 설정한다. 소스에 저장하지 않는다.
.\gradlew.bat test --tests '*AppointmentConcurrencyTest' --info
```

개별 실행:

```powershell
.\gradlew.bat test --tests '*AppointmentConcurrencyTest.observeCurrentBehavior' --info
.\gradlew.bat test --tests '*AppointmentConcurrencyTest.activeSlotMustContainAtMostOneAppointment' --info
```

## 결과 해석

- A는 성공/실패/예외 종류/DB 저장 수/활성 슬롯 수/감사 로그 수/실행 시간을 기록한다.
  중복이나 요청 예외 자체는 관찰 결과다. 준비 실패·대기 시간 초과는 테스트 실행 오류다.
- B는 활성 슬롯 수가 1건 이하인지 검증한다. 요청이 전부 실패하거나 DB/인증 오류 또는
  감사 로그 누락이 있으면 성공으로 처리하지 않는다. `DuplicateAppointmentException`만
  정상 중복 거절로 구분한다. 전체 실패 수에는 이 거절도 포함하며 별도 건수도 출력한다.
- 보고서는 `build/reports/appointment-concurrency/A-observation.txt`, `B-requirement.txt`에 저장된다.
  B의 assertion 전에 저장하므로 요구사항 위반으로 실패해도 결과가 남는다.
  JUnit 결과는 `build/test-results/test`, HTML은 `build/reports/tests/test`에 생성된다.
- 시간은 시작 신호부터 서비스와 감사 AOP의 반환까지이며 fixture 준비·결과 조회·정리는 제외한다.
- 실제 DB의 `SHOW CREATE TABLE` 결과와 격리 수준을 보고서에 포함한다. 격리 수준은 바꾸지 않는다.
- 작업 스레드 100개, Hikari 최대 커넥션 20개다. 시작 신호가 동시에 전달되어도
  DB 동시 실행 수는 커넥션 풀·트랜잭션·DB 스케줄링에 제한된다.
- 현재 서비스의 `@Transactional` 안에서 의사 행에 `PESSIMISTIC_WRITE` 잠금을 획득하고
  중복 조회와 저장을 실행한다. 테스트에서 바깥 트랜잭션을 추가하지 않는다. 감사 저장은 별도 REQUIRES_NEW다.
  감사 AOP는 저장 오류를 삼키므로 성공 예약 ID와 감사 로그 ID를 별도로 비교한다.
- SecurityContext는 각 작업 스레드에 STAFF/LoginUser로 설정 후 제거한다.
  HTTP/JWT 필터를 통과하는 부하 테스트는 아니다.
- 각 테스트의 고유 fixture ID만 사용해 FK 순서대로 정리한다. 전체 테이블 삭제는 하지 않는다.
  작업 종료가 확인되지 않은 경우 삭제 경합을 피하도록 fixture를 보존하고 테스트를 실패시킨다.

## 의사 행 잠금 추가 후 재실행 (2026-10-26, 최신)

사용자가 추가한 `@Transactional`과 `DoctorRepository.findByIdForUpdate()`의
`PESSIMISTIC_WRITE`를 그대로 사용해 실제 MySQL 8.0.43에서 실행했다.
서비스/Repository는 수정하지 않았다. 테스트의 오래된 트랜잭션 설명만 현재 설정에 맞췄다.

| 항목 | A: 관찰 | B: 요구사항 |
| --- | ---: | ---: |
| 전체 요청 | 100 | 100 |
| 성공 | 1 | 1 |
| 중복 거절(DuplicateAppointmentException) | 99 | 99 |
| 기타 DB/인증 오류 | 0 | 0 |
| 저장 예약 / 동일 슬롯 활성 예약 | 1 / 1 | 1 / 1 |
| 성공 감사 로그 / 전체 감사 로그 | 1 / 100 | 1 / 100 |
| 성공 감사 로그 누락 | 0 | 0 |
| 요청 실행 시간 | 268ms | 127ms |
| 결과 | 통과 | 통과 |

첫 실행도 A/B 모두 성공 1건, 중복 거절 99건으로 통과했다(A 248ms, B 124ms).
최종 보고서 재생성 실행의 결과가 위 표다. 작업 스레드는 100개, DB 커넥션 풀은 최대 20개다.
테스트 fixture users/doctors/patients가 정리되어 0건임을 별도 JDBC 조회로 확인했다.
이번 시나리오에서 동일 슬롯 중복은 관찰되지 않았다. 다른 의사/시간이나 취소 후 재예약 시나리오는
이 테스트에서 검증하지 않는다.

## 중복 검사만 추가한 상태의 재실행 (2026-10-26, 이전 결과)

현재 `existsByDoctor_IdAndScheduledAtAndStatusNot(..., CANCELLED)` 검사가 있는 코드로 실행했다.
운영 로직을 추가로 수정하지 않았으며, 테스트만 업무 중복 거절과 기타 오류를 구분하도록 갱신했다.

| 항목 | A: 관찰 | B: 요구사항 |
| --- | ---: | ---: |
| 전체 요청 | 100 | 100 |
| 성공 | 58 | 20 |
| 실패(전부 DuplicateAppointmentException) | 42 | 80 |
| 기타 DB/인증 오류 | 0 | 0 |
| 저장 예약 / 동일 슬롯 활성 예약 | 58 / 58 | 20 / 20 |
| 성공 감사 로그 / 전체 감사 로그 | 58 / 100 | 20 / 100 |
| 성공 감사 로그 누락 | 0 | 0 |
| 요청 실행 시간 | 217ms | 40ms |
| 결과 | 통과 | 실패 |

B의 유일한 assertion 실패는 `20 <= 1` 위반이다. 테스트 fixture users/doctors/patients가
정리되어 모두 0건인 것을 별도 JDBC 조회로 확인했다.
이번 작업의 첫 실행은 A 성공 100/거절 0, B 성공 31/거절 69였다. 스케줄링에 따라 건수는 달라지지만
두 실행 모두 동일 슬롯의 복수 예약이 관찰됐다. 100개 스레드와 최대 DB 커넥션 20개를 사용했다.
보고서 파일은 최신 실행 결과로 갱신된다.

## 중복 검사 추가 전 실행 확인 (2026-10-26)

- 사용자 테이블 생성 후 재확인: MySQL `8.0.43`, 필요한 테이블 존재 확인.
- 일반 샌드박스 실행은 Gradle 플러그인 해석에 실패했지만, 샌드박스 외부에서 캐시된
  Gradle `9.8.2`로 실행하자 컴파일과 두 테스트 실행이 완료되었다. 의존성/빌드 설정은 변경하지 않았다.
- 요청 슬롯은 `2026-10-27T09:00`, 동일한 의사·환자에 대해 각 테스트 100건이다.

| 항목 | A: 관찰 | B: 요구사항 |
| --- | ---: | ---: |
| 전체 요청 | 100 | 100 |
| 성공 | 100 | 100 |
| 실패 | 0 | 0 |
| 실패 예외 종류 | 없음 | 없음 |
| 저장 예약 | 100 | 100 |
| 동일 슬롯 활성 예약 | 100 | 100 |
| 성공 감사 로그 | 100 | 100 |
| 감사 로그 누락 | 0 | 0 |
| 요청 실행 시간 | 122ms | 59ms |
| 결과 | 통과 | 실패 |

- B의 유일한 assertion 실패: 활성 예약 수 `100 <= 1`이 성립하지 않는다.
  인증/DB 오류로 인한 실패가 아니라 동일 슬롯 중복 예약 요구사항 위반이다.
- 첫 실행에서는 JDBC LocalDateTime 조건 조회가 0건을 반환해 집계 불일치가 발생했다.
  테스트의 슬롯 조건을 저장과 동일한 JPA 시간 바인딩으로 변경하고 재실행한 최종 결과가 위 표다.
  실제 DB의 `scheduled_at` 원시 값은 `2026-10-27T00:00`으로 관찰되었고, JPA 슬롯 조회는
  요청의 `09:00` 조건으로 100건을 반환했다. 원시 시간 그룹도 보고서에 기록한다.
  애플리케이션의 시간대 설정이나 저장 로직은 변경하지 않았다.
- 실 DB appointments는 doctor_id/patient_id FK와 필수 컬럼 NOT NULL을 가지고 있으며
  슬롯 UNIQUE는 없다. doctors는 user/department FK 및 user/license UNIQUE,
  departments는 code UNIQUE, users는 username UNIQUE를 가진다. 모두 InnoDB다.
  audit_logs는 user_id NOT NULL이며 실제 DDL에 user_id FK는 없다.
- 기존 FK/UNIQUE/격리 수준을 변경하지 않았다. JDBC 격리 수준은 `4`(REPEATABLE_READ)였다.
- 테스트 메서드의 데이터 정리는 assertion 실패 시에도 @AfterEach에서 실행된다.

위 명령으로 재현한다. 같은 의사·시간에 `SCHEDULED` 예약이 2건 이상 커밋되면 B는 실패하며
테스트는 해결 코드를 적용하지 않는다. 이 수치는 해당 실행의 측정값이며 재실행 시간은 달라질 수 있다.
