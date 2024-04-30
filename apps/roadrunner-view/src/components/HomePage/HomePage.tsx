import './HomePage.css';
import Map, { useMap } from "react-map-gl";
import { useEffect, useState } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { useAuth0 } from "@auth0/auth0-react";
import { Feature } from "geojson";
import mapboxgl from "mapbox-gl";

export const HomePage = () => {
    const { getAccessTokenSilently } = useAuth0();

    const [token, setToken] = useState("");

    const { homePageMap } = useMap();

    const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;
    const mapboxMapStyle = process.env.REACT_APP_MAPBOX_MAP_STYLE!;

    const [isMapLoaded, setIsMapLoaded] = useState(false);
    const [isDataLoaded, setIsDataLoaded] = useState(false);

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
                {!isDataLoaded ?
                    <div>
                        <SpinnerLoading />
                    </div>
                    :
                    <></>
                }
            </Map>
        </div>
    )
}
