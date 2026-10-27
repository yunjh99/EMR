import {
  cloneElement,
  isValidElement,
  useId,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ChevronRight,
  CircleAlert,
  Inbox,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
} from "lucide-react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { errorDetails, errorMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Page } from "@/lib/types";

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-5">
      <div>
        <p className="section-label mb-2">{eyebrow}</p>
        <h1 className="text-[27px] font-semibold tracking-tight">{title}</h1>
        <p className="mt-2 text-[13px] leading-relaxed text-muted-foreground">
          {description}
        </p>
      </div>
      {action && <div className="pt-6">{action}</div>}
    </div>
  );
}
export function StatusBadge({ status }: { status: string }) {
  const labels: Record<string, string> = {
    WAITING: "진료 대기",
    IN_PROGRESS: "진료 중",
    COMPLETED: "진료 완료",
    CANCELLED: "취소",
    SCHEDULED: "예약 예정",
    CHECKED_IN: "접수 완료",
    active: "활성",
    inactive: "비활성",
  };
  const color = ["COMPLETED", "CHECKED_IN", "active"].includes(status)
    ? "bg-emerald-50 text-emerald-700"
    : ["IN_PROGRESS", "SCHEDULED"].includes(status)
      ? "bg-blue-50 text-blue-600"
      : status === "WAITING"
        ? "bg-amber-50 text-amber-700"
        : "bg-slate-100 text-slate-500";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-1 text-[11px] font-medium",
        color,
      )}
    >
      <span className="size-1.5 rounded-full bg-current opacity-60" />
      {labels[status] || status}
    </span>
  );
}
export function EmptyState({
  title = "조회된 정보가 없습니다",
  description = "검색 조건을 바꾸거나 새로운 정보를 등록해 주세요.",
  icon = false,
  children,
}: {
  title?: string;
  description?: string;
  icon?: boolean;
  children?: ReactNode;
}) {
  const Icon = icon ? LockKeyhole : Inbox;
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-[#f0f5f4] text-[#8fa8a2]">
        <Icon size={23} strokeWidth={1.5} />
      </div>
      <h3 className="text-sm font-semibold">{title}</h3>
      <p className="mt-2 max-w-lg text-xs leading-6 text-muted-foreground">
        {description}
      </p>
      {children && <div className="mt-5">{children}</div>}
    </div>
  );
}
export function ErrorBox({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="my-4 flex items-start gap-3 rounded-lg border border-red-100 bg-red-50/60 p-4 text-sm text-red-700"
    >
      <CircleAlert size={18} className="mt-0.5 shrink-0" />
      <div className="min-w-0 flex-1">
        <p>{errorMessage(error)}</p>
        {errorDetails(error).map((text, i) => (
          <p key={i} className="mt-1 text-xs">
            {text}
          </p>
        ))}
      </div>
      {retry && (
        <Button variant="outline" size="sm" onClick={retry}>
          <RefreshCw size={13} />
          다시 시도
        </Button>
      )}
    </div>
  );
}
export function LoadingRows({ columns = 5 }: { columns?: number }) {
  return (
    <div aria-label="불러오는 중" role="status" className="space-y-5 p-6">
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          className="grid gap-8"
          style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
        >
          {Array.from({ length: columns }, (_, c) => (
            <span key={c} className="h-3 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      ))}
    </div>
  );
}
export interface Column<T> {
  label: string;
  render: (row: T) => ReactNode;
  className?: string;
}
export function DataTable<T extends { id: number }>({
  rows,
  columns,
  empty,
}: {
  rows: T[];
  columns: Column<T>[];
  empty?: ReactNode;
}) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.label} className={c.className}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              {columns.map((c) => (
                <td key={c.label} className={c.className}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && (empty || <EmptyState />)}
    </div>
  );
}
export function Pagination({
  page,
  onPage,
}: {
  page?: Pick<Page<unknown>, "number" | "totalPages" | "totalElements">;
  onPage: (page: number) => void;
}) {
  if (!page) return null;
  return (
    <div className="flex items-center justify-between border-t px-5 py-3.5 text-xs text-muted-foreground">
      <span>
        전체{" "}
        <b className="font-semibold text-foreground">
          {page.totalElements.toLocaleString()}
        </b>
        건
      </span>
      <div className="flex items-center gap-3">
        <Button
          aria-label="이전 페이지"
          variant="outline"
          size="sm"
          disabled={page.number <= 0}
          onClick={() => onPage(page.number - 1)}
        >
          <ArrowLeft size={14} />
        </Button>
        <span>
          {page.totalPages ? page.number + 1 : 0} / {page.totalPages}
        </span>
        <Button
          aria-label="다음 페이지"
          variant="outline"
          size="sm"
          disabled={page.number + 1 >= page.totalPages}
          onClick={() => onPage(page.number + 1)}
        >
          <ArrowRight size={14} />
        </Button>
      </div>
    </div>
  );
}
export function Field({
  label,
  help,
  children,
}: {
  label: string;
  help?: string;
  children: ReactNode;
}) {
  const generatedId = useId();
  const controlId =
    isValidElement<{ id?: string }>(children) && children.props.id
      ? children.props.id
      : generatedId;
  const control = isValidElement<{ id?: string; "aria-describedby"?: string }>(
    children,
  )
    ? cloneElement(children, {
        id: controlId,
        "aria-describedby":
          [
            children.props["aria-describedby"],
            help ? `${generatedId}-help` : undefined,
          ]
            .filter(Boolean)
            .join(" ") || undefined,
      })
    : children;
  return (
    <div className="field">
      <label htmlFor={controlId}>{label}</label>
      {control}
      {help && (
        <span id={`${generatedId}-help`} className="field-help">
          {help}
        </span>
      )}
    </div>
  );
}
export function FormModal({
  title,
  description,
  open,
  onOpenChange,
  children,
  onSubmit,
  pending = false,
  error,
  submitLabel = "저장",
}: {
  title: string;
  description: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  pending?: boolean;
  error?: unknown;
  submitLabel?: string;
}) {
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!pending) onOpenChange(value);
      }}
    >
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[580px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-5 py-2">
          {children}
          {!!error && <ErrorBox error={error} />}
          <div className="flex justify-end gap-2 border-t pt-5">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              onClick={() => onOpenChange(false)}
            >
              닫기
            </Button>
            <Button type="submit" disabled={pending}>
              {pending && <LoaderCircle size={15} className="animate-spin" />}
              {pending ? "저장 중…" : submitLabel}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
export function ConfirmAction({
  label,
  title,
  description,
  onConfirm,
  disabled = false,
}: {
  label: string;
  title: string;
  description: string;
  onConfirm: () => Promise<unknown>;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!pending) {
          setOpen(value);
          setError(undefined);
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={disabled}>
          {label}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {!!error && <ErrorBox error={error} />}
        <div className="mt-4 flex justify-end gap-2">
          <Button
            variant="outline"
            disabled={pending}
            onClick={() => setOpen(false)}
          >
            돌아가기
          </Button>
          <Button
            disabled={pending}
            onClick={async () => {
              setPending(true);
              try {
                await onConfirm();
                toast.success("처리가 완료되었습니다.");
                setOpen(false);
              } catch (e) {
                setError(e);
              } finally {
                setPending(false);
              }
            }}
          >
            {pending ? "처리 중…" : label}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
export function ConnectionRequired() {
  return (
    <div className="panel">
      <EmptyState
        icon
        title="업무 공간에 연결해 주세요"
        description="서버에서 인증이 확인된 사용자에게만 환자 정보와 업무 기능이 표시됩니다."
      >
        <Button asChild>
          <Link to="/connect">
            연결 안내
            <ChevronRight size={15} />
          </Link>
        </Button>
      </EmptyState>
    </div>
  );
}
export function ReadyLabel({
  ready,
  children,
}: {
  ready: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-xs",
        ready ? "text-primary" : "text-muted-foreground",
      )}
    >
      {ready ? (
        <CheckCircle2 size={14} />
      ) : (
        <span className="size-1.5 rounded-full bg-slate-300" />
      )}
      {children}
    </span>
  );
}
export function formValues(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
  return Object.fromEntries(new FormData(event.currentTarget)) as Record<
    string,
    string
  >;
}
