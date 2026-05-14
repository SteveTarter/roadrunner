import { DropdownItem, DropdownMenu, DropdownToggle, UncontrolledDropdown } from "reactstrap";

export const ManageMenu = (props: {
  openCreateVehicle: any,
  openCrissCross: any,
  toggleSimTable: any,
  toggleShowActiveVehiclePlot: any
}) => {

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
