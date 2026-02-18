import React, { useEffect, useMemo, useState } from "react";
import { fetchUserAttributes } from "aws-amplify/auth";
import { AppNavBar } from "../NavBar/AppNavBar";

type UserInfo = {
  name?: string;
  email?: string;
  picture?: string;
};

export const ProfilePage: React.FC = () => {
  const [user, setUser] = useState<UserInfo | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadUser() {
      try {
        const attrs = await fetchUserAttributes();
        const info: UserInfo = {
          name: attrs.name ?? attrs.given_name ?? attrs.family_name ?? attrs.email,
          email: attrs.email,
          picture: (attrs as any).picture,
        };

        if (!cancelled) setUser(info);
      } catch (e) {
        // Guard should prevent access; if we get here, just show nothing.
        if (!cancelled) setUser(null);
      }
    }

    loadUser();
    return () => {
      cancelled = true;
    };
  }, []);

  const displayName = useMemo(() => user?.name ?? user?.email ?? "User", [user]);

  if (!user) {
    return null;
  }

  return (
    <>
      <AppNavBar />
      <div>
        <p id="page-description">
          <span>
          You can use the <strong>ID Token</strong> to get the profile
          information of an authenticated user.
          </span>
          <span>
          <strong>Only authenticated users can access this page.</strong>
          </span>
        </p>

        <div className="profile-grid" style={{ position: "fixed", top: 100, left: 10 }}>
          <div className="profile__header">
            {user.picture ? (
              <img
                src={user.picture}
                alt="Profile"
                className="profile__avatar"
              />
            ) : (
              <div
                className="profile__avatar"
                style={{
                  width: 96,
                  height: 96,
                  borderRadius: "50%",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: "#ddd",
                  color: "#333",
                  fontWeight: 700,
                  fontSize: 40,
                }}
                aria-label="Profile"
                title={displayName}
              >
                {displayName.slice(0, 1).toUpperCase()}
              </div>
            )}

            <div className="profile__headline">
              <h2 className="profile__title">{user.name}</h2>
              <span className="profile__description">{user.email}</span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};
