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

export const HomePage = () => {
    const { getAccessTokenSilently } = useAuth0();
    const { homePageMap } = useMap();

    const [token, setToken] = useState("");

    const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;
    const mapboxMapStyle = process.env.REACT_APP_MAPBOX_MAP_STYLE!;

    const [circleRadius, setCircleRadius] = useState(5);
    const [isMapLoaded, setIsMapLoaded] = useState(false);
    const [isDataLoaded, setIsDataLoaded] = useState(false);
    const [vehicleStateList, setVehicleStateList] = useState<VehicleState[]>([]);
    const [vehicleDisplayMap, setVehicleDisplayMap] = useState<MapWrapper<string,VehicleDisplay>>();

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
        if(vehicleDisplayMap) {
            return;
        }

        setVehicleDisplayMap(new MapWrapper<string,VehicleDisplay>());
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
                        if(!vehicleDisplayMap?.get(vehicleState.id)) {
                            let vehicleDisplay = new VehicleDisplay(circleRadius, false);
                            vehicleDisplayMap?.set(vehicleState.id,vehicleDisplay);
                        }
                        return vehicleState;
                    })
                });

        }
        catch (error: any) {
            console.log(`Error caught in fetchVehicleStateList: ${error.message}`);
        }
    }

    function onZoom(viewStateChangeEvent: { viewState: any; }) {
        let MIN_RADIUS = 5.0;
        let MAX_RADIUS = 120.0;
        let MIN_ZOOM = 15.0;
        let MAX_ZOOM = 22.0;
        let viewState = viewStateChangeEvent.viewState;
        let currentZoom = viewState.zoom;
        let radius = MIN_RADIUS;
        if (currentZoom < MIN_ZOOM) {
            radius = MIN_RADIUS;
        }
        else if (currentZoom > MAX_ZOOM) {
            radius = MAX_RADIUS;
        }
        else {
            radius = (MAX_RADIUS * Math.pow((2.0 / 3.0), (MAX_ZOOM - currentZoom)));
        }

        if(radius < 0) {
            radius = MIN_RADIUS;
        }

        // console.log(`Zoom is ${currentZoom}; Setting circle radius to ${radius}`)
        setCircleRadius(radius);
    }

    function onClick(event: MapLayerMouseEvent) {
        console.log(`X = ${event.point.x} Y = ${event.point.y}`);
        let bestVehicle: VehicleDisplay|undefined = {} as VehicleDisplay;
        let bestDistance = 100;
        vehicleStateList.forEach( (vehicleState) => {
            let point = homePageMap?.project({ lng: vehicleState.degLongitude, lat: vehicleState.degLatitude });

            // Calculate the distance of the click from the vehicle
            if(point) {
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
        if(bestVehicle) {
            bestVehicle.routeVisible = !bestVehicle.routeVisible;
        }
}

    useEffect(() => {
        const animation = window.requestAnimationFrame(() =>
            fetchVehicleStateList()
        );
        return () => window.cancelAnimationFrame(animation);
    });

    return (
        <div className="body row scroll-y">
            <Map
                id="homePageMap"
                mapStyle={mapboxMapStyle}
                mapboxAccessToken={mapboxToken}
                onLoad={() => setIsMapLoaded(true)}
                initialViewState={{
                    longitude: -97.5,
                    latitude: 32.75,
                    zoom: 10,
                }}
                onClick={(event) => onClick(event)}
                onZoom={(viewStateChangeEvent) => onZoom(viewStateChangeEvent)}
            >
                {isDataLoaded ?
                    <>
                        <ControlPanel
                            vehicleStateList={vehicleStateList}
                        />
                        {vehicleStateList && (vehicleStateList.length > 0) && (
                            vehicleStateList.map(function (vehicleState) {
                                let vehicleDisplay = vehicleDisplayMap?.get(vehicleState.id);
                                if (!vehicleDisplay) return;
                                vehicleDisplay.circleRadius = circleRadius;
                                return <VehicleIcon
                                    key={vehicleState.id}
                                    vehicleState={vehicleState}
                                    vehicleDisplay={vehicleDisplay} />
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
    )
}
