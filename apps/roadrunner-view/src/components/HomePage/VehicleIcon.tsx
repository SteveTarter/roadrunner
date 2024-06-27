import { Layer, Popup, Source } from "react-map-gl";
import type { LayerProps } from "react-map-gl";
import type { Feature, FeatureCollection } from "geojson";
import { useEffect, useState } from "react";
import { useAuth0 } from "@auth0/auth0-react";
import { VehicleDisplay } from "../../models/VehicleDisplay";
import { VehicleState } from "../../models/VehicleState";
import { Card } from "react-bootstrap";

export const VehicleIcon: React.FC<{
    vehicleState: VehicleState,
    vehicleDisplay: VehicleDisplay
}> = (props) => {
    const { getAccessTokenSilently } = useAuth0();

    const [token, setToken] = useState("");
    const [directionsGeometry, setDirectionsGeometry] = useState([]);

    const MPS_TO_MPH = 2.236936;

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
                'line-color': `${colorCode}`,
                'line-opacity': 0.5
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
    let pointLayer = getVehicleLayer(props.vehicleState.id, props.vehicleState.colorCode, props.vehicleDisplay.circleRadius);
    let lineData = getLineData(props.vehicleState.id, directionsGeometry);
    let lineLayer = getLineLayer(props.vehicleState.id, props.vehicleState.colorCode, props.vehicleDisplay.circleRadius);
    let lineVisible = props.vehicleDisplay.routeVisible;
    let popupVisible = props.vehicleDisplay.popupVisible;

    return (
        <>
            <Source type='geojson' data={pointData}>
                <Layer {...pointLayer} />
            </Source>
            {directionsGeometry && lineVisible && (
                <Source type='geojson' data={lineData}>
                    <Layer {...lineLayer} />
                </Source>
            )}
            {popupVisible && (
                <Popup
                    longitude={props.vehicleState.degLongitude}
                    latitude={props.vehicleState.degLatitude}
                    anchor="bottom-left">
                    <Card.Body>
                        <Card.Text>
                        Speed: {(Math.round(MPS_TO_MPH * props.vehicleState.metersPerSecond * 10) / 10).toFixed(1)} MPH
                        </Card.Text>
                  </Card.Body>
                </Popup>
            )}
        </>
    );
}
