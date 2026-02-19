import React, { ComponentType, useEffect, useRef, useState } from "react";
import { fetchAuthSession, signInWithRedirect } from "aws-amplify/auth";
import { SpinnerLoading } from "./components/Utils/SpinnerLoading";

interface AuthenticationGuardProps {
  component: ComponentType;
  autoRedirect?: boolean;
}

type Status = "checking" | "authed" | "unauth" | "error";

export const AuthenticationGuard: React.FC<AuthenticationGuardProps> = ({
   component,
  autoRedirect = true
}) => {
  const Component = component;

  const [status, setStatus] = useState<Status>("checking");
  const [error, setError] = useState<string | null>(null);

  // Prevent multiple redirect attempts in a single mount
  const redirectStartedRef = useRef(false);

  useEffect(() => {
      let cancelled = false;

      async function ensureAuthenticated() {

      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (accessToken) {
          if (!cancelled) setStatus("authed");
          return;
        }

        // No token/session
        if (!cancelled) setStatus("unauth");

        if (autoRedirect && !redirectStartedRef.current) {
          redirectStartedRef.current = true;
          await signInWithRedirect();
        }
      } catch (err: any) {
        console.error("Auth check failed:", err);
        if (!cancelled) {
          setError(err?.message ?? String(err));
          setStatus("error");
        }
      }
    }

    ensureAuthenticated();

    return () => {
      cancelled = true;
    };
  }, [autoRedirect]);

  if (status === "checking") {
    return (
      <div className="page-layout">
        <SpinnerLoading />
      </div>
    );
  }

  if (status === "authed") {
    return <Component />;
  }

  // unauth or error
  return (
    <div className="page-layout">
      <div style={{ padding: 16 }}>
        {status === "error" ? (
          <div style={{ marginBottom: 12 }}>Auth error: {error}</div>
        ) : (
          <div style={{ marginBottom: 12 }}>Not logged in.</div>
        )}
        <button onClick={() => signInWithRedirect()}>Log in</button>
      </div>
    </div>
  );
};
