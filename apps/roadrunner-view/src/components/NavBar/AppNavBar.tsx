import React, { useState } from "react";
import { NavLink as RouterNavLink } from "react-router-dom";
import fontawesome from '@fortawesome/fontawesome'
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faPowerOff, faUser } from '@fortawesome/fontawesome-free-solid'

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

import { useAuth0 } from "@auth0/auth0-react";
import { NavBarBrand } from "./NavBarBrand";

export const AppNavBar = ({ additionalMenuItems }: { additionalMenuItems?: React.ReactNode; }) => {

  const [isOpen, setIsOpen] = useState(false);

  const {
    user,
    isAuthenticated,
    loginWithRedirect,
    logout,
  } = useAuth0();
  const toggle = () => setIsOpen(!isOpen);

  const landingPageUri = process.env.REACT_APP_LANDING_PAGE_URL;

  const logoutWithRedirect = () =>
    logout({
      logoutParams: {
        returnTo: landingPageUri,
      }
    });

  fontawesome.library.add(faPowerOff, faUser);

  return (
    <div className="nav-container">
      <Navbar color="light" light expand="md">
          <NavBarBrand />
          <NavbarToggler onClick={toggle} />
          <Collapse isOpen={isOpen} navbar>
            <Nav className="me-auto" navbar>
              <NavItem>
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
                    onClick={() => loginWithRedirect()}
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
                      <img
                        src={user?.picture}
                        alt="Profile"
                        className="nav-user-profile rounded-circle"
                        width="50"
                      />
                    </DropdownToggle>
                    <DropdownMenu>
                      <DropdownItem header>{user?.name}</DropdownItem>
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
                        onClick={() => logoutWithRedirect()}
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
