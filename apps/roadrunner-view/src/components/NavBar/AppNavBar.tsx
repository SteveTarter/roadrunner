import './AppNavBar.css'
import React, { useEffect, useState } from "react";
import { NavLink as RouterNavLink } from "react-router-dom";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { CONFIG } from "../../config";

import {
  Collapse,
  Navbar,
  NavbarToggler,
  Nav,
  NavItem,
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

export const AppNavBar = ({
  additionalMenuItems
}: {
   additionalMenuItems?: (closeNavbar: () => void) => React.ReactNode;
}) => {

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

  return (
    <div className="nav-container">
      <Navbar color="light" light expand="md">
        <NavBarBrand />
        <NavbarToggler onClick={toggle} />
        <Collapse isOpen={isOpen} navbar>
          {/* Left-aligned items */}
          <Nav className="me-auto" navbar>
            {additionalMenuItems && additionalMenuItems(() => setIsOpen(false))}
          </Nav>

          {/* Right-aligned items */}
          <Nav className="ms-auto" navbar>
            {!isAuthenticated && (
              <NavItem>
                <Button
                  id="qsLoginBtn"
                  color="primary"
                  className="btn-margin"
                  onClick={() => signInWithRedirect()}
                >
                  Log in
                </Button>
              </NavItem>
            )}

            {isAuthenticated && user && (
              <UncontrolledDropdown nav inNavbar>
                <DropdownToggle nav caret id="profileDropDown">
                  {user.picture ? (
                    <img
                      src={user.picture}
                      alt="Profile"
                      className="nav-user-profile rounded-circle"
                      width="50"
                    />
                  ) : (
                    <div
                      style={{
                        width: "35px",
                        height: "35px",
                        borderRadius: "50%",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: "#ddd",
                        color: "#333",
                        fontWeight: 600,
                      }}
                      aria-label="Profile"
                      title={user.name || user.email || "User"}
                    >
                      {(user.name || user.email || "U").slice(0, 1).toUpperCase()}
                    </div>
                  )}
                </DropdownToggle>

                <DropdownMenu>
                  <DropdownItem header>{user.name || user.email}</DropdownItem>
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
                    onClick={() => logoutAndReturn()}
                  >
                    <FontAwesomeIcon icon="power-off" className="mr-3" />
                    Logout
                  </DropdownItem>
                </DropdownMenu>
              </UncontrolledDropdown>
            )}
          </Nav>
        </Collapse>
      </Navbar>
    </div>
  );
};
