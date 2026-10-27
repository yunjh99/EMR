package com.example.emr.patients.service;

import com.example.emr.patients.repository.PatientNumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PatientNumberGenerator {

    private final PatientNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        sequenceRepository.increment();
        long number = sequenceRepository.currentNumber();

        if (number <= 0 || number > 9_999_999_999L) {
            throw new IllegalStateException("Invalid patient number.");
        }

        return String.format(Locale.ROOT, "%010d", number);
    }
}
