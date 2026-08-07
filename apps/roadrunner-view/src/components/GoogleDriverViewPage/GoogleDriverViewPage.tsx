import './GoogleDriverViewPage.css';
import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { VehicleState } from "../../models/VehicleState";
import { ActiveVehiclePlot } from "../Shared/ActiveVehiclePlot";
import { PlaybackClock } from '../Utils/PlaybackClock';
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { Button, Card } from 'react-bootstrap';
import { useApiIsLoaded } from '@vis.gl/react-google-maps';
import { useNavigate } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faHome, faMagic, faBars, faChartLine, faSatellite } from '@fortawesome/free-solid-svg-icons';
import { ViewControl } from '../DriverViewPage/ViewControl';
import { getRestUrl } from "../../config";
import { useVehicleData } from '../../hooks/useVehicleData';
import { usePlayback } from "../../context/PlaybackContext";
import { useMapViewState } from '../../context/MapViewStateContext';
import { fetchAuthSession } from 'aws-amplify/auth';

const getStaticGlbUrl = (hexColor: string | undefined): string => {
  if (!hexColor) return '/models/mitsubishi/source/Untitled_green.glb';
  
  let hex = hexColor.replace('#', '').toUpperCase();
  if (hex.length === 3) {
    hex = hex.split('').map(c => c + c).join('');
  }
  if (hex.length !== 6) return '/models/mitsubishi/source/Untitled_green.glb';
  
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  
  if (d !== 0) {
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
    }
    h = Math.round(h * 60);
  }
  
  const s = max === 0 ? 0 : d / max;
  const v = max / 255;
  
  if (s < 0.15) {
    if (v > 0.8) return '/models/mitsubishi/source/Untitled_white.glb';
    return '/models/mitsubishi/source/Untitled_gray.glb';
  }
  
  if (h >= 345 || h < 15) return '/models/mitsubishi/source/Untitled_red.glb';
  if (h >= 15 && h < 45) return '/models/mitsubishi/source/Untitled_orange.glb';
  if (h >= 45 && h < 75) return '/models/mitsubishi/source/Untitled_yellow.glb';
  if (h >= 75 && h < 150) return '/models/mitsubishi/source/Untitled_green.glb';
  if (h >= 150 && h < 200) return '/models/mitsubishi/source/Untitled_teal.glb';
  if (h >= 200 && h < 250) return '/models/mitsubishi/source/Untitled_blue.glb';
  if (h >= 250 && h < 290) return '/models/mitsubishi/source/Untitled_purple.glb';
  return '/models/mitsubishi/source/Untitled_pink.glb';
};

export const GoogleVehicleModel = ({ vState, vehicleId }: { vState: any, vehicleId: string }) => {
  const modelRef = useRef<any>(null);
  const modelSrc = getStaticGlbUrl(vState.colorCode);

  useEffect(() => {
    const el = modelRef.current;
    if (!el) return;

    el.position = {
      lat: vState.degLatitude,
      lng: vState.degLongitude,
      altitude: 0
    };

    el.orientation = {
      heading: vState.degBearing + 180,
      tilt: -90,
      roll: 0
    };
  }, [vState.degLatitude, vState.degLongitude, vState.degBearing]);

  return (
    <gmp-model-3d
      key={modelSrc}
      ref={modelRef}
      src={modelSrc}
      scale="1.0"
      altitude-mode="clamp-to-ground"
    />
  );
};

