# 온유 EMR 프론트엔드

기존 Spring Boot 의료 CRM에 연결되는 PC 업무용 React/TypeScript 앱입니다. 온유 의료원은 포트폴리오용 가상 기관명입니다. 실제 앱에 mock 환자·가짜 JWT·역할 선택기를 넣지 않습니다.

## 실행

검증 환경: Node 24.13.0, npm 11.6.2, Java 21, MySQL 8.0.43.

```powershell
# 기존 백엔드는 새 API를 반영하도록 재시작합니다.
cd C:\portfolio\emr
$env:CORS_ALLOWED_ORIGINS='http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:4173,http://localhost:4173'
.\gradlew.bat bootRun

# 다른 터미널
cd C:\portfolio\emr\frontend
npm.cmd ci
npm.cmd run dev
```

화면: **http://127.0.0.1:5173**. PowerShell 정책으로 npm.ps1이 막히면 npm.cmd를 사용합니다. 백엔드는 현재 사용자의 ddl-auto=update 설정을 유지합니다. 기존 이력 마이그레이션 적용 상태도 확인해야 하며, 프론트 실행 자체는 DB를 변경하지 않습니다.

`.env.example`을 참고해 필요한 경우 `.env.local`을 만듭니다.

```dotenv
VITE_API_BASE_URL=/api
API_PROXY_TARGET=http://localhost:8080
```

개발 프록시도 변경 요청의 Origin을 전달합니다. 백엔드는 로컬 `127.0.0.1`/`localhost`의 5173·4173 포트를 기본 허용하므로 별도 환경변수 없이 실행할 수 있습니다. `CORS_ALLOWED_ORIGINS`를 설정하면 기본 목록 전체를 대체합니다. 로그인에서 403이 나면 백엔드 재시작과 해당 환경변수를 확인하세요. 운영 배포는 같은 Origin의 `/api` 역방향 프록시와 SPA fallback을 권장합니다. HTTPS 환경에서는 `AUTH_REFRESH_COOKIE_SECURE=true`로 HttpOnly refresh 쿠키의 Secure 속성을 활성화합니다.

## 로그인과 재발급

`/connect`에서 실제 계정과 비밀번호로 로그인합니다. 사용자 ID나 JWT를 직접 붙여 넣을 필요가 없습니다. 로컬 데모 초기화가 켜져 있으면 백엔드 시작 시 `demo_admin`, `demo_doctor`, `demo_staff`, `demo_nurse` 계정이 생성되며 비밀번호는 모두 `star`입니다. 기존 계정은 덮어쓰지 않습니다. 가상 환자·예약·접수·기록 구성과 초기화 비활성화 방법은 [데모 데이터 안내](../docs/demo-data.md)를 참고하세요.

- `POST /api/login`: 비밀번호와 계정 활성 상태 검증, 사용자 정보/액세스 토큰 응답
- `POST /api/reissue`: HttpOnly refresh 쿠키의 서명·만료·종류와 현재 계정 활성 상태 검증
- `POST /api/logout`: refresh 쿠키 제거 및 클라이언트 세션 해제

액세스 토큰은 메모리에만 둡니다. refresh 토큰은 HttpOnly/SameSite=Strict, `/api/reissue` 경로의 쿠키로 저장되며 JS에서 읽지 않습니다. 새로고침하면 서버 재발급으로 본인 계정·역할을 복원합니다. 만료된 액세스 토큰은 재발급 요청에 전달하지 않습니다. 401에서 재발급에 실패하면 인증과 조회 캐시를 제거합니다. 변경 요청은 자동 재전송하지 않으며 사용자에게 재확인을 안내합니다.

현재 refresh 방식은 기존 JWT를 이용한 stateless 방식입니다. 로그아웃은 브라우저의 쿠키를 제거하지만 이미 복사된 토큰을 서버에서 개별 폐기하지는 않습니다. 운영 전 서버 측 refresh 세션/rotation 재사용 감지와 로그인 요청 제한을 추가 검토해야 합니다. 비활성 계정은 재발급과 기존 액세스 토큰 사용 모두 거절됩니다.

## 화면과 권한

| 화면 | 기능 |
| --- | --- |
| 업무 개요 | 실제 예약·대기 수, 의료진·진료과 수, 역할별 바로가기 |
| 직원 관리 | ADMIN 등록·검색·상세·역할/활성 수정·페이지 |
| 진료과/의료진 | 임상 역할 조회, ADMIN 등록·수정·비활성화 |
| 환자 관리 | STAFF 이름/번호/연락처 검색·상세·등록·수정·방문 접수 연결 |
| 예약 관리 | STAFF/DOCTOR/NURSE 필터·페이지·상세, STAFF 등록·취소·예약 환자 접수 |
| 접수·진료 대기 | STAFF/NURSE 현황 조회, DOCTOR 본인 담당 접수만 조회 |
| 접수 상세 | STAFF 대기 접수 취소, 담당 DOCTOR 진료 시작·기록 최초 작성 |
| 환자별 진료 기록 | DOCTOR 환자 검색·이전 기록·단건 조회 |
| 진료 기록 상세 | 담당 DOCTOR 정정, 이전 버전 보존·사유 입력 |
| 수정 이력 | DOCTOR/NURSE 버전 목록·전체 원본 상세 |
| 서비스 연결 현황 | 연결 완료와 아직 제공하지 않는 기능 구분 |

