import './DriverViewPage.css';
import { useCallback, useEffect, useMemo, useState } from "react";
import Map, { FullscreenControl, useMap } from "react-map-gl";
import { VehicleState } from "../../models/VehicleState";
import { ActiveVehiclePlot } from "../Shared/ActiveVehiclePlot";
import { PlaybackClock } from '../Utils/PlaybackClock';
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { Button, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSatellite, faHome, faMap, faMagic, faBars, faChartLine } from '@fortawesome/free-solid-svg-icons';
import { ViewControl } from './ViewControl';
import { CONFIG } from "../../config";
import { useVehicleData } from '../../hooks/useVehicleData';
import { usePlayback } from "../../context/PlaybackContext";
import { useMapViewState } from '../../context/MapViewStateContext';

export const DriverViewPage = () => {
  // Get the Vehicle ID from the URL in the window
  const vehicleId = (window.location.pathname).split('/')[2];

  const navigate = useNavigate();
  const { driverViewPageMap } = useMap();
  const mapboxToken = CONFIG.MAPBOX_TOKEN;

  // Constants
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";
  const MPS_TO_MPH = 2.236936;

  // States
  const [lastState, setLastState] = useState<VehicleState | null>(null);
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_SATELLITE);
  const [isMapReady, setIsMapReady] = useState(false);
  const [assetsLoaded, setAssetsLoaded] = useState(false);
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);
  const [goingHome, setGoingHome] = useState(false);

  // Driver view offset from straight ahead
  const [degViewOffset, setDegViewOffset] = useState(0);

  const { playbackOffset } = usePlayback();

  const {
    homeMapViewState,
    setHomeMapViewState
  } = useMapViewState();


  // Integrated Hook
  // Driver view usually wants a static size (e.g., 20) for calculation logic
  const {
    vehicleStateMap,
    isDataLoaded,
    setIsInterpolationEnabled,
    isInterpolationEnabled,
    version
  } = useVehicleData({
    vehicleSize: 20,
    intervalMs: 50
  });

  // Logic to find current vehicle
  const vehicleState = useMemo(() => {
    return vehicleStateMap.get(vehicleId) || lastState;
  }, [vehicleId, vehicleStateMap, lastState]);

  const gotoHomePage = useCallback(() => {
    if(goingHome || !vehicleState) return;
    setGoingHome(true);

    setHomeMapViewState({
      ...homeMapViewState,
      longitude: vehicleState.degLongitude,
      latitude: vehicleState.degLatitude
    });

    navigate('/home');
  }, [navigate, vehicleState, homeMapViewState, setHomeMapViewState, goingHome]);

  // Handle Auto-Redirects as a Side Effect
  useEffect(() => {
    const msCurrentTime = Date.now() - playbackOffset;

    // Case 1: Data has gone stale
    if (lastState && (lastState.msEpochLastRun < msCurrentTime - (30 * 1000))) {
      gotoHomePage();
    }

    // Case 2: Explicit update for last state
    const currentState = vehicleStateMap.get(vehicleId);
    if (currentState && (lastState?.msEpochLastRun !== currentState.msEpochLastRun)) {
      setLastState(currentState);
    }
  }, [vehicleStateMap, vehicleId, lastState, playbackOffset, gotoHomePage]);

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
    const map = driverViewPageMap?.getMap();
    if (!data || !map || !isMapReady || !assetsLoaded) return;

    // Check for timeouts
    const MS_VEHICLE_TIMEOUT = 30 * 1000;
    const msCurrentTime = Date.now() - playbackOffset;
     if ((msCurrentTime - data.msEpochLastRun) >= MS_VEHICLE_TIMEOUT) {
      gotoHomePage();
    }

    // Warping to (0, 0) signals loss of data feed
    if(data.degLatitude === 0 && data.degLongitude === 0) {
      gotoHomePage();
    }

    const degViewBearing = data.degBearing + degViewOffset;
    driverViewPageMap?.setBearing(degViewBearing);

    const mRange = 20.0 * window.innerHeight / 932.0;
    const shiftedPoint = getCoordinateAtBearingAndRange(
      data.degLatitude,
      data.degLongitude,
      degViewBearing,
      mRange
    );

    // Ensure we aren't sending NaN or [0,0] to Mapbox
    if (shiftedPoint && !isNaN(shiftedPoint.lng) && !isNaN(shiftedPoint.lat)) {
      map.jumpTo({
        center: [shiftedPoint.lng, shiftedPoint.lat],
        bearing: degViewBearing,
        //animate: false // Critical for high-frequency updates
      })
    }
  }, [
    degViewOffset,
    driverViewPageMap,
    getCoordinateAtBearingAndRange,
    isMapReady,
    assetsLoaded,
    gotoHomePage,
    playbackOffset
  ]);

  // React to vehicle updates from the hook
  useEffect(() => {
    if (vehicleState) {
      updateMapView(vehicleState);
    }
  }, [
    vehicleState,
    updateMapView
  ]);

  // Handle Model Injection & Style Switches
  useEffect(() => {
    const map = driverViewPageMap?.getMap();
    if (!map) return;

    const setupLayers = () => {
      // If the style isn't ready, wait 200ms and try again
      if (!map.isStyleLoaded()) {
        setTimeout(setupLayers, 200);
        return;
      }

      try {
        // Add Model
        if (!map.hasModel('mitsubishi-car')) {
          map.addModel('mitsubishi-car', '/models/mitsubishi/source/Untitled.glb');
        }

        // Add Source
        if (!map.getSource('vehicle-positions')) {
          map.addSource('vehicle-positions', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] }
          });
        }

        // Add Layer
        if (!map.getLayer('vehicle-layer')) {
          map.addLayer({
            id: 'vehicle-layer',
            type: 'model',
            source: 'vehicle-positions',
            layout: {
              'model-id': 'mitsubishi-car'
            },
            paint: {
              'model-rotation': [
                0,
                0,
                ['+', ['get', 'bearing'], 180]
              ],
              'model-scale': [1, 1, 1],
              'model-type': 'common-3d',

              // Grab the color from the feature properties
              'model-color': ['get', 'vehicleColor'],
              // 1.0 = fully replace texture color, 0.7 = heavy tint
              'model-color-mix-intensity': 0.7
            }
          });
        }

        // Terrain & Fog
        if (!map.getSource('mapbox-dem')) {
            map.addSource('mapbox-dem', {
              type: 'raster-dem',
              url: 'mapbox://mapbox.mapbox-terrain-dem-v1',
              tileSize: 512,
              maxzoom: 14
            });
            map.setTerrain({ source: 'mapbox-dem', exaggeration: 1.0 });
        }

        setIsMapReady(true);
        setAssetsLoaded(true);
        console.log("3D assets loaded.");
      } catch (e) {
        console.error("Error loading 3D assets:", e);
      }
    };

    // Listen for style changes
    map.on('style.load', setupLayers);

    // Manually trigger if it's the first load or a style toggle
    setupLayers();

    return () => {
      map.off('style.load', setupLayers);
    };
  }, [driverViewPageMap, mapStyle]);

  // Update GeoJSON Source
  useEffect(() => {
    const map = driverViewPageMap?.getMap();
    if (!map || !isMapReady) return;

    const features = Array.from(vehicleStateMap.values())
      .filter((vState: any) => vState.id !== vehicleId)
      .map((vState: any) => ({
        type: 'Feature',
        properties: {
          id: vState.id,
          bearing: vState.degBearing,
          vehicleColor: vState.colorCode || '#FFFFFF'
        },
        geometry: {
          type: 'Point',
          coordinates: [
            vState.degLongitude,
            vState.degLatitude
          ]
        }
      }));

    const source: any = map.getSource('vehicle-positions');
    if (source) {
      source.setData({ type: 'FeatureCollection', features });
    }
  }, [version, vehicleStateMap, driverViewPageMap, isMapReady, vehicleId]);

  const toggleMapStyle = useCallback(() => {
    setIsMapReady(false);
    setMapStyle(prevStyle => (prevStyle === MAP_STYLE_STREET ? MAP_STYLE_SATELLITE : MAP_STYLE_STREET));
  }, []);

  const managerHost = useMemo(() => {
    if (!vehicleState) return "";
    const host = vehicleState.managerHost;
    const lastDashIndex = host.lastIndexOf('-');
    return lastDashIndex >= 0 ? host.substring(lastDashIndex + 1) : host;
  }, [vehicleState]);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(!showActiveVehiclePlot)
  }, [showActiveVehiclePlot]);

  // Show the map if we have isDataLoaded OR we have a lastState to show
  const shouldShowMap = (isDataLoaded || lastState) && vehicleState;

  return (
    <div className="body row scroll-y">
      {shouldShowMap ? (
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
          >
            <PlaybackClock />
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
            <div style={{ position: "fixed", top: 10, left: 110 }}>
              <Button onClick={() => setIsInterpolationEnabled(!isInterpolationEnabled)}>
                <FontAwesomeIcon
                  icon={isInterpolationEnabled ? faMagic : faBars}
                  title={isInterpolationEnabled ? "Disable Smoothing" : "Enable Smoothing"}                    className="mr-3"
                />
              </Button>
            </div>
            <div style={{ position: "fixed", top: 10, left: 160 }}>
              <Button onClick={() => setShowActiveVehiclePlot(!showActiveVehiclePlot)}>
                <FontAwesomeIcon icon={faChartLine}/>
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
            {showActiveVehiclePlot &&
              <ActiveVehiclePlot
                toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
                vehicleId={vehicleId}
              />
            }
            <FullscreenControl />
          </Map>
      ) : (
          <SpinnerLoading />
      )}
    </div>
  )

}