export const GoogleDriverViewPage = () => {
  const vehicleId = (window.location.pathname).split('/')[3]; // /google/driver-view/:vehicleId

  const navigate = useNavigate();
  const apiIsLoaded = useApiIsLoaded();
  const [lastState, setLastState] = useState<VehicleState | null>(null);
  const [isMapReady, setIsMapReady] = useState(false);
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);
  const [goingHome, setGoingHome] = useState(false);
  const [hasSeenValidPosition, setHasSeenValidPosition] = useState(false);
  const [isSearchingSession, setIsSearchingSession] = useState(false);
  const [hasCheckedHistory, setHasCheckedHistory] = useState(false);

  const [degViewOffset, setDegViewOffset] = useState(0);
  const MPS_TO_MPH = 2.236936;

  const { playbackOffset, setPlaybackSession, dataSource } = usePlayback();
  const { homeMapViewState, setHomeMapViewState } = useMapViewState();

  const mapRef = useRef<any>(null);
  const missingTimestampRef = useRef<number | null>(null);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(prev => !prev);
  }, []);



  // Integrated Hook
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

  const vehicleState = useMemo(() => {
    return vehicleStateMap.get(vehicleId) || lastState;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vehicleId, vehicleStateMap, lastState, version]);

  const managerHost = useMemo(() => {
    if (!vehicleState) return "";
    const host = vehicleState.managerHost;
    const lastDashIndex = host.lastIndexOf('-');
    return lastDashIndex >= 0 ? host.substring(lastDashIndex + 1) : host;
  }, [vehicleState]);

  const gotoHomePage = useCallback(() => {
    if (goingHome) return;
    setGoingHome(true);

    if (vehicleState && !(vehicleState.degLatitude === 0 && vehicleState.degLongitude === 0)) {
      setHomeMapViewState({
        ...homeMapViewState,
        longitude: vehicleState.degLongitude,
        latitude: vehicleState.degLatitude
      });
    }

    navigate('/google/home');
  }, [navigate, vehicleState, homeMapViewState, setHomeMapViewState, goingHome]);

  // Import Maps 3D library programmatically
  useEffect(() => {
    if (!apiIsLoaded) return;
    let active = true;
    async function load3D() {
      try {
        await google.maps.importLibrary("maps3d");
        if (active) {
          setIsMapReady(true);
        }
      } catch (e) {
        console.error("Failed to load maps3d library in driver view:", e);
      }
    }
    load3D();
    return () => { active = false; };
  }, [apiIsLoaded]);

  // Check if vehicle is absent in active tracking and query historical session if needed
  useEffect(() => {
    if (!isDataLoaded || version === 0 || !vehicleId || vehicleStateMap.has(vehicleId) || isSearchingSession || goingHome) {
      if (isDataLoaded && version > 0 && vehicleStateMap.has(vehicleId)) {
        setHasCheckedHistory(true);
      }
      return;
    }

    async function fetchHistoricalSession() {
      setIsSearchingSession(true);
      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("Session expired");
          return;
        }

        const apiPath = dataSource === 'postgis' ? '/api/db-vehicle' : '/api/vehicle';
        const url = getRestUrl(`${apiPath}/get-vehicle-session/${vehicleId}`);
        const res = await fetch(url, {
          method: 'GET',
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          credentials: 'include'
        });

        if (!res.ok) {
          throw new Error(`Session not found on server (status ${res.status})`);
        }

        const sessionData = await res.json();

        if (sessionData && sessionData.start) {
          const msEpochPlayback = new Date().getTime() - playbackOffset;

          if (!sessionData.end && (msEpochPlayback > sessionData.start)) {
            console.debug("Running simulation within our time: Not messing with time");
          } else if ((msEpochPlayback > sessionData.start) && (msEpochPlayback < sessionData.end)) {
            console.debug("Within start/end window: Not messing with time")
          } else if (lastState && sessionData.end && (msEpochPlayback >= sessionData.end)) {
            console.log("Playback has reached the end of the session. Navigating home.");
            gotoHomePage();
          } else {
            console.log("Setting playback session to ", sessionData.start);
            setPlaybackSession(sessionData.start);
          }
        }
      } catch (error) {
        console.error("Historical session lookup failed, exiting to home:", error);
        gotoHomePage();
      } finally {
        setIsSearchingSession(false);
        setHasCheckedHistory(true);
      }
    }

    fetchHistoricalSession();
  }, [isDataLoaded, vehicleStateMap, vehicleId, gotoHomePage, isSearchingSession, navigate, playbackOffset, setPlaybackSession, lastState, goingHome, version, dataSource]);

  // Handle Auto-Redirects
  useEffect(() => {
    const msCurrentTime = Date.now() - playbackOffset;

    if ((playbackOffset === 0) && lastState && (lastState.msEpochLastRun < msCurrentTime - (30 * 1000))) {
      gotoHomePage();
    }

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
    const mapEl = mapRef.current;
    if (!data || !mapEl || !isMapReady) return;

    if (data.degLatitude === 0 && data.degLongitude === 0) {
      if (hasSeenValidPosition) {
        gotoHomePage();
      }
      return;
    }

    const degViewBearing = data.degBearing + degViewOffset;
    const mRange = 20.0 * window.innerHeight / 932.0;
    const shiftedPoint = getCoordinateAtBearingAndRange(
      data.degLatitude,
      data.degLongitude,
      degViewBearing,
      mRange
    );

    if (shiftedPoint && !isNaN(shiftedPoint.lng) && !isNaN(shiftedPoint.lat)) {
      const targetCameraAltitude = 2.2; // 2.2 meters (approx 7.2 feet) above ground (driver eye level)
      const targetTilt = 86; // 86 degrees tilt (looks slightly down at the road)
      const tiltRad = (targetTilt * Math.PI) / 180;
      const centerAltitude = targetCameraAltitude - mRange * Math.cos(tiltRad);

      mapEl.flyCameraTo({
        endCamera: {
          center: { lat: shiftedPoint.lat, lng: shiftedPoint.lng, altitude: centerAltitude },
          heading: degViewBearing,
          tilt: targetTilt,
          range: mRange,
          altitudeMode: 'RELATIVE_TO_GROUND'
        },
        durationMillis: 0
      });
    }
  }, [
    degViewOffset,
    getCoordinateAtBearingAndRange,
    isMapReady,
    gotoHomePage,
    hasSeenValidPosition
  ]);

  // React to vehicle updates from the hook
  useEffect(() => {
    if (goingHome) return;

    if (vehicleState) {
      if (vehicleState.degLatitude === 0 && vehicleState.degLongitude === 0) {
        if (hasSeenValidPosition) {
          if (missingTimestampRef.current === null) {
            missingTimestampRef.current = Date.now();
          } else if (Date.now() - missingTimestampRef.current > 10000) {
            gotoHomePage();
          }
        }
      } else {
        setHasSeenValidPosition(true);
        missingTimestampRef.current = null; // Reset missing timer when valid
        updateMapView(vehicleState);
      }
    } else if (isDataLoaded && version > 0 && !isSearchingSession && hasCheckedHistory) {
      // The target vehicle went invalid / was not found
      if (missingTimestampRef.current === null) {
        missingTimestampRef.current = Date.now();
      } else if (Date.now() - missingTimestampRef.current > 10000) {
        gotoHomePage();
      }
    }
  }, [
    vehicleState,
    isDataLoaded,
    isSearchingSession,
    hasCheckedHistory,
    updateMapView,
    gotoHomePage,
    goingHome,
    version,
    hasSeenValidPosition
  ]);

  const shouldShowMap = isDataLoaded && vehicleState;

  return (
    <div className="body row scroll-y">
        {shouldShowMap && isMapReady ? (
        <div style={{ width: '100%', height: '100%', position: 'absolute' }}>
          <gmp-map-3d
            ref={mapRef}
            mode="satellite"
            default-ui-hidden="true"
            altitude-mode="RELATIVE_TO_GROUND"
            style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}
          >
            {Array.from(vehicleStateMap.values())
              .filter((vState) => vState.id !== vehicleId)
              .map((vState) => (
                <GoogleVehicleModel
                  key={vState.id}
                  vState={vState}
                  vehicleId={vehicleId}
                />
              ))}
          </gmp-map-3d>

            {/* Float-left buttons */}
            <div
              style={{
                position: "absolute",
                top: "10px",
                left: "10px",
                display: "flex",
                gap: "10px",
                zIndex: 1000,
              }}
            >
              <Button
                variant="light"
                onClick={gotoHomePage}
                className="shadow-sm"
                title="Return to home page"
              >
                <FontAwesomeIcon icon={faHome} />
              </Button>
              <Button
                variant="light"
                onClick={() => navigate(`/google/3d-view?vehicleId=${vehicleId}`)}
                className="shadow-sm"
                title="Go to 3D View"
              >
                <FontAwesomeIcon icon={faSatellite} />
              </Button>
            </div>

            {/* timeline control */}
            <PlaybackClock />

            {/* view controller offset panel */}
            <div style={{ position: "absolute", bottom: "10px", left: "50%", transform: "translateX(-50%)", zIndex: 1000, width: "11rem" }}>
              <Card style={{
                width: '11rem',
                textAlign: 'center',
                borderTop: `6px solid ${vehicleState?.colorCode || '#007bff'}`,
                boxShadow: '0 4px 8px rgba(0,0,0,0.2)'
              }}>
                <Card.Body>
                  <ViewControl degViewOffset={degViewOffset} setDegViewOffset={setDegViewOffset} />
                  <Card.Text style={{ fontSize: '1.1rem' }}>
                    <br/>
                    {`Vehicle:${vehicleState?.id.substring(0, 8)}`}<br/>
                    {`${(Math.round(MPS_TO_MPH * (vehicleState?.metersPerSecond || 0) * 10) / 10)
                      .toFixed(1)
                      .padStart(4, ' ')}`} MPH<br />
                    {`${(Math.round((vehicleState?.degBearing || 0) * 10) / 10)
                      .toFixed(1)
                      .padStart(5, ' ')}`}&deg;<br />
                    Host: {managerHost}<br />
                    {(((vehicleState?.nsLastExec || 0) / 1000000.0))
                      .toFixed(3)} ms<br />
                  </Card.Text>
                </Card.Body>
              </Card>
            </div>

            {/* Float right tools */}
            <div
              style={{
                position: "absolute",
                top: "10px",
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
                onClick={() => setIsInterpolationEnabled(!isInterpolationEnabled)}
                title={isInterpolationEnabled ? "Disable Smoothing" : "Enable Smoothing"}
              >
                <FontAwesomeIcon icon={isInterpolationEnabled ? faMagic : faBars} />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={toggleShowActiveVehiclePlot}
                title="Active Vehicle Plot"
              >
                <FontAwesomeIcon icon={faChartLine} />
              </Button>
            </div>

            {showActiveVehiclePlot && (
              <ActiveVehiclePlot
                toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
                vehicleId={vehicleId}
              />
            )}
        </div>
      ) : (
        <SpinnerLoading />
      )}
      </div>
  );
};
