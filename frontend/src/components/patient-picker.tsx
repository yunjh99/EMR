import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";
import { api } from "@/lib/api";
import type { PatientResponse } from "@/lib/types";
import { ErrorBox, Field } from "@/components/common";

export function PatientPicker({
  value,
  onChange,
}: {
  value: PatientResponse | null;
  onChange: (patient: PatientResponse | null) => void;
}) {
  const [search, setSearch] = useState("");
  const [mode, setMode] = useState("name");
  const patients = useQuery({
    queryKey: ["patients", "picker", mode, search],
    queryFn: ({ signal }) =>
      api.patients(
        { [mode]: search.trim(), page: 0, size: 10, active: true },
        signal,
      ),
    enabled: search.trim().length > 0 && !value,
  });
  return (
    <div className="space-y-3">
      {value ? (
        <div className="flex items-center justify-between rounded-lg border border-primary/20 bg-accent p-4">
          <div>
            <p className="text-sm font-semibold">
              {value.name}{" "}
              <span className="ml-2 text-xs font-normal text-muted-foreground">
                {value.patientNo}
              </span>
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {value.birthDate} · {value.phone}
            </p>
          </div>
          <button
            type="button"
            aria-label="환자 다시 선택"
            onClick={() => {
              onChange(null);
              setSearch("");
            }}
          >
            <X size={16} />
          </button>
        </div>
      ) : (
        <>
          <div className="flex items-end gap-2">
            <Field label="검색 기준">
              <select
                className="control w-[120px]"
                value={mode}
                onChange={(e) => setMode(e.target.value)}
              >
                <option value="name">환자명</option>
                <option value="patientNo">환자 번호</option>
                <option value="phone">전화번호</option>
              </select>
            </Field>
            <div className="flex-1">
              <Field label="환자 검색">
                <input
                  className="control"
                  placeholder="환자를 검색해 선택해 주세요"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") e.preventDefault();
                  }}
                />
              </Field>
            </div>
            <Search size={16} className="mb-3 text-muted-foreground" />
          </div>
          {search.trim() && (
            <div className="max-h-52 overflow-y-auto rounded-lg border">
              {patients.isPending ? (
                <p className="p-4 text-xs text-muted-foreground">검색 중…</p>
              ) : patients.error ? (
                <ErrorBox
                  error={patients.error}
                  retry={() => void patients.refetch()}
                />
              ) : patients.data?.content.length ? (
                patients.data.content.map((patient) => (
                  <button
                    key={patient.id}
                    type="button"
                    className="flex w-full items-center justify-between border-b p-3 text-left text-xs last:border-b-0 hover:bg-accent"
                    onClick={() => onChange(patient)}
                  >
                    <span className="font-medium">
                      {patient.name}
                      <span className="ml-2 font-normal text-muted-foreground">
                        {patient.patientNo}
                      </span>
                    </span>
                    <span className="text-muted-foreground">
                      {patient.birthDate} · {patient.phone}
                    </span>
                  </button>
                ))
              ) : (
                <p className="p-4 text-xs text-muted-foreground">
                  일치하는 환자가 없습니다.
                </p>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
