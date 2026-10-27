package com.example.emr.encounters.service;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.encounter_histories.domain.EncounterHistory;
import com.example.emr.encounter_histories.dto.EncounterHistoryResponse;
import com.example.emr.encounter_histories.repository.EncounterHistoryRepository;
import com.example.emr.encounters.domain.Encounter;
import com.example.emr.encounters.dto.EncounterCreateRequest;
import com.example.emr.encounters.dto.EncounterUpdateRequest;
import com.example.emr.encounters.dto.EncounterResponse;
import com.example.emr.encounters.repository.EncounterRepository;
import com.example.emr.global.error.exception.DuplicateEncounterException;
import com.example.emr.global.error.exception.EncounterNotFoundException;
import com.example.emr.global.error.exception.EncounterHistoryNotFoundException;
import com.example.emr.global.error.exception.InvalidVisitStatusException;
import com.example.emr.global.error.exception.VisitNotFoundException;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitStatus;
import com.example.emr.visits.repository.VisitRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@Validated
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final EncounterHistoryRepository encounterHistoryRepository;

    // 진료 기록 생성
    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    @Audit(action = "CREATE_ENCOUNTER", targetTable = "encounters", targetId = "#result")
    public Long createEncounter(EncounterCreateRequest request, LoginUser loginUser) {

        // 1. 접수 조회 및 잠금
        Visit visit = visitRepository
                .findByIdForUpdate(request.visitId())
                .orElseThrow(VisitNotFoundException::new);

        // 2. 로그인 사용자가 담당 의사인지 확인
        validateAssignedDoctor(visit, loginUser);

        // 3. 진료 중일 때만 진료 기록 생성 가능
        if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new InvalidVisitStatusException();
        }

        // 4. 이미 작성한 진료 기록 확인
        if (encounterRepository.existsByVisit_Id(visit.getId())) {
            throw new DuplicateEncounterException();
        }

        // 5. 진료 기록 생성
        Encounter encounter = encounterRepository.save(request.toEntity(visit));

        // 6. 같은 트랜잭션에서 완료 처리. 실패하면 INSERT와 상태 변경이 함께 롤백된다.
        // 완료 감사 이벤트는 CREATE_ENCOUNTER로 통합한다.
        visit.complete();
        return encounter.getId();
    }

    // 진료 기록 수정
    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    @Audit(action = "UPDATE_ENCOUNTER", targetTable = "encounters", targetId = "#encounterId")
    public void updateEncounter(Long encounterId, @Valid EncounterUpdateRequest request, LoginUser loginUser) {
        // 1. 진료 기록에 연결된 접수 ID 조회. 기록이 없으면 404 예외 발생
        Long visitId = encounterRepository.findVisitIdById(encounterId)
                .orElseThrow(EncounterNotFoundException::new);

        // 2. 접수를 먼저 잠가 진료 완료 및 다른 수정 요청과 동시에 처리되지 않도록 함
        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(VisitNotFoundException::new);

        // 3. 로그인 사용자가 해당 접수의 담당 의사인지 확인
        validateAssignedDoctor(visit, loginUser);

        // 4. Visit → Encounter 순서로 잠금 획득. 수정 후에도 Visit은 COMPLETED를 유지한다.
        Encounter encounter = encounterRepository.findByIdForUpdate(encounterId)
                .orElseThrow(EncounterNotFoundException::new);

        // 5. null 필드를 제외하고 실제 변경이 없다면 이력·버전을 생성하지 않는다.
        if (!encounter.hasChanges(request.chiefComplaint(), request.clinicalNote(), request.treatmentPlan())) {
            return;
        }

        // 6. 수정 직전의 전체 내용을 같은 트랜잭션에서 보존한다.
        encounterHistoryRepository.save(new EncounterHistory(encounter,
                userRepository.getReferenceById(loginUser.userId()), request.changeReason()));

        // 7~8. 내용 수정과 업무 버전 증가는 update()에서 함께 처리한다. 별도 save()는 필요 없다.
        encounter.update(request.chiefComplaint(), request.clinicalNote(), request.treatmentPlan());
    }

    // 기존 진료 기록 조회 정책처럼 조회 자체의 감사 로그는 추가하지 않는다.
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public Page<EncounterHistoryResponse> getEncounterHistories(Long encounterId, Pageable pageable) {
        requireEncounter(encounterId);
        // 클라이언트 정렬과 관계없이 업무 버전 오름차순으로 과거 기록을 제공한다.
        Pageable byVersion = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("version"));
        return encounterHistoryRepository.findByEncounter_Id(encounterId, byVersion)
                .map(EncounterHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public EncounterHistoryResponse getEncounterHistory(Long encounterId, Long historyId) {
        requireEncounter(encounterId);
        return EncounterHistoryResponse.from(encounterHistoryRepository.findByIdAndEncounter_Id(historyId, encounterId)
                .orElseThrow(EncounterHistoryNotFoundException::new));
    }

    private void requireEncounter(Long encounterId) {
        if (!encounterRepository.existsById(encounterId)) {
            throw new EncounterNotFoundException();
        }
    }

    // 진료 기록 단건·상세 조회: 담당 의사가 아니어도 의사 권한으로 조회 가능
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR')")
    public EncounterResponse getEncounter(Long encounterId) {
        Encounter encounter = encounterRepository.findWithDetailsById(encounterId)
                .orElseThrow(EncounterNotFoundException::new);
        return EncounterResponse.from(encounter);
    }

    // 환자별 진료 기록 목록: 담당 의사에 관계없이 해당 환자의 기록만 페이지 조회
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR')")
    public Page<EncounterResponse> getPatientEncounters(Long patientId, Pageable pageable) {
        return encounterRepository.findAllByPatientId(patientId, pageable)
                .map(EncounterResponse::from);
    }

    // 로그인 사용자가 담당 의사인지 확인
    private void validateAssignedDoctor(Visit visit, LoginUser loginUser) {
        if(!visit.isAssignedDoctor(loginUser.userId())) {
            throw new AccessDeniedException("담당 의사가 아닙니다.");
        }
    }
}
