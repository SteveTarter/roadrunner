import { fetchAuthSession } from "aws-amplify/auth";
import { DropdownItem, DropdownMenu, DropdownToggle, UncontrolledDropdown } from "reactstrap";
import { CONFIG } from "../../config";

export const ManageMenu = (props: {
  openCreateVehicle: any,
  toggleSimTable: any,
  toggleShowActiveVehiclePlot: any
}) => {

  type ApiErrorResponse = {
    message?: string;
    status?: number;
    timestamp?: string;
  };

  async function readApiError(response: Response): Promise<string> {
    const contentType = response.headers.get("content-type") ?? "";

    try {
      if (contentType.includes("application/json")) {
        const body = (await response.json()) as ApiErrorResponse;
        return body.message || `Request failed with status ${response.status}`;
      }

      const text = await response.text();
      return text || `Request failed with status ${response.status}`;
    } catch {
      return `Request failed with status ${response.status}`;
    }
  }

  const handleCreateCrissCross = async () => {
    const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/create-crisscross`;
    const body = {
      degLatitude: 32.74666,
      degLongitude: -97.319507,
      kmRadius: 10.0,
      vehicleCount: 15,
    };

    try {
      // Get the latest session right before the call
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();

      if (!accessToken) {
        console.error("Session expired");
        return;
      }

      const response = await fetch(url, {
        method: 'post',
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        const message = await readApiError(response);
        throw new Error(message);
      }

      await response.json();
    } catch (error) {
      console.error("Error creating criss-cross:", error);
      alert(error);
    }
  };

  function handleCreateVehicle() {
    props.openCreateVehicle();
  }

  function handleToggleSimTable() {
    props.toggleSimTable();
  }

  function handleToggleShowActiveVehiclePlot() {
    props.toggleShowActiveVehiclePlot();
  }

  return (
    <div className="d-flex justify-content-left align-items-center">
      <UncontrolledDropdown nav inNavbar className="centered-dropdown">
        <DropdownToggle nav caret style={{ fontSize: "1.1rem" }}>
          Manage
        </DropdownToggle>
        <DropdownMenu className="wide-dropdown">
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
          Sim Table
          </DropdownItem>
          <DropdownItem
            id="simTable"
            onClick={() => handleToggleShowActiveVehiclePlot()}
          >
          Active Vehicle Plot
          </DropdownItem>
        </DropdownMenu>
      </UncontrolledDropdown>
    </div>
  )
}
