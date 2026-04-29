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
import { faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { Button } from 'react-bootstrap';
import { CreateVehiclePanel } from './CreateVehiclePanel';
import { SimulationTable } from './SimulationTable';
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { useVehicleData } from '../../hooks/useVehicleData';

export const HomePage = () => {
  const { homePageMap } = useMap();

  const mapboxToken = CONFIG.MAPBOX_TOKEN;
  const [vehicleSize, setVehicleSize] = useState(5);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false)
  const [showSimTable, setShowSimTable] = useState(false);

  const {
    clearPlayback
  } = usePlayback();
  const {
    vehicleStateMap,
    vehicleDisplayMap,
    isDataLoaded,
    version,
    clearData,
    setAllRoutesVisibility
  } = useVehicleData({
    vehicleSize
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
    clearData();
  }, [showSimTable, clearData]);

  const returnToNow = useCallback(() => {
    clearPlayback();
    clearData();
  }, [clearPlayback, clearData]);

  const hideAllRoutes = () => setAllRoutesVisibility(false);
  const showAllRoutes = () => setAllRoutesVisibility(true);

  return (
    <>
      <div className="map-container">
        <Map
          id="homePageMap"
          mapStyle={mapStyle}
          mapboxAccessToken={mapboxToken}
          fog={{}}
          initialViewState={{
            longitude: -97.5,
            latitude: 32.75,
            zoom: 10,
          }}
          onClick={(event) => onClick(event)}
          onZoom={(viewStateChangeEvent) => onZoom(viewStateChangeEvent)}
        >
          <AppNavBar additionalMenuItems={
            <ManageMenu
              openCreateVehicle={openCreateVehicle}
              toggleSimTable={toggleSimTable}
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
              {showSimTable &&
                <SimulationTable
                  toggleSimTable={toggleSimTable}
                  returnToNow={returnToNow}
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
