import {
  ArrowUpRight,
  CheckCircle2,
  CircleDashed,
  ShieldCheck,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useSession } from "@/auth/session";
import { PageHeader } from "@/components/common";

export const capabilities = [
  {
    name: "직원 · 진료과 · 의료진 관리",
    ready: true,
    note: "조회, 등록, 수정과 비활성화 · 관리자 권한",
    path: "/users",
  },
  {
    name: "환자 기본 정보 등록 · 수정",
    ready: true,
    note: "환자 검색·상세와 등록·수정·접수 연결",
    path: "/patients",
  },
  {
    name: "예약 현황 · 등록 · 취소",
    ready: true,
    note: "날짜 · 의사 · 상태 필터, 30분 단위 예약",
    path: "/appointments",
  },
  {
    name: "진료 기록 · 환자별 기록 조회",
    ready: true,
    note: "의사 권한, 담당 의사와 관계없이 조회",
    path: "/encounters",
  },
  {
    name: "진료 기록 정정 · 수정 이력",
    ready: true,
    note: "담당 의사만 정정 · 의사와 간호사가 이력 조회",
    path: "/histories",
  },
  {
    name: "일반 로그인 · 토큰 재발급",
    ready: true,
    note: "실제 계정 인증, HttpOnly 쿠키 재발급, 로그아웃",
    path: "/connect",
  },
  {
    name: "환자 검색 · 상세 조회",
    ready: true,
    note: "환자명·번호·연락처 검색과 상세 정보",
    path: "/patients",
  },
  {
    name: "방문 접수 등록 · 대기 현황",
    ready: true,
    note: "예약 및 예약 없는 접수, 당일 목록·상세",
    path: "/visits",
  },
  {
    name: "진료 시작 · 접수 취소",
    ready: true,
    note: "상태와 담당자에 따른 실제 변경, 서버 최종 검증",
    path: "/visits",
  },
  {
    name: "진료 기록 최초 작성",
    ready: true,
    note: "진료 중 담당 의사 작성, 저장 시 자동 완료",
    path: "/encounters",
  },
  {
    name: "예약 가능 슬롯 조회",
    ready: false,
    note: "예약 내역은 확인할 수 있지만 슬롯 존재 여부는 조회할 수 없습니다.",
    path: "/appointments",
  },
  {
    name: "감사 로그 · 환자 변경 이력 조회",
    ready: false,
    note: "조회 API가 연결되어 있지 않습니다.",
    path: "",
  },
];
export function CapabilitiesPage() {
  const { user } = useSession();
  return (
    <div className="page">
      <PageHeader
        eyebrow="WORKSPACE / CONNECTION STATUS"
        title="서비스 연결 현황"
        description="실제 서버에 연결되는 기능과 준비 중인 기능을 구분해 안내합니다."
      />
      <div className="flex items-center gap-3 rounded-xl bg-[#edf5f1] p-5">
        <ShieldCheck size={22} className="shrink-0 text-primary" />
        <p className="text-xs leading-6 text-[#577d70]">
          모든 변경 작업은 서버의 인증·인가와 상태 검증을 따릅니다. 준비 중인
          기능은 임의 데이터나 가짜 성공 결과를 표시하지 않습니다.
        </p>
      </div>
      <section className="panel overflow-hidden">
        <div className="grid grid-cols-[1fr_1.4fr_120px] border-b bg-muted/50 px-6 py-3 text-xs text-muted-foreground">
          <span>업무 기능</span>
          <span>제공 범위</span>
          <span>연결 상태</span>
        </div>
        {capabilities.map((item) => (
          <div
            key={item.name}
            className="grid grid-cols-[1fr_1.4fr_120px] items-center gap-3 border-b px-6 py-5 last:border-b-0"
          >
            <div className="flex items-center gap-3">
              {item.ready ? (
                <CheckCircle2 size={17} className="shrink-0 text-primary" />
              ) : (
                <CircleDashed size={17} className="shrink-0 text-slate-300" />
              )}
              <span className="text-[13px] font-medium">{item.name}</span>
            </div>
            <p className="text-xs leading-5 text-muted-foreground">
              {item.note}
            </p>
            {item.ready ? (
              <span className="text-xs font-medium text-primary">
                {user ? "역할에 따라 사용" : "인증 연결 필요"}
              </span>
            ) : (
              <span className="text-xs text-muted-foreground">준비 중</span>
            )}
          </div>
        ))}
      </section>
      <Link
        to="/connect"
        className="flex items-center gap-1 text-xs text-primary"
      >
        인증 연결 안내
        <ArrowUpRight size={13} />
      </Link>
    </div>
  );
}
