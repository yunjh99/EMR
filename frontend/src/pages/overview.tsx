import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  ClipboardList,
  FileCheck2,
  HeartPulse,
  Plus,
  Stethoscope,
  UsersRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useSession } from "@/auth/session";
import { api } from "@/lib/api";
import { dateTime, localDate, roleNames } from "@/lib/policy";
import { Button } from "@/components/ui/button";
import {
  DataTable,
  EmptyState,
  ErrorBox,
  LoadingRows,
  PageHeader,
  StatusBadge,
} from "@/components/common";

export function OverviewPage() {
  const { user } = useSession();
  const clinical = !!user && user.role !== "ADMIN";
  const waiting = useQuery({
    queryKey: ["visits", "overview", localDate()],
    queryFn: ({ signal }) =>
      api.visits(
        { date: localDate(), status: "WAITING", page: 0, size: 1 },
        signal,
      ),
    enabled: clinical,
  });
  const doctors = useQuery({
    queryKey: ["doctors", "active"],
    queryFn: ({ signal }) => api.doctors({}, signal),
    enabled: !!user,
  });
  const departments = useQuery({
    queryKey: ["departments", false],
    queryFn: ({ signal }) => api.departments(false, signal),
    enabled: !!user,
  });
  const bookings = useQuery({
    queryKey: ["appointments", "overview", localDate()],
    queryFn: ({ signal }) =>
      api.appointments(
        { date: localDate(), status: "SCHEDULED", page: 0, size: 5 },
        signal,
      ),
    enabled: clinical,
  });
  const stats = [
    {
      label: "오늘 예정 예약",
      value: clinical && bookings.data ? bookings.data.totalElements : "—",
      suffix: "건",
      description: clinical ? "예약 예정 상태 기준" : "원무과 · 의료진 조회",
      Icon: CalendarDays,
      color: "bg-[#eaf2fb] text-[#678abb]",
    },
    {
      label: "접수 · 진료 대기",
      value: waiting.data?.totalElements ?? "—",
      suffix: "명",
      description: "오늘 진료 대기 상태 기준",
      Icon: UsersRound,
      color: "bg-[#edf6f2] text-[#699b86]",
    },
    {
      label: "등록 의료진",
      value: doctors.data?.length ?? "—",
      suffix: "명",
      description: "활성 의료진 기준",
      Icon: Stethoscope,
      color: "bg-[#f2eff9] text-[#9a85b6]",
    },
    {
      label: "운영 진료과",
      value: departments.data?.length ?? "—",
      suffix: "개",
      description: "활성 진료과 기준",
      Icon: HeartPulse,
      color: "bg-[#fbf3e8] text-[#bc995e]",
    },
  ];
  return (
    <div className="page">
      <PageHeader
        eyebrow="YOUR DAILY WORKSPACE"
        title={user ? `${user.name}님, 안녕하세요` : "오늘의 업무를 시작하세요"}
        description={
          user
            ? `${roleNames[user.role]} 업무에 필요한 정보를 한곳에서 확인하세요.`
            : "환자와 의료진을 연결하는, 온유 의료원의 업무 공간입니다."
        }
        action={
          <Button variant="outline" asChild>
            <Link to={user?.role === "STAFF" ? "/appointments" : "/connect"}>
              <CalendarDays size={15} />
              {user?.role === "STAFF" ? "예약 일정 보기" : "연결 상태 확인"}
            </Link>
          </Button>
        }
      />
      {!user && (
        <div className="flex items-center justify-between rounded-xl border border-[#d8e7e1] bg-[#edf5f1] px-5 py-4">
          <div className="flex gap-3">
            <span className="mt-0.5 flex size-8 items-center justify-center rounded-lg bg-white text-primary">
              <FileCheck2 size={18} />
            </span>
            <div>
              <p className="text-[13px] font-semibold text-[#356a5b]">
                안전한 업무 시작을 위해 인증 연결이 필요합니다
              </p>
              <p className="mt-1 text-xs text-[#6c8e83]">
                연결 전에는 실제 환자 정보와 업무 데이터가 표시되지 않습니다.
              </p>
            </div>
          </div>
          <Link
            to="/connect"
            className="flex items-center gap-2 whitespace-nowrap text-xs font-semibold text-primary"
          >
            연결 안내
            <ArrowRight size={14} />
          </Link>
        </div>
      )}
      <div className="grid grid-cols-4 gap-4">
        {stats.map(({ label, value, suffix, description, Icon, color }) => (
          <div key={label} className="panel px-5 py-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">
                {label}
              </span>
              <span
                className={`flex size-9 items-center justify-center rounded-xl ${color}`}
              >
                <Icon size={18} strokeWidth={1.7} />
              </span>
            </div>
            <div className="mt-4 flex items-baseline gap-2">
              <strong className="text-[31px] font-semibold tracking-tight">
                {value}
              </strong>
              <span className="text-xs text-muted-foreground">{suffix}</span>
            </div>
            <p className="mt-2 text-[11px] text-muted-foreground">
              {description}
            </p>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-[1fr_310px] gap-5">
        <section className="panel overflow-hidden">
          <div className="flex items-center justify-between px-5 py-5">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold">오늘의 예약</h2>
              <span className="rounded bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
                {localDate().replaceAll("-", ".")}
              </span>
            </div>
            {clinical && (
              <Link
                className="flex items-center gap-1 text-xs text-muted-foreground hover:text-primary"
                to="/appointments"
              >
                전체 보기
                <ArrowUpRight size={13} />
              </Link>
            )}
          </div>
          {clinical ? (
            bookings.isPending ? (
              <LoadingRows columns={4} />
            ) : bookings.error ? (
              <div className="px-5">
                <ErrorBox
                  error={bookings.error}
                  retry={() => void bookings.refetch()}
                />
              </div>
            ) : (
              <DataTable
                rows={bookings.data?.content || []}
                columns={[
                  {
                    label: "예약 시간",
                    render: (r) => dateTime(r.scheduledAt).slice(11),
                  },
                  {
                    label: "환자명",
                    render: (r) => (
                      <span className="font-medium">{r.patientName}</span>
                    ),
                  },
                  { label: "담당 의사", render: (r) => r.doctorName },
                  {
                    label: "상태",
                    render: (r) => <StatusBadge status={r.status} />,
                  },
                ]}
              />
            )
          ) : (
            <>
              <div className="grid grid-cols-4 border-y bg-[#f8fafb] px-5 py-3 text-[11px] text-muted-foreground">
                <span>예약 시간</span>
                <span>환자명</span>
                <span>담당 의사</span>
                <span>상태</span>
              </div>
              <EmptyState
                title={
                  user
                    ? "관리자 계정은 예약 조회 권한이 없습니다"
                    : "연결 후 예약 정보를 확인할 수 있습니다"
                }
                description={
                  user
                    ? "관리자는 직원·진료과·의료진 관리 메뉴를 이용해 주세요."
                    : "로그인 후 예약과 진료 정보를 확인할 수 있습니다."
                }
                icon
              />
            </>
          )}
        </section>
        <section className="panel flex flex-col p-5">
          <p className="section-label">QUICK ACCESS</p>
          <h2 className="mt-2 text-sm font-semibold">업무 바로가기</h2>
          <div className="mt-5 space-y-2">
            {(user?.role === "ADMIN"
              ? [
                  {
                    to: "/users",
                    label: "직원 관리",
                    sub: "계정 · 권한 · 활성 상태",
                    Icon: UsersRound,
                  },
                  {
                    to: "/doctors",
                    label: "의료진 관리",
                    sub: "의사 정보와 진료과 관리",
                    Icon: Stethoscope,
                  },
                ]
              : user?.role === "STAFF"
                ? [
                    {
                      to: "/patients",
                      label: "환자 등록",
                      sub: "새로운 환자 정보 입력",
                      Icon: Plus,
                    },
                    {
                      to: "/appointments",
                      label: "예약 관리",
                      sub: "일정 조회 및 예약 등록",
                      Icon: CalendarDays,
                    },
                  ]
                : user?.role === "DOCTOR"
                  ? [
                      {
                        to: "/encounters",
                        label: "환자 진료 기록",
                        sub: "이전 기록 조회 및 정정",
                        Icon: FileCheck2,
                      },
                      {
                        to: "/visits",
                        label: "접수 · 진료 대기",
                        sub: "본인 담당 환자 진료 시작",
                        Icon: ClipboardList,
                      },
                    ]
                  : [
                      {
                        to: user ? "/histories" : "/connect",
                        label: user ? "진료 기록 수정 이력" : "업무 공간 연결",
                        sub: user ? "정정 전 원본 확인" : "직원 계정 로그인",
                        Icon: FileCheck2,
                      },
                      {
                        to: "/capabilities",
                        label: "서비스 연결 현황",
                        sub: "사용 가능한 기능 확인",
                        Icon: ClipboardList,
                      },
                    ]
            ).map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className="group flex items-center gap-3 rounded-lg border border-transparent p-3 transition hover:border-border hover:bg-muted/60"
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-[#f1f6f4] text-[#6b9285]">
                  <item.Icon size={17} />
                </span>
                <span className="flex-1">
                  <span className="block text-xs font-medium">
                    {item.label}
                  </span>
                  <span className="mt-1 block text-[10px] text-muted-foreground">
                    {item.sub}
                  </span>
                </span>
                <ArrowUpRight
                  size={14}
                  className="text-slate-300 group-hover:text-primary"
                />
              </Link>
            ))}
          </div>
          <div className="mt-auto rounded-lg bg-[#f7f9fa] p-3.5 text-[11px] leading-5 text-muted-foreground">
            접수 취소는 대기 중에만 가능합니다.
            <br />
            진료 기록 저장 시 진료가 자동 완료됩니다.
          </div>
        </section>
      </div>
      <section className="panel px-6 py-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="section-label">PATIENT JOURNEY</p>
            <h2 className="mt-1.5 text-sm font-semibold">
              방문에서 진료 기록까지
            </h2>
          </div>
          <Link to="/capabilities" className="text-xs text-primary">
            업무 흐름 확인 →
          </Link>
        </div>
        <div className="mt-6 flex items-center justify-between">
          {[
            "환자 확인",
            "예약 · 방문 접수",
            "진료 시작",
            "기록 작성 · 자동 완료",
            "기록 정정 · 이력 보존",
          ].map((step, i) => (
            <div key={step} className="flex items-center gap-3">
              <span className="flex size-7 items-center justify-center rounded-full border bg-[#f8faf9] text-[11px] text-[#7b9a90]">
                0{i + 1}
              </span>
              <span className="text-[11px] text-muted-foreground">{step}</span>
              {i < 4 && <span className="ml-3 text-slate-300">→</span>}
            </div>
          ))}
        </div>
        <p className="mt-5 text-[10px] text-muted-foreground">
          환자 확인부터 접수와 진료 기록까지 실제 업무 흐름으로 연결됩니다.
        </p>
      </section>
    </div>
  );
}
