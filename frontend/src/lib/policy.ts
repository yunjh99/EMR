import type { Role, VisitStatus } from "./types";

export const roleNames: Record<Role, string> = {
  ADMIN: "관리자",
  STAFF: "원무과",
  DOCTOR: "의사",
  NURSE: "간호사",
};
export const roles: Role[] = ["ADMIN", "STAFF", "DOCTOR", "NURSE"];
export function mayEditEncounter(
  role: Role,
  currentDoctorId: number | undefined,
  assignedDoctorId: number,
) {
  return (
    role === "DOCTOR" &&
    currentDoctorId !== undefined &&
    currentDoctorId === assignedDoctorId
  );
}
export function visitActions(
  role: Role,
  status: VisitStatus,
  assigned: boolean,
) {
  return {
    cancel: role === "STAFF" && status === "WAITING",
    start: role === "DOCTOR" && assigned && status === "WAITING",
  };
}
export const navItems: {
  path: string;
  label: string;
  icon: string;
  roles: Role[];
  group: string;
}[] = [
  { path: "/", label: "업무 개요", icon: "home", roles, group: "WORKSPACE" },
  {
    path: "/patients",
    label: "환자 관리",
    icon: "patients",
    roles: ["STAFF"],
    group: "진료 업무",
  },
  {
    path: "/appointments",
    label: "예약 관리",
    icon: "calendar",
    roles: ["STAFF", "DOCTOR", "NURSE"],
    group: "진료 업무",
  },
  {
    path: "/visits",
    label: "접수 · 진료 대기",
    icon: "visit",
    roles: ["STAFF", "DOCTOR", "NURSE"],
    group: "진료 업무",
  },
  {
    path: "/encounters",
    label: "진료 기록",
    icon: "record",
    roles: ["DOCTOR"],
    group: "진료 업무",
  },
  {
    path: "/histories",
    label: "진료 기록 수정 이력",
    icon: "history",
    roles: ["DOCTOR", "NURSE"],
    group: "진료 업무",
  },
  {
    path: "/users",
    label: "직원 관리",
    icon: "users",
    roles: ["ADMIN"],
    group: "병원 관리",
  },
  {
    path: "/departments",
    label: "진료과 관리",
    icon: "department",
    roles,
    group: "병원 관리",
  },
  {
    path: "/doctors",
    label: "의료진 관리",
    icon: "doctor",
    roles,
    group: "병원 관리",
  },
  {
    path: "/capabilities",
    label: "서비스 연결 현황",
    icon: "connection",
    roles,
    group: "시스템",
  },
];
export const slots = Array.from(
  { length: 18 },
  (_, i) =>
    `${String(9 + Math.floor(i / 2)).padStart(2, "0")}:${i % 2 ? "30" : "00"}`,
);
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function positiveId(value: string) {
  if (
    !/^\d+$/.test(value) ||
    !Number.isSafeInteger(Number(value)) ||
    Number(value) < 1
  )
    throw new Error("올바른 ID를 입력해 주세요.");
  return Number(value);
}
export function dateTime(value: string | null | undefined) {
  return value ? value.replace("T", " ").slice(0, 16) : "—";
}
