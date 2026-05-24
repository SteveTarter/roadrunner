import { useState, useEffect } from 'react';
import { NavItem, NavLink } from "reactstrap";
import { fetchAuthSession } from "aws-amplify/auth";

export const ManageMenu = (props: {
  openCreateVehicle: any,
  openCrissCross: any,
  toggleSimTable: any,
  toggleShowActiveVehiclePlot: any,
  closeNavbar: () => void
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
    props.closeNavbar();
  };

  function handleCreateVehicle() {
    props.openCreateVehicle();
    props.closeNavbar();
  }

  function handleToggleSimTable() {
    props.toggleSimTable();
    props.closeNavbar();
  }

  function handleToggleShowActiveVehiclePlot() {
    props.toggleShowActiveVehiclePlot();
    props.closeNavbar();
  }

  return (
    <>
      {/* Only show items that make vehicles to creator group members */}
      {isCreator && (
        <>
          <NavItem>
            <NavLink
              id="createVehicleBtn"
              onClick={() => handleCreateVehicle()}
              style={{ cursor: "pointer" }}
            >
              Create vehicle
            </NavLink>
          </NavItem>
          <NavItem>
            <NavLink
              id="crissCrossBtn"
              onClick={() => handleCreateCrissCross()}
              style={{ cursor: "pointer" }}
            >
              Create criss-cross
            </NavLink>
          </NavItem>
        </>
      )}
      <NavItem>
        <NavLink
            id="simTable"
            onClick={() => handleToggleSimTable()}
            style={{ cursor: "pointer" }}
          >
          Sim Table
        </NavLink>
      </NavItem>
      <NavItem>
        <NavLink
          id="simTable"
          onClick={() => handleToggleShowActiveVehiclePlot()}
          style={{ cursor: "pointer" }}
        >
          Active Vehicle Plot
        </NavLink>
      </NavItem>
    </>
  )
}
