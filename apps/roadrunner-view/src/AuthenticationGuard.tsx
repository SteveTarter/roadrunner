import { withAuthenticationRequired } from "@auth0/auth0-react";
import React, { ComponentType } from "react";
import { SpinnerLoading } from "./components/Utils/SpinnerLoading";

interface AuthenticationGuardProps {
  component: ComponentType;
}

export const AuthenticationGuard: React.FC<AuthenticationGuardProps> = ({
  component,
}) => {
  const Component = withAuthenticationRequired(component, {
  onRedirecting: () => (
    <div className="page-layout">
    <SpinnerLoading />
    </div>
  ),
  });

  return <Component />;
};
