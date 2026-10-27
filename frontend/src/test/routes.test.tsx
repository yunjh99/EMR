import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { SessionProvider } from "@/auth/session";
import { App } from "@/App";

function route(path: string) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <SessionProvider>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  );
}
describe("미인증 및 준비 상태 라우팅", () => {
  it("미인증 업무 화면에서 개인정보 요청 없이 연결 안내를 표시한다", async () => {
    route("/patients");
    expect(
      await screen.findByText("업무 공간에 연결해 주세요"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "환자 등록" }),
    ).not.toBeInTheDocument();
  });
  it("실제 서버용 계정 로그인 화면을 제공한다", () => {
    route("/connect");
    expect(screen.getByText("직원 로그인")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
  });
  it("미구현 기능을 연결 완료로 표시하지 않는다", () => {
    route("/capabilities");
    expect(screen.getByText("방문 접수 등록 · 대기 현황")).toBeInTheDocument();
    expect(screen.getAllByText("준비 중").length).toBeGreaterThan(0);
  });
  it("잘못된 경로는 돌아갈 수 있는 404 화면을 표시한다", () => {
    route("/not-a-page");
    expect(screen.getByText("페이지를 찾을 수 없습니다")).toBeInTheDocument();
  });
});
