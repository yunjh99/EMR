import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  api,
  ApiError,
  dayReservations,
  http,
  setAccessToken,
  setExpiryHandler,
} from "@/lib/api";

// Contract tests only: fixtures are not included in the running application.
let mock: MockAdapter;
const ok = (data: unknown) => ({ status: 200, message: "테스트 응답", data });
beforeEach(() => {
  mock = new MockAdapter(http);
  setAccessToken();
  setExpiryHandler();
});
afterEach(() => {
  mock.restore();
  setAccessToken();
  setExpiryHandler();
});

describe("실제 Controller/DTO 요청 계약", () => {
  it("예약 생성은 현재 DTO의 reson과 entity ID 참조를 그대로 보낸다", async () => {
    mock.onPost("/appointments").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        doctor: { id: 2 },
        patient: { id: 4 },
        scheduledAt: "2026-11-01T09:30:00",
        reson: "테스트 예약",
      });
      return [201, ok(null)];
    });
    await api.createAppointment({
      doctor: { id: 2 },
      patient: { id: 4 },
      scheduledAt: "2026-11-01T09:30:00",
      reson: "테스트 예약",
    });
  });
  it("전체 진료 기록이 아닌 환자별 경로만 호출한다", async () => {
    mock.onGet("/patients/7/encounters").reply((config) => {
      expect(config.params).toEqual({ page: 1, size: 20 });
      return [
        200,
        ok({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 1,
          size: 20,
          empty: true,
        }),
      ];
    });
    await api.patientEncounters(7, 1);
    expect(mock.history.get[0].url).not.toBe("/encounters");
  });
  it("실제 이력 상세 경로에 Encounter와 History ID를 함께 보낸다", async () => {
    mock
      .onGet("/encounters/7/histories/9")
      .reply(200, ok({ id: 9, encounterId: 7, version: 1 }));
    expect((await api.history(7, 9)).encounterId).toBe(7);
  });
  it("정정 사유를 PATCH에 포함하고 완료 API를 호출하지 않는다", async () => {
    mock.onPatch("/encounters/2").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        clinicalNote: "정정",
        changeReason: "검사 결과 반영",
      });
      return [200, ok(null)];
    });
    await api.updateEncounter(2, {
      clinicalNote: "정정",
      changeReason: "검사 결과 반영",
    });
    expect(mock.history.patch).toHaveLength(1);
  });
  it("당일 예약을 모든 페이지에서 읽어 누락된 점유 시간을 만들지 않는다", async () => {
    mock.onGet("/appointments").reply((config) => [
      200,
      ok({
        content: [{ id: Number(config.params.page) + 1 }],
        totalPages: 3,
        totalElements: 3,
        number: config.params.page,
        size: 200,
        empty: false,
      }),
    ]);
    const result = await dayReservations("2", "2026-11-01");
    expect(result.map((row) => row.id)).toEqual([1, 2, 3]);
    expect(mock.history.get.map((row) => row.params.page)).toEqual([0, 1, 2]);
  });
  it("환자 수정은 생략한 필드를 null로 만들지 않는다", async () => {
    mock.onPatch("/patients/4").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ phone: "010-0000-0000" });
      return [200, ok(null)];
    });
    await api.updatePatient(4, { phone: "010-0000-0000" });
  });
});

describe("오류와 인증 만료", () => {
  it("공통 validation 응답을 보존하되 Axios 요청 정보는 노출하지 않는다", async () => {
    mock.onPost("/patients").reply(400, {
      status: 400,
      message: "입력값을 확인해 주세요.",
      validation: { name: "환자명은 필수입니다." },
    });
    const error = await api
      .createPatient({
        name: "",
        birthDate: "",
        gender: "F",
        phone: "",
        address: "",
      })
      .catch((value: unknown) => value);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).validation.name).toBe("환자명은 필수입니다.");
    expect(error).not.toHaveProperty("config");
  });
  it("401이면 만료 처리를 호출하고 다음 요청에서 인증 헤더를 제거한다", async () => {
    const expired = vi.fn();
    setExpiryHandler(expired);
    // A mock HTTP header, not a JWT and never accepted as a runtime login.
    setAccessToken("unit-test-header");
    mock
      .onGet("/users/1")
      .reply(401, { status: 401, message: "인증 실패", validation: {} });
    await expect(api.user(1)).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
    mock.onGet("/departments").reply(200, ok([]));
    await api.departments();
    expect(mock.history.get.at(-1)?.headers?.Authorization).toBeUndefined();
  });
  it("403은 세션 만료로 처리하지 않는다", async () => {
    const expired = vi.fn();
    setExpiryHandler(expired);
    mock
      .onGet("/encounters/1")
      .reply(403, { status: 403, message: "권한이 없습니다.", validation: {} });
    await expect(api.encounter(1)).rejects.toMatchObject({ status: 403 });
    expect(expired).not.toHaveBeenCalled();
  });
  it("연결 실패를 사용자 메시지로 바꾼다", async () => {
    mock.onGet("/departments").networkError();
    await expect(api.departments()).rejects.toThrow(
      "서버에 연결할 수 없습니다",
    );
  });
});
