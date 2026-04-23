import './HomePage.css';
import Map, { useMap } from "react-map-gl";
import { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { VehicleIcon } from './VehicleIcon';
import { VehicleDisplay } from '../../models/VehicleDisplay';
import { VehicleState } from '../../models/VehicleState';
import { MapWrapper } from '../Utils/MapWrapper';
import { PlaybackClock } from '../Utils/PlaybackClock';
import { AppNavBar } from '../NavBar/AppNavBar';
import { ManageMenu } from './ManageMenu';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { Button } from 'react-bootstrap';
import { CreateVehiclePanel } from './CreateVehiclePanel';
import { SimulationTable } from './SimulationTable';
import { fetchAuthSession, signInWithRedirect } from "aws-amplify/auth";
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";

export const HomePage = () => {
  const { homePageMap } = useMap();

  const [token, setToken] = useState("");
  const mapboxToken = CONFIG.MAPBOX_TOKEN;
  const [vehicleSize, setVehicleSize] = useState(5);
  const [isMapLoaded, setIsMapLoaded] = useState(false);
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false)
  const [pageNumber, setPageNumber] = useState(0);
  const [vehicleStateMapVersion, setVehicleStateMapVersion] = useState(0);
  const [showSimTable, setShowSimTable] = useState(false);

  const vehicleStateMapRef = useRef(new MapWrapper<string, VehicleState>());
  const vehicleDisplayMapRef = useRef(new MapWrapper<string, VehicleDisplay>());
  const isFetchingRef = useRef(false);

  const { playbackOffset, clearPlayback } = usePlayback();

  // Map styles
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_STREET);

  // Vehicle dimensions controls - maybe move to configurables?
  const MIN_SIZE = 5.0;   // Smallest vehicle size
  const MAX_SIZE = 120.0;   // Largest vehicle size
  const MIN_ZOOM = 15.0;    // Zoom level at which vehicle size sticks at minimum
  const MAX_ZOOM = 22.0;    // Zoom level at which vehicle size sticks at maximum

  // Millisecond duration between frame redraws
  const MS_FRAME_TIME = 100;

  // Vehicle timeout in seconds
  const SECS_VEHICLE_TIMEOUT = 30;

  useEffect(() => {
    let cancelled = false;

    async function loadToken() {
      if (token) return;

      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          // No cached/refreshable session -> interactive login
          await signInWithRedirect();
          return;
        }

        if (!cancelled) setToken(accessToken);
      } catch (e) {
        console.error("Error fetching Cognito token:", e);
        // optional: fall back to interactive login
        // await signInWithRedirect();
      }
    }

    loadToken();
    return () => {
      cancelled = true;
    };
  }, [token]);

  // Memoize vehicle state list derived from the ref
  const vehicleStateList = useMemo(() => {
    return Array.from(vehicleStateMapRef.current.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vehicleStateMapVersion]);

  interface ApiResponse {
    _embedded: {
      vehicleStates: VehicleState[];
    }
    page: {
      size: number,
      totalElements: number,
      totalPages: number,
      number: number
    }
  }

  const fetchVehicleStateList = useCallback(() => {
    if (!token || !isMapLoaded || isFetchingRef.current) return;

    isFetchingRef.current = true;

    // Timeout vehicles if they haven't updated lately
    const msEpochTimeoutTime = Date.now() - (SECS_VEHICLE_TIMEOUT * 1000) - playbackOffset;
    vehicleStateMapRef.current = vehicleStateMapRef.current.filter(
      state => state.msEpochLastRun > msEpochTimeoutTime
    );

    // Get the latest VehicleStates
    const restUrlBase = CONFIG.ROADRUNNER_REST_URL_BASE;
    let getStatesUrl = `${restUrlBase}/api/playback/state?page=${pageNumber}`;

    // Calculate the target timestamp, if needed
    if (playbackOffset !== 0) {
      const targetDate = new Date(Date.now() - playbackOffset);
      const isoTimestamp = targetDate.toISOString();
      getStatesUrl += `&timestamp=${encodeURIComponent(isoTimestamp)}`;
    }

    fetch(getStatesUrl, {
      method: 'get',
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(response => response.json())
      .then((data: ApiResponse) => {
        // Type the data as an ApiResponse
        let newPageNumber = pageNumber + 1;
        if (newPageNumber >= data.page.totalPages) {
          newPageNumber = 0
        }
        setPageNumber(newPageNumber);
        setIsDataLoaded(true);

        if (data._embedded) {
          data._embedded.vehicleStates.forEach((vehicleState: VehicleState) => {
            vehicleStateMapRef.current.set(vehicleState.id, vehicleState);
            if (!vehicleDisplayMapRef.current.get(vehicleState.id)) {
              const vehicleDisplay = new VehicleDisplay(vehicleSize, false, false);
              vehicleDisplayMapRef.current.set(vehicleState.id, vehicleDisplay);
            }
          });
        }
        setVehicleStateMapVersion(v => v + 1);
      })
      .catch(error => {
        console.log(`Error caught during fetch in fetchVehicleStateList: ${error.message}`);
        setIsDataLoaded(false);
      })
      .finally(() => {
        isFetchingRef.current = false;
      });
  }, [token, isMapLoaded, pageNumber, vehicleSize, playbackOffset]);

  useEffect(() => {
    if (!isMapLoaded) return;
    const interval = setInterval(fetchVehicleStateList, MS_FRAME_TIME);
    return () => clearInterval(interval);
  }, [fetchVehicleStateList, isMapLoaded]);

  const hideAllRoutes = useCallback(() => {
    vehicleStateMapRef.current.forEach((vehicleState: VehicleState) => {
      let vehicleDisplay = vehicleDisplayMapRef.current.get(vehicleState.id);
      if (vehicleDisplay) {
        vehicleDisplay.routeVisible = false;
      }
    })
  }, []);

  const showAllRoutes = useCallback(() => {
    vehicleStateMapRef.current.forEach((vehicleState: VehicleState) => {
      const vehicleDisplay = vehicleDisplayMapRef.current.get(vehicleState.id);
      if (vehicleDisplay) {
        vehicleDisplay.routeVisible = true;
      }
    })
  }, []);

  const fitAllOnScreen = useCallback(() => {
    if (!isDataLoaded || vehicleStateMapRef.current.size() === 0) return;

    let minLongitude = 360.0;
    let minLatitude = 360.0;
    let maxLongitude = -360.0;
    let maxLatitude = -360.0;
    vehicleStateMapRef.current.forEach((vehicleState: VehicleState) => {
      minLatitude = Math.min(minLatitude, vehicleState.degLatitude);
      minLongitude = Math.min(minLongitude, vehicleState.degLongitude);
      maxLatitude = Math.max(maxLatitude, vehicleState.degLatitude);
      maxLongitude = Math.max(maxLongitude, vehicleState.degLongitude);
    })

    // Expand size by 5% each way;
    let deltaLongitude = Math.max(0.1, maxLongitude - minLongitude);
    let deltaLatitude = Math.max(0.1, maxLatitude - minLatitude);
    minLongitude -= (0.05 * deltaLongitude);
    maxLongitude += (0.05 * deltaLongitude);
    minLatitude -= (0.05 * deltaLatitude);
    maxLatitude += (0.05 * deltaLatitude);

    homePageMap?.fitBounds([[minLongitude, minLatitude], [maxLongitude, maxLatitude]]);
  }, [isDataLoaded, homePageMap]);

  const toggleMapStyle = useCallback(() => {
    setIsTransitioning(true);
    if (mapStyle === MAP_STYLE_STREET) {
      setMapStyle(MAP_STYLE_SATELLITE);
      homePageMap?.getMap().setFog({});
    }
    else {
      setMapStyle(MAP_STYLE_STREET);
    }
    setTimeout(() => setIsTransitioning(false), 500);
  }, [mapStyle, homePageMap]);

  const onZoom = useCallback((viewState: any) => {
    const currentZoom = viewState.zoom;
    let size = MIN_SIZE;
    if (currentZoom >= MIN_ZOOM && currentZoom <= MAX_ZOOM) {
      size = (MAX_SIZE * Math.pow((2.0 / 3.0), (MAX_ZOOM - currentZoom)));
    }
    else if (currentZoom > MAX_ZOOM) {
      size = MAX_SIZE;
    }
    if (size < 0) size = MIN_SIZE;
    setVehicleSize(size);
  }, []);

  const onClick = useCallback((event: any) => {
    let bestVehicle: VehicleDisplay | undefined = {} as VehicleDisplay;
    let bestDistance = 100;
    vehicleStateMapRef.current.forEach((vehicleState: VehicleState) => {
      const point = homePageMap?.project({ lng: vehicleState.degLongitude, lat: vehicleState.degLatitude });

      // Calculate the distance of the click from the vehicle
      if (point) {
        let dx = point.x - event.point.x;
        let dy = point.y - event.point.y;
        let distance = Math.sqrt(dx * dx + dy * dy);

        // If the distance is less than 50, then toggle visibility
        if (distance < bestDistance) {
          bestDistance = distance;
          bestVehicle = vehicleDisplayMapRef.current.get(vehicleState.id);
        }
      }
    })
    if (bestVehicle) {
      bestVehicle.routeVisible = !bestVehicle.routeVisible;
      bestVehicle.popupVisible = !bestVehicle.popupVisible;
    }
  }, [homePageMap]);

  const onLoad = useCallback(() => {
    setIsMapLoaded(true);
  }, []);

  const openCreateVehicle = useCallback(() => {
    setIsCreateVehicleActive(true);
  }, []);

  const toggleSimTable = useCallback(() => {
    setShowSimTable(!showSimTable);
  }, [showSimTable]);

  const returnToNow = useCallback(() => {
    clearPlayback();
    vehicleStateMapRef.current.clear();
    setVehicleStateMapVersion(v => v + 1);
  }, [clearPlayback]);

  return (
    <>
      <div className="map-container">
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
          <AppNavBar additionalMenuItems={<ManageMenu openCreateVehicle={openCreateVehicle} toggleSimTable={toggleSimTable} />} />
          {(isDataLoaded && !isTransitioning) ?
            <>
              <PlaybackClock />
              {isCreateVehicleActive && (
                <CreateVehiclePanel
                  setIsCreateVehicleActive={setIsCreateVehicleActive}
                />
              )}
              <div style={{ position: "fixed", top: 100, left: 10 }}>
                <Button onClick={fitAllOnScreen}>
                  <FontAwesomeIcon title="Fit All" icon={faUpRightAndDownLeftFromCenter} className="mr-3" />
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 60 }}>
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
              <div style={{ position: "fixed", top: 100, left: 110 }}>
                <Button onClick={showAllRoutes}>
                  <FontAwesomeIcon title="Show All Routes" icon={faEye} className="mr-3" />
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 160 }}>
                <Button onClick={hideAllRoutes}>
                  <FontAwesomeIcon title="Hide All Routes" icon={faEyeSlash} className="mr-3" />
                </Button>
              </div>
              {showSimTable && <SimulationTable toggleSimTable={toggleSimTable} returnToNow={returnToNow}/>}
              {vehicleStateList.map((vehicleState) => {
                const vehicleDisplay = vehicleDisplayMapRef.current.get(vehicleState.id);
                if (vehicleDisplay) {
                  vehicleDisplay.size = vehicleSize;
                  return (
                    <VehicleIcon
                      key={vehicleState.id}
                      vehicleState={vehicleState}
                      vehicleDisplay={vehicleDisplay}
                    />
                  );
                }
                return null;
              })}
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
