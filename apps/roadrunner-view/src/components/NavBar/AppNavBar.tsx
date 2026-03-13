import './AppNavBar.css'
import React, { useEffect, useMemo, useState } from "react";
import { NavLink as RouterNavLink } from "react-router-dom";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { CONFIG } from "../../config";

import {
  Collapse,
  Navbar,
  NavbarToggler,
  Nav,
  NavItem,
  NavLink,
  Button,
  UncontrolledDropdown,
  DropdownToggle,
  DropdownMenu,
  DropdownItem,
} from "reactstrap";

import { signInWithRedirect, signOut, fetchAuthSession } from "aws-amplify/auth";
import { NavBarBrand } from "./NavBarBrand";

type UserInfo = {
  name?: string;
  email?: string;
  picture?: string;
};

export const AppNavBar = ({ additionalMenuItems }: { additionalMenuItems?: React.ReactNode; }) => {

  const [isOpen, setIsOpen] = useState(false);

  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState<UserInfo | null>(null);

  const toggle = () => setIsOpen(!isOpen);

  const landingPageUri = CONFIG.LANDING_PAGE_URL;

  // Load auth state + user attributes
  useEffect(() => {
    let cancelled = false;

    async function loadAuth() {
      try {
        const session = await fetchAuthSession();

        const accessToken = session.tokens?.accessToken?.toString();
        const idToken = session.tokens?.idToken;
        const claims = (idToken?.payload ?? {}) as any;

        // Only treat as authenticated if we actually have tokens
        if (!accessToken || !idToken) {
          if (!cancelled) {
            setIsAuthenticated(false);
            setUser(null);
          }
          return;
        }


        const info: UserInfo = {
          name: claims?.name ?? claims?.given_name ?? claims?.email,
          email: claims?.email,
          picture: claims?.picture,
        };
        if (!cancelled) {
          setUser(info);
          setIsAuthenticated(true);
        }
      } catch  (e) {
        console.error("Error in loadAuth():", e);
        if (!cancelled) {
          setIsAuthenticated(false);
          setUser(null);
        }
      }
    }

    loadAuth();

    return () => { cancelled = true; };
  }, []);

  const displayName = useMemo(() => user?.name ?? user?.email ?? "User", [user]);

  const logoutAndReturn = async () => {
    try {
      await signOut({ global: true });
    } finally {
      if (landingPageUri) {
        window.location.assign(landingPageUri);
      } else {
        window.location.assign("/");
      }
    }
  };

  const login = async () => {
    await signInWithRedirect();
  };

  return (
    <div className="nav-container">
      <Navbar color="light" light expand="md">
          <NavBarBrand />
          <NavbarToggler onClick={toggle} />
          <Collapse isOpen={isOpen} navbar>
            <Nav className="me-auto" navbar>
              <NavItem style={{ fontSize: "1.1rem" }}>
                <NavLink
                  tag={RouterNavLink}
                  to="/"
                  exact="true"
                  className="router-link-exact-active"
                >
                  Home
                </NavLink>
              </NavItem>

              {!isAuthenticated && (
                <NavItem>
                  <Button
                    id="qsLoginBtn"
                    color="primary"
                    className="btn-margin"
                    onClick={login}
                  >
                    Log in
                  </Button>
                </NavItem>
              )}
            </Nav>

            <Nav navbar>
              {isAuthenticated && (
                <>
                  {additionalMenuItems}
                  <UncontrolledDropdown nav inNavbar>
                    <DropdownToggle nav caret>
                      {user?.picture ? (
                        <img
                          src={user?.picture}
                          alt="Profile"
                          className="nav-user-profile rounded-circle"
                          width="50"
                        />
                      ) : (
                        // fallback: circle with first letter
                        <div
                          className="nav-user-profile rounded-circle"
                          style={{
                            width: 50,
                            height: 50,
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            background: "#ddd",
                            color: "#333",
                            fontWeight: 600,
                          }}
                          aria-label="Profile"
                          title={displayName}
                        >
                          {displayName.slice(0, 1).toUpperCase()}
                        </div>
                      )}
                    </DropdownToggle>

                    <DropdownMenu>
                      <DropdownItem header>{displayName}</DropdownItem>
                      <DropdownItem
                        tag={RouterNavLink}
                        to="/profile"
                        className="dropdown-profile"
                      >
                        <FontAwesomeIcon icon="user" className="mr-3" />
                        Profile
                      </DropdownItem>
                      <DropdownItem
                        id="qsLogoutBtn"
                        onClick={logoutAndReturn}
                      >
                        <FontAwesomeIcon icon="power-off" className="mr-3" />
                        Logout
                      </DropdownItem>
                    </DropdownMenu>
                  </UncontrolledDropdown>
                </>
              )}
            </Nav>
          </Collapse>
      </Navbar>
    </div>
  );
};
