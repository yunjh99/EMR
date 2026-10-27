import { useState } from "react";
import { ArrowRight, Check, ShieldCheck } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useSession } from "@/auth/session";
import { roleNames } from "@/lib/policy";
import { Button } from "@/components/ui/button";
import { ErrorBox, Field, PageHeader, formValues } from "@/components/common";

export function ConnectPage() {
  const { login, user } = useSession();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  return (
    <div className="page">
      <PageHeader
        eyebrow="SECURE WORKSPACE"
        title="업무 공간 로그인"
        description="병원에서 발급받은 직원 계정으로 로그인합니다."
      />
      <div className="grid grid-cols-[.95fr_1.2fr] gap-6">
        <section className="relative overflow-hidden rounded-2xl bg-[#173f48] p-9 text-white">
          <div className="absolute -right-20 top-8 size-72 rounded-full border border-white/10" />
          <div className="absolute -right-8 top-20 size-48 rounded-full border border-white/10" />
          <ShieldCheck
            size={34}
            strokeWidth={1.2}
            className="mb-12 text-[#bde2d7]"
          />
          <p className="text-[11px] tracking-[.2em] text-[#8bb8b2]">
            ONE WORKSPACE, BETTER CARE
          </p>
          <h2 className="mt-4 text-[29px] font-medium leading-[1.5] tracking-tight">
            환자의 방문부터
            <br />
            진료 기록까지,
            <br />
            <span className="text-[#bde2d7]">하나로 이어지는 업무.</span>
          </h2>
          <p className="mt-5 text-xs leading-6 text-[#b8ced1]">
            원무과, 의료진, 관리자가
            <br />
            각자의 역할에 맞는 정보를 확인합니다.
          </p>
          <div className="mt-12 space-y-3 border-t border-white/15 pt-6">
            {[
              "서버에서 확인하는 직원 계정",
              "역할에 따른 업무 접근",
              "정정 전 원본과 수정 사유 보존",
            ].map((text) => (
              <p
                key={text}
                className="flex items-center gap-2 text-xs text-[#cee3e0]"
              >
                <Check size={13} />
                {text}
              </p>
            ))}
          </div>
        </section>
        <section className="panel p-8">
          {user ? (
            <>
              <h2 className="text-lg font-semibold">로그인되어 있습니다</h2>
              <p className="mt-3 text-sm text-muted-foreground">
                {user.name} · {roleNames[user.role]}
              </p>
              <Button className="mt-6" asChild>
                <Link to="/">
                  업무 개요로 이동
                  <ArrowRight size={15} />
                </Link>
              </Button>
            </>
          ) : (
            <>
              <h2 className="text-xl font-semibold">직원 로그인</h2>
              <p className="mt-3 text-[13px] leading-7 text-muted-foreground">
                계정과 비밀번호를 확인해 주세요. 개인 환자 정보는 로그인 후
                허용된 역할에서만 조회됩니다.
              </p>
              <form
                className="mt-8 space-y-5"
                onSubmit={async (event) => {
                  const values = formValues(event);
                  setError(undefined);
                  setPending(true);
                  try {
                    await login(values.username.trim(), values.password);
                    navigate("/");
                  } catch (e) {
                    setError(e);
                  } finally {
                    setPending(false);
                  }
                }}
              >
                <Field label="로그인 ID">
                  <input
                    className="control"
                    name="username"
                    required
                    maxLength={20}
                    autoComplete="username"
                  />
                </Field>
                <Field label="비밀번호">
                  <input
                    className="control"
                    name="password"
                    type="password"
                    required
                    autoComplete="current-password"
                  />
                </Field>
                {!!error && <ErrorBox error={error} />}
                <Button className="w-full" disabled={pending}>
                  {pending ? "로그인 중…" : "로그인"}
                  <ArrowRight size={15} />
                </Button>
              </form>
              <p className="mt-6 text-xs leading-6 text-muted-foreground">
                계정 발급이나 비밀번호 확인이 필요하면 병원 관리자에게 문의해
                주세요.
              </p>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
