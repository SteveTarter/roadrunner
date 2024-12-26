import './HomePage.css';
import Map, { MapLayerMouseEvent, useMap } from "react-map-gl";
import { useEffect, useState } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { useAuth0 } from "@auth0/auth0-react";
import { ControlPanel } from './ControlPanel';
import { VehicleIcon } from './VehicleIcon';
import { VehicleDisplay } from '../../models/VehicleDisplay';
import { VehicleState } from '../../models/VehicleState';
import { MapWrapper } from '../Utils/MapWrapper';
import { AppNavBar } from '../NavBar/AppNavBar';

export const HomePage = () => {
    const { getAccessTokenSilently } = useAuth0();

    const { homePageMap } = useMap();

    const [token, setToken] = useState("");

    const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;

    const [vehicleSize, setVehicleSize] = useState(5);
    const [isMapLoaded, setIsMapLoaded] = useState(false);
    const [isDataLoaded, setIsDataLoaded] = useState(false);
    const [isTransitioning, setIsTransitioning] = useState(false);

    const [vehicleStateList, setVehicleStateList] = useState<VehicleState[]>([]);
    const [vehicleDisplayMap, setVehicleDisplayMap] = useState<MapWrapper<string, VehicleDisplay>>();

    // Map styles
    const MAP_STYLE_STREET = "mapbox://styles/mapbox/streets-v12";
    const MAP_STYLE_SATELLITE = "mapbox://styles/mapbox/satellite-streets-v12";
    const [mapStyle, setMapStyle] = useState(MAP_STYLE_STREET);

    const [count, setCount] = useState(0);

    // Vehicle dimensions controls - maybe move to configurables?
    const MIN_SIZE = 5.0;     // Smallest vehicle size
    const MAX_SIZE = 120.0;   // Largest vehicle size
    const MIN_ZOOM = 15.0;      // Zoom level at which vehicle size sticks at minimum
    const MAX_ZOOM = 22.0;      // Zoom level at which vehicle size sticks at maximum

    // Millisecond duration between frame redraws
    const MS_FRAME_TIME = 500;

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
        if (vehicleDisplayMap) {
            return;
        }

        setVehicleDisplayMap(new MapWrapper<string, VehicleDisplay>());
    }, [vehicleDisplayMap]);

    function fetchVehicleStateList() {
        if (!token || token.length === 0) {
            return;
        }
        if (!isMapLoaded) {
            return;
        }

        try {
            // Get the latest VehicleStates
            const restUrlBase = process.env.REACT_APP_ROADRUNNER_REST_URL_BASE!;
            const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-all-vehicle-states`;
            fetch(getStatesUrl, {
                method: 'get',
                headers: {
                    Authorization: `Bearer ${token}`,
                }
            })
                .then(async response => response.json())
                .then(data => {
                    setVehicleStateList(data);
                    setIsDataLoaded(true);
                    data.map((vehicleState: VehicleState) => {
                        if (!vehicleDisplayMap?.get(vehicleState.id)) {
                            let vehicleDisplay = new VehicleDisplay(vehicleSize, false, false);
                            vehicleDisplayMap?.set(vehicleState.id, vehicleDisplay);
                        }
                        return vehicleState;
                    })
                })
                .catch(error => {
                    console.log(`Error caught during fetch in fetchVehicleStateList: ${error.message}`);
                    setIsDataLoaded(false);
                    return <></>;
                });
        }
        catch (error: any) {
            console.log(`Error caught in fetchVehicleStateList: ${error.message}`);
            setIsDataLoaded(false);
        }
    }

    function hideAllRoutes() {
        vehicleStateList.forEach((vehicleState) => {
            let vehicleDisplay = vehicleDisplayMap?.get(vehicleState.id);
            if (vehicleDisplay) {
                vehicleDisplay.routeVisible = false;
            }
        })
    }

    function showAllRoutes() {
        vehicleStateList.forEach((vehicleState) => {
            let vehicleDisplay = vehicleDisplayMap?.get(vehicleState.id);
            if (vehicleDisplay) {
                vehicleDisplay.routeVisible = true;
            }
        })
    }

    function fitAllOnScreen() {
        if (!isDataLoaded) {
            return;
        }

        if (vehicleStateList.length === 0) {
            return;
        }

        let minLongitude = 360.0;
        let minLatitude = 360.0;
        let maxLongitude = -360.0;
        let maxLatitude = -360.0;
        vehicleStateList.forEach((vehicleState) => {
            if (vehicleState.degLatitude < minLatitude) {
                minLatitude = vehicleState.degLatitude;
            }
            if (vehicleState.degLongitude < minLongitude) {
                minLongitude = vehicleState.degLongitude;
            }
            if (vehicleState.degLatitude > maxLatitude) {
                maxLatitude = vehicleState.degLatitude;
            }
            if (vehicleState.degLongitude > maxLongitude) {
                maxLongitude = vehicleState.degLongitude;
            }
        })

        // Expand size by 5% each way;
        let deltaLongitude = Math.max(0.1, maxLongitude - minLongitude);
        let deltaLatitude = Math.max(0.1, maxLatitude - minLatitude);
        minLongitude -= (0.05 * deltaLongitude);
        maxLongitude += (0.05 * deltaLongitude);
        minLatitude -= (0.05 * deltaLatitude);
        maxLatitude += (0.05 * deltaLatitude);

        homePageMap?.fitBounds([[minLongitude, minLatitude], [maxLongitude, maxLatitude]]);
    }

    function toggleMapStyle() {
        setIsTransitioning(true);
        // console.log("Transition started");
        if (mapStyle === MAP_STYLE_STREET) {
            setMapStyle(MAP_STYLE_SATELLITE);
            homePageMap?.getMap().setFog({});
        }
        else {
            setMapStyle(MAP_STYLE_STREET);
        }
        setTimeout(() => {
            // console.log("Transition ended");
            setIsTransitioning(false);
        }, 500);
    }

    function onZoom(viewStateChangeEvent: { viewState: any; }) {
        // console.log("onZoom()");
        let viewState = viewStateChangeEvent.viewState;
        let currentZoom = viewState.zoom;
        let size = MIN_SIZE;
        if (currentZoom < MIN_ZOOM) {
            size = MIN_SIZE;
        }
        else if (currentZoom > MAX_ZOOM) {
            size = MAX_SIZE;
        }
        else {
            size = (MAX_SIZE * Math.pow((2.0 / 3.0), (MAX_ZOOM - currentZoom)));
        }

        if (size < 0) {
            size = MIN_SIZE;
        }

        setVehicleSize(size);
    }

    function onClick(event: MapLayerMouseEvent) {
        console.log("onClick()");
        let bestVehicle: VehicleDisplay | undefined = {} as VehicleDisplay;
        let bestDistance = 100;
        vehicleStateList.forEach((vehicleState) => {
            let point = homePageMap?.project({ lng: vehicleState.degLongitude, lat: vehicleState.degLatitude });

            // Calculate the distance of the click from the vehicle
            if (point) {
                let xDelta = point.x - event.point.x;
                let yDelta = point.y - event.point.y;
                let distance = Math.sqrt(xDelta * xDelta + yDelta * yDelta);

                // If the distance is less than 50, then toggle visibility
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestVehicle = vehicleDisplayMap?.get(vehicleState.id);
                }
            }
        })
        if (bestVehicle) {
            bestVehicle.routeVisible = !bestVehicle.routeVisible;
            bestVehicle.popupVisible = !bestVehicle.popupVisible;
        }
    }

    function onLoad() {
        setIsMapLoaded(true);
        console.log("onLoad()");
    }

    useEffect(() => {
        const timer = setTimeout(() => {
            try {
                fetchVehicleStateList();
                setCount(count + 1);    // Update count to trigger effect again
            }
            catch (error: any) {
                console.log(`Error caught in fetchVehicleStateList: ${error.message}`);
                setIsDataLoaded(false);
            }
        }, MS_FRAME_TIME)
        return () => clearTimeout(timer);
        // eslint-disable-next-line        
    }, [count]);

    return (
        <>
            <div className="body row scroll-y">
                <Map
                    id="homePageMap"
                    mapStyle={mapStyle}
                    mapboxAccessToken={mapboxToken}
                    onLoad={() => onLoad()}
                    fog={{}}
                    initialViewState={{
                        longitude: -97.5,
                        latitude: 32.75,
                        zoom: 10,
                    }}
                    onClick={(event) => onClick(event)}
                    onZoom={(viewStateChangeEvent) => onZoom(viewStateChangeEvent)}
                >
                    <AppNavBar />
                    {(isDataLoaded && !isTransitioning) ?
                        <>
                            <ControlPanel
                                vehicleStateList={vehicleStateList}
                                hideAllRoutes={hideAllRoutes}
                                showAllRoutes={showAllRoutes}
                                fitAllOnScreen={fitAllOnScreen}
                                toggleMapStyle={toggleMapStyle}
                            />
                            {vehicleStateList && (vehicleStateList.length > 0) && (
                                // eslint-disable-next-line
                                vehicleStateList.map((vehicleState) => {
                                    let vehicleDisplay = vehicleDisplayMap?.get(vehicleState.id);
                                    if (vehicleDisplay) {
                                        vehicleDisplay.size = vehicleSize;
                                        return <VehicleIcon
                                            key={vehicleState.id}
                                            vehicleState={vehicleState}
                                            vehicleDisplay={vehicleDisplay} />
                                    }
                                })
                            )}
                        </>
                        :
                        <div>
                            <SpinnerLoading />
                        </div>
                    }
                </Map>
            </div>
        </>
    )
}
