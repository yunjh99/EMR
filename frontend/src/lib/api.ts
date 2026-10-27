import axios from "axios";
import type * as T from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public validation: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}
export const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
export const http = axios.create({ baseURL: baseUrl, timeout: 15000 });
let accessToken: string | undefined;
let onExpired: (() => void) | undefined;
export function setAccessToken(value?: string) {
  accessToken = value;
}
export function setExpiryHandler(handler?: () => void) {
  onExpired = handler;
}
http.interceptors.request.use((config) => {
  if (accessToken) config.headers.set("Authorization", `Bearer ${accessToken}`);
  return config;
});
http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error))
      return Promise.reject(new ApiError("요청을 처리하지 못했습니다.", 0));
    const status = error.response?.status || 0;
    if (status === 401) {
      setAccessToken();
      onExpired?.();
    }
    const body = error.response?.data as Partial<T.FailResponse> | undefined;
    if (
      status === 403 &&
      typeof error.response?.data === "string" &&
      error.response.data.includes("Invalid CORS request")
    ) {
      return Promise.reject(
        new ApiError(
          "서버가 이 웹 주소의 연결을 허용하지 않습니다. 연결 설정을 확인해 주세요.",
          status,
        ),
      );
    }
    const fallback =
      status === 403
        ? "이 작업을 수행할 권한이 없습니다."
        : status === 404
          ? "요청한 정보를 찾을 수 없습니다."
          : status === 409
            ? "다른 요청으로 상태가 변경되었거나 중복된 요청입니다. 새로고침 후 확인해 주세요."
            : status === 401
              ? "인증이 만료되었거나 유효하지 않습니다. 다시 연결해 주세요."
              : status === 0
                ? "서버에 연결할 수 없습니다. 서버 실행 상태와 연결 설정을 확인해 주세요."
                : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    // Discard Axios config/headers: credentials must never reach UI errors or telemetry.
    return Promise.reject(
      new ApiError(
        status < 500 && typeof body?.message === "string"
          ? body.message
          : fallback,
        status,
        body?.validation && typeof body.validation === "object"
          ? body.validation
          : {},
      ),
    );
  },
);
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}
export function errorDetails(error: unknown) {
  return error instanceof ApiError ? Object.values(error.validation) : [];
}
async function get<T>(path: string, params?: object, signal?: AbortSignal) {
  return (await http.get<T.SuccessResponse<T>>(path, { params, signal })).data
    .data;
}
async function mutate(
  method: "post" | "put" | "patch",
  path: string,
  body?: unknown,
) {
  return (
    await http.request<T.SuccessResponse<null>>({
      method,
      url: path,
      data: body,
    })
  ).data;
}
async function createWithId(path: string, body: unknown) {
  return (await http.post<T.SuccessResponse<number>>(path, body)).data;
}
// Auth requests intentionally have no access-token interceptor, including when the old token expired.
export const authHttp = axios.create({
  baseURL: baseUrl,
  timeout: 15000,
  withCredentials: true,
});
export const authApi = {
  login: async (username: string, password: string) => {
    try {
      return (
        await authHttp.post<T.SuccessResponse<T.AuthResponse>>("/login", {
          username,
          password,
        })
      ).data.data;
    } catch (error) {
      throw new ApiError(
        axios.isAxiosError(error) && error.response?.status === 401
          ? "로그인 정보를 확인해 주세요."
          : "로그인 요청에 실패했습니다. 서버 연결과 입력값을 확인해 주세요.",
        axios.isAxiosError(error) ? error.response?.status || 0 : 0,
      );
    }
  },
  reissue: async (signal?: AbortSignal) =>
    (
      await authHttp.post<T.SuccessResponse<T.AuthResponse>>(
        "/reissue",
        undefined,
        { signal },
      )
    ).data.data,
  logout: async () => {
    await authHttp.post("/logout");
  },
};
function clean(params: object) {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== "" && value !== undefined,
    ),
  );
}
export const api = {
  user: (id: number, signal?: AbortSignal) =>
    get<T.UserResponse>(`/users/${id}`, undefined, signal),
  users: (params: T.UserSearch, signal?: AbortSignal) =>
    get<T.Page<T.UserListResponse>>("/users", clean(params), signal),
  createUser: (body: T.UserCreateRequest) => mutate("post", "/users", body),
  updateUser: (id: number, body: T.UserUpdateRequest) =>
    mutate("patch", `/users/${id}`, body),
  departments: (includeInactive = false, signal?: AbortSignal) =>
    get<T.DepartmentResponse[]>("/departments", { includeInactive }, signal),
  department: (id: number, includeInactive = false, signal?: AbortSignal) =>
    get<T.DepartmentResponse>(
      `/departments/${id}`,
      { includeInactive },
      signal,
    ),
  createDepartment: (body: T.DepartmentRequest) =>
    mutate("post", "/departments", body),
  updateDepartment: (id: number, body: T.DepartmentRequest) =>
    mutate("put", `/departments/${id}`, body),
  deactivateDepartment: (id: number) =>
    mutate("patch", `/departments/${id}/deactivate`),
  doctors: (
    params: { departmentId?: string; includeInactive?: boolean } = {},
    signal?: AbortSignal,
  ) => get<T.DoctorResponse[]>("/doctors", clean(params), signal),
  doctor: (id: number, includeInactive = false, signal?: AbortSignal) =>
    get<T.DoctorResponse>(`/doctors/${id}`, { includeInactive }, signal),
  createDoctor: (body: T.DoctorCreateRequest) =>
    mutate("post", "/doctors", body),
  updateDoctor: (id: number, body: T.DoctorUpdateRequest) =>
    mutate("put", `/doctors/${id}`, body),
  deactivateDoctor: (id: number) =>
    mutate("patch", `/doctors/${id}/deactivate`),
  createPatient: (body: T.PatientCreateRequest) =>
    createWithId("/patients", body),
  patients: (params: T.PatientSearch, signal?: AbortSignal) =>
    get<T.Page<T.PatientResponse>>("/patients", clean(params), signal),
  patient: (id: number, signal?: AbortSignal) =>
    get<T.PatientResponse>(`/patients/${id}`, undefined, signal),
  visits: (params: T.VisitSearch, signal?: AbortSignal) =>
    get<T.Page<T.VisitResponse>>("/visits", clean(params), signal),
  visit: (id: number, signal?: AbortSignal) =>
    get<T.VisitResponse>(`/visits/${id}`, undefined, signal),
  createVisit: (body: {
    patientId: number;
    doctorId: number;
    appointmentId?: number;
  }) => createWithId("/visits", body),
  createEncounter: (body: {
    visitId: number;
    chiefComplaint: string;
    clinicalNote: string;
    treatmentPlan: string;
  }) => createWithId("/encounters", body),
  updatePatient: (id: number, body: T.PatientUpdateRequest) =>
    mutate("patch", `/patients/${id}`, body),
  appointments: (params: T.AppointmentSearch, signal?: AbortSignal) =>
    get<T.Page<T.AppointmentResponse>>(
      "/appointments",
      { ...clean(params), sort: "scheduledAt,asc" },
      signal,
    ),
  appointment: (id: number, signal?: AbortSignal) =>
    get<T.AppointmentResponse>(`/appointments/${id}`, undefined, signal),
  createAppointment: (body: T.AppointmentCreateRequest) =>
    mutate("post", "/appointments", body),
  cancelAppointment: (id: number) =>
    mutate("patch", `/appointments/${id}/cancel`),
  // These endpoints exist. No Visit GET/POST or Encounter POST is assumed.
  cancelVisit: (id: number) => mutate("patch", `/visits/${id}/cancel`),
  startVisit: (id: number) => mutate("patch", `/visits/${id}/start`),
  encounter: (id: number, signal?: AbortSignal) =>
    get<T.EncounterResponse>(`/encounters/${id}`, undefined, signal),
  patientEncounters: (patientId: number, page: number, signal?: AbortSignal) =>
    get<T.Page<T.EncounterResponse>>(
      `/patients/${patientId}/encounters`,
      { page, size: 20 },
      signal,
    ),
  updateEncounter: (id: number, body: T.EncounterUpdateRequest) =>
    mutate("patch", `/encounters/${id}`, body),
  histories: (id: number, page: number, signal?: AbortSignal) =>
    get<T.Page<T.EncounterHistoryResponse>>(
      `/encounters/${id}/histories`,
      { page, size: 20 },
      signal,
    ),
  history: (id: number, historyId: number, signal?: AbortSignal) =>
    get<T.EncounterHistoryResponse>(
      `/encounters/${id}/histories/${historyId}`,
      undefined,
      signal,
    ),
};
export async function dayReservations(
  doctorId: string,
  date: string,
  signal?: AbortSignal,
) {
  const first = await api.appointments(
    { doctorId, date, page: 0, size: 200 },
    signal,
  );
  const records = [...first.content];
  for (let page = 1; page < first.totalPages; page++) {
    if (signal?.aborted) throw new DOMException("Aborted", "AbortError");
    records.push(
      ...(await api.appointments({ doctorId, date, page, size: 200 }, signal))
        .content,
    );
  }
  return records;
}
