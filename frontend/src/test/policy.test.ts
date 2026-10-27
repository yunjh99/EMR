import { describe, expect, it } from "vitest";
import {
  mayEditEncounter,
  navItems,
  positiveId,
  slots,
  visitActions,
} from "@/lib/policy";
import type { Role, VisitStatus } from "@/lib/types";

describe("실제 서버 정책에 맞는 화면 권한", () => {
  it.each([
    "WAITING",
    "IN_PROGRESS",
    "COMPLETED",
    "CANCELLED",
  ] as VisitStatus[])("STAFF는 %s 상태에 맞는 작업만 표시한다", (status) => {
    expect(visitActions("STAFF", status, false)).toEqual({
      cancel: status === "WAITING",
      start: false,
    });
  });
  it("담당 DOCTOR만 대기 접수를 시작할 수 있다", () => {
    expect(visitActions("DOCTOR", "WAITING", true).start).toBe(true);
    expect(visitActions("DOCTOR", "WAITING", false).start).toBe(false);
    expect(visitActions("DOCTOR", "COMPLETED", true).start).toBe(false);
  });
  it.each(["NURSE", "ADMIN"] as Role[])(
    "%s에는 상태 변경 버튼이 없다",
    (role) => {
      expect(visitActions(role, "WAITING", true)).toEqual({
        cancel: false,
        start: false,
      });
    },
  );
  it("수정은 담당 의사 ID로 판정하며 이름이나 userId와 혼동하지 않는다", () => {
    expect(mayEditEncounter("DOCTOR", 3, 3)).toBe(true);
    expect(mayEditEncounter("DOCTOR", 2, 3)).toBe(false);
    expect(mayEditEncounter("DOCTOR", undefined, 3)).toBe(false);
    expect(mayEditEncounter("STAFF", 3, 3)).toBe(false);
    expect(mayEditEncounter("NURSE", 3, 3)).toBe(false);
  });
  it.each(["ADMIN", "STAFF", "DOCTOR", "NURSE"] as Role[])(
    "%s 메뉴는 해당 역할에 한정된다",
    (role) => {
      const paths = navItems
        .filter((item) => item.roles.includes(role))
        .map((item) => item.path);
      expect(paths.includes("/users")).toBe(role === "ADMIN");
      expect(paths.includes("/patients")).toBe(role === "STAFF");
      expect(paths.includes("/encounters")).toBe(role === "DOCTOR");
      expect(paths.includes("/histories")).toBe(
        role === "DOCTOR" || role === "NURSE",
      );
    },
  );
});

describe("예약 시간 및 식별자", () => {
  it("09:00부터 17:30까지 30분 간격 18개만 제공한다", () => {
    expect(slots).toHaveLength(18);
    expect(slots[0]).toBe("09:00");
    expect(slots.at(-1)).toBe("17:30");
    expect(slots).not.toContain("18:00");
    expect(new Set(slots).size).toBe(18);
  });
  it.each(["0", "-1", "1.2", "abc", "9007199254740992"])(
    "잘못된 ID %s를 거절한다",
    (value) => expect(() => positiveId(value)).toThrow(),
  );
});
