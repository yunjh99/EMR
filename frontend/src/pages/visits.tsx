import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ClipboardList, Plus, RefreshCw } from "lucide-react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { toast } from "sonner";
import { useSession } from "@/auth/session";
import { api } from "@/lib/api";
import { dateTime, localDate, visitActions } from "@/lib/policy";
import type { VisitResponse, VisitStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { VisitRegistration } from "@/components/visit-registration";
import {
  ConfirmAction,
  DataTable,
  EmptyState,
  ErrorBox,
  Field,
  LoadingRows,
  PageHeader,
  Pagination,
  StatusBadge,
  formValues,
} from "@/components/common";

function VisitButtons({
  visit,
  detail = false,
}: {
  visit: VisitResponse;
  detail?: boolean;
}) {
  const { user } = useSession();
  const client = useQueryClient();
  const navigate = useNavigate();
  if (!user) return null;
  const allowed = visitActions(
    user.role,
    visit.status,
    user.id === visit.doctorUserId,
  );
  return (
    <div className="flex flex-wrap gap-2">
      {!detail && (
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/visits/${visit.id}`}>접수 상세</Link>
        </Button>
      )}
      {allowed.cancel && (
        <ConfirmAction
          label="접수 취소"
          title="대기 접수를 취소할까요?"
          description="접수 내역은 보존되며 예약 상태는 변경하지 않습니다."
          onConfirm={async () => {
            await api.cancelVisit(visit.id);
            await client.invalidateQueries({ queryKey: ["visits"] });
          }}
        />
      )}
      {allowed.start && (
        <ConfirmAction
          label="진료 보기"
          title="진료를 시작할까요?"
          description={`${visit.patientName} 환자의 진료를 시작합니다.`}
          onConfirm={async () => {
            await api.startVisit(visit.id);
            await client.invalidateQueries({ queryKey: ["visits"] });
            navigate(`/visits/${visit.id}`);
          }}
        />
      )}
      {user.role === "DOCTOR" &&
        user.id === visit.doctorUserId &&
        visit.status === "IN_PROGRESS" &&
        !detail && (
          <Button size="sm" asChild>
            <Link to={`/visits/${visit.id}`}>기록 작성</Link>
          </Button>
        )}
      {visit.encounterId && user.role === "DOCTOR" && (
        <Button variant="outline" size="sm" asChild>
          <Link to={`/encounters/${visit.encounterId}`}>진료 기록</Link>
        </Button>
      )}
      {visit.encounterId && user.role === "NURSE" && (
        <Button variant="outline" size="sm" asChild>
          <Link to={`/histories?encounterId=${visit.encounterId}`}>
            수정 이력
          </Link>
        </Button>
      )}
    </div>
  );
}

export function VisitsPage() {
  const { user } = useSession();
  const staff = user?.role === "STAFF";
  const client = useQueryClient();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState({
    date: localDate(),
    doctorId: "",
    status: "" as VisitStatus | "",
    page: 0,
    size: 20,
  });
  const [creating, setCreating] = useState(false);
  const registering = staff && (creating || params.get("register") === "1");
  const patientId = params.get("patientId") || "";
  const appointmentId = Number(params.get("appointmentId")) || undefined;
  const list = useQuery({
    queryKey: ["visits", "list", filters, patientId],
    queryFn: ({ signal }) => api.visits({ ...filters, patientId }, signal),
  });
  const counts = useQuery({
    queryKey: ["visits", "counts", filters.date, filters.doctorId, patientId],
    queryFn: async ({ signal }) =>
      Object.fromEntries(
        await Promise.all(
          (["WAITING", "IN_PROGRESS", "COMPLETED", "CANCELLED"] as const).map(
            async (status) => [
              status,
              (
                await api.visits(
                  {
                    date: filters.date,
                    doctorId: filters.doctorId,
                    patientId,
                    status,
                    page: 0,
                    size: 1,
                  },
                  signal,
                )
              ).totalElements,
            ],
          ),
        ),
      ),
  });
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
  });
  const booking = useQuery({
    queryKey: ["appointment", appointmentId],
    queryFn: ({ signal }) => api.appointment(appointmentId!, signal),
    enabled: !!registering && !!appointmentId,
  });
  const close = () => {
    setCreating(false);
    const next = new URLSearchParams(params);
    next.delete("register");
    next.delete("appointmentId");
    setParams(next, { replace: true });
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="PATIENT CARE / VISITS"
        title="접수 · 진료 대기"
        description={
          user?.role === "DOCTOR"
            ? "본인이 담당하는 접수를 확인하고 진료를 시작합니다."
            : "환자의 접수 상태와 진료 진행 흐름을 확인합니다."
        }
        action={
          staff && (
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} />
              방문 접수
            </Button>
          )
        }
      />
      <div className="grid grid-cols-4 gap-4">
        {(["WAITING", "IN_PROGRESS", "COMPLETED", "CANCELLED"] as const).map(
          (status) => (
            <button
              key={status}
              className="panel p-5 text-left hover:border-primary/30"
              onClick={() => setFilters({ ...filters, status, page: 0 })}
            >
              <StatusBadge status={status} />
              <p className="mt-4 text-2xl font-semibold">
                {counts.data?.[status] ?? "—"}
              </p>
              <p className="mt-2 text-[11px] text-muted-foreground">
                선택한 날짜 · 조회 권한 기준
              </p>
            </button>
          ),
        )}
      </div>
      <section className="panel">
        <div className="flex gap-3 border-b p-5">
          <input
            aria-label="접수 날짜"
            className="control w-[160px]"
            type="date"
            value={filters.date}
            onChange={(e) => {
              if (e.target.value)
                setFilters({ ...filters, date: e.target.value, page: 0 });
            }}
          />
          <select
            aria-label="담당 의사 필터"
            className="control w-[210px]"
            value={filters.doctorId}
            onChange={(e) =>
              setFilters({ ...filters, doctorId: e.target.value, page: 0 })
            }
          >
            <option value="">
              {user?.role === "DOCTOR" ? "본인 담당 접수" : "전체 의료진"}
            </option>
            {doctors.data
              ?.filter((d) => user?.role !== "DOCTOR" || d.userId === user.id)
              .map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} · {d.departmentName}
                </option>
              ))}
          </select>
          <select
            aria-label="접수 상태 필터"
            className="control w-[150px]"
            value={filters.status}
            onChange={(e) =>
              setFilters({
                ...filters,
                status: e.target.value as VisitStatus | "",
                page: 0,
              })
            }
          >
            <option value="">전체 상태</option>
            <option value="WAITING">진료 대기</option>
            <option value="IN_PROGRESS">진료 중</option>
            <option value="COMPLETED">진료 완료</option>
            <option value="CANCELLED">접수 취소</option>
          </select>
          <Button
            variant="ghost"
            className="ml-auto"
            onClick={() =>
              void client.invalidateQueries({ queryKey: ["visits"] })
            }
          >
            <RefreshCw size={14} />
            새로고침
          </Button>
        </div>
        {(counts.error || doctors.error) && (
          <div className="px-5">
            <ErrorBox error={counts.error || doctors.error} />
          </div>
        )}
        {list.isPending ? (
          <LoadingRows />
        ) : list.error ? (
          <div className="p-5">
            <ErrorBox error={list.error} retry={() => void list.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={list.data?.content || []}
            columns={[
              {
                label: "접수 시각",
                render: (r) => dateTime(r.checkedInAt).slice(11),
              },
              {
                label: "환자명",
                render: (r) => (
                  <span className="font-medium">
                    {r.patientName}
                    <span className="ml-2 text-[10px] text-muted-foreground">
                      {r.patientNo}
                    </span>
                  </span>
                ),
              },
              {
                label: "담당 의료진",
                render: (r) => `${r.departmentName} · ${r.doctorName}`,
              },
              {
                label: "방문 유형",
                render: (r) =>
                  r.visitType === "APPOINTMENT"
                    ? "예약 방문"
                    : "예약 없는 방문",
              },
              {
                label: "접수 상태",
                render: (r) => <StatusBadge status={r.status} />,
              },
              { label: "업무", render: (r) => <VisitButtons visit={r} /> },
            ]}
          />
        )}
        <Pagination
          page={list.data}
          onPage={(page) => setFilters({ ...filters, page })}
        />
      </section>
      <p className="note">
        진료 기록을 최초 저장하면 자동으로 진료가 완료됩니다. 이후 정정은 완료
        상태를 유지하며 이전 버전을 보존합니다.
      </p>
      {registering && appointmentId && booking.error && (
        <ErrorBox error={booking.error} />
      )}
      {registering && (!appointmentId || booking.data) && (
        <VisitRegistration
          appointment={booking.data}
          initialPatientId={Number(patientId) || undefined}
          onClose={close}
          onCreated={(id) => {
            close();
            navigate(`/visits/${id}`);
          }}
        />
      )}
    </div>
  );
}

function EncounterWriter({ visit }: { visit: VisitResponse }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>();
  const save = useMutation({
    mutationFn: (values: Record<string, string>) =>
      api.createEncounter({
        visitId: visit.id,
        chiefComplaint: values.chiefComplaint.trim(),
        clinicalNote: values.clinicalNote.trim(),
        treatmentPlan: values.treatmentPlan.trim(),
      }),
    onSuccess: async (result) => {
      toast.success("진료 기록이 저장되고 진료가 완료되었습니다.");
      await client.invalidateQueries({ queryKey: ["visits"] });
      await client.invalidateQueries({
        queryKey: ["patientEncounters", visit.patientId],
      });
      navigate(`/encounters/${result.data}`);
    },
  });
  return (
    <form
      className="panel space-y-5 p-6"
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
      <h2 className="flex items-center gap-2 text-sm font-semibold">
        <ClipboardList size={17} className="text-primary" />
        진료 기록 작성
      </h2>
      <Field label="주호소">
        <textarea
          className="control h-24 py-2"
          name="chiefComplaint"
          required
          maxLength={2000}
        />
      </Field>
      <Field label="진료 소견">
        <textarea
          className="control h-36 py-2"
          name="clinicalNote"
          required
          maxLength={5000}
        />
      </Field>
      <Field label="치료 계획">
        <textarea
          className="control h-28 py-2"
          name="treatmentPlan"
          required
          maxLength={5000}
        />
      </Field>
      {!!error && <ErrorBox error={error} />}
      <div className="flex items-center justify-between border-t pt-5">
        <p className="text-xs text-muted-foreground">
          최초 저장 시 진료가 자동 완료됩니다. 임시 저장 기능은 없습니다.
        </p>
        <Button disabled={save.isPending}>
          {save.isPending ? "저장 중…" : "진료 기록 저장"}
        </Button>
      </div>
    </form>
  );
}

export function VisitDetailPage() {
  const { id } = useParams();
  const visitId = Number(id);
  const valid = Number.isSafeInteger(visitId) && visitId > 0;
  const { user } = useSession();
  const query = useQuery({
    queryKey: ["visits", "detail", visitId],
    queryFn: ({ signal }) => api.visit(visitId, signal),
    enabled: valid,
  });
  const visit = query.data;
  return (
    <div className="page">
      <Link
        to="/visits"
        className="flex items-center gap-1 text-xs text-muted-foreground"
      >
        <ArrowLeft size={13} />
        접수 목록으로
      </Link>
      <PageHeader
        eyebrow="PATIENT CARE / VISIT DETAIL"
        title="접수 상세 · 진료"
        description="현재 접수 상태와 담당 의사를 서버에서 확인합니다."
      />
      {!valid ? (
        <EmptyState title="올바른 접수 ID가 필요합니다" />
      ) : query.isPending ? (
        <div className="panel">
          <LoadingRows />
        </div>
      ) : query.error ? (
        <ErrorBox error={query.error} retry={() => void query.refetch()} />
      ) : (
        visit && (
          <>
            <section className="panel p-6">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold">
                  {visit.patientName}
                  <span className="ml-3 text-xs font-normal text-muted-foreground">
                    {visit.patientNo}
                  </span>
                </h2>
                <StatusBadge status={visit.status} />
              </div>
              <dl className="mt-5 grid grid-cols-4 gap-4 border-t pt-5 text-xs">
                {[
                  ["담당 의사", visit.doctorName],
                  ["진료과", visit.departmentName],
                  ["접수 일시", dateTime(visit.checkedInAt)],
                  ["완료 일시", dateTime(visit.completedAt)],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd className="mt-2 font-medium">{value}</dd>
                  </div>
                ))}
              </dl>
              <div className="mt-6">
                <VisitButtons visit={visit} detail />
              </div>
            </section>
            {user?.role === "DOCTOR" &&
              user.id === visit.doctorUserId &&
              visit.status === "IN_PROGRESS" && (
                <EncounterWriter visit={visit} />
              )}
            {visit.status === "WAITING" && (
              <p className="note">
                담당 의사가 진료 보기를 눌러 시작한 뒤 기록을 작성할 수
                있습니다.
              </p>
            )}
            {visit.status === "COMPLETED" && (
              <p className="note">
                진료가 완료되었습니다. 담당 의사는 진료 기록에서 정정 사유와
                함께 내용을 수정할 수 있습니다.
              </p>
            )}
          </>
        )
      )}
    </div>
  );
}
