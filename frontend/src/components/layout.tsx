import {
  Activity,
  ArrowUpRight,
  Building2,
  CalendarDays,
  ChevronRight,
  CircleHelp,
  ClipboardList,
  FileClock,
  FileText,
  HeartPulse,
  LayoutDashboard,
  LogOut,
  PlugZap,
  Settings2,
  ShieldCheck,
  Stethoscope,
  Users,
  UsersRound,
} from "lucide-react";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  type NavigateFunction,
} from "react-router-dom";
import { useNavigate } from "react-router-dom";
import { useSession } from "@/auth/session";
import { navItems, roleNames } from "@/lib/policy";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Suspense } from "react";
import { LoadingRows } from "@/components/common";

const icons = {
  home: LayoutDashboard,
  patients: UsersRound,
  calendar: CalendarDays,
  visit: ClipboardList,
  record: FileText,
  history: FileClock,
  users: Users,
  department: Building2,
  doctor: Stethoscope,
  connection: PlugZap,
};
function disconnectAndReturn(
  disconnect: () => void,
  navigate: NavigateFunction,
) {
  disconnect();
  navigate("/connect");
}
export function Layout() {
  const { user, disconnect } = useSession();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const visible = user
    ? navItems.filter((item) => item.roles.includes(user.role))
    : navItems.filter((item) => ["/", "/capabilities"].includes(item.path));
  const groups = [...new Set(visible.map((item) => item.group))];
  const title =
    navItems.find((item) => item.path === pathname)?.label ||
    (pathname === "/connect" ? "업무 공간 연결" : "진료 기록 상세");
  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-30 flex w-[236px] flex-col border-r bg-white">
        <Link to="/" className="flex h-[81px] items-center gap-3 px-6">
          <span className="flex size-10 items-center justify-center rounded-xl bg-[#173f48] text-[#c6e9df]">
            <HeartPulse size={24} strokeWidth={1.8} />
          </span>
          <span>
            <span className="block text-xl font-bold tracking-tight text-[#173f48]">
              온유 <span className="font-normal">EMR</span>
            </span>
            <span className="text-[10px] tracking-widest text-muted-foreground">
              CARE, CONNECTED.
            </span>
          </span>
        </Link>
        <div className="mx-5 mb-6 mt-3 flex items-center gap-2 rounded-lg bg-[#f5f8f7] px-3 py-3">
          <Building2 size={15} className="text-[#6d8f85]" />
          <div>
            <p className="text-xs font-semibold">온유 의료원</p>
            <p className="mt-0.5 text-[10px] text-muted-foreground">
              병원 업무 관리 시스템
            </p>
          </div>
        </div>
        <nav aria-label="주 메뉴" className="space-y-6 px-3">
          {groups.map((group) => (
            <div key={group}>
              <p className="section-label mb-2 px-3 text-[10px]">{group}</p>
              <div className="space-y-1">
                {visible
                  .filter((item) => item.group === group)
                  .map((item) => {
                    const Icon = icons[item.icon as keyof typeof icons];
                    return (
                      <NavLink
                        key={item.path}
                        to={item.path}
                        end={item.path === "/"}
                        className={({ isActive }) =>
                          cn(
                            "flex items-center gap-3 rounded-lg px-3 py-2.5 text-[13px] transition-colors",
                            isActive
                              ? "bg-[#eaf4ef] font-semibold text-[#277a69]"
                              : "text-[#6c7d86] hover:bg-slate-50 hover:text-foreground",
                          )
                        }
                      >
                        <Icon size={17} strokeWidth={1.7} />
                        {item.label}
                      </NavLink>
                    );
                  })}
              </div>
            </div>
          ))}
        </nav>
        {!user && (
          <div className="mx-5 mt-8 rounded-lg border border-dashed p-3.5">
            <ShieldCheck size={19} className="mb-2 text-muted-foreground" />
            <p className="text-xs font-medium">인증 후 업무를 시작합니다</p>
            <p className="mt-1.5 text-[11px] leading-5 text-muted-foreground">
              역할에 맞는 메뉴가 연결 후 표시됩니다.
            </p>
            <Link
              to="/connect"
              className="mt-3 flex items-center gap-1 text-xs font-medium text-primary"
            >
              연결 안내
              <ChevronRight size={12} />
            </Link>
          </div>
        )}
        <div className="mt-auto border-t p-5">
          <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
            <span
              className={cn(
                "size-1.5 rounded-full",
                user ? "bg-primary" : "bg-amber-400",
              )}
            />
            {user ? "인증된 사용자로 연결됨" : "인증 연결 전"}
          </div>
          <p className="mt-2 text-[10px] text-slate-400">
            © ONYU EMR · Portfolio project
          </p>
        </div>
      </aside>
      <div className="pl-[236px]">
        <header className="sticky top-0 z-20 flex h-[72px] items-center justify-between border-b bg-white/95 px-8 backdrop-blur">
          <div className="flex items-center gap-2 text-xs">
            <span className="text-muted-foreground">워크스페이스</span>
            <ChevronRight size={12} className="text-slate-300" />
            <span className="font-medium">{title}</span>
          </div>
          <div className="flex items-center gap-5">
            <span className="hidden items-center gap-2 text-xs text-muted-foreground xl:flex">
              <CalendarDays size={14} />
              {new Date().toLocaleDateString("ko-KR", {
                year: "numeric",
                month: "long",
                day: "numeric",
                weekday: "short",
              })}
            </span>
            <Link
              to="/capabilities"
              aria-label="서비스 연결 현황"
              className="text-muted-foreground hover:text-primary"
            >
              <CircleHelp size={18} />
            </Link>
            <span className="h-6 w-px bg-border" />
            {user ? (
              <div className="flex items-center gap-3">
                <span className="flex size-8 items-center justify-center rounded-full bg-[#edf2ef] text-xs font-semibold text-primary">
                  {user.name.slice(0, 1)}
                </span>
                <div>
                  <p className="text-xs font-semibold">{user.name}</p>
                  <p className="text-[10px] text-muted-foreground">
                    {roleNames[user.role]}
                  </p>
                </div>
                <button
                  aria-label="연결 해제"
                  onClick={() => disconnectAndReturn(disconnect, navigate)}
                  className="ml-2 text-muted-foreground hover:text-destructive"
                >
                  <LogOut size={16} />
                </button>
              </div>
            ) : (
              <Button size="sm" variant="outline" asChild>
                <Link to="/connect">
                  업무 공간 연결
                  <ArrowUpRight size={13} />
                </Link>
              </Button>
            )}
          </div>
        </header>
        <main id="main-content">
          <Suspense
            fallback={
              <div className="page">
                <div className="panel">
                  <LoadingRows />
                </div>
              </div>
            }
          >
            <Outlet />
          </Suspense>
        </main>
        <footer className="mx-8 flex justify-between border-t py-5 text-[10px] text-slate-400">
          <span className="flex items-center gap-1.5">
            <Activity size={12} />
            진료에 집중할 수 있도록, 연결된 업무 공간
          </span>
          <span className="flex items-center gap-1">
            <Settings2 size={11} />
            ON YU · EMR WORKSPACE
          </span>
        </footer>
      </div>
    </div>
  );
}
