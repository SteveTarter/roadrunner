import './DriverViewPage.css';
import { useAuth0 } from "@auth0/auth0-react";
import { useCallback, useEffect, useState } from "react";
import Map, { useMap } from "react-map-gl";
import { VehicleState } from "../../models/VehicleState";
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import mapboxgl from 'mapbox-gl';

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
    // const MAP_STYLE_LITE = "mapbox://styles/mapbox/light-v11";
    const MAP_STYLE_SATELLITE = "mapbox://styles/mapbox/satellite-streets-v12";

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

    /*
    function updateCameraPosition(position: mapboxgl.LngLatLike, altitude: number, pitch: number, bearing: number) {
        const camera = driverViewPageMap?.getMap().getFreeCameraOptions();

        if (camera) {
            camera.setPitchBearing(pitch, bearing);
            camera.position = mapboxgl.MercatorCoordinate.fromLngLat(
                position,
                altitude
            );

            driverViewPageMap?.getMap().setFreeCameraOptions(camera);
        }
    }
    */

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
                    /*
                    let position: mapboxgl.LngLatLike = {lat: data.degLatitude, lng: data.degLongitude};
                    let altitude: number = 5.0;
                    let pitch: number = 85.0;
                    let bearing: number = data.degBearing;
                    updateCameraPosition(position, altitude, pitch, bearing);
                    */
                    driverViewPageMap?.setBearing(data.degBearing);
                    let shiftedPoint = getCoordinateAtBearingAndRange(data.degLatitude, data.degLongitude, data.degBearing, 25.0);
                    driverViewPageMap?.setCenter(shiftedPoint);
                    let freeCameraOptions = driverViewPageMap?.getMap().getFreeCameraOptions();
                    if (freeCameraOptions) {
                        const position = { lng: data.degLongitude, lat: data.degLatitude };
                        const altitude = 3.0;
                        let newPosition = mapboxgl.MercatorCoordinate.fromLngLat(position, altitude);
                        //console.log(`oldPosition = ${freeCameraOptions.position?.x}, ${freeCameraOptions.position?.y}, ${freeCameraOptions.position?.z}\nnewPosition = ${newPosition.x}, ${newPosition.y}, ${newPosition.z}`);
                        freeCameraOptions.position = newPosition;
                    }

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

    function getCoordinateAtBearingAndRange(degLatitude: number, degLongitude: number, degBearing: number, mRange: number) {
        const KM_EARTH_RADIUS = 6378.14;

        let radLatitude = degLatitude / 180.0 * Math.PI;
        let radLongitude = degLongitude / 180.0 * Math.PI;
        let radBearing = degBearing / 180.0 * Math.PI;
        let kmRange = mRange / 1000.0;

        let radLatitudeDest = Math.asin(Math.sin(radLatitude) * Math.cos(kmRange / KM_EARTH_RADIUS) + Math.cos(radLatitude) * Math.sin(kmRange / KM_EARTH_RADIUS) * Math.cos(radBearing));
        let radLongitudeDest = radLongitude + Math.atan2(Math.sin(radBearing) * Math.sin(kmRange / KM_EARTH_RADIUS) * Math.cos(radLatitude), Math.cos(kmRange / KM_EARTH_RADIUS) - Math.sin(radLatitude) * Math.sin(radLatitude));
        let degLatitudeDest = radLatitudeDest / Math.PI * 180.0;
        let degLongitudeDest = radLongitudeDest / Math.PI * 180.0;

        return { lng: degLongitudeDest, lat: degLatitudeDest };
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
            driverViewPageMap?.getMap().addSource('mapbox-dem', {
                type: 'raster-dem',
                url: 'mapbox://mapbox.mapbox-terrain-dem-v1',
                tileSize: 512,
                maxzoom: 14
            });
            driverViewPageMap?.getMap().setTerrain({ source: 'mapbox-dem', exaggeration: 1.0 });
        }, MS_LOAD_DELAY_TIME);
        return () => clearTimeout(timer);
    }, [driverViewPageMap]);

    return (
        <div className="body row scroll-y">
            {(isDataLoaded && vehicleState) ?
                <>
                    <Map
                        id="driverViewPageMap"
                        mapStyle={MAP_STYLE_SATELLITE}
                        mapboxAccessToken={mapboxToken}
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
