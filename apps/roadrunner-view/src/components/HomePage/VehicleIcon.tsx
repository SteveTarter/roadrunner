import { VehicleState } from "../../models/VehicleState";
import { Layer, Source } from "react-map-gl";
import type { LayerProps } from "react-map-gl";
import type { FeatureCollection } from "geojson";

export const VehicleIcon: React.FC<{
    vehicleState: VehicleState, circleRadius: number
}> = (props) => {

    function getVehicleLayer(id: string, colorCode: string, circleRadius: number): LayerProps {
        return {
            id: `point-${id}`,
            type: 'circle',
            paint: {
                'circle-radius': circleRadius,
                'circle-color': `${colorCode}`
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
    let pointLayer = getVehicleLayer(props.vehicleState.id, props.vehicleState.colorCode, props.circleRadius);

    return (
        <>
            <Source type='geojson'  data={pointData}>
                <Layer {...pointLayer} />
            </Source>
        </>
      );
}
