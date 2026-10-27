package com.example.emr.encounter_histories.repository;

import com.example.emr.encounter_histories.domain.EncounterHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

// 일반 기능에서는 이력의 신규 저장과 조회만 제공한다. 수정·삭제 메서드는 노출하지 않는다.
public interface EncounterHistoryRepository extends Repository<EncounterHistory, Long> {
    EncounterHistory save(EncounterHistory history);
    Page<EncounterHistory> findByEncounter_Id(Long encounterId, Pageable pageable);
    Optional<EncounterHistory> findByIdAndEncounter_Id(Long id, Long encounterId);
}
