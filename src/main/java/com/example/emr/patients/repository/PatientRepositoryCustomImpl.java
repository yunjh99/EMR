package com.example.emr.patients.repository;

import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.dto.PatientSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import static com.example.emr.patients.domain.QPatient.patient;

@Repository
@RequiredArgsConstructor
public class PatientRepositoryCustomImpl implements PatientRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Patient> findAllPatientByCondition(PatientSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        if (condition != null) {
            contains(where, patient.name, condition.name());
            contains(where, patient.phone, condition.phone());
            contains(where, patient.address, condition.address());
            contains(where, patient.patientNo, condition.patientNo());
            if (condition.birthDate() != null) where.and(patient.birthDate.eq(condition.birthDate()));
            if (condition.active() != null) where.and(patient.isActive.eq(condition.active()));
        }
        var query = queryFactory.selectFrom(patient).where(where);
        for (Sort.Order order : pageable.getSort()) query.orderBy(toOrderSpecifier(order));
        // 같은 정렬 값에서도 페이지 순서가 안정적이도록 ID를 보조 정렬로 사용한다.
        if (pageable.getSort().getOrderFor("id") == null) query.orderBy(patient.id.desc());
        if (pageable.isPaged()) query.offset(pageable.getOffset()).limit(pageable.getPageSize());
        var content = query.fetch();
        Long total = queryFactory.select(patient.count()).from(patient).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private void contains(BooleanBuilder where, StringPath field, String value) {
        if (value != null && !value.isBlank()) where.and(field.contains(value.strip()));
    }

    private OrderSpecifier<?> toOrderSpecifier(Sort.Order order) {
        ComparableExpressionBase<?> field = switch (order.getProperty()) {
            case "id" -> patient.id;
            case "name" -> patient.name;
            case "patientNo" -> patient.patientNo;
            case "birthDate" -> patient.birthDate;
            case "gender" -> patient.gender;
            case "phone" -> patient.phone;
            case "address" -> patient.address;
            case "active", "isActive" -> patient.isActive;
            case "createdAt" -> patient.createdAt;
            case "updatedAt" -> patient.updatedAt;
            default -> throw new IllegalArgumentException("지원하지 않는 환자 정렬 필드: " + order.getProperty());
        };
        OrderSpecifier<?> result = order.isAscending() ? field.asc() : field.desc();
        return switch (order.getNullHandling()) {
            case NULLS_FIRST -> result.nullsFirst();
            case NULLS_LAST -> result.nullsLast();
            default -> result;
        };
    }
}
