import { useAuth0 } from "@auth0/auth0-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DropdownItem, DropdownMenu, DropdownToggle, UncontrolledDropdown } from "reactstrap";

export const ManageMenu = () => {
  const { getAccessTokenSilently } = useAuth0();
  const navigate = useNavigate();

  const [token, setToken] = useState("");

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

  return (
    <UncontrolledDropdown nav inNavbar>
      <DropdownToggle nav caret>
        Manage
      </DropdownToggle>
      <DropdownMenu>
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
  )
}
