import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search } from "lucide-react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { localDate } from "@/lib/policy";
import type {
  PatientCreateRequest,
  PatientResponse,
  PatientUpdateRequest,
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
  ErrorBox,
  Field,
  FormModal,
  LoadingRows,
  PageHeader,
  Pagination,
  StatusBadge,
  formValues,
} from "@/components/common";

export function PatientsPage() {
  const client = useQueryClient();
  const [filters, setFilters] = useState({
    name: "",
    patientNo: "",
    phone: "",
    page: 0,
    size: 20,
  });
  const [mode, setMode] = useState<"create" | "update" | null>(null);
  const [selected, setSelected] = useState<PatientResponse | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [error, setError] = useState<unknown>();
  const list = useQuery({
    queryKey: ["patients", filters],
    queryFn: ({ signal }) => api.patients(filters, signal),
  });
  const detail = useQuery({
    queryKey: ["patient", detailId],
    queryFn: ({ signal }) => api.patient(detailId!, signal),
    enabled: detailId !== null,
  });
  const save = useMutation({
    mutationFn: async (v: Record<string, string>) => {
      if (mode === "create")
        return api.createPatient({
          name: v.name.trim(),
          phone: v.phone.trim(),
          address: v.address.trim(),
          gender: v.gender as "M" | "F",
          birthDate: v.birthDate,
        } satisfies PatientCreateRequest);
      if (!selected) throw new Error("수정할 환자를 선택해 주세요.");
      const body: PatientUpdateRequest = {};
      if (v.name.trim()) body.name = v.name.trim();
      if (v.phone.trim()) body.phone = v.phone.trim();
      if (v.address.trim()) body.address = v.address.trim();
      if (v.birthDate) body.birthDate = v.birthDate;
      if (v.gender) body.gender = v.gender as "M" | "F";
      return api.updatePatient(selected.id, body);
    },
    onSuccess: async () => {
      toast.success(
        mode === "create"
          ? "환자가 등록되었습니다."
          : "환자 정보가 수정되었습니다.",
      );
      setMode(null);
      await client.invalidateQueries({ queryKey: ["patients"] });
      await client.invalidateQueries({ queryKey: ["patient"] });
    },
  });
  const show = (
    value: "create" | "update",
    patient: PatientResponse | null = null,
  ) => {
    setSelected(patient);
    setError(undefined);
    save.reset();
    setMode(value);
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="PATIENT CARE / PATIENTS"
        title="환자 관리"
        description="환자를 검색하고 기본 정보와 방문 접수를 관리합니다."
        action={
          <Button onClick={() => show("create")}>
            <Plus size={16} />
            환자 등록
          </Button>
        }
      />
      <section className="panel">
        <form
          className="flex gap-3 border-b p-5"
          onSubmit={(event) => {
            const v = formValues(event);
            setFilters({
              ...filters,
              name: v.name.trim(),
              patientNo: v.patientNo.trim(),
              phone: v.phone.trim(),
              page: 0,
            });
          }}
        >
          <input
            name="name"
            className="control"
            placeholder="환자명"
            aria-label="환자명 검색"
          />
          <input
            name="patientNo"
            className="control"
            placeholder="환자 번호"
            aria-label="환자 번호 검색"
          />
          <input
            name="phone"
            className="control"
            placeholder="연락처"
            aria-label="연락처 검색"
          />
          <Button variant="outline">
            <Search size={14} />
            검색
          </Button>
        </form>
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
                label: "환자명",
                render: (r) => (
                  <button
                    className="text-link"
                    onClick={() => setDetailId(r.id)}
                  >
                    {r.name}
                  </button>
                ),
              },
              {
                label: "환자 번호",
                render: (r) => (
                  <span className="font-mono text-xs">{r.patientNo}</span>
                ),
              },
              {
                label: "생년월일 · 성별",
                render: (r) =>
                  `${r.birthDate} · ${r.gender === "F" ? "여" : "남"}`,
              },
              { label: "연락처", render: (r) => r.phone },
              {
                label: "상태",
                render: (r) => (
                  <StatusBadge status={r.active ? "active" : "inactive"} />
                ),
              },
              {
                label: "관리",
                render: (r) => (
                  <div className="flex gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => show("update", r)}
                    >
                      정보 수정
                    </Button>
                    <Button variant="outline" size="sm" asChild>
                      <Link to={`/visits?register=1&patientId=${r.id}`}>
                        방문 접수
                      </Link>
                    </Button>
                  </div>
                ),
              },
            ]}
          />
        )}
        <Pagination
          page={list.data}
          onPage={(page) => setFilters({ ...filters, page })}
        />
      </section>
      <FormModal
        key={`${mode}-${selected?.id}`}
        title={mode === "create" ? "신규 환자 등록" : "환자 정보 수정"}
        description={
          mode === "create"
            ? "환자의 정확한 기본 정보를 입력해 주세요."
            : "수정하지 않은 항목은 유지됩니다."
        }
        open={mode !== null}
        onOpenChange={(open) => {
          if (!open) setMode(null);
        }}
        error={error}
        pending={save.isPending}
        submitLabel={mode === "create" ? "환자 등록" : "정보 수정"}
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
        <div className="grid grid-cols-2 gap-4">
          <Field label="환자명">
            <input
              className="control"
              name="name"
              maxLength={100}
              required={mode === "create"}
              defaultValue={selected?.name}
            />
          </Field>
          <Field label="생년월일">
            <input
              className="control"
              type="date"
              name="birthDate"
              max={localDate()}
              required={mode === "create"}
              defaultValue={selected?.birthDate}
            />
          </Field>
          <Field label="성별">
            <select
              className="control"
              name="gender"
              required={mode === "create"}
              defaultValue={selected?.gender || ""}
            >
              <option value="">
                {mode === "update" ? "변경하지 않음" : "선택해 주세요"}
              </option>
              <option value="M">남성</option>
              <option value="F">여성</option>
            </select>
          </Field>
          <Field label="연락처">
            <input
              className="control"
              type="tel"
              name="phone"
              maxLength={20}
              required={mode === "create"}
              defaultValue={selected?.phone}
            />
          </Field>
        </div>
        <Field label="주소">
          <input
            className="control"
            name="address"
            maxLength={255}
            required={mode === "create"}
            defaultValue={selected?.address}
          />
        </Field>
      </FormModal>
      <Dialog
        open={detailId !== null}
        onOpenChange={(open) => {
          if (!open) setDetailId(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>환자 상세</DialogTitle>
            <DialogDescription>환자 기본 정보를 확인합니다.</DialogDescription>
          </DialogHeader>
          {detail.isPending ? (
            <LoadingRows columns={2} />
          ) : detail.error ? (
            <ErrorBox error={detail.error} />
          ) : (
            detail.data && (
              <div className="space-y-5">
                <h2 className="text-lg font-semibold">
                  {detail.data.name}{" "}
                  <span className="ml-2 text-xs text-muted-foreground">
                    {detail.data.patientNo}
                  </span>
                </h2>
                <dl className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt className="text-xs text-muted-foreground">생년월일</dt>
                    <dd className="mt-1">{detail.data.birthDate}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">연락처</dt>
                    <dd className="mt-1">{detail.data.phone}</dd>
                  </div>
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">주소</dt>
                    <dd className="mt-1">{detail.data.address}</dd>
                  </div>
                </dl>
                <Button asChild>
                  <Link to={`/visits?register=1&patientId=${detail.data.id}`}>
                    방문 접수
                  </Link>
                </Button>
              </div>
            )
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
