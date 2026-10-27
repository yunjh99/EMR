import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Stethoscope } from "lucide-react";
import { toast } from "sonner";
import { useSession } from "@/auth/session";
import { api } from "@/lib/api";
import { positiveId, roleNames, roles } from "@/lib/policy";
import type {
  DepartmentResponse,
  DoctorResponse,
  DoctorCreateRequest,
  Role,
  UserCreateRequest,
  UserListResponse,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
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

export function UsersPage() {
  const { user, disconnect } = useSession();
  const client = useQueryClient();
  const [filters, setFilters] = useState({
    name: "",
    username: "",
    role: "" as Role | "",
    active: "",
    page: 0,
    size: 20,
  });
  const [selected, setSelected] = useState<UserListResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<unknown>();
  const list = useQuery({
    queryKey: ["users", filters],
    queryFn: ({ signal }) => api.users(filters, signal),
  });
  const detail = useQuery({
    queryKey: ["user", selected?.id],
    queryFn: ({ signal }) => api.user(selected!.id, signal),
    enabled: open && !!selected,
  });
  const save = useMutation({
    mutationFn: async (values: Record<string, string>) => {
      if (selected)
        return api.updateUser(selected.id, {
          name: values.name.trim(),
          role: values.role as Role,
          active: values.active === "true",
        });
      if (new TextEncoder().encode(values.password).length > 72)
        throw new Error("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
      return api.createUser({
        username: values.username.trim(),
        password: values.password,
        name: values.name.trim(),
        role: values.role as Role,
      } satisfies UserCreateRequest);
    },
    onSuccess: async () => {
      toast.success(
        selected ? "직원 정보가 수정되었습니다." : "직원이 등록되었습니다.",
      );
      setOpen(false);
      await client.invalidateQueries({ queryKey: ["users"] });
      await client.invalidateQueries({ queryKey: ["user"] });
      await client.invalidateQueries({ queryKey: ["doctors"] });
      if (selected?.id === user?.id) {
        disconnect();
        toast.info("본인 계정 정보가 변경되어 인증을 다시 확인해 주세요.");
      }
    },
  });
  const show = (row: UserListResponse | null) => {
    setSelected(row);
    setError(undefined);
    save.reset();
    setOpen(true);
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="HOSPITAL MANAGEMENT / STAFF"
        title="직원 관리"
        description="병원 직원의 계정과 업무 권한, 활성 상태를 관리합니다."
        action={
          <Button onClick={() => show(null)}>
            <Plus size={16} />
            직원 등록
          </Button>
        }
      />
      <section className="panel">
        <form
          className="grid grid-cols-[1fr_1fr_160px_150px_auto] gap-3 border-b p-5"
          onSubmit={(event) => {
            const v = formValues(event);
            setFilters({
              ...filters,
              name: v.name.trim(),
              username: v.username.trim(),
              role: v.role as Role | "",
              active: v.active,
              page: 0,
            });
          }}
        >
          <input
            aria-label="직원명 검색"
            className="control"
            placeholder="직원명 검색"
            name="name"
          />
          <input
            aria-label="로그인 ID 검색"
            className="control"
            placeholder="로그인 ID 검색"
            name="username"
          />
          <select className="control" name="role" aria-label="역할 필터">
            <option value="">전체 역할</option>
            {roles.map((role) => (
              <option key={role} value={role}>
                {roleNames[role]}
              </option>
            ))}
          </select>
          <select className="control" name="active" aria-label="활성 상태 필터">
            <option value="">전체 상태</option>
            <option value="true">활성</option>
            <option value="false">비활성</option>
          </select>
          <Button variant="outline">
            <Search size={15} />
            검색
          </Button>
        </form>
        <div className="flex items-center justify-between px-5 py-4">
          <h2 className="text-sm font-semibold">
            직원 목록{" "}
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              {list.data ? `${list.data.totalElements}명` : ""}
            </span>
          </h2>
          <span className="text-[11px] text-muted-foreground">ADMIN 전용</span>
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
            columns={[
              {
                label: "직원명",
                render: (r) => (
                  <button className="text-link" onClick={() => show(r)}>
                    {r.name}
                  </button>
                ),
              },
              { label: "로그인 ID", render: (r) => r.username },
              { label: "업무 역할", render: (r) => roleNames[r.role] },
              {
                label: "활성 상태",
                render: (r) => (
                  <StatusBadge status={r.active ? "active" : "inactive"} />
                ),
              },
              {
                label: "관리",
                render: (r) => (
                  <Button size="sm" variant="ghost" onClick={() => show(r)}>
                    상세 · 수정
                  </Button>
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
        open={open}
        onOpenChange={setOpen}
        title={selected ? "직원 상세 · 수정" : "직원 등록"}
        description="계정의 권한 변경은 다음 서버 요청부터 적용됩니다."
        error={error || detail.error}
        pending={save.isPending}
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
        <div key={selected?.id || "new"} className="space-y-5">
          {selected ? (
            <div className="rounded-lg bg-muted p-3 text-xs text-muted-foreground">
              {detail.isPending
                ? "계정 상세 확인 중…"
                : `사용자 #${selected.id} · ${detail.data?.username || selected.username}`}
            </div>
          ) : (
            <>
              <Field label="로그인 ID">
                <input
                  className="control"
                  name="username"
                  required
                  maxLength={20}
                  pattern="\S+"
                  autoComplete="off"
                />
              </Field>
              <Field label="초기 비밀번호" help="UTF-8 기준 최대 72바이트">
                <input
                  className="control"
                  name="password"
                  required
                  type="password"
                  autoComplete="new-password"
                />
              </Field>
            </>
          )}
          <Field label="직원명">
            <input
              className="control"
              name="name"
              required
              maxLength={100}
              defaultValue={selected?.name}
            />
          </Field>
          <Field label="업무 역할">
            <select
              className="control"
              name="role"
              defaultValue={selected?.role || "STAFF"}
            >
              {roles.map((role) => (
                <option key={role} value={role}>
                  {roleNames[role]}
                </option>
              ))}
            </select>
          </Field>
          {selected && (
            <Field label="활성 상태">
              <select
                className="control"
                name="active"
                defaultValue={String(selected.active)}
              >
                <option value="true">활성</option>
                <option value="false">비활성</option>
              </select>
            </Field>
          )}
        </div>
      </FormModal>
    </div>
  );
}

export function DepartmentsPage() {
  const { user } = useSession();
  const admin = user?.role === "ADMIN";
  const client = useQueryClient();
  const [inactive, setInactive] = useState(false);
  const [selected, setSelected] = useState<DepartmentResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<unknown>();
  const list = useQuery({
    queryKey: ["departments", inactive],
    queryFn: ({ signal }) => api.departments(admin && inactive, signal),
  });
  const detail = useQuery({
    queryKey: ["department", selected?.id, admin],
    queryFn: ({ signal }) => api.department(selected!.id, admin, signal),
    enabled: open && !!selected,
  });
  const invalidate = async () => {
    await client.invalidateQueries({ queryKey: ["departments"] });
    await client.invalidateQueries({ queryKey: ["department"] });
    await client.invalidateQueries({ queryKey: ["doctors"] });
  };
  const save = useMutation({
    mutationFn: (v: Record<string, string>) =>
      selected
        ? api.updateDepartment(selected.id, {
            code: v.code.trim(),
            name: v.name.trim(),
          })
        : api.createDepartment({ code: v.code.trim(), name: v.name.trim() }),
    onSuccess: async () => {
      toast.success("진료과 정보가 저장되었습니다.");
      setOpen(false);
      await invalidate();
    },
  });
  const show = (row: DepartmentResponse | null) => {
    setSelected(row);
    setError(undefined);
    setOpen(true);
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="HOSPITAL MANAGEMENT / DEPARTMENTS"
        title="진료과 관리"
        description="운영 중인 진료과를 확인하고 관리합니다."
        action={
          admin && (
            <Button onClick={() => show(null)}>
              <Plus size={16} />
              진료과 등록
            </Button>
          )
        }
      />
      <section className="panel">
        <div className="flex items-center justify-between p-5">
          <h2 className="text-sm font-semibold">진료과 목록</h2>
          {admin && (
            <label className="flex items-center gap-2 text-xs text-muted-foreground">
              <input
                type="checkbox"
                checked={inactive}
                onChange={(e) => setInactive(e.target.checked)}
              />
              비활성 포함
            </label>
          )}
        </div>
        {list.isPending ? (
          <LoadingRows columns={4} />
        ) : list.error ? (
          <div className="px-5">
            <ErrorBox error={list.error} retry={() => void list.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={list.data || []}
            columns={[
              {
                label: "진료과명",
                render: (r) => (
                  <button className="text-link" onClick={() => show(r)}>
                    {r.name}
                  </button>
                ),
              },
              {
                label: "진료과 코드",
                render: (r) => (
                  <span className="font-mono text-xs text-muted-foreground">
                    {r.code}
                  </span>
                ),
              },
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
                    <Button size="sm" variant="ghost" onClick={() => show(r)}>
                      {admin ? "상세 · 수정" : "상세 보기"}
                    </Button>
                    {admin && r.active && (
                      <ConfirmAction
                        label="비활성화"
                        title="진료과를 비활성화할까요?"
                        description={`${r.name} 진료과의 기록은 보존됩니다.`}
                        onConfirm={async () => {
                          await api.deactivateDepartment(r.id);
                          await invalidate();
                        }}
                      />
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
      </section>
      <FormModal
        title={selected ? "진료과 상세" : "진료과 등록"}
        description="진료과 코드는 공백 없이 최대 20자입니다."
        open={open}
        onOpenChange={setOpen}
        pending={save.isPending}
        error={error || detail.error}
        submitLabel={admin ? "저장" : "확인"}
        onSubmit={async (event) => {
          const v = formValues(event);
          if (!admin) {
            setOpen(false);
            return;
          }
          setError(undefined);
          try {
            await save.mutateAsync(v);
          } catch (e) {
            setError(e);
          }
        }}
      >
        <div key={selected?.id || "new"} className="space-y-5">
          <Field label="진료과 코드">
            <input
              className="control"
              name="code"
              required
              maxLength={20}
              pattern="\S+"
              readOnly={!admin}
              defaultValue={selected?.code}
            />
          </Field>
          <Field label="진료과명">
            <input
              className="control"
              name="name"
              required
              maxLength={100}
              readOnly={!admin}
              defaultValue={selected?.name}
            />
          </Field>
        </div>
      </FormModal>
    </div>
  );
}

export function DoctorsPage() {
  const { user } = useSession();
  const admin = user?.role === "ADMIN";
  const client = useQueryClient();
  const [departmentId, setDepartmentId] = useState("");
  const [inactive, setInactive] = useState(false);
  const [selected, setSelected] = useState<DoctorResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState("existing");
  const [error, setError] = useState<unknown>();
  const list = useQuery({
    queryKey: ["doctors", departmentId, inactive],
    queryFn: ({ signal }) =>
      api.doctors({ departmentId, includeInactive: admin && inactive }, signal),
  });
  const departments = useQuery({
    queryKey: ["departments", false],
    queryFn: ({ signal }) => api.departments(false, signal),
  });
  const candidates = useQuery({
    queryKey: ["users", "doctorCandidates"],
    queryFn: ({ signal }) =>
      api.users({ role: "DOCTOR", active: "true", page: 0, size: 200 }, signal),
    enabled: admin && open && !selected && mode === "existing",
  });
  const detail = useQuery({
    queryKey: ["doctor", selected?.id, admin],
    queryFn: ({ signal }) => api.doctor(selected!.id, admin, signal),
    enabled: open && !!selected,
  });
  const invalidate = async () => {
    await client.invalidateQueries({ queryKey: ["doctors"] });
    await client.invalidateQueries({ queryKey: ["doctor"] });
    await client.invalidateQueries({ queryKey: ["users"] });
  };
  const save = useMutation({
    mutationFn: (v: Record<string, string>) => {
      const common = {
        departmentId: positiveId(v.departmentId),
        licenseNo: v.licenseNo.trim(),
        specialty: v.specialty.trim() || null,
      };
      if (selected) return api.updateDoctor(selected.id, common);
      const request: DoctorCreateRequest = { ...common };
      if (mode === "existing") request.userId = positiveId(v.userId);
      else {
        if (new TextEncoder().encode(v.password).length > 72)
          throw new Error("비밀번호는 UTF-8 기준 최대 72바이트입니다.");
        request.account = {
          username: v.username.trim(),
          password: v.password,
          name: v.name.trim(),
        };
      }
      return api.createDoctor(request);
    },
    onSuccess: async () => {
      toast.success("의료진 정보가 저장되었습니다.");
      setOpen(false);
      await invalidate();
    },
  });
  const show = (row: DoctorResponse | null) => {
    setSelected(row);
    setMode("existing");
    setError(undefined);
    setOpen(true);
  };
  return (
    <div className="page">
      <PageHeader
        eyebrow="HOSPITAL MANAGEMENT / DOCTORS"
        title="의료진 관리"
        description="담당 진료과와 의료진 정보를 확인합니다."
        action={
          admin && (
            <Button onClick={() => show(null)}>
              <Plus size={16} />
              의사 등록
            </Button>
          )
        }
      />
      <section className="panel">
        <div className="flex items-center justify-between p-5">
          <select
            className="control max-w-[210px]"
            aria-label="진료과 필터"
            value={departmentId}
            onChange={(e) => setDepartmentId(e.target.value)}
          >
            <option value="">전체 진료과</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
          {admin && (
            <label className="flex items-center gap-2 text-xs text-muted-foreground">
              <input
                type="checkbox"
                checked={inactive}
                onChange={(e) => setInactive(e.target.checked)}
              />
              비활성 포함
            </label>
          )}
        </div>
        {departments.error && (
          <div className="px-5">
            <ErrorBox
              error={departments.error}
              retry={() => void departments.refetch()}
            />
          </div>
        )}
        {list.isPending ? (
          <LoadingRows />
        ) : list.error ? (
          <div className="px-5">
            <ErrorBox error={list.error} retry={() => void list.refetch()} />
          </div>
        ) : (
          <DataTable
            rows={list.data || []}
            columns={[
              {
                label: "의료진",
                render: (r) => (
                  <button
                    onClick={() => show(r)}
                    className="text-link flex items-center gap-2"
                  >
                    <Stethoscope size={15} className="text-[#8aac9f]" />
                    {r.name}
                  </button>
                ),
              },
              { label: "진료과", render: (r) => r.departmentName },
              { label: "전문 분야", render: (r) => r.specialty || "—" },
              {
                label: "활성 상태",
                render: (r) => (
                  <StatusBadge status={r.active ? "active" : "inactive"} />
                ),
              },
              {
                label: "관리",
                render: (r) => (
                  <div className="flex gap-2">
                    <Button size="sm" variant="ghost" onClick={() => show(r)}>
                      {admin ? "상세 · 수정" : "상세 보기"}
                    </Button>
                    {admin && r.active && (
                      <ConfirmAction
                        label="비활성화"
                        title="의료진을 비활성화할까요?"
                        description={`${r.name} 의사의 기존 진료 기록은 보존됩니다.`}
                        onConfirm={async () => {
                          await api.deactivateDoctor(r.id);
                          await invalidate();
                        }}
                      />
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
      </section>
      <FormModal
        title={selected ? "의료진 상세" : "의사 등록"}
        description="의사 계정과 진료과를 연결합니다. 권한은 서버에서 확인합니다."
        open={open}
        onOpenChange={setOpen}
        pending={save.isPending}
        error={error || detail.error}
        submitLabel={admin ? "저장" : "확인"}
        onSubmit={async (event) => {
          const v = formValues(event);
          if (!admin) {
            setOpen(false);
            return;
          }
          setError(undefined);
          try {
            await save.mutateAsync(v);
          } catch (e) {
            setError(e);
          }
        }}
      >
        <div key={selected?.id || mode} className="space-y-5">
          {!selected ? (
            <>
              <Field label="계정 연결 방식">
                <select
                  className="control"
                  value={mode}
                  onChange={(e) => setMode(e.target.value)}
                >
                  <option value="existing">기존 의사 계정 연결</option>
                  <option value="new">새 의사 계정 함께 생성</option>
                </select>
              </Field>
              {mode === "existing" ? (
                <>
                  <Field
                    label="의사 계정"
                    help="활성 DOCTOR 계정 중 첫 200건을 표시합니다. 목록에 없으면 신규 계정 생성 방식을 이용하거나 직원 관리에서 확인해 주세요."
                  >
                    <select className="control" name="userId" required>
                      <option value="">계정 선택</option>
                      {candidates.data?.content.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name} · {u.username}
                        </option>
                      ))}
                    </select>
                  </Field>
                  {candidates.error && (
                    <ErrorBox
                      error={candidates.error}
                      retry={() => void candidates.refetch()}
                    />
                  )}
                </>
              ) : (
                <>
                  <Field label="의사명">
                    <input
                      className="control"
                      name="name"
                      required
                      maxLength={100}
                    />
                  </Field>
                  <Field label="로그인 ID">
                    <input
                      className="control"
                      name="username"
                      required
                      maxLength={20}
                      pattern="\S+"
                    />
                  </Field>
                  <Field label="초기 비밀번호">
                    <input
                      className="control"
                      name="password"
                      type="password"
                      required
                      autoComplete="new-password"
                    />
                  </Field>
                </>
              )}
            </>
          ) : (
            <div className="rounded-lg bg-muted p-3 text-xs">
              {selected.name} · 사용자 #{selected.userId}
            </div>
          )}
          <Field label="소속 진료과">
            <select
              className="control"
              name="departmentId"
              required
              disabled={!admin}
              defaultValue={selected?.departmentId || ""}
            >
              <option value="">진료과 선택</option>
              {selected &&
                !departments.data?.some(
                  (d) => d.id === selected.departmentId,
                ) && (
                  <option value={selected.departmentId}>
                    {selected.departmentName} (현재 소속)
                  </option>
                )}
              {departments.data?.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="의사 면허번호">
            <input
              className="control"
              name="licenseNo"
              required
              maxLength={30}
              pattern="\S+"
              readOnly={!admin}
              defaultValue={selected?.licenseNo}
            />
          </Field>
          <Field label="전문 분야">
            <input
              className="control"
              name="specialty"
              maxLength={100}
              readOnly={!admin}
              defaultValue={selected?.specialty || ""}
            />
          </Field>
        </div>
      </FormModal>
    </div>
  );
}
