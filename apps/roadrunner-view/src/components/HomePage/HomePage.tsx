import './HomePage.css';
import Map from "react-map-gl";
import { useEffect, useState } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { useAuth0 } from "@auth0/auth0-react";
import { VehicleState } from '../../models/VehicleState';
import { ControlPanel } from './ControlPanel';
import { VehicleIcon } from './VehicleIcon';

export const HomePage = () => {
    const { getAccessTokenSilently } = useAuth0();

    const [token, setToken] = useState("");

    const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;
    const mapboxMapStyle = process.env.REACT_APP_MAPBOX_MAP_STYLE!;

    const [isMapLoaded, setIsMapLoaded] = useState(false);
    const [isDataLoaded, setIsDataLoaded] = useState(false);
    const [vehicleStateList, setVehicleStateList] = useState<VehicleState[]>([]);

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
                });

        }
        catch (error: any) {
            console.log(`Error caught in fetchAppSettings: ${error.message}`);
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
            >
                {isDataLoaded ?
                    <ControlPanel 
                        vehicleStateList={vehicleStateList}
                    />
                    :
                    <div>
                        <SpinnerLoading />
                    </div>
                }
                {vehicleStateList && (vehicleStateList.length > 0) && (
                    vehicleStateList.map(function(vehicleState){
                        return <VehicleIcon vehicleState={vehicleState}/>
                    })
                )}
            </Map>
        </div>
    )
}
