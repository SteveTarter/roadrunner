import { VehicleState } from "../../models/VehicleState";
import { Layer, Source } from "react-map-gl";
import type { LayerProps } from "react-map-gl";
import type { FeatureCollection } from "geojson";

export const VehicleIcon: React.FC<{
    vehicleState: VehicleState
}> = (props) => {

    function getVehicleLayer(id: string): LayerProps {
        return {
            id: `point-${id}`,
            type: 'circle',
            paint: {
                'circle-radius': 10,
                'circle-color': '#007cbf'
            }
        };
    }
    
    function getVehiclePoint(latitude: number, longitude: number): FeatureCollection {
        return {
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature', geometry: { type: 'Point', coordinates: [longitude, latitude] },
                    properties: null
                }
            ]
        };
    }

    let pointData = getVehiclePoint(props.vehicleState.degLatitude, props.vehicleState.degLongitude);
    let pointLayer = getVehicleLayer(props.vehicleState.id);

    return (
        <>
            <Source type='geojson'  data={pointData}>
                <Layer {...pointLayer} />
            </Source>
        </>
      );
}
