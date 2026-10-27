import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type { AppointmentResponse, PatientResponse } from "@/lib/types";
import { Field, FormModal, ErrorBox } from "@/components/common";
import { PatientPicker } from "@/components/patient-picker";

export function VisitRegistration({
  appointment,
  initialPatientId,
  onClose,
  onCreated,
}: {
  appointment?: AppointmentResponse;
  initialPatientId?: number;
  onClose: () => void;
  onCreated: (id: number) => void;
}) {
  const client = useQueryClient();
  const [patient, setPatient] = useState<PatientResponse | null>(null);
  const [doctorId, setDoctorId] = useState(
    appointment ? String(appointment.doctorId) : "",
  );
  const [error, setError] = useState<unknown>();
  const patientId = appointment?.patientId || initialPatientId;
  const initial = useQuery({
    queryKey: ["patient", patientId],
    queryFn: ({ signal }) => api.patient(patientId!, signal),
    enabled: !!patientId,
  });
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
  });
  useEffect(() => {
    if (initial.data) setPatient(initial.data);
  }, [initial.data]);
  const save = useMutation({
    mutationFn: async () => {
      if (!patient || !doctorId)
        throw new Error("환자와 담당 의사를 선택해 주세요.");
      return api.createVisit({
        patientId: patient.id,
        doctorId: Number(doctorId),
        ...(appointment ? { appointmentId: appointment.id } : {}),
      });
    },
    onSuccess: async (response) => {
      toast.success("방문 접수가 등록되었습니다.");
      await Promise.all([
        client.invalidateQueries({ queryKey: ["visits"] }),
        client.invalidateQueries({ queryKey: ["appointments"] }),
        client.invalidateQueries({ queryKey: ["appointment"] }),
      ]);
      onCreated(response.data);
    },
  });
  return (
    <FormModal
      open
      onOpenChange={onClose}
      title={appointment ? "예약 환자 방문 접수" : "예약 없는 방문 접수"}
      description="환자와 담당 의사를 확인해 주세요. 접수 진료과와 방문 유형은 서버에서 결정합니다."
      pending={save.isPending}
      error={error}
      submitLabel="접수 등록"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(undefined);
        try {
          await save.mutateAsync();
        } catch (e) {
          setError(e);
        }
      }}
    >
      {appointment ? (
        <div className="rounded-lg bg-accent p-4 text-sm">
          <p className="font-semibold">
            {appointment.patientName} · {appointment.doctorName}
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            예약 #{appointment.id} ·{" "}
            {appointment.scheduledAt.replace("T", " ").slice(0, 16)}
          </p>
        </div>
      ) : (
        <PatientPicker value={patient} onChange={setPatient} />
      )}
      {initial.error && (
        <ErrorBox error={initial.error} retry={() => void initial.refetch()} />
      )}
      <Field label="담당 의사">
        <select
          className="control"
          value={doctorId}
          required
          disabled={!!appointment}
          onChange={(e) => setDoctorId(e.target.value)}
        >
          <option value="">의사 선택</option>
          {doctors.data?.map((doctor) => (
            <option key={doctor.id} value={doctor.id}>
              {doctor.name} · {doctor.departmentName}
            </option>
          ))}
        </select>
      </Field>
      {doctors.error && (
        <ErrorBox error={doctors.error} retry={() => void doctors.refetch()} />
      )}
    </FormModal>
  );
}
