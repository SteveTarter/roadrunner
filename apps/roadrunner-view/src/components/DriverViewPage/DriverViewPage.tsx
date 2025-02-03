import './DriverViewPage.css';
import { useAuth0 } from "@auth0/auth0-react";
import { useCallback, useEffect, useState } from "react";
import Map, { FullscreenControl, useMap } from "react-map-gl";
import { VehicleState } from "../../models/VehicleState";
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { Button, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSatellite, faHome, faMap } from '@fortawesome/free-solid-svg-icons';
import { ViewControl } from './ViewControl';

export const DriverViewPage = () => {
  // Get the Vehicle ID from the URL in the window
  const vehicleId = (window.location.pathname).split('/')[2];
  const { getAccessTokenSilently } = useAuth0();
  const navigate = useNavigate();
  const { driverViewPageMap } = useMap();
  const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;

  const [token, setToken] = useState("");
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [vehicleState, setVehicleState] = useState<VehicleState>();
  const [managerHost, setManagerHost] = useState("");

  // Driver view offset from straight ahead
  const [degViewOffset, setDegViewOffset] = useState(0);

  // Map style
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_SATELLITE);

  // Conversion from meters per second to miles per hour.
  const MPS_TO_MPH = 2.236936;

  // Millisecond duration between frame redraws.
  const MS_FRAME_TIME = 100;

  // Millisecond delay before executing load code.
  const MS_LOAD_DELAY_TIME = 500;

  useEffect(() => {
    if (!token) {
      const audience = process.env.REACT_APP_AUTH0_AUDIENCE;
      getAccessTokenSilently({ authorizationParams: { audience } })
        .then(setToken)
        .catch(error => console.error("Error fetching token:", error));
    }
  }, [token, getAccessTokenSilently]);

  const gotoHomePage = useCallback(() => {
    navigate('/home');
  }, [navigate]);

  const getCoordinateAtBearingAndRange = useCallback((degLatitude: number, degLongitude: number, degBearing: number, mRange: number) => {
    const KM_EARTH_RADIUS = 6378.14;

    const radLatitude = degLatitude / 180.0 * Math.PI;
    const radLongitude = degLongitude / 180.0 * Math.PI;
    const radBearing = degBearing / 180.0 * Math.PI;
    const kmRange = mRange / 1000.0;

    const radLatitudeDest = Math.asin(
      Math.sin(radLatitude) * Math.cos(kmRange / KM_EARTH_RADIUS) +
      Math.cos(radLatitude) * Math.sin(kmRange / KM_EARTH_RADIUS) * Math.cos(radBearing)
    );
    const radLongitudeDest = radLongitude + Math.atan2(
      Math.sin(radBearing) * Math.sin(kmRange / KM_EARTH_RADIUS) * Math.cos(radLatitude),
      Math.cos(kmRange / KM_EARTH_RADIUS) - Math.sin(radLatitude) * Math.sin(radLatitude)
    );
    const degLatitudeDest = radLatitudeDest / Math.PI * 180.0;
    const degLongitudeDest = radLongitudeDest / Math.PI * 180.0;

    return { lng: degLongitudeDest, lat: degLatitudeDest };
  }, []);

  const updateMapView = useCallback((data: VehicleState) => {
    const degViewBearing = data.degBearing + degViewOffset;
    driverViewPageMap?.setBearing(degViewBearing);
    const mRange = 20.0 * window.innerHeight / 932.0;
    const shiftedPoint = getCoordinateAtBearingAndRange(data.degLatitude, data.degLongitude, degViewBearing, mRange);
    driverViewPageMap?.setCenter(shiftedPoint);
  }, [degViewOffset, driverViewPageMap, getCoordinateAtBearingAndRange]);

  const fetchVehicleState = useCallback(() => {
    if (!token) return;

    // Get the latest VehicleState
    const restUrlBase = process.env.REACT_APP_ROADRUNNER_REST_URL_BASE!;
    const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-vehicle-state/${vehicleId}`;
    fetch(getStatesUrl, {
      method: 'get',
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(response => response.json())
      .then((data: VehicleState) => {
        setVehicleState(data);
        setIsDataLoaded(true);

        // Set the manager host, shortening it if from a kubernetes pod.
        const host: string = data.managerHost;
        const lastDashIndex = host.lastIndexOf('-');
        setManagerHost(lastDashIndex >= 0 ? host.substring(lastDashIndex + 1) : host);

        updateMapView(data);
      })
      .catch(error => {
        console.error(`Error during fetchVehicleState: ${error.message}`);
        setIsDataLoaded(false);
        gotoHomePage();
      });
  }, [token, vehicleId, gotoHomePage, updateMapView]);

  useEffect(() => {
    const intervalId = setInterval(() => {
      fetchVehicleState();
    }, MS_FRAME_TIME);
    return () => clearInterval(intervalId);
  }, [fetchVehicleState]);

  const toggleMapStyle = useCallback(() => {
    setIsTransitioning(true);
    setMapStyle(prevStyle => (prevStyle === MAP_STYLE_STREET ? MAP_STYLE_SATELLITE : MAP_STYLE_STREET));
    setTimeout(() => setIsTransitioning(false), 500);
  }, []);

  const onMapLoad = useCallback(() => {
    const timer = setTimeout(() => {
      console.log("onMapLoad()");
      const map = driverViewPageMap?.getMap();
      const layers = map?.getStyle()?.layers;
      if (layers) {
        for (const layer of layers) {
          if (layer.type === 'symbol' && layer.layout && layer.layout['text-field']) {
            driverViewPageMap?.getMap().removeLayer(layer.id);
          }
        }
      }

      map?.setFog({
        range: [19, 20],
        'horizon-blend': 0.3,
        color: 'white',
        'high-color': '#add8e6',
        'space-color': '#d8f2ff',
        'star-intensity': 0.0
      });

      map?.addSource('mapbox-dem', {
        type: 'raster-dem',
        url: 'mapbox://mapbox.mapbox-terrain-dem-v1',
        tileSize: 512,
        maxzoom: 14
      });
      map?.setTerrain({ source: 'mapbox-dem', exaggeration: 1.0 });
    }, MS_LOAD_DELAY_TIME);
    return () => clearTimeout(timer);
  }, [driverViewPageMap]);

  return (
    <div className="body row scroll-y">
      {(isDataLoaded && !isTransitioning && vehicleState) ? (
          <Map
            id="driverViewPageMap"
            mapStyle={mapStyle}
            mapboxAccessToken={mapboxToken}
            initialViewState={{
              zoom: 21,
              pitch: 84,
            }}
            pitch={80}
            zoom={22}
            minZoom={19}
            maxZoom={24}
            onLoad={onMapLoad}
          >
            <div style={{ position: "fixed", top: 10, left: 10 }}>
              <Button onClick={gotoHomePage}>
                <FontAwesomeIcon icon={faHome} className="mr-3" />
              </Button>
            </div>
            <div style={{ position: "fixed", top: 10, left: 60 }}>
              <Button onClick={toggleMapStyle}>
                {(mapStyle === MAP_STYLE_STREET) ?
                  <>
                    <FontAwesomeIcon title="Satellte Display" icon={faSatellite} className="mr-3" />
                  </>
                  :
                  <FontAwesomeIcon title="Map Display" icon={faMap} className="mr-3" />
                }
              </Button>
            </div>
            <div style={{ position: "fixed", bottom: 0, left: "50%", transform: "translateX(-50%)", width: "10rem" }}>

              <Card style={{ width: '10rem', alignSelf: 'end', textAlign: 'center' }}>
                <Card.Body>
                  <ViewControl degViewOffset={degViewOffset} setDegViewOffset={setDegViewOffset} />
                  <Card.Text style={{ fontSize: '1.1rem' }}>
                    <br />
                    {`${(Math.round(MPS_TO_MPH * vehicleState.metersPerSecond * 10) / 10)
                      .toFixed(1)
                      .padStart(4, ' ')}`} MPH<br />
                    {`${(Math.round(vehicleState.degBearing * 10) / 10)
                      .toFixed(1)
                      .padStart(5, ' ')}`}&deg;<br />
                    Host: {managerHost}<br />
                    {(vehicleState.nsLastExec / 1000000.0)
                      .toFixed(3)} ms<br />
                  </Card.Text>
                </Card.Body>
              </Card>
            </div>
            <FullscreenControl />
          </Map>
      ) : (
          <SpinnerLoading />
      )}
    </div>
  )

}
