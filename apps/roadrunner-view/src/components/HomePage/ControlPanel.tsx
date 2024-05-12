import { VehicleState } from "../../models/VehicleState";

export const ControlPanel: React.FC<{
    vehicleStateList: VehicleState[]
}> = (props) => {
    return (
        <div className="control-panel">
          <h3>Roadrunner Viewer</h3>
          <p>View active vehicles running in Roadrunner Server.</p>
          <p/>
          <p>Count of active vehicles is: {props.vehicleStateList.length}</p>
        </div>
      );
}
