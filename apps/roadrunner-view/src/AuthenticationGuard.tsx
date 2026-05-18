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
          if (!cancelled) {
            // Handle Post-Login Redirection
            // Check if there is a saved deep link from a previous unauthenticated session
            const deepLink = sessionStorage.getItem("redirect_after_login");

            if (deepLink && deepLink !== window.location.pathname) {
              sessionStorage.removeItem("redirect_after_login"); // Clear immediately to prevent infinite loops
              window.location.replace(deepLink); // Perform a browser reload to the deep-linked path
              return;
            }

            setStatus("authed");
          }
          return;
        }

        // No token/session
        if (!cancelled) setStatus("unauth");

        if (autoRedirect && !redirectStartedRef.current) {
          redirectStartedRef.current = true;

          // Save Intended Destination Before Redirect
          // Capture the exact deep link path (e.g., /driver-view/deb6907a)
          if (window.location.pathname && window.location.pathname !== "/") {
            sessionStorage.setItem("redirect_after_login", window.location.pathname);
          }

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

  // Helper function to handle manual login clicks
  const handleManualLogin = async () => {
    if (window.location.pathname && window.location.pathname !== "/") {
      sessionStorage.setItem("redirect_after_login", window.location.pathname);
    }
    await signInWithRedirect();
  };

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
        <button onClick={handleManualLogin}>Log in</button>
      </div>
    </div>
  );
};
