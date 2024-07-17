import './DriverViewPage.css';
import { useAuth0 } from "@auth0/auth0-react";
import { useCallback, useEffect, useState } from "react";
import Map, { useMap } from "react-map-gl";
import { VehicleState } from "../../models/VehicleState";
import { SpinnerLoading } from "../Utils/SpinnerLoading";

export const DriverViewPage = () => {
    // Get the Vehicle ID from the URL in the window
    const vehicleId = (window.location.pathname).split('/')[2];

    const { getAccessTokenSilently } = useAuth0();

    const [token, setToken] = useState("");

    const { driverViewPageMap } = useMap();

    const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;

    const [isDataLoaded, setIsDataLoaded] = useState(false);

    const [vehicleState, setVehicleState] = useState<VehicleState>();

    // Map styles
    const MAP_STYLE_LITE = "mapbox://styles/mapbox/light-v11";

    const [count, setCount] = useState(0);

    // Millisecond duration between frame redraws.
    const MS_FRAME_TIME = 100;

    // Millisecond delay before executing load code.
    const MS_LOAD_DELAY_TIME = 500;

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

    function fetchVehicleState() {
        if (!token || token.length === 0) {
            return;
        }

        try {
            // Get the latest VehicleState
            const restUrlBase = process.env.REACT_APP_ROADRUNNER_REST_URL_BASE!;
            const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-vehicle-state/${vehicleId}`;
            fetch(getStatesUrl, {
                method: 'get',
                headers: {
                    Authorization: `Bearer ${token}`,
                }
            })
                .then(async response => response.json())
                .then(data => {
                    setVehicleState(data);
                    setIsDataLoaded(true);
                    driverViewPageMap?.setBearing(data.degBearing);
                    driverViewPageMap?.setCenter({ lng: data.degLongitude, lat: data.degLatitude });
                    return data;
                })
                .catch(error => {
                    console.log(`Error caught during fetch in fetchVehicleState: ${error.message}`);
                    setIsDataLoaded(false);
                    return <></>;
                });
        }
        catch (error: any) {
            console.log(`Error caught in fetchVehicleState: ${error.message}`);
            setIsDataLoaded(false);
        }
    }

    useEffect(() => {
        const timer = setTimeout(() => {
            try {
                fetchVehicleState();
                setCount(count + 1);    // Update count to trigger effect again
            }
            catch (error: any) {
                console.log(`Error caught in fetchVehicleState: ${error.message}`);
                setIsDataLoaded(false);
            }
        }, MS_FRAME_TIME)
        return () => clearTimeout(timer);
        // eslint-disable-next-line
    }, [count]);

    const onMapLoad = useCallback(() => {
        const timer = setTimeout(() => {
            console.log("onMapLoad()");
            const layers = driverViewPageMap?.getStyle().layers;
            if (layers) {
                for (const layer of layers) {
                    if (layer.type === 'symbol' && layer.layout && layer.layout['text-field']) {
                        driverViewPageMap?.getMap().removeLayer(layer.id);
                    }
                }
            }

            driverViewPageMap?.getMap().addLayer({
                id: '3d-buildings',
                source: 'composite',
                'source-layer': 'building',
                filter: ['==', 'extrude', 'true'],
                type: 'fill-extrusion',
                minzoom: 15,
                paint: {
                    'fill-extrusion-color': '#aaa',
                    'fill-extrusion-height': [
                        'interpolate',
                        ['linear'],
                        ['zoom'],
                        15,
                        0,
                        15.05,
                        ['get', 'height']
                    ],
                    'fill-extrusion-base': [
                        'interpolate',
                        ['linear'],
                        ['zoom'],
                        15,
                        0,
                        15.05,
                        ['get', 'min_height']
                    ],
                    'fill-extrusion-opacity': 0.8
                }
            });
        }, MS_LOAD_DELAY_TIME);
        return () => clearTimeout(timer);
    }, [driverViewPageMap]);

    return (
        <div className="body row scroll-y">
            {(isDataLoaded && vehicleState) ?
                <>
                    <Map
                        id="driverViewPageMap"
                        mapStyle={MAP_STYLE_LITE}
                        mapboxAccessToken={mapboxToken}
                        fog={{}}
                        initialViewState={{
                            zoom: 21,
                            pitch: 85,
                        }}
                        onLoad={onMapLoad}
                    >
                    </Map>
                </>
                :
                <div>
                    <SpinnerLoading />
                </div>
            }
        </div>
    )

}
