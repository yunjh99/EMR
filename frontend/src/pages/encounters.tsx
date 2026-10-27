import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  FileClock,
  FilePenLine,
  FileText,
  Search,
} from "lucide-react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { toast } from "sonner";
import { useSession } from "@/auth/session";
import { PatientPicker } from "@/components/patient-picker";
import { api } from "@/lib/api";
import { dateTime, mayEditEncounter, positiveId } from "@/lib/policy";
import type {
  EncounterResponse,
  EncounterUpdateRequest,
  PatientResponse,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DataTable,
  EmptyState,
  ErrorBox,
  Field,
  FormModal,
  LoadingRows,
  PageHeader,
  Pagination,
  StatusBadge,
  formValues,
} from "@/components/common";

function parsedId(value: string | null | undefined) {
  try {
    return value ? positiveId(value) : undefined;
  } catch {
    return undefined;
  }
}
export function EncountersPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const patientId = parsedId(params.get("patientId"));
  const [pickedPatient, setPickedPatient] = useState<PatientResponse | null>(
    null,
  );
  const page = Math.max(0, Number(params.get("page")) || 0);
  const [error, setError] = useState<unknown>();
  const list = useQuery({
    queryKey: ["patientEncounters", patientId, page],
    queryFn: ({ signal }) => api.patientEncounters(patientId!, page, signal),
    enabled: !!patientId,
  });
  return (
    <div className="page">
      <PageHeader
        eyebrow="PATIENT CARE / CLINICAL RECORDS"
        title="환자 진료 기록"
        description="환자별 이전 진료 기록을 확인합니다. 전체 환자의 기록을 한꺼번에 조회하지 않습니다."
      />
      <section className="panel">
        <div className="border-b p-5">
          <PatientPicker
            value={pickedPatient}
            onChange={(patient) => {
              setPickedPatient(patient);
              setParams(
                patient ? { patientId: String(patient.id), page: "0" } : {},
              );
            }}
          />
        </div>
        <div className="grid grid-cols-[1fr_1fr] gap-6 p-5">
          <form
            onSubmit={(event) => {
              const v = formValues(event);
              setError(undefined);
              try {
                setParams({
                  patientId: String(positiveId(v.patientId)),
                  page: "0",
                });
              } catch (e) {
                setError(e);
              }
            }}
          >
            <label
              className="mb-2 block text-xs font-medium"
              htmlFor="record-patient"
            >
              환자별 진료 기록 조회
            </label>
            <div className="flex gap-2">
              <input
                id="record-patient"
                className="control"
                name="patientId"
                type="number"
                min="1"
                required
                defaultValue={patientId}
                placeholder="확인된 환자 ID 입력"
              />
              <Button variant="outline">
                <Search size={14} />
                조회
              </Button>
            </div>
          </form>
          <form
            onSubmit={(event) => {
              const v = formValues(event);
              setError(undefined);
              try {
                navigate(`/encounters/${positiveId(v.encounterId)}`);
              } catch (e) {
                setError(e);
              }
            }}
          >
            <label
              className="mb-2 block text-xs font-medium"
              htmlFor="record-direct"
            >
              진료 기록 바로 찾기
            </label>
            <div className="flex gap-2">
              <input
                id="record-direct"
                className="control"
                name="encounterId"
                type="number"
                min="1"
                required
                placeholder="진료 기록 ID 입력"
              />
              <Button variant="outline">상세 보기</Button>
            </div>
          </form>
        </div>
        {!!error && (
          <div className="px-5">
            <ErrorBox error={error} />
          </div>
        )}
        <p className="border-t px-5 py-3 text-[11px] text-muted-foreground">
          환자를 검색해 이전 기록을 확인할 수 있습니다. 새 기록은 접수·진료
          대기에서 담당 환자의 진료를 시작한 뒤 작성합니다.
        </p>
      </section>
      <section className="panel">
        <div className="flex items-center gap-2 border-b p-5">
          <FileText size={17} className="text-primary" />
          <h2 className="text-sm font-semibold">
            {patientId ? `환자 #${patientId}의 진료 기록` : "진료 기록 목록"}
          </h2>
        </div>
        {!patientId ? (
          <EmptyState
            title="조회할 환자를 선택해 주세요"
            description="환자 ID를 입력하면 해당 환자의 진료 기록만 표시됩니다."
          />
        ) : list.isPending ? (
          <LoadingRows />
        ) : list.error ? (
          <div className="px-5">
            <ErrorBox error={list.error} retry={() => void list.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={list.data?.content || []}
            columns={[
              {
                label: "기록 일시",
                render: (r) => (
                  <Link className="text-link" to={`/encounters/${r.id}`}>
                    {dateTime(r.createdAt)}
                  </Link>
                ),
              },
              {
                label: "환자",
                render: (r) => (
                  <span>
                    {r.patientName}
                    <span className="ml-2 text-[10px] text-muted-foreground">
                      {r.patientNo}
                    </span>
                  </span>
                ),
              },
              {
                label: "진료과 · 의사",
                render: (r) => (
                  <span>
                    {r.departmentName} · {r.doctorName}
                  </span>
                ),
              },
              {
                label: "주호소",
                render: (r) => (
                  <span className="block max-w-[220px] truncate">
                    {r.chiefComplaint}
                  </span>
                ),
              },
              { label: "업무 버전", render: (r) => `v${r.version}` },
              {
                label: "접수 상태",
                render: (r) => <StatusBadge status={r.visitStatus} />,
              },
              {
                label: "조회",
                render: (r) => (
                  <Button variant="ghost" size="sm" asChild>
                    <Link to={`/encounters/${r.id}`}>상세 보기</Link>
                  </Button>
                ),
              },
            ]}
          />
        )}
        {patientId && (
          <Pagination
            page={list.data}
            onPage={(value) =>
              setParams({ patientId: String(patientId), page: String(value) })
            }
          />
        )}
      </section>
    </div>
  );
}

