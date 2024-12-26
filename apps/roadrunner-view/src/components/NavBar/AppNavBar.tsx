import React, { useEffect, useState } from "react";
import { NavLink as RouterNavLink, useNavigate } from "react-router-dom";
import fontawesome from '@fortawesome/fontawesome'
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faPowerOff, faUser } from '@fortawesome/fontawesome-free-solid'

import {
  Collapse,
  Container,
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

export const AppNavBar = () => {
  const { getAccessTokenSilently } = useAuth0();
  const navigate = useNavigate();

  const [isOpen, setIsOpen] = useState(false);
  const [token, setToken] = useState("");

  const {
  user,
  isAuthenticated,
  loginWithRedirect,
  logout,
  } = useAuth0();
  const toggle = () => setIsOpen(!isOpen);

  const landingPageUri = process.env.REACT_APP_LANDING_PAGE_URL;

  useEffect(() => {
  if (token) {
    return;
  }

  const audience = process.env.REACT_APP_AUTH0_AUDIENCE;
  getAccessTokenSilently({
    authorizationParams: {
    audience: audience,
    }
  })
    .then(async token => {
    setToken(token);
    });
  }, [token, getAccessTokenSilently]);

  const logoutWithRedirect = () =>
  logout({
    logoutParams: {
    returnTo: landingPageUri,
    }
  });

  const handleCreateCrissCross = async () => {
  const url = `${process.env.REACT_APP_ROADRUNNER_REST_URL_BASE}/api/vehicle/create-crisscross`;
  const body = {
    degLatitude: 32.74666,
    degLongitude: -97.319507,
    kmRadius: 10.0,
    vehicleCount: 15,
  };

  try {
    const response = await fetch(url, {
    method: 'post',
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
    });

    if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`);
    }

    const data = await response.json();
    console.log("Success:", data);
  } catch (error) {
    console.error("Error creating criss-cross:", error);
    alert("Failed to create criss-cross.");
  }
  };

  const handleResetServer = async () => {
  const url = `${process.env.REACT_APP_ROADRUNNER_REST_URL_BASE}/api/vehicle/reset-server`;
  try {
    const response = await fetch(url, {
    method: 'get',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    });

    if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`);
    }

    const data = await response.json();
    console.log("Success:", data);
    navigate("/home");
  } catch (error) {
    console.error("Error resetting server:", error);
    alert("Failed to reset server.");
  }
  };

  fontawesome.library.add(faPowerOff, faUser);

  return (
  <div className="nav-container">
    <Navbar color="light" light expand="md" container={false}>
    <Container>
      <NavBarBrand />
      <NavbarToggler onClick={toggle} />
      <Collapse isOpen={isOpen} navbar>
      <Nav className="mr-auto" navbar>
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
      </Nav>
      <Nav className="d-none d-md-block" navbar style={{ marginLeft: 'auto' }}>
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
        <div className="d-flex justify-content-start align-items-center">
        {isAuthenticated && (
          <UncontrolledDropdown nav inNavbar>
          <DropdownToggle nav caret id="manageDropDown">
            Manage
          </DropdownToggle>
          <DropdownMenu>
            <DropdownItem header>Manage</DropdownItem>
            <DropdownItem
            id="crissCrossBtn"
            onClick={() => handleCreateCrissCross()}
            >
            Create criss-cross
            </DropdownItem>
            <DropdownItem
            id="resetServerBtn"
            onClick={() => handleResetServer()}
            >
            Reset server
            </DropdownItem>
          </DropdownMenu>
          </UncontrolledDropdown>
        )}
        {isAuthenticated && (
          <UncontrolledDropdown nav inNavbar>
          <DropdownToggle nav caret id="profileDropDown">
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
            <FontAwesomeIcon icon="user" className="mr-3" /> Profile
            </DropdownItem>
            <DropdownItem
            id="qsLogoutBtn"
            onClick={() => logoutWithRedirect()}
            >
            <FontAwesomeIcon icon="power-off" className="mr-3" /> Log
            out
            </DropdownItem>
          </DropdownMenu>
          </UncontrolledDropdown>
        )}
        </div>
      </Nav>
      </Collapse>
    </Container>
    </Navbar>
  </div>
  );
};
