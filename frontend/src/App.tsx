import { Component, lazy, type ReactNode } from "react";
import { Link, Route, Routes } from "react-router-dom";
import { useSession } from "@/auth/session";
import { Layout } from "@/components/layout";
import { ConnectionRequired, EmptyState } from "@/components/common";
import { Button } from "@/components/ui/button";
import type { Role } from "@/lib/types";
import { OverviewPage } from "@/pages/overview";
import { ConnectPage } from "@/pages/connect";
import { CapabilitiesPage } from "@/pages/capabilities";

const UsersPage = lazy(() =>
  import("@/pages/admin").then((module) => ({ default: module.UsersPage })),
);
const DepartmentsPage = lazy(() =>
  import("@/pages/admin").then((module) => ({
    default: module.DepartmentsPage,
  })),
);
const DoctorsPage = lazy(() =>
  import("@/pages/admin").then((module) => ({ default: module.DoctorsPage })),
);
const PatientsPage = lazy(() =>
  import("@/pages/patients").then((module) => ({
    default: module.PatientsPage,
  })),
);
const AppointmentsPage = lazy(() =>
  import("@/pages/appointments").then((module) => ({
    default: module.AppointmentsPage,
  })),
);
const VisitsPage = lazy(() =>
  import("@/pages/visits").then((module) => ({ default: module.VisitsPage })),
);
const VisitDetailPage = lazy(() =>
  import("@/pages/visits").then((module) => ({
    default: module.VisitDetailPage,
  })),
);
const EncountersPage = lazy(() =>
  import("@/pages/encounters").then((module) => ({
    default: module.EncountersPage,
  })),
);
const EncounterDetailPage = lazy(() =>
  import("@/pages/encounters").then((module) => ({
    default: module.EncounterDetailPage,
  })),
);
const HistoriesPage = lazy(() =>
  import("@/pages/encounters").then((module) => ({
    default: module.HistoriesPage,
  })),
);

function RoleRoute({
  roles,
  children,
}: {
  roles: Role[];
  children: ReactNode;
}) {
  const { user, ready } = useSession();
  if (!ready)
    return (
      <div className="page">
        <p role="status" className="text-sm text-muted-foreground">
          인증 정보를 확인하고 있습니다…
        </p>
      </div>
    );
  if (!user)
    return (
      <div className="page">
        <ConnectionRequired />
      </div>
    );
  if (!roles.includes(user.role))
    return (
      <div className="page">
        <div className="panel">
          <EmptyState
            icon
            title="접근 권한이 없습니다"
            description="현재 계정에 허용된 메뉴에서 업무를 진행해 주세요."
          >
            <Button asChild variant="outline">
              <Link to="/">업무 개요로</Link>
            </Button>
          </EmptyState>
        </div>
      </div>
    );
  return children;
}
const allRoles: Role[] = ["ADMIN", "STAFF", "DOCTOR", "NURSE"];
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route
          path="visits/:id"
          element={
            <RoleRoute roles={["STAFF", "DOCTOR", "NURSE"]}>
              <VisitDetailPage />
            </RoleRoute>
          }
        />
        <Route index element={<OverviewPage />} />
        <Route path="connect" element={<ConnectPage />} />
        <Route path="capabilities" element={<CapabilitiesPage />} />
        <Route
          path="users"
          element={
            <RoleRoute roles={["ADMIN"]}>
              <UsersPage />
            </RoleRoute>
          }
        />
        <Route
          path="departments"
          element={
            <RoleRoute roles={allRoles}>
              <DepartmentsPage />
            </RoleRoute>
          }
        />
        <Route
          path="doctors"
          element={
            <RoleRoute roles={allRoles}>
              <DoctorsPage />
            </RoleRoute>
          }
        />
        <Route
          path="patients"
          element={
            <RoleRoute roles={["STAFF"]}>
              <PatientsPage />
            </RoleRoute>
          }
        />
        <Route
          path="appointments"
          element={
            <RoleRoute roles={["STAFF", "DOCTOR", "NURSE"]}>
              <AppointmentsPage />
            </RoleRoute>
          }
        />
        <Route
          path="visits"
          element={
            <RoleRoute roles={["STAFF", "DOCTOR", "NURSE"]}>
              <VisitsPage />
            </RoleRoute>
          }
        />
        <Route
          path="encounters"
          element={
            <RoleRoute roles={["DOCTOR"]}>
              <EncountersPage />
            </RoleRoute>
          }
        />
        <Route
          path="encounters/:id"
          element={
            <RoleRoute roles={["DOCTOR"]}>
              <EncounterDetailPage />
            </RoleRoute>
          }
        />
        <Route
          path="histories"
          element={
            <RoleRoute roles={["DOCTOR", "NURSE"]}>
              <HistoriesPage />
            </RoleRoute>
          }
        />
        <Route
          path="*"
          element={
            <div className="page">
              <div className="panel">
                <EmptyState title="페이지를 찾을 수 없습니다">
                  <Button variant="outline" asChild>
                    <Link to="/">업무 개요로 돌아가기</Link>
                  </Button>
                </EmptyState>
              </div>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}
export class AppErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <div className="mx-auto max-w-xl p-12">
        <h1 className="text-xl font-semibold">화면을 표시하지 못했습니다</h1>
        <p className="my-5 text-sm text-muted-foreground">
          페이지를 새로 열어 다시 연결해 주세요.
        </p>
        <Button onClick={() => window.location.assign("/")}>처음으로</Button>
      </div>
    ) : (
      this.props.children
    );
  }
}
