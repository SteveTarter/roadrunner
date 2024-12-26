import { useAuth0 } from "@auth0/auth0-react";
import React from "react";
import { AppNavBar } from "../NavBar/AppNavBar";

export const ProfilePage: React.FC = () => {
  const { user } = useAuth0();

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
    <div className="profile-grid">
      <div className="profile__header">
      <img
        src={user.picture}
        alt="Profile"
        className="profile__avatar"
      />
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
