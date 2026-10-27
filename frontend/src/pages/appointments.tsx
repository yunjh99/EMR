import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Plus,
  RefreshCw,
} from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { useSession } from "@/auth/session";
import { api, dayReservations } from "@/lib/api";
import { dateTime, localDate, positiveId, slots } from "@/lib/policy";
import { cn } from "@/lib/utils";
import type { AppointmentStatus, PatientResponse } from "@/lib/types";
import { PatientPicker } from "@/components/patient-picker";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  ConfirmAction,
  DataTable,
  ErrorBox,
  Field,
  FormModal,
  LoadingRows,
  PageHeader,
  Pagination,
  StatusBadge,
  formValues,
} from "@/components/common";

function CreateAppointment({
  date: initialDate,
  onClose,
}: {
  date: string;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const [doctorId, setDoctorId] = useState("");
  const [patient, setPatient] = useState<PatientResponse | null>(null);
  const [date, setDate] = useState(initialDate);
  const [time, setTime] = useState("");
  const [error, setError] = useState<unknown>();
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
  });
  const reservations = useQuery({
    queryKey: ["appointments", "daySlots", doctorId, date],
    queryFn: ({ signal }) => dayReservations(doctorId, date, signal),
    enabled: !!doctorId && !!date,
  });
  const save = useMutation({
    mutationFn: (values: Record<string, string>) => {
      if (!slots.includes(time)) throw new Error("예약 시간을 선택해 주세요.");
      if (!patient) throw new Error("환자를 검색해 선택해 주세요.");
      return api.createAppointment({
        doctor: { id: positiveId(doctorId) },
        patient: { id: patient.id },
        scheduledAt: `${date}T${time}:00`,
        reson: values.reason.trim(),
      });
    },
    onSuccess: async () => {
      toast.success("예약이 등록되었습니다.");
      await client.invalidateQueries({ queryKey: ["appointments"] });
      onClose();
    },
  });
  return (
    <FormModal
      open
      onOpenChange={onClose}
      title="예약 등록"
      description="담당 의사와 시간을 선택하고 환자를 연결합니다."
      pending={save.isPending}
      error={error}
      submitLabel="예약 등록"
      onSubmit={async (event) => {
        const values = formValues(event);
        setError(undefined);
        try {
          await save.mutateAsync(values);
        } catch (e) {
          setError(e);
        }
      }}
    >
      <PatientPicker value={patient} onChange={setPatient} />
      <div className="grid grid-cols-2 gap-4">
        <Field label="예약일">
          <input
            className="control"
            type="date"
            required
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setTime("");
            }}
          />
        </Field>
        <Field label="담당 의사">
          <select
            className="control"
            required
            value={doctorId}
            onChange={(e) => {
              setDoctorId(e.target.value);
              setTime("");
            }}
          >
            <option value="">의사 선택</option>
            {doctors.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} · {d.departmentName}
              </option>
            ))}
          </select>
        </Field>
      </div>
      {doctors.error && (
        <ErrorBox error={doctors.error} retry={() => void doctors.refetch()} />
      )}
      <div>
        <p className="mb-3 text-[13px] font-medium">
          예약 시간{" "}
          <span className="ml-2 text-[11px] font-normal text-muted-foreground">
            09:00–18:00 · 30분 단위
          </span>
        </p>
        {reservations.error ? (
          <ErrorBox
            error={reservations.error}
            retry={() => void reservations.refetch()}
          />
        ) : (
          <div className="grid grid-cols-6 gap-2">
            {slots.map((slot) => {
              const record = reservations.data?.find(
                (a) => a.scheduledAt.slice(11, 16) === slot,
              );
              return (
                <button
                  type="button"
                  key={slot}
                  aria-pressed={time === slot}
                  disabled={
                    !doctorId ||
                    reservations.isFetching ||
                    !!record ||
                    !reservations.data
                  }
                  onClick={() => setTime(slot)}
                  title={
                    record
                      ? record.status === "CANCELLED"
                        ? "취소 기록이 있습니다. 현재 DB 제약으로 같은 슬롯 재예약이 제한됩니다."
                        : "이미 예약된 시간입니다."
                      : "예약 내역 없음. 실제 슬롯 존재 여부는 서버에서 확인합니다."
                  }
                  className={cn(
                    "rounded-md border px-2 py-2 text-xs transition disabled:cursor-not-allowed disabled:bg-muted disabled:text-slate-300",
                    time === slot &&
                      "border-primary bg-accent font-semibold text-primary",
                  )}
                >
                  {slot}
                </button>
              );
            })}
          </div>
        )}
        <p className="mt-3 text-[11px] leading-5 text-muted-foreground">
          예약 내역 기준 표시입니다. 빈 시간도 슬롯이 준비되지 않았을 수 있으며
          최종 가능 여부는 서버가 확인합니다. 취소 내역이 있는 시간은 현재 DB
          제약으로 재예약이 제한됩니다.
        </p>
      </div>
      <Field label="예약 사유">
        <textarea
          className="control h-20 py-2"
          name="reason"
          maxLength={255}
          placeholder="예약 사유를 입력해 주세요 (선택)"
        />
      </Field>
    </FormModal>
  );
}

