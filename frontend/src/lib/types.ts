export type Role = "ADMIN" | "STAFF" | "DOCTOR" | "NURSE";
export interface AuthResponse {
  user: UserResponse;
  accessToken: string;
  expiresIn: number;
}
export interface PatientResponse extends PatientCreateRequest {
  id: number;
  patientNo: string;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}
export interface PatientSearch {
  name?: string;
  patientNo?: string;
  phone?: string;
  active?: boolean;
  page: number;
  size: number;
}
export interface VisitResponse {
  id: number;
  patientId: number;
  patientNo: string;
  patientName: string;
  doctorId: number;
  doctorUserId: number;
  doctorName: string;
  departmentId: number;
  departmentName: string;
  appointmentId: number | null;
  visitType: "APPOINTMENT" | "WALK_IN";
  status: VisitStatus;
  encounterId: number | null;
  checkedInAt: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}
export interface VisitSearch {
  date: string;
  doctorId?: string;
  patientId?: string;
  status?: VisitStatus | "";
  page: number;
  size: number;
}
export type VisitStatus = "WAITING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type AppointmentStatus = "SCHEDULED" | "CHECKED_IN" | "CANCELLED";
export interface SuccessResponse<T> {
  status: number;
  message: string;
  data: T;
}
export interface FailResponse {
  status: number;
  message: string;
  validation: Record<string, string>;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  empty: boolean;
}
export interface UserResponse {
  id: number;
  username: string;
  name: string;
  role: Role;
}
export interface UserListResponse extends UserResponse {
  active: boolean;
}
export interface UserCreateRequest {
  username: string;
  password: string;
  name: string;
  role: Role;
}
export interface UserUpdateRequest {
  name?: string;
  role?: Role;
  active?: boolean;
}
export interface UserSearch {
  username?: string;
  name?: string;
  role?: Role | "";
  active?: string;
  page: number;
  size: number;
}
export interface DepartmentResponse {
  id: number;
  code: string;
  name: string;
  active: boolean;
}
export interface DepartmentRequest {
  code: string;
  name: string;
}
export interface DoctorResponse {
  id: number;
  userId: number;
  name: string;
  departmentId: number;
  departmentName: string;
  licenseNo: string;
  specialty: string | null;
  active: boolean;
}
export interface DoctorUpdateRequest {
  departmentId: number;
  licenseNo: string;
  specialty: string | null;
}
export interface DoctorCreateRequest extends DoctorUpdateRequest {
  userId?: number;
  account?: { username: string; password: string; name: string };
}
export interface PatientCreateRequest {
  name: string;
  phone: string;
  address: string;
  gender: "M" | "F";
  birthDate: string;
}
export type PatientUpdateRequest = Partial<PatientCreateRequest>;
export interface AppointmentResponse {
  id: number;
  doctorId: number;
  doctorName: string;
  patientId: number;
  patientName: string;
  scheduledAt: string;
  status: AppointmentStatus;
  reason: string | null;
  createdAt: string;
  updatedAt: string | null;
}
// The backend DTO currently spells the request field "reson" and takes entity references.
export interface AppointmentCreateRequest {
  doctor: { id: number };
  patient: { id: number };
  scheduledAt: string;
  reson: string;
}
export interface AppointmentSearch {
  date: string;
  doctorId?: string;
  status?: AppointmentStatus | "";
  page: number;
  size: number;
}
export interface EncounterResponse {
  id: number;
  visitId: number;
  patientId: number;
  patientNo: string;
  patientName: string;
  doctorId: number;
  doctorName: string;
  departmentId: number;
  departmentName: string;
  visitStatus: VisitStatus;
  chiefComplaint: string;
  clinicalNote: string;
  treatmentPlan: string;
  version: number;
  createdAt: string;
  updatedAt: string | null;
}
export interface EncounterUpdateRequest {
  chiefComplaint?: string | null;
  clinicalNote?: string | null;
  treatmentPlan?: string | null;
  changeReason: string;
}
export interface EncounterHistoryResponse {
  id: number;
  encounterId: number;
  version: number;
  chiefComplaint: string | null;
  clinicalNote: string | null;
  treatmentPlan: string | null;
  changedBy: number;
  changeReason: string;
  changedAt: string;
}