function ClinicalText({
  label,
  children,
}: {
  label: string;
  children: string | null;
}) {
  return (
    <section className="rounded-lg border p-5">
      <h3 className="mb-3 text-xs font-semibold text-primary">{label}</h3>
      <p className="whitespace-pre-wrap break-words text-[13px] leading-7">
        {children || "기록 없음"}
      </p>
    </section>
  );
}

function EditEncounter({
  record,
  onClose,
}: {
  record: EncounterResponse;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const [error, setError] = useState<unknown>();
  const save = useMutation({
    mutationFn: (v: Record<string, string>) => {
      const body: EncounterUpdateRequest = {
        changeReason: v.changeReason.trim(),
      };
      if (!body.changeReason) throw new Error("정정 사유를 입력해 주세요.");
      if (v.chiefComplaint !== record.chiefComplaint)
        body.chiefComplaint = v.chiefComplaint;
      if (v.clinicalNote !== record.clinicalNote)
        body.clinicalNote = v.clinicalNote;
      if (v.treatmentPlan !== record.treatmentPlan)
        body.treatmentPlan = v.treatmentPlan;
      return api.updateEncounter(record.id, body);
    },
    onSuccess: async () => {
      toast.success(
        "진료 기록이 저장되었습니다. 변경 전 기록은 수정 이력에서 확인할 수 있습니다.",
      );
      await Promise.all([
        client.invalidateQueries({ queryKey: ["encounter", record.id] }),
        client.invalidateQueries({
          queryKey: ["patientEncounters", record.patientId],
        }),
        client.invalidateQueries({ queryKey: ["histories", record.id] }),
      ]);
      onClose();
    },
  });
  return (
    <FormModal
      open
      onOpenChange={onClose}
      title="진료 기록 정정"
      description={`현재 v${record.version} · 실제 변경 시 이전 버전 전체가 보존됩니다. 진료 완료 상태는 유지됩니다.`}
      error={error}
      pending={save.isPending}
      submitLabel="정정 내용 저장"
      onSubmit={async (event) => {
        const v = formValues(event);
        setError(undefined);
        try {
          await save.mutateAsync(v);
        } catch (e) {
          setError(e);
        }
      }}
    >
      <Field label="주호소">
        <textarea
          className="control h-20 py-2"
          name="chiefComplaint"
          defaultValue={record.chiefComplaint}
          required
        />
      </Field>
      <Field label="진료 소견">
        <textarea
          className="control h-28 py-2"
          name="clinicalNote"
          defaultValue={record.clinicalNote}
          required
        />
      </Field>
      <Field label="치료 계획">
        <textarea
          className="control h-24 py-2"
          name="treatmentPlan"
          defaultValue={record.treatmentPlan}
          required
        />
      </Field>
      <Field
        label="정정 사유 (필수)"
        help="수정한 사용자와 시각, 정정 사유가 이전 원본과 함께 보존됩니다."
      >
        <textarea
          className="control h-20 py-2"
          name="changeReason"
          required
          maxLength={500}
          placeholder="정정 사유를 구체적으로 입력해 주세요."
        />
      </Field>
    </FormModal>
  );
}

export function EncounterDetailPage() {
  const { id } = useParams();
  const encounterId = parsedId(id);
  const { user } = useSession();
  const [editing, setEditing] = useState(false);
  const record = useQuery({
    queryKey: ["encounter", encounterId],
    queryFn: ({ signal }) => api.encounter(encounterId!, signal),
    enabled: !!encounterId,
  });
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
  });
  const ownDoctorId = doctors.data?.find((d) => d.userId === user?.id)?.id;
  const data = record.data;
  const canEdit =
    !!user && !!data && mayEditEncounter(user.role, ownDoctorId, data.doctorId);
  return (
    <div className="page">
      <Link
        to="/encounters"
        className="flex items-center gap-1 text-xs text-muted-foreground hover:text-primary"
      >
        <ArrowLeft size={13} />
        환자별 기록으로
      </Link>
      <PageHeader
        eyebrow="CLINICAL RECORD / DETAIL"
        title="진료 기록 상세"
        description="현재 최신 기록입니다. 정정 전 원본은 수정 이력에서 확인할 수 있습니다."
        action={
          canEdit && (
            <Button onClick={() => setEditing(true)}>
              <FilePenLine size={15} />
              기록 정정
            </Button>
          )
        }
      />
      {!encounterId ? (
        <EmptyState title="올바른 진료 기록 ID가 필요합니다" />
      ) : record.isPending ? (
        <div className="panel">
          <LoadingRows />
        </div>
      ) : record.error ? (
        <ErrorBox error={record.error} retry={() => void record.refetch()} />
      ) : (
        data && (
          <>
            <section className="panel p-6">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-semibold">{data.patientName}</h2>
                <span className="rounded-md bg-muted px-2 py-1 font-mono text-[11px] text-muted-foreground">
                  {data.patientNo}
                </span>
                <StatusBadge status={data.visitStatus} />
                <span className="ml-auto rounded-md bg-accent px-3 py-1 text-xs font-medium text-primary">
                  최신 버전 v{data.version}
                </span>
              </div>
              <dl className="mt-5 grid grid-cols-4 gap-5 border-t pt-5 text-xs">
                {[
                  ["진료과", data.departmentName],
                  ["담당 의사", data.doctorName],
                  ["최초 작성", dateTime(data.createdAt)],
                  ["마지막 정정", dateTime(data.updatedAt)],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd className="mt-2 font-medium">{value}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section className="panel space-y-4 p-6">
              <ClinicalText label="주호소 · Chief complaint">
                {data.chiefComplaint}
              </ClinicalText>
              <ClinicalText label="진료 소견 · Clinical note">
                {data.clinicalNote}
              </ClinicalText>
              <ClinicalText label="치료 계획 · Treatment plan">
                {data.treatmentPlan}
              </ClinicalText>
            </section>
            {doctors.error && (
              <ErrorBox
                error={doctors.error}
                retry={() => void doctors.refetch()}
              />
            )}
            <p className="text-xs text-muted-foreground">
              {canEdit
                ? "담당 의사는 진료 완료 후에도 정정 사유와 함께 기록을 수정할 수 있습니다."
                : "기록 정정은 서버에서 담당 의사로 확인된 사용자에게만 제공됩니다."}
            </p>
            <div className="flex justify-end">
              <Button variant="outline" asChild>
                <Link to={`/histories?encounterId=${data.id}`}>
                  <FileClock size={15} />
                  이전 버전 확인
                </Link>
              </Button>
            </div>
            {editing && canEdit && (
              <EditEncounter record={data} onClose={() => setEditing(false)} />
            )}
          </>
        )
      )}
    </div>
  );
}

function HistoryPanel({ encounterId }: { encounterId: number }) {
  const [page, setPage] = useState(0);
  const [historyId, setHistoryId] = useState<number | null>(null);
  const list = useQuery({
    queryKey: ["histories", encounterId, page],
    queryFn: ({ signal }) => api.histories(encounterId, page, signal),
  });
  const detail = useQuery({
    queryKey: ["history", encounterId, historyId],
    queryFn: ({ signal }) => api.history(encounterId, historyId!, signal),
    enabled: historyId !== null,
  });
  return (
    <>
      <section className="panel">
        <div className="flex items-center justify-between border-b p-5">
          <h2 className="text-sm font-semibold">
            진료 기록 #{encounterId} · 이전 버전
          </h2>
          <span className="text-[11px] text-muted-foreground">
            업무 버전 오름차순
          </span>
        </div>
        {list.isPending ? (
          <LoadingRows />
        ) : list.error ? (
          <div className="px-5">
            <ErrorBox error={list.error} retry={() => void list.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={list.data?.content || []}
            empty={
              <EmptyState
                title="보존된 이전 버전이 없습니다"
                description="최초 생성 후 실제 수정이 없는 기록에는 수정 이력이 생성되지 않습니다."
              />
            }
            columns={[
              {
                label: "버전",
                render: (r) => (
                  <button
                    className="text-link"
                    onClick={() => setHistoryId(r.id)}
                  >
                    v{r.version} 원본
                  </button>
                ),
              },
              { label: "정정 시각", render: (r) => dateTime(r.changedAt) },
              { label: "정정 사용자", render: (r) => `사용자 #${r.changedBy}` },
              {
                label: "정정 사유",
                render: (r) => (
                  <span className="block max-w-[400px] truncate">
                    {r.changeReason}
                  </span>
                ),
              },
              {
                label: "조회",
                render: (r) => (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setHistoryId(r.id)}
                  >
                    원본 보기
                  </Button>
                ),
              },
            ]}
          />
        )}
        <Pagination page={list.data} onPage={setPage} />
      </section>
      <Dialog
        open={historyId !== null}
        onOpenChange={(open) => {
          if (!open) setHistoryId(null);
        }}
      >
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-[700px]">
          <DialogHeader>
            <DialogTitle>
              수정 전 원본 {detail.data ? `· v${detail.data.version}` : ""}
            </DialogTitle>
            <DialogDescription>
              진료 기록 #{encounterId}에 속한 전체 원본입니다. 이력은
              수정·삭제할 수 없습니다.
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
              <div className="space-y-4">
                <div className="rounded-lg bg-accent p-4 text-xs leading-6">
                  <p className="font-medium text-primary">
                    {detail.data.changeReason}
                  </p>
                  <p className="mt-1 text-muted-foreground">
                    사용자 #{detail.data.changedBy} ·{" "}
                    {dateTime(detail.data.changedAt)}
                  </p>
                </div>
                <ClinicalText label="주호소">
                  {detail.data.chiefComplaint}
                </ClinicalText>
                <ClinicalText label="진료 소견">
                  {detail.data.clinicalNote}
                </ClinicalText>
                <ClinicalText label="치료 계획">
                  {detail.data.treatmentPlan}
                </ClinicalText>
              </div>
            )
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

export function HistoriesPage() {
  const [params, setParams] = useSearchParams();
  const encounterId = parsedId(params.get("encounterId"));
  const [error, setError] = useState<unknown>();
  return (
    <div className="page">
      <PageHeader
        eyebrow="CLINICAL RECORD / VERSION HISTORY"
        title="진료 기록 수정 이력"
        description="정정 전 전체 원본과 수정한 사용자, 시각, 사유를 확인합니다."
      />
      <form
        className="panel flex items-end gap-3 p-5"
        onSubmit={(event) => {
          const v = formValues(event);
          setError(undefined);
          try {
            setParams({ encounterId: String(positiveId(v.encounterId)) });
          } catch (e) {
            setError(e);
          }
        }}
      >
        <div className="w-[300px]">
          <Field label="진료 기록 ID">
            <input
              className="control"
              name="encounterId"
              type="number"
              min="1"
              required
              defaultValue={encounterId}
              placeholder="진료 기록 ID 입력"
            />
          </Field>
        </div>
        <Button variant="outline">
          <Search size={15} />
          이력 조회
        </Button>
      </form>
      {!!error && <ErrorBox error={error} />}
      {encounterId ? (
        <HistoryPanel key={encounterId} encounterId={encounterId} />
      ) : (
        <section className="panel">
          <EmptyState
            title="원본을 확인할 진료 기록을 선택해 주세요"
            description="담당 의사 여부와 관계없이 의사·간호사 권한으로 수정 이력을 조회할 수 있습니다."
          />
        </section>
      )}
    </div>
  );
}
