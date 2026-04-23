import { fetchAuthSession } from "aws-amplify/auth";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DropdownItem, DropdownMenu, DropdownToggle, UncontrolledDropdown } from "reactstrap";
import { CONFIG } from "../../config";

export const ManageMenu = (props: {
  openCreateVehicle: any,
  toggleSimTable: any
}) => {
  const navigate = useNavigate();

  const [token, setToken] = useState("");

  // Load (and silently refresh) an access token
  useEffect(() => {
    let cancelled = false;

    async function loadToken() {
      if (token) return;

      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("No access token available. Route guard should have redirected to login.");
          return;
        }

        if (!cancelled) setToken(accessToken);
      } catch (error: any) {
        console.error("Error fetching token:", error?.message ?? error);
      }
    }

    loadToken();
    return () => { cancelled = true; };
  }, [token]);

  const handleCreateCrissCross = async () => {
    const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/create-crisscross`;
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

      await response.json();
    } catch (error) {
      console.error("Error creating criss-cross:", error);
      alert("Failed to create criss-cross.");
    }
  };

  const handleResetServer = async () => {
    const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/reset-server-INHIBITTED`;
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

  function handleCreateVehicle() {
    props.openCreateVehicle();
  }

  function handleToggleSimTable() {
    props.toggleSimTable();
  }
  return (
    <div className="d-flex justify-content-left align-items-center">
      <UncontrolledDropdown nav inNavbar className="centered-dropdown">
        <DropdownToggle nav caret style={{ fontSize: "1.1rem" }}>
          Manage
        </DropdownToggle>
        <DropdownMenu>
          <DropdownItem
            id="createVehicleBtn"
            onClick={() => handleCreateVehicle()}
          >
            Create vehicle
          </DropdownItem>
          <DropdownItem
            id="crissCrossBtn"
            onClick={() => handleCreateCrissCross()}
          >
            Create criss-cross
          </DropdownItem>
          <DropdownItem
            id="simTable"
            onClick={() => handleToggleSimTable()}
          >
          Toggle Sim Table
          </DropdownItem>
          <DropdownItem
            id="resetServerBtn"
            onClick={() => handleResetServer()}
          >
            Reset server
          </DropdownItem>
        </DropdownMenu>
      </UncontrolledDropdown>
    </div>
  )
}
