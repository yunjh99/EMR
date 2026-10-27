import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";
import { authApi } from "@/lib/api";
beforeEach(() => {
  vi.spyOn(authApi, "reissue").mockRejectedValue(
    new Error("No test refresh cookie"),
  );
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
