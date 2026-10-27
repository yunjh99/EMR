import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { SessionProvider } from "@/auth/session";
import { queryClient } from "@/lib/query";
import { App, AppErrorBoundary } from "./App";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <BrowserRouter>
            <a
              href="#main-content"
              className="fixed -top-20 left-2 z-50 rounded bg-primary p-3 text-white focus:top-2"
            >
              본문 바로가기
            </a>
            <App />
            <Toaster position="top-right" richColors closeButton />
          </BrowserRouter>
        </SessionProvider>
      </QueryClientProvider>
    </AppErrorBoundary>
  </StrictMode>,
);
