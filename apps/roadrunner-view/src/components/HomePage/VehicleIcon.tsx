import { VehicleState } from "../../models/VehicleState";
import { Layer, Source, useMap } from "react-map-gl";
import type { LayerProps } from "react-map-gl";
import type { Feature, FeatureCollection } from "geojson";
import { useEffect, useState } from "react";
import { useAuth0 } from "@auth0/auth0-react";

export const VehicleIcon: React.FC<{
    vehicleState: VehicleState, circleRadius: number
}> = (props) => {
    const { getAccessTokenSilently } = useAuth0();
    const { current: map } = useMap();

    const [token, setToken] = useState("");
    const [lineVisible, setLineVisible] = useState(false);
    const [directionsGeometry, setDirectionsGeometry] = useState([]);

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

    useEffect(() => {

        function fetchVehicleDirections() {
            if (!token || token.length === 0) {
                return;
            }

            try {
                // Get the latest VehicleStates
                const restUrlBase = process.env.REACT_APP_ROADRUNNER_REST_URL_BASE!;
                const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-vehicle-directions/${props.vehicleState.id}`;
                fetch(getStatesUrl, {
                    method: 'get',
                    headers: {
                        Authorization: `Bearer ${token}`,
                    }
                })
                    .then(async response => response.json())
                    .then(data => {
                        let dg: any = [];
                        for (let i = 0; i < data.routes[0].legs[0].steps.length; ++i) {
                            dg.push(data.routes[0].legs[0].steps[i].geometry.coordinates);
                        }

                        setDirectionsGeometry(dg);
                    });

            }
            catch (error: any) {
                console.log(`Error caught in fetchVehicleDirections: ${error.message}`);
            }
        };
        fetchVehicleDirections();
    }, [props.vehicleState.id, token, getAccessTokenSilently]);

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

    function getLineLayer(id: string, colorCode: string, circleRadius: number): LayerProps {
        return {
            id: `line-${id}`,
            type: 'line',
            paint: {
                'line-width': circleRadius / 2.0,
                'line-color': `${colorCode}`
            }
        };
    }

    function getLineData(id: string, directionsGeometry: any): Feature {
        return {
            type: 'Feature',
            properties: {
                title: `line-${id}`
            },
            geometry: {
                type: 'MultiLineString',
                coordinates: directionsGeometry
            }
        };
    }

    let pointData = getVehiclePoint(props.vehicleState.degLatitude, props.vehicleState.degLongitude);
    let pointLayer = getVehicleLayer(props.vehicleState.id, props.vehicleState.colorCode, props.circleRadius);
    let lineData = getLineData(props.vehicleState.id, directionsGeometry);
    let lineLayer = getLineLayer(props.vehicleState.id, props.vehicleState.colorCode, props.circleRadius);

    // Toggle the route visibility if user clicks on the vehicle. 
    map?.on('click', (event) => {
        // Determine the screen position of the vehicle
        let point = map.project({ lng: props.vehicleState.degLongitude, lat: props.vehicleState.degLatitude });

        // Calculate the distance of the click from the vehicle
        let xDelta = point.x - event.point.x;
        let yDelta = point.y - event.point.y;
        let distance = Math.sqrt(xDelta * xDelta + yDelta * yDelta);

        // If the distance is less than 25, then toggle visibility
        if (distance < 25.0) {
            setLineVisible(!lineVisible);
        }
    })

    return (
        <>
            <Source type='geojson' data={pointData}>
                <Layer {...pointLayer} />
            </Source>
            {directionsGeometry && lineVisible ?
                <Source type='geojson' data={lineData}>
                    <Layer {...lineLayer} />
                </Source>
                :
                <></>
            }
        </>
    );
}
