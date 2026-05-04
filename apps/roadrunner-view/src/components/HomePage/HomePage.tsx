import './HomePage.css';
import Map, { useMap } from "react-map-gl";
import { useState, useCallback, useMemo } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading"
import { VehicleIcon } from './VehicleIcon';
import { VehicleDisplay } from '../../models/VehicleDisplay';
import { VehicleState } from '../../models/VehicleState';
import { PlaybackClock } from '../Utils/PlaybackClock';
import { AppNavBar } from '../NavBar/AppNavBar';
import { ManageMenu } from './ManageMenu';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash, faMagic, faBars } from '@fortawesome/free-solid-svg-icons';
import { Button } from 'react-bootstrap';
import { CreateVehiclePanel } from './CreateVehiclePanel';
import { SimulationTable } from './SimulationTable';
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { useMapViewState } from '../../context/MapViewStateContext';
import { useVehicleData } from '../../hooks/useVehicleData';
import { ActiveVehiclePlot } from './ActiveVehiclePlot';

export const HomePage = () => {
  const { homePageMap } = useMap();

  const mapboxToken = CONFIG.MAPBOX_TOKEN;
  const [vehicleSize, setVehicleSize] = useState(5);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false)
  const [showSimTable, setShowSimTable] = useState(false);
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);

  const {
    clearPlayback
  } = usePlayback();

  const {
    homeMapViewState,
    setHomeMapViewState
  } = useMapViewState();

  const {
    vehicleStateMap,
    vehicleDisplayMap,
    isDataLoaded,
    version,
    setIsInterpolationEnabled,
    isInterpolationEnabled,
    clearData,
    setAllRoutesVisibility
  } = useVehicleData({
    vehicleSize: 20,
    intervalMs: 50
   });

  // Map styles
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_STREET);

  // Vehicle dimensions controls - maybe move to configurables?
  const MIN_SIZE = 5.0;   // Smallest vehicle size
  const MAX_SIZE = 120.0;   // Largest vehicle size
  const MIN_ZOOM = 15.0;    // Zoom level at which vehicle size sticks at minimum
  const MAX_ZOOM = 22.0;    // Zoom level at which vehicle size sticks at maximum

  // Memoize vehicle state list derived from the ref
  const vehicleStateList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  const fitAllOnScreen = useCallback(() => {
    if (!isDataLoaded || vehicleStateMap.size() === 0) return;

    let minLongitude = 360.0;
    let minLatitude = 360.0;
    let maxLongitude = -360.0;
    let maxLatitude = -360.0;
    vehicleStateMap.forEach((vehicleState: VehicleState) => {
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
  }, [isDataLoaded, homePageMap, vehicleStateMap]);

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

  const onClick = useCallback((event: any) => {
    let bestVehicle: VehicleDisplay | undefined = {} as VehicleDisplay;
    let bestDistance = 50;
    vehicleStateMap.forEach((vehicleState: VehicleState) => {
      const point = homePageMap?.project({
        lng: vehicleState.degLongitude,
        lat: vehicleState.degLatitude
      });

      // Calculate the distance of the click from the vehicle
      if (point) {
        let dx = point.x - event.point.x;
        let dy = point.y - event.point.y;
        let distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < bestDistance) {
          bestDistance = distance;
          bestVehicle = vehicleDisplayMap.get(vehicleState.id);
        }
      }
    })
    if (bestVehicle) {
      bestVehicle.routeVisible = !bestVehicle.routeVisible;
      bestVehicle.popupVisible = !bestVehicle.popupVisible;
    }
  // eslint-disable-next-line
  }, [homePageMap, vehicleStateMap, vehicleDisplayMap, version]);

  const openCreateVehicle = useCallback(() => {
    setIsCreateVehicleActive(true);
  }, []);

  const toggleSimTable = useCallback(() => {
    setShowSimTable(!showSimTable);
    setShowActiveVehiclePlot(false);
    clearData();
  }, [showSimTable, clearData]);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(!showActiveVehiclePlot)
    setShowSimTable(false);
    clearData();
  }, [showActiveVehiclePlot, clearData]);

  const returnToNow = useCallback(() => {
    clearPlayback();
    clearData();
  }, [clearPlayback, clearData]);

  const onMove = useCallback((evt: any) => {
    const { viewState } = evt;

    // Handle vehicle sizing
    const currentZoom = viewState.zoom;

    let size = MIN_SIZE;
    if (currentZoom >= MIN_ZOOM && currentZoom <= MAX_ZOOM) {
      size = (MAX_SIZE * Math.pow((2.0 / 3.0), (MAX_ZOOM - currentZoom)));
    } else if (currentZoom > MAX_ZOOM) {
      size = MAX_SIZE;
    }
    setVehicleSize(Math.max(size, MIN_SIZE));

    // Mapbox occasionally fires onMove with 0,0 on mount.
    // Ensure we actually have coordinates before saving to Context.
    if (viewState.latitude !== 0 && viewState.longitude !== 0) {
      setHomeMapViewState(viewState);
    }
  }, [setHomeMapViewState]);

  const hideAllRoutes = () => setAllRoutesVisibility(false);
  const showAllRoutes = () => setAllRoutesVisibility(true);

  return (
    <>
      <div className="map-container">
        <Map
          id="homePageMap"
          {...homeMapViewState}
          mapStyle={mapStyle}
          mapboxAccessToken={mapboxToken}
          fog={{}}
          onMove={onMove}
          onClick={(event) => onClick(event)}
        >
          <AppNavBar additionalMenuItems={
            <ManageMenu
              openCreateVehicle={openCreateVehicle}
              toggleSimTable={toggleSimTable}
              toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
            />
          }
          />
          {(isDataLoaded && !isTransitioning) ?
            <>
              <PlaybackClock />
              {isCreateVehicleActive && (
                <CreateVehiclePanel
                  returnToNow={returnToNow}
                  setIsCreateVehicleActive={setIsCreateVehicleActive}
                />
              )}
              <div style={{ position: "fixed", top: 100, left: 10 }}>
                <Button onClick={fitAllOnScreen}>
                  <FontAwesomeIcon
                    title="Fit All"
                    icon={faUpRightAndDownLeftFromCenter}
                    className="mr-3"
                  />
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 60 }}>
                <Button onClick={toggleMapStyle}>
                  {(mapStyle === MAP_STYLE_STREET) ?
                    <>
                      <FontAwesomeIcon
                        title="Satellte Display"
                        icon={faSatellite}
                        className="mr-3"
                      />
                    </>
                    :
                    <FontAwesomeIcon
                      title="Map Display"
                      icon={faMap}
                      className="mr-3"
                    />
                  }
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 110 }}>
                <Button onClick={showAllRoutes}>
                  <FontAwesomeIcon
                    title="Show All Routes"
                    icon={faEye}
                    className="mr-3"
                  />
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 160 }}>
                <Button onClick={hideAllRoutes}>
                  <FontAwesomeIcon
                    title="Hide All Routes"
                    icon={faEyeSlash}
                    className="mr-3"
                  />
                </Button>
              </div>
              <div style={{ position: "fixed", top: 100, left: 210 }}>
                <Button onClick={() => setIsInterpolationEnabled(!isInterpolationEnabled)}>
                  <FontAwesomeIcon
                    icon={isInterpolationEnabled ? faMagic : faBars}
                    title={isInterpolationEnabled ? "Disable Smoothing" : "Enable Smoothing"}                    className="mr-3"
                  />
                </Button>
              </div>
              {showSimTable &&
                <SimulationTable
                  toggleSimTable={toggleSimTable}
                  returnToNow={returnToNow}
                />
              }
              {showActiveVehiclePlot &&
                <ActiveVehiclePlot
                  toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
                />
              }
              {vehicleStateList.map((vehicleState) => {
                const vehicleDisplay = vehicleDisplayMap.get(vehicleState.id);
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
