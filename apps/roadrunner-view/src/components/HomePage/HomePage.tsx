import './HomePage.css';
import Map, { MapLayerMouseEvent, useMap } from "react-map-gl";
import { useEffect, useState } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { useAuth0 } from "@auth0/auth0-react";
import { VehicleIcon } from './VehicleIcon';
import { VehicleDisplay } from '../../models/VehicleDisplay';
import { VehicleState } from '../../models/VehicleState';
import { MapWrapper } from '../Utils/MapWrapper';
import { AppNavBar } from '../NavBar/AppNavBar';
import { ManageMenu } from './ManageMenu';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { library } from '@fortawesome/fontawesome-svg-core';
import { faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { Button } from 'react-bootstrap';
import { CreateVehiclePanel } from './CreateVehiclePanel';

export const HomePage = () => {
  const { getAccessTokenSilently } = useAuth0();

  const { homePageMap } = useMap();

  const [token, setToken] = useState("");

  const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;

  const [vehicleSize, setVehicleSize] = useState(5);
  const [isMapLoaded, setIsMapLoaded] = useState(false);
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false)

  const [pageNumber, setPageNumber] = useState(0);
  const [vehicleStateList, setVehicleStateList] = useState<VehicleState[]>([]);
  const [vehicleStateMap, setVehicleStateMap] = useState<MapWrapper<string, VehicleState>>();
  const [vehicleDisplayMap, setVehicleDisplayMap] = useState<MapWrapper<string, VehicleDisplay>>();

  // Map styles
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_STREET);

  const [count, setCount] = useState(0);

  // Vehicle dimensions controls - maybe move to configurables?
  const MIN_SIZE = 5.0;   // Smallest vehicle size
  const MAX_SIZE = 120.0;   // Largest vehicle size
  const MIN_ZOOM = 15.0;    // Zoom level at which vehicle size sticks at minimum
  const MAX_ZOOM = 22.0;    // Zoom level at which vehicle size sticks at maximum

  // Millisecond duration between frame redraws
  const MS_FRAME_TIME = 500;

  // Vehicle timeout in seconds
  const SECS_VEHICLE_TIMEOUT = 30;

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

  useEffect(() => {
    if (vehicleStateMap) {
      return;
    }

    setVehicleStateMap(new MapWrapper<string, VehicleState>());
  }, [vehicleStateMap]);

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

  function fetchVehicleStateList() {
    if (!token || token.length === 0) {
      return;
    }
    if (!isMapLoaded) {
      return;
    }
    if (!vehicleStateMap) {
      return;
    }
    if (!vehicleDisplayMap) {
      return;
    }

    // Remove Vehicles from the Map that haven't been updated in SECS_VEHICLE_TIMEOUT seconds
    const msEpochTimeoutTime = new Date().getTime() - (SECS_VEHICLE_TIMEOUT * 1000);
    const vMap = vehicleStateMap.filter(state => state.msEpochLastRun > msEpochTimeoutTime);

    try {
      // Get the latest VehicleStates
      const restUrlBase = process.env.REACT_APP_ROADRUNNER_REST_URL_BASE!;

      // Add page and pageSize parameters to the query string
      const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-all-vehicle-states?page=${pageNumber}`;

      fetch(getStatesUrl, {
        method: 'get',
        headers: {
          Authorization: `Bearer ${token}`,
        }
      })
        .then(async response => response.json())
        .then((data: ApiResponse) => {
          // Type the data as an ApiResponse
          let newPageNumber = pageNumber + 1;
          if (newPageNumber >= data.page.totalPages) {
            newPageNumber = 0
          }
          setPageNumber(newPageNumber);
          setIsDataLoaded(true);

          if (data._embedded) {
            const vehicleStates = data._embedded.vehicleStates;
            vehicleStates.forEach((vehicleState: VehicleState) => {
              vMap.set(vehicleState.id, vehicleState);
              if (!vehicleDisplayMap.get(vehicleState.id)) {
                let vehicleDisplay = new VehicleDisplay(vehicleSize, false, false);
                vehicleDisplayMap.set(vehicleState.id, vehicleDisplay);
              }
            });
          }
          setVehicleStateMap(vMap);
          setVehicleStateList(Array.from(vMap.values()));
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
    vehicleStateMap?.forEach((vehicleState: VehicleState) => {
      let vehicleDisplay = vehicleDisplayMap?.get(vehicleState.id);
      if (vehicleDisplay) {
        vehicleDisplay.routeVisible = false;
      }
    })
  }

  function showAllRoutes() {
    vehicleStateMap?.forEach((vehicleState: VehicleState) => {
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

    if (!vehicleStateMap) {
      return;
    }

    if (vehicleStateMap.size() === 0) {
      return;
    }

    let minLongitude = 360.0;
    let minLatitude = 360.0;
    let maxLongitude = -360.0;
    let maxLatitude = -360.0;
    vehicleStateMap.forEach((vehicleState: VehicleState) => {
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
    vehicleStateMap?.forEach((vehicleState: VehicleState) => {
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

  function openCreateVehicle() {
    setIsCreateVehicleActive(true);
  }

  useEffect(() => {
    const timer = setTimeout(() => {
      try {
        fetchVehicleStateList();
        setCount(count + 1);  // Update count to trigger effect again
      }
      catch (error: any) {
        console.log(`Error caught in fetchVehicleStateList: ${error.message}`);
        setIsDataLoaded(false);
      }
    }, MS_FRAME_TIME)
    return () => clearTimeout(timer);
    // eslint-disable-next-line
  }, [count]);

  library.add(faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash);

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
          <AppNavBar additionalMenuItems={<ManageMenu openCreateVehicle={openCreateVehicle} />} />
          {(isDataLoaded && vehicleDisplayMap && vehicleStateList && !isTransitioning) ?
            <>
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
