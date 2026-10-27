package com.example.emr.appointments.domain;

import java.sql.SQLException;
import java.util.regex.Pattern;

/** Recognizes only MySQL duplicate key errors for the active-slot index. */
public final class AppointmentConstraintViolations {
    private static final Pattern ACTIVE_SLOT_KEY = Pattern.compile(
            "(?s).*for key ['`](?:[^'`]*\\.)?uk_active_doctor_slot['`]$");

    private AppointmentConstraintViolations() {}

    public static boolean isActiveSlotDuplicate(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
                    && "23000".equals(sql.getSQLState()) && sql.getMessage() != null
                    && ACTIVE_SLOT_KEY.matcher(sql.getMessage()).matches()) {
                return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }
}