export function AppointmentsPage() {
  const { user } = useSession();
  const staff = user?.role === "STAFF";
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const date = params.get("date") || localDate();
  const doctorId = params.get("doctorId") || "";
  const status = (params.get("status") || "") as AppointmentStatus | "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const [creating, setCreating] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
  });
  const query = useQuery({
    queryKey: ["appointments", "list", date, doctorId, status, page],
    queryFn: ({ signal }) =>
      api.appointments({ date, doctorId, status, page, size: 20 }, signal),
  });
  const detail = useQuery({
    queryKey: ["appointment", detailId],
    queryFn: ({ signal }) => api.appointment(detailId!, signal),
    enabled: detailId !== null,
  });
  const filter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    next.set(key, value);
    if (key !== "page") next.set("page", "0");
    setParams(next);
  };
  const stepDate = (days: number) => {
    const value = new Date(`${date}T12:00:00`);
    value.setDate(value.getDate() + days);
    filter("date", localDate(value));
  };
  const cancel = async (id: number) => {
    await api.cancelAppointment(id);
    await client.invalidateQueries({ queryKey: ["appointments"] });
    await client.invalidateQueries({ queryKey: ["appointment", id] });
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="PATIENT CARE / APPOINTMENTS"
        title="예약 관리"
        description="의사별 예약 현황을 확인하고 환자의 방문 일정을 관리합니다."
        action={
          staff && (
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} />
              예약 등록
            </Button>
          )
        }
      />
      <section className="panel">
        <div className="flex flex-wrap items-center gap-3 border-b p-5">
          <div className="flex items-center gap-2">
            <Button
              aria-label="전날"
              variant="outline"
              size="sm"
              onClick={() => stepDate(-1)}
            >
              <ChevronLeft size={15} />
            </Button>
            <input
              aria-label="예약 날짜"
              className="control w-[155px]"
              type="date"
              required
              value={date}
              onChange={(e) => {
                if (e.target.value) filter("date", e.target.value);
              }}
            />
            <Button
              aria-label="다음 날"
              variant="outline"
              size="sm"
              onClick={() => stepDate(1)}
            >
              <ChevronRight size={15} />
            </Button>
          </div>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => filter("date", localDate())}
          >
            오늘
          </Button>
          <span className="mx-1 h-5 w-px bg-border" />
          <select
            className="control w-[190px]"
            aria-label="담당 의사 필터"
            value={doctorId}
            onChange={(e) => filter("doctorId", e.target.value)}
          >
            <option value="">전체 의료진</option>
            {doctors.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} · {d.departmentName}
              </option>
            ))}
          </select>
          <select
            className="control w-[150px]"
            aria-label="예약 상태 필터"
            value={status}
            onChange={(e) => filter("status", e.target.value)}
          >
            <option value="">전체 상태</option>
            <option value="SCHEDULED">예약 예정</option>
            <option value="CHECKED_IN">접수 완료</option>
            <option value="CANCELLED">취소</option>
          </select>
          <Button
            className="ml-auto"
            variant="ghost"
            size="sm"
            onClick={() =>
              void client.invalidateQueries({ queryKey: ["appointments"] })
            }
          >
            <RefreshCw size={14} />
            새로고침
          </Button>
        </div>
        <div className="flex items-center justify-between p-5">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <CalendarDays size={16} className="text-primary" />
            예약 현황
            <span className="ml-1 text-xs font-normal text-muted-foreground">
              {query.data ? `${query.data.totalElements}건` : ""}
            </span>
          </h2>
          <span className="text-[11px] text-muted-foreground">
            09:00–17:30 시작 · 30분 간격
          </span>
        </div>
        {doctors.error && (
          <div className="px-5">
            <ErrorBox
              error={doctors.error}
              retry={() => void doctors.refetch()}
            />
          </div>
        )}
        {query.isPending ? (
          <LoadingRows />
        ) : query.error ? (
          <div className="px-5">
            <ErrorBox error={query.error} retry={() => void query.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={query.data?.content || []}
            columns={[
              {
                label: "예약 시간",
                render: (r) => (
                  <span className="font-semibold tabular-nums">
                    {dateTime(r.scheduledAt).slice(11)}
                  </span>
                ),
              },
              {
                label: "환자명",
                render: (r) => (
                  <button
                    className="text-link"
                    onClick={() => setDetailId(r.id)}
                  >
                    {r.patientName}
                    <span className="ml-2 text-[10px] font-normal text-muted-foreground">
                      #{r.patientId}
                    </span>
                  </button>
                ),
              },
              { label: "담당 의사", render: (r) => r.doctorName },
              {
                label: "예약 사유",
                render: (r) => (
                  <span className="block max-w-[230px] truncate text-muted-foreground">
                    {r.reason || "—"}
                  </span>
                ),
              },
              {
                label: "상태",
                render: (r) => <StatusBadge status={r.status} />,
              },
              {
                label: "관리",
                render: (r) => (
                  <div className="flex gap-2">
                    {staff && r.status === "SCHEDULED" && (
                      <Button size="sm" variant="outline" asChild>
                        <Link
                          to={`/visits?register=1&appointmentId=${r.id}&patientId=${r.patientId}`}
                        >
                          방문 접수
                        </Link>
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDetailId(r.id)}
                    >
                      상세
                    </Button>
                    {staff && r.status === "SCHEDULED" && (
                      <ConfirmAction
                        label="예약 취소"
                        title="예약을 취소할까요?"
                        description="취소 내역은 보존됩니다. 현재 DB 제약으로 같은 시간에 재예약이 제한될 수 있습니다."
                        onConfirm={() => cancel(r.id)}
                      />
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
        <Pagination
          page={query.data}
          onPage={(value) => filter("page", String(value))}
        />
      </section>
      {staff && (
        <p className="note">
          방문한 예약 환자는 예약 행의 방문 접수로 연결해 주세요. 예약 취소와
          접수 취소는 서로 다른 작업이며, 접수 취소가 예약 상태를 되돌리지는
          않습니다.
        </p>
      )}
      {creating && (
        <CreateAppointment date={date} onClose={() => setCreating(false)} />
      )}
      <Dialog
        open={detailId !== null}
        onOpenChange={(open) => {
          if (!open) setDetailId(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>예약 상세</DialogTitle>
            <DialogDescription>
              예약 정보와 현재 상태를 확인합니다.
            </DialogDescription>
          </DialogHeader>
          {detail.isPending ? (
            <LoadingRows columns={2} />
          ) : detail.error ? (
            <ErrorBox
              error={detail.error}
              retry={() => void detail.refetch()}
            />
          ) : (
            detail.data && (
              <div className="space-y-5 py-2">
                <div className="flex items-center justify-between">
                  <span className="text-lg font-semibold">
                    {detail.data.patientName}
                  </span>
                  <StatusBadge status={detail.data.status} />
                </div>
                <dl className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt className="text-xs text-muted-foreground">예약 일시</dt>
                    <dd className="mt-2">
                      {dateTime(detail.data.scheduledAt)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">담당 의사</dt>
                    <dd className="mt-2">{detail.data.doctorName}</dd>
                  </div>
                </dl>
                <div className="rounded-lg bg-muted p-4 text-sm leading-7">
                  {detail.data.reason || "등록된 예약 사유가 없습니다."}
                </div>
                <p className="text-xs text-muted-foreground">
                  환자 ID #{detail.data.patientId} · 예약 ID #{detail.data.id}
                </p>
              </div>
            )
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
