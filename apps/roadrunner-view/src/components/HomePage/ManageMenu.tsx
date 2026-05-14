import { useState, useEffect } from 'react';
import { DropdownItem, DropdownMenu, DropdownToggle, UncontrolledDropdown } from "reactstrap";
import { fetchAuthSession } from "aws-amplify/auth";

export const ManageMenu = (props: {
  openCreateVehicle: any,
  openCrissCross: any,
  toggleSimTable: any,
  toggleShowActiveVehiclePlot: any
}) => {

  const [isCreator, setIsCreator] = useState(false);

  useEffect(() => {
    const checkUserGroup = async () => {
      try {
        const session = await fetchAuthSession(); //
        // Extract groups from the IdToken payload
        const groups = (session.tokens?.idToken?.payload['cognito:groups'] as string[]) || [];
        setIsCreator(groups.includes('creator'));
      } catch (error) {
        console.error("Error fetching user groups", error);
        setIsCreator(false);
      }
    };

    checkUserGroup();
  }, []);

  const handleCreateCrissCross = async () => {
    props.openCrissCross();
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
          {/* Only show items that make vehicles to creator group members */}
          {isCreator && (
            <>
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
              <DropdownItem divider />
            </>
          )}
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