역할별 메뉴와 직접 URL 접근을 모두 제한하며 서버가 최종 인가합니다. ADMIN은 임상 역할에 자동 포함하지 않습니다. NURSE는 최신 진료 기록 조회·작성·정정 권한이 없어 수정 이력만 조회합니다.

## 실제 업무 흐름

예약 환자:

1. STAFF 로그인 → 환자 검색 또는 등록
2. 예약 등록에서 환자 검색·선택 → 의사와 시간 선택 → 예약 생성
3. 예약 행의 방문 접수 → 예약 환자/의사 확인 → 접수 등록
4. 담당 DOCTOR 로그인 → 본인 대기 목록 → 진료 보기
5. 주호소·진료 소견·치료 계획 최초 저장 → 자동 COMPLETED 및 completedAt 설정
6. 이후 정정 사유와 함께 수정 → 최신 버전 증가·이전 전체 원본 조회

예약 없는 환자:

1. STAFF의 환자 목록 또는 접수 목록에서 방문 접수
2. 환자 검색·선택과 담당 의사 지정 → WALK_IN 접수
3. 담당 DOCTOR 진료 시작 → 기록 저장 → 자동 완료

대기 접수만 STAFF가 취소할 수 있습니다. 예약 환자 접수 취소가 Appointment 상태를 되돌리지는 않습니다. 완료·취소는 종료 상태이며 별도 진료 완료 버튼은 없습니다. 완료된 Encounter 정정은 허용하며 Visit 상태와 완료 시각은 유지합니다.

## API 계약

- 공통 성공 `{ status, message, data }`, 실패 `{ status, message, validation }`
- 페이지 `data.content/totalElements/totalPages/number/size`, 번호 0부터 시작
- 환자/접수/진료 기록 생성은 새 ID를 data 숫자로 반환
- 접수 등록 `{ patientId, doctorId, appointmentId? }`: 실제 엔티티 조회·소속 진료과·방문 유형은 서버 결정
- 접수 조회에 `doctorUserId`, `encounterId`, 상태와 완료 시각 제공. DOCTOR 범위는 서버가 본인으로 제한
- 예약 생성은 기존 DTO 철자에 따라 `reson`, 응답은 `reason`. 의사·환자는 `{ id }` 참조로 전송
- 환자 PATCH는 생략 필드를 null로 만들지 않음
- Encounter PATCH는 변경 필드와 필수 changeReason(최대 500자)
- 전체 Encounter GET, 별도 Visit complete API는 제공/호출하지 않음

목록·상세·이력은 TanStack Query를 사용하며 각 변경 후 연관 쿼리를 갱신합니다. 오류에는 비밀번호나 Authorization 요청 설정을 노출하지 않습니다. 토큰을 URL/콘솔/localStorage/sessionStorage에 보관하지 않습니다.

## 남은 기능

- 실제 예약 슬롯 가용 여부 조회: 아직 API가 없어 예약 목록의 모든 페이지로 점유만 표시합니다. 빈 시간도 슬롯 존재를 보장하지 않으며 저장 시 서버가 최종 결정합니다.
- 현재 전체 `(doctor_id, scheduled_at)` UNIQUE 때문에 취소 후 같은 슬롯 재예약은 제한됩니다. 기존 제약조건은 변경하지 않았습니다.
- 감사 로그 조회·환자 정보 변경 이력 조회는 미구현 상태입니다.
- 운영용 서버 측 refresh 폐기/재사용 감지와 로그인 요청 제한은 후속 개선 항목입니다.

## 빌드와 테스트

```powershell
npm.cmd run build
npm.cmd run test
npm.cmd run preview
```

프론트 단위/컴포넌트 테스트는 32개입니다. 실제 브라우저 검증은 프로덕션 번들 + 전체 Spring Boot + 격리 MySQL에서 정상 비밀번호 로그인과 두 업무 시나리오를 실행합니다. 테스트용 계정/환자만 사용하고 테스트 종료 시 전용 스키마 데이터를 정리합니다.

```powershell
# frontend에서 먼저 npm.cmd run build
cd C:\portfolio\emr
$mysqlJar = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.mysql/mysql-connector-j" -Recurse -Filter 'mysql-connector-j-*.jar' | Select-Object -First 1).FullName
$schema = 'emr_bench_frontend_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
java --class-path $mysqlJar db/AppointmentSchemaTool.java clone $schema
$env:FRONTEND_TEST_MYSQL_URL="jdbc:mysql://localhost:3306/${schema}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC"
.\gradlew.bat test --tests '*FrontendLiveSmokeTest' --tests '*EncounterReadTest' --tests '*EmrApplicationTests' --no-daemon --rerun-tasks
```

필요하면 FRONTEND_TEST_MYSQL_USERNAME/PASSWORD를 지정합니다. 기존 복제 도구는 main 또는 APPOINTMENT_TEST_MYSQL_* 접속 설정을 사용합니다. 테스트 서버 18080과 preview 5177이 비어 있어야 합니다. Edge headless가 기본이며 Playwright Chromium을 설치했다면 SMOKE_BROWSER=chromium을 설정할 수 있습니다. Gradle은 프론트 파일 변경을 자동 감지하지 못할 수 있으므로 --rerun-tasks를 사용합니다.

브라우저 결과는 `frontend/test-results/`, 로그는 `build/frontend-smoke/browser.log`입니다. 임시 테스트 자격 증명 파일은 finally에서 삭제하며 결과·소스에 남기지 않습니다. 최종 결과와 변경 파일은 [후속 구현 보고서](../docs/core-workflow-implementation.md)에 정리합니다.
