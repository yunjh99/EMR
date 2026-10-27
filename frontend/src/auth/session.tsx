import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { toast } from "sonner";
import { authApi, setAccessToken, setExpiryHandler } from "@/lib/api";
import { queryClient } from "@/lib/query";
import { roles } from "@/lib/policy";
import type { AuthResponse, UserResponse } from "@/lib/types";

export function tokenSubject(token: string): string {
  try {
    const part = token.split(".")[1];
    const bytes = Uint8Array.from(
      atob(part.replace(/-/g, "+").replace(/_/g, "/")),
      (char) => char.charCodeAt(0),
    );
    const body = JSON.parse(new TextDecoder().decode(bytes)) as {
      sub?: unknown;
      exp?: unknown;
    };
    if (
      typeof body.sub !== "string" ||
      typeof body.exp !== "number" ||
      body.exp * 1000 <= Date.now()
    )
      throw new Error();
    return body.sub;
  } catch {
    throw new Error("유효한 서버 인증 응답이 필요합니다.");
  }
}
interface SessionContext {
  user: UserResponse | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<void>;
  disconnect: () => void;
}
const Session = createContext<SessionContext | null>(null);
export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [ready, setReady] = useState(false);
  const generation = useRef(0);
  const clear = useCallback(() => {
    generation.current++;
    setAccessToken();
    setUser(null);
    void queryClient.cancelQueries();
    queryClient.clear();
  }, []);
  const accept = useCallback((response: AuthResponse) => {
    if (
      !roles.includes(response.user.role) ||
      tokenSubject(response.accessToken) !== response.user.username
    )
      throw new Error("인증 정보를 확인하지 못했습니다.");
    setAccessToken(response.accessToken);
    setUser(response.user);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    const version = generation.current;
    void authApi
      .reissue(controller.signal)
      .then((response) => {
        if (!controller.signal.aborted && generation.current === version)
          accept(response);
      })
      .catch(() => {})
      .finally(() => {
        if (!controller.signal.aborted) setReady(true);
      });
    return () => controller.abort();
  }, [accept]);
  useEffect(() => {
    let refreshing: Promise<void> | undefined;
    setExpiryHandler(() => {
      if (refreshing) return;
      const version = generation.current;
      refreshing = authApi
        .reissue()
        .then((response) => {
          if (generation.current !== version) return;
          queryClient.clear();
          accept(response);
          toast.info(
            "인증을 갱신했습니다. 실패한 저장 요청은 다시 확인해 주세요.",
          );
        })
        .catch(() => {
          if (generation.current === version) {
            clear();
            toast.error("인증이 만료되었습니다. 다시 로그인해 주세요.");
          }
        })
        .finally(() => {
          refreshing = undefined;
        });
    });
    return () => setExpiryHandler();
  }, [accept, clear]);
  const login = async (username: string, password: string) => {
    clear();
    const version = generation.current;
    const response = await authApi.login(username, password);
    if (generation.current === version) {
      accept(response);
      setReady(true);
    }
  };
  const disconnect = () => {
    clear();
    setReady(true);
    void authApi
      .logout()
      .catch(() =>
        toast.error(
          "서버 로그아웃에 실패했습니다. 로그인 화면에서 다시 시도해 주세요.",
        ),
      );
  };
  return (
    <Session.Provider value={{ user, ready, login, disconnect }}>
      {children}
    </Session.Provider>
  );
}
export function useSession() {
  const context = useContext(Session);
  if (!context) throw new Error("SessionProvider is required");
  return context;
}
