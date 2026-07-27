import './GoogleHomePage.css';
import { Map, useMap } from "@vis.gl/react-google-maps";
import { useState, useCallback, useMemo, useEffect } from "react";
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { GoogleVehicleIcon } from './GoogleVehicleIcon';
import { VehicleState } from '../../models/VehicleState';
import { PlaybackClock } from '../Utils/PlaybackClock';
import { AppNavBar } from '../NavBar/AppNavBar';
import { ManageMenu } from '../HomePage/ManageMenu';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash, faMagic, faBars, faChartLine } from '@fortawesome/free-solid-svg-icons';
import { Button } from 'react-bootstrap';
import { CreateVehiclePanel } from '../HomePage/CreateVehiclePanel';
import { SimulationTable } from '../HomePage/SimulationTable';
import { usePlayback } from "../../context/PlaybackContext";
import { useMapViewState } from '../../context/MapViewStateContext';
import { useVehicleData } from '../../hooks/useVehicleData';
import { ActiveVehiclePlot } from '../Shared/ActiveVehiclePlot';
import { CrissCrossPanel } from '../HomePage/CrissCrossPanel';
import { useNavigate } from "react-router-dom";
import { BookmarksPanel } from '../HomePage/BookmarksPanel';

export const GoogleHomePage = () => {
  const map = useMap('homePageMap');
  const stableMapRef = useMemo(() => {
    return {
      current: {
        getMap: () => map
      }
    };
  }, [map]);

  const [vehicleSize, setVehicleSize] = useState(5);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false);
  const [isCrissCrossActive, setIsCrissCrossActive] = useState(false);
  const [showSimTable, setShowSimTable] = useState(false);
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);
  const [showBookmarksPanel, setShowBookmarksPanel] = useState(false);

  const navigate = useNavigate();

  const {
    clearPlayback,
    setPlaybackSession
  } = usePlayback();

  const {
    homeMapViewState,
    setHomeMapViewState
  } = useMapViewState();

  const [mapStyle, setMapStyle] = useState<'roadmap' | 'satellite'>('roadmap');

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

  const MIN_SIZE = 5.0;
  const MAX_SIZE = 120.0;
  const MIN_ZOOM = 15.0;
  const MAX_ZOOM = 22.0;

  const vehicleStateList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  useEffect(() => {
    async function setInitialLocation() {
      try {
        const response = await fetch('https://ipapi.co/json/');
        const data = await response.json();

        if (data.latitude && data.longitude) {
          console.log("Setting location to {}, {}", data.latitude, data.longitude);
          setHomeMapViewState((prev: any) => ({
            ...prev,
            latitude: data.latitude,
            longitude: data.longitude,
            zoom: 10
          }));
        }
      } catch (error) {
        console.error("IP Geolocation failed, falling back to default.", error);
      }
    }

    if (homeMapViewState.latitude === 32.75 && homeMapViewState.longitude === -97.5) {
      setInitialLocation();
    }
  }, [homeMapViewState.latitude, homeMapViewState.longitude, setHomeMapViewState]);

  const fitAllOnScreen = useCallback(() => {
    if (!isDataLoaded || vehicleStateMap.size() === 0 || !map) return;

    const bounds = new google.maps.LatLngBounds();
    vehicleStateMap.forEach((vehicleState: VehicleState) => {
      bounds.extend({ lat: vehicleState.degLatitude, lng: vehicleState.degLongitude });
    });

    const ne = bounds.getNorthEast();
    const sw = bounds.getSouthWest();

    // If the bounds have no area (e.g. single vehicle or all vehicles at same location)
    if (ne.lat() === sw.lat() && ne.lng() === sw.lng()) {
      map.setCenter(ne);
      map.setZoom(16); // Comfortably zoomed-out neighborhood level
    } else {
      map.fitBounds(bounds);
    }
  }, [isDataLoaded, map, vehicleStateMap]);

  const toggleMapStyle = useCallback(() => {
    setMapStyle(prev => (prev === 'roadmap' ? 'satellite' : 'roadmap'));
  }, []);

  const openCreateVehicle = useCallback(() => {
    setIsCreateVehicleActive(true);
    setIsCrissCrossActive(false);
    setShowBookmarksPanel(false);
    setShowSimTable(false);
    setShowActiveVehiclePlot(false);
  }, []);

  const openCrissCross = useCallback(() => {
    setIsCrissCrossActive(true);
    setIsCreateVehicleActive(false);
    setShowBookmarksPanel(false);
    setShowSimTable(false);
    setShowActiveVehiclePlot(false);
  }, []);

  const toggleSimTable = useCallback(() => {
    setShowSimTable(!showSimTable);
    setIsCreateVehicleActive(false);
    setIsCrissCrossActive(false);
    setShowBookmarksPanel(false);
    setShowActiveVehiclePlot(false);
    clearData();
  }, [showSimTable, clearData]);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(!showActiveVehiclePlot);
    setIsCreateVehicleActive(false);
    setIsCrissCrossActive(false);
    setShowBookmarksPanel(false);
    setShowSimTable(false);
    clearData();
  }, [showActiveVehiclePlot, clearData]);

  const toggleBookmarksPanel = useCallback(() => {
    setShowBookmarksPanel(!showBookmarksPanel);
    setIsCreateVehicleActive(false);
    setIsCrissCrossActive(false);
    setShowSimTable(false);
    setShowActiveVehiclePlot(false);
  }, [showBookmarksPanel]);

  const handleSelectBookmark = useCallback((vehicleId: string, startTime: number) => {
    const timeFormatOptions: Intl.DateTimeFormatOptions = {
      timeZone: 'UTC',
      month: 'numeric',
      day: 'numeric',
      year: '2-digit',
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    };

    const sessionTime = new Date(startTime).toLocaleTimeString([], timeFormatOptions) + 'Z';
    setPlaybackSession(sessionTime);
    navigate(`/google/driver-view/${vehicleId}`);
    setShowBookmarksPanel(false);
  }, [navigate, setPlaybackSession]);

  const returnToNow = useCallback(() => {
    clearPlayback();
    clearData();
  }, [clearPlayback, clearData]);

  const onCameraChanged = useCallback((evt: any) => {
    const { center, zoom } = evt.detail;
    let size = MIN_SIZE;
    if (zoom >= MIN_ZOOM && zoom <= MAX_ZOOM) {
      size = (MAX_SIZE * Math.pow((2.0 / 3.0), (MAX_ZOOM - zoom)));
    } else if (zoom > MAX_ZOOM) {
      size = MAX_SIZE;
    }
    setVehicleSize(Math.max(size, MIN_SIZE));

    if (center.lat !== 0 && center.lng !== 0) {
      setHomeMapViewState({
        latitude: center.lat,
        longitude: center.lng,
        zoom: zoom,
        bearing: evt.detail.heading || 0,
        pitch: evt.detail.tilt || 0,
        padding: { top: 0, bottom: 0, left: 0, right: 0 }
      });
    }
  }, [setHomeMapViewState]);

  const hideAllRoutes = () => setAllRoutesVisibility(false);
  const showAllRoutes = () => setAllRoutesVisibility(true);

  return (
    <>
      <div className="map-container">
        <Map
          id="homePageMap"
          center={{ lat: homeMapViewState.latitude, lng: homeMapViewState.longitude }}
          zoom={homeMapViewState.zoom}
          heading={homeMapViewState.bearing}
          tilt={homeMapViewState.pitch}
          mapTypeId={mapStyle === 'satellite' ? 'satellite' : 'roadmap'}
          onCameraChanged={onCameraChanged}
          gestureHandling={'greedy'}
          disableDefaultUI={true}
          mapId="DEMO_MAP_ID"
          style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}
        >
          {isDataLoaded && (
            <>
              {vehicleStateList.map((vehicleState) => {
                const vehicleDisplay = vehicleDisplayMap.get(vehicleState.id);
                if (vehicleDisplay) {
                  vehicleDisplay.size = vehicleSize;
                  return (
                    <GoogleVehicleIcon
                      key={vehicleState.id}
                      vehicleState={vehicleState}
                      vehicleDisplay={vehicleDisplay}
                    />
                  );
                }
                return null;
              })}
            </>
          )}
        </Map>

        {/* UI overlays rendered OUTSIDE the Map canvas to allow direct click interaction */}
        <AppNavBar additionalMenuItems={(closeNavbar) => (
          <ManageMenu
            openCreateVehicle={openCreateVehicle}
            openCrissCross={openCrissCross}
            toggleSimTable={toggleSimTable}
            toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
            toggleBookmarksPanel={toggleBookmarksPanel}
            closeNavbar={closeNavbar}
          />
        )}
        />
        {(isDataLoaded) ?
          <>
            <PlaybackClock />
             {isCreateVehicleActive && (
               <CreateVehiclePanel
                 returnToNow={returnToNow}
                 setIsCreateVehicleActive={setIsCreateVehicleActive}
                 mapRef={stableMapRef as any}
               />
             )}
             {isCrissCrossActive && (
               <CrissCrossPanel
                 returnToNow={returnToNow}
                 setIsCrissCrossActive={setIsCrissCrossActive}
                 mapRef={stableMapRef as any}
               />
             )}
            {showBookmarksPanel && (
              <BookmarksPanel
                onClose={() => setShowBookmarksPanel(false)}
                onSelectBookmark={handleSelectBookmark}
              />
            )}
            {/* --- Responsive Map Tools Container --- */}
            <div
              className="map-tools-container"
              style={{
                position: "absolute",
                top: "30px",
                right: "10px",
                display: "flex",
                flexDirection: "column",
                gap: "10px",
                zIndex: 1000,
              }}
            >
              <Button
                variant="light"
                className="shadow-sm"
                onClick={fitAllOnScreen}
              >
                <FontAwesomeIcon
                  title="Fit All"
                  icon={faUpRightAndDownLeftFromCenter}
                  className="mr-3"
                />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={toggleMapStyle}
              >
                {(mapStyle === 'roadmap') ?
                  <>
                    <FontAwesomeIcon
                      title="Satellite Display"
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

              <Button
                variant="light"
                className="shadow-sm"
                onClick={showAllRoutes}
              >
                <FontAwesomeIcon
                  title="Show All Routes"
                  icon={faEye}
                  className="mr-3"
                />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={hideAllRoutes}
              >
                <FontAwesomeIcon
                  title="Hide All Routes"
                  icon={faEyeSlash}
                  className="mr-3"
                />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={() => setIsInterpolationEnabled(!isInterpolationEnabled)}
              >
                <FontAwesomeIcon
                  icon={isInterpolationEnabled ? faMagic : faBars}
                  title={isInterpolationEnabled ? "Disable Smoothing" : "Enable Smoothing"}
                  className="mr-3"
                />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={() => setShowActiveVehiclePlot(!showActiveVehiclePlot)}
              >
                <FontAwesomeIcon
                  title="Active Vehicle Plot"
                  icon={faChartLine}
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
          </>
          :
          <div>
            <SpinnerLoading />
          </div>
        }
      </div>
    </>
  );
};
