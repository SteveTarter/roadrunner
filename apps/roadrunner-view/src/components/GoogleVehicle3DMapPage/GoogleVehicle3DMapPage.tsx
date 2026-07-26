import './GoogleVehicle3DMapPage.css';
import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { useNavigate } from 'react-router-dom';
import { AppNavBar } from '../NavBar/AppNavBar';
import { PlaybackClock } from '../Utils/PlaybackClock';
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { Button, Card, Form } from 'react-bootstrap';
import { useApiIsLoaded } from '@vis.gl/react-google-maps';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faSatellite,
  faMap,
  faMagic,
  faBars,
  faUpRightAndDownLeftFromCenter,
  faInfoCircle,
  faChartLine,
  faChevronDown,
  faChevronUp
} from '@fortawesome/free-solid-svg-icons';
import { useVehicleData } from '../../hooks/useVehicleData';
import { usePlayback } from "../../context/PlaybackContext";
import { ActiveVehiclePlot } from '../Shared/ActiveVehiclePlot';

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

const getOffsetCenter = (lat: number, lng: number, heading: number, range: number, tilt: number) => {
  if (tilt <= 0) return { lat, lng };
  
  const offsetDistance = range * 0.22 * Math.sin((tilt * Math.PI) / 180);
  const headingRad = (heading * Math.PI) / 180;
  const dx = offsetDistance * Math.sin(headingRad);
  const dy = offsetDistance * Math.cos(headingRad);
  
  const dLat = dy / 111111;
  const dLng = dx / (111111 * Math.cos((lat * Math.PI) / 180));
  
  return {
    lat: lat + dLat,
    lng: lng + dLng,
    altitude: 0
  };
};

export const GoogleVehicleModel = ({ vState }: { vState: any }) => {
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

export const GoogleVehicle3DMapPage = () => {
  const navigate = useNavigate();
  const { playbackOffset } = usePlayback();
  const apiIsLoaded = useApiIsLoaded();

  const [mapStyle, setMapStyle] = useState<'satellite' | 'hybrid'>('satellite');
  const [isMapReady, setIsMapReady] = useState(false);
  const [focusedVehicleId, setFocusedVehicleId] = useState<string>("");
  const [hasCenteredInitially, setHasCenteredInitially] = useState(false);
  const [cameraMode, setCameraMode] = useState<'chase' | 'fixed'>('chase');
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);
  const [isGuideMinimized, setIsGuideMinimized] = useState(true);
  const [isFocusMinimized, setIsFocusMinimized] = useState(false);

  const mapRef = useRef<any>(null);
  const userBearingOffsetRef = useRef<number>(0);
  const isProgrammaticUpdateRef = useRef<boolean>(false);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(prev => !prev);
  }, []);


  // Hide native move controls in shadow DOM while keeping and shifting the compass/zoom controls
  useEffect(() => {
    const mapEl = mapRef.current;
    if (!mapEl) return;

    let intervalId: any = null;

    const findAndHideControls = () => {
      const shadowRoot = mapEl.shadowRoot;
      if (!shadowRoot) return;

      const elements = shadowRoot.querySelectorAll('*');
      elements.forEach((el: any) => {
        const label = (el.getAttribute('aria-label') || el.getAttribute('title') || '').toLowerCase();
        const className = (el.className || '').toString().toLowerCase();
        const tagName = el.tagName.toLowerCase();

        // 1. Hide move/pan controls
        if (
          (label.includes('move') || label.includes('pan') || className.includes('move') || className.includes('pan')) &&
          !label.includes('zoom') &&
          !label.includes('compass') &&
          !label.includes('tilt') &&
          tagName !== 'canvas' &&
          tagName !== 'gmp-map-3d'
        ) {
          el.style.display = 'none';
        }

        // 2. Shift absolute containers containing zoom, compass, or tilt controls
        const style = el.style;
        const computedStyle = window.getComputedStyle(el);
        const isAbsolute = style.position === 'absolute' || computedStyle.position === 'absolute' || style.position === 'fixed' || computedStyle.position === 'fixed';

        if (isAbsolute && tagName !== 'gmp-map-3d' && tagName !== 'canvas') {
          const hasControls = el.querySelector('[aria-label*="zoom"], [aria-label*="compass"], [aria-label*="tilt"], [title*="zoom"], [title*="compass"], [title*="tilt"]');
          if (hasControls) {
            el.style.setProperty('bottom', '95px', 'important');
          }
        }
      });
    };

    intervalId = setInterval(findAndHideControls, 500);

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [isMapReady]);

  // Integrated Hook for polling and interpolation
  const {
    vehicleStateMap,
    isDataLoaded,
    version,
    setIsInterpolationEnabled,
    isInterpolationEnabled,
  } = useVehicleData({
    vehicleSize: 20,
    intervalMs: 50
  });

  const handleCameraModeChange = useCallback((mode: 'chase' | 'fixed') => {
    setCameraMode(mode);
    const mapEl = mapRef.current;
    if (mode === 'chase' && focusedVehicleId && mapEl) {
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      if (vehicle) {
        userBearingOffsetRef.current = (mapEl.heading || 0) - vehicle.degBearing;
      }
    }
  }, [focusedVehicleId, vehicleStateMap]);

  // Derived list of active vehicles
  const vehicleList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  // Redirect to Google Home Page if vehicle data stream ends (no active updates for 30s)
  useEffect(() => {
    if (!isDataLoaded || playbackOffset !== 0) return;

    const checkStreamActive = () => {
      const msCurrentTime = Date.now() - playbackOffset;
      const states = Array.from(vehicleStateMap.values());
      
      if (states.length > 0) {
        const maxEpoch = Math.max(...states.map(v => v.msEpochLastRun));
        if (maxEpoch < msCurrentTime - (30 * 1000)) {
          console.log("No telemetry updates received for 30 seconds. Returning home.");
          navigate('/google');
        }
      }
    };

    const intervalId = setInterval(checkStreamActive, 5000);
    return () => clearInterval(intervalId);
  }, [isDataLoaded, vehicleStateMap, playbackOffset, navigate]);

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
        console.error("Failed to load maps3d library:", e);
      }
    }
    load3D();
    return () => { active = false; };
  }, [apiIsLoaded]);

  // Handle manual orientation adjustments by the user
  useEffect(() => {
    const mapEl = mapRef.current;
    if (!mapEl) return;

    const handleHeadingChange = () => {
      if (isProgrammaticUpdateRef.current) return;
      if (focusedVehicleId && cameraMode === 'chase') {
        const vehicle = vehicleStateMap.get(focusedVehicleId);
        if (vehicle) {
          userBearingOffsetRef.current = (mapEl.heading || 0) - vehicle.degBearing;
        }
      }
    };

    mapEl.addEventListener('gmp-headingchange', handleHeadingChange);
    return () => {
      mapEl.removeEventListener('gmp-headingchange', handleHeadingChange);
    };
  }, [focusedVehicleId, cameraMode, vehicleStateMap, version]);

  // Handle camera positioning when a vehicle is focused
  useEffect(() => {
    if (!focusedVehicleId || cameraMode === 'fixed') return;
    const vehicle = vehicleStateMap.get(focusedVehicleId);
    const mapEl = mapRef.current;
    if (vehicle && mapEl) {
      isProgrammaticUpdateRef.current = true;
      const currentRange = (mapEl.range && mapEl.range > 10) ? mapEl.range : 60.96;
      const currentTilt = (mapEl.tilt !== undefined && mapEl.tilt !== null) ? mapEl.tilt : 45;
      const targetHeading = vehicle.degBearing + userBearingOffsetRef.current;
      
      mapEl.center = getOffsetCenter(
        vehicle.degLatitude,
        vehicle.degLongitude,
        targetHeading,
        currentRange,
        currentTilt
      );
      mapEl.range = currentRange;
      mapEl.tilt = currentTilt;
      if (cameraMode === 'chase') {
        mapEl.heading = targetHeading;
      }
      isProgrammaticUpdateRef.current = false;
    }
  }, [focusedVehicleId, vehicleStateMap, version, cameraMode]);

  const lastFocusedIdRef = useRef<string>("");

  // Zoom in and center on the vehicle when focused
  useEffect(() => {
    if (!focusedVehicleId) {
      lastFocusedIdRef.current = "";
      return;
    }

    if (focusedVehicleId !== lastFocusedIdRef.current) {
      lastFocusedIdRef.current = focusedVehicleId;
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      const mapEl = mapRef.current;
      if (vehicle && mapEl) {
        userBearingOffsetRef.current = 0; // Reset offset on new focus
        isProgrammaticUpdateRef.current = true;
        
        mapEl.center = getOffsetCenter(
          vehicle.degLatitude,
          vehicle.degLongitude,
          vehicle.degBearing,
          60.96,
          45
        );
        mapEl.range = 60.96; // Zoom in close to see the vehicle model (approx 200 feet)
        mapEl.tilt = 45; // 45 degrees azimuth angle
        if (cameraMode === 'chase') {
          mapEl.heading = vehicle.degBearing;
        }
        isProgrammaticUpdateRef.current = false;
      }
    }
  }, [focusedVehicleId, vehicleStateMap, cameraMode]);

  // Handle keypress controls for camera range, tilt, and yaw
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const mapEl = mapRef.current;
      if (!mapEl) return;

      const activeEl = document.activeElement;
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'SELECT' || activeEl.tagName === 'TEXTAREA')) {
        return;
      }

      const key = e.key.toLowerCase();
      let handled = false;

      // I / O: follow distance (range)
      if (key === 'i') {
        isProgrammaticUpdateRef.current = true;
        mapEl.range = Math.max(10, (mapEl.range || 90) - 10);
        isProgrammaticUpdateRef.current = false;
        handled = true;
      } else if (key === 'o') {
        isProgrammaticUpdateRef.current = true;
        mapEl.range = Math.min(3000, (mapEl.range || 90) + 10);
        isProgrammaticUpdateRef.current = false;
        handled = true;
      }
      // K / L: pitch angle (tilt)
      else if (key === 'k') {
        isProgrammaticUpdateRef.current = true;
        mapEl.tilt = Math.max(0, Math.min(85, (mapEl.tilt || 65) - 3));
        isProgrammaticUpdateRef.current = false;
        handled = true;
      } else if (key === 'l') {
        isProgrammaticUpdateRef.current = true;
        mapEl.tilt = Math.max(0, Math.min(85, (mapEl.tilt || 65) + 3));
        isProgrammaticUpdateRef.current = false;
        handled = true;
      }
      // A / D: yaw offset (heading)
      else if (key === 'a') {
        isProgrammaticUpdateRef.current = true;
        if (focusedVehicleId && cameraMode === 'chase') {
          userBearingOffsetRef.current = (userBearingOffsetRef.current - 5 + 360) % 360;
          const vehicle = vehicleStateMap.get(focusedVehicleId);
          if (vehicle) {
            mapEl.heading = vehicle.degBearing + userBearingOffsetRef.current;
          }
        } else {
          mapEl.heading = ((mapEl.heading || 0) - 5 + 360) % 360;
        }
        isProgrammaticUpdateRef.current = false;
        handled = true;
      } else if (key === 'd') {
        isProgrammaticUpdateRef.current = true;
        if (focusedVehicleId && cameraMode === 'chase') {
          userBearingOffsetRef.current = (userBearingOffsetRef.current + 5) % 360;
          const vehicle = vehicleStateMap.get(focusedVehicleId);
          if (vehicle) {
            mapEl.heading = vehicle.degBearing + userBearingOffsetRef.current;
          }
        } else {
          mapEl.heading = ((mapEl.heading || 0) + 5) % 360;
        }
        isProgrammaticUpdateRef.current = false;
        handled = true;
      }

      if (handled) {
        e.preventDefault();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [focusedVehicleId, cameraMode, vehicleStateMap]);

  // Centering camera initially on the first active vehicle if available
  useEffect(() => {
    if (isDataLoaded && isMapReady && !hasCenteredInitially && vehicleList.length > 0) {
      const firstVehicle = vehicleList[0];
      const mapEl = mapRef.current;
      if (mapEl) {
        isProgrammaticUpdateRef.current = true;
        mapEl.center = {
          lat: firstVehicle.degLatitude,
          lng: firstVehicle.degLongitude,
          altitude: 0
        };
        mapEl.range = 1000;
        mapEl.tilt = 65;
        mapEl.heading = -20;
        isProgrammaticUpdateRef.current = false;
      }
      setHasCenteredInitially(true);
    }
  }, [isDataLoaded, isMapReady, vehicleList, hasCenteredInitially]);

  // Fit view bounds to contain all active vehicles
  const fitAllOnScreen = useCallback(() => {
    if (!isDataLoaded || vehicleList.length === 0) return;

    let minLng = 180, maxLng = -180, minLat = 90, maxLat = -90;
    vehicleList.forEach(v => {
      minLng = Math.min(minLng, v.degLongitude);
      maxLng = Math.max(maxLng, v.degLongitude);
      minLat = Math.min(minLat, v.degLatitude);
      maxLat = Math.max(maxLat, v.degLatitude);
    });

    const centerLat = (minLat + maxLat) / 2;
    const centerLng = (minLng + maxLng) / 2;
    const deltaLat = maxLat - minLat;
    const deltaLng = maxLng - minLng;

    const distLat = deltaLat * 111000;
    const distLng = deltaLng * 111000 * Math.cos(centerLat * Math.PI / 180);
    const maxDist = Math.max(distLat, distLng, 500);

    const mapEl = mapRef.current;
    if (mapEl) {
      isProgrammaticUpdateRef.current = true;
      mapEl.center = { lat: centerLat, lng: centerLng, altitude: 0 };
      mapEl.range = maxDist * 1.5;
      mapEl.heading = -20;
      mapEl.tilt = 65;
      isProgrammaticUpdateRef.current = false;
    }
  }, [isDataLoaded, vehicleList]);

  // Toggle Street (hybrid) vs Satellite maps
  const toggleMapStyle = useCallback(() => {
    setMapStyle(prev => (prev === 'satellite' ? 'hybrid' : 'satellite'));
  }, []);

  const focusedVehicle = vehicleStateMap.get(focusedVehicleId);
  const focusedVehicleColor = focusedVehicle?.colorCode;

  const shouldShowMap = isDataLoaded || vehicleList.length > 0;

  return (
    <div className="body row scroll-y">
        {shouldShowMap && isMapReady ? (
          <div className="map-container-3d">
            <gmp-map-3d
              ref={mapRef}
              mode={mapStyle}
              style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}
            >
              {vehicleList.map((vState) => (
                <GoogleVehicleModel
                  key={vState.id}
                  vState={vState}
                />
              ))}
            </gmp-map-3d>

            {/* Standard shared navigation bar */}
            <AppNavBar />

            {/* Timeline playback control */}
            <PlaybackClock />

            {/* Google Earth Navigation Guide */}
            <div className="controls-guide-card">
              <div
                className="controls-guide-title"
                onClick={() => setIsGuideMinimized(!isGuideMinimized)}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  width: '100%',
                  marginBottom: isGuideMinimized ? '0' : '6px',
                  borderBottom: isGuideMinimized ? 'none' : '1px solid rgba(0, 0, 0, 0.1)',
                  paddingBottom: isGuideMinimized ? '0' : '4px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <FontAwesomeIcon icon={faInfoCircle} />
                  <span>3D Camera Controls</span>
                </div>
                <FontAwesomeIcon icon={isGuideMinimized ? faChevronUp : faChevronDown} style={{ fontSize: '0.75rem', color: '#666', marginLeft: '8px' }} />
              </div>
              {!isGuideMinimized && (
                <ul className="controls-guide-list" style={{ paddingLeft: '16px', margin: 0, fontSize: '0.85rem' }}>
                  <li><strong>I / O</strong>: Follow closer / further (distance)</li>
                  <li><strong>K / L</strong>: Tilt down / up (pitch)</li>
                  <li><strong>A / D</strong>: Orbit left / right (yaw)</li>
                </ul>
              )}
            </div>

            {/* Focus Panel */}
            <div className="focus-panel-container">
              <Card className="focus-card">
                <Card.Body className="focus-card-body">
                  <div
                    className="focus-title"
                    onClick={() => setIsFocusMinimized(!isFocusMinimized)}
                    style={{
                      cursor: 'pointer',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: isFocusMinimized ? '0' : '8px'
                    }}
                  >
                    <span>Focus Target</span>
                    <FontAwesomeIcon icon={isFocusMinimized ? faChevronUp : faChevronDown} style={{ fontSize: '0.75rem', color: '#666' }} />
                  </div>
                  {!isFocusMinimized && (
                    <>
                      <Form.Select
                        size="sm"
                        className="focus-select"
                        value={focusedVehicleId}
                        onChange={(e) => setFocusedVehicleId(e.target.value)}
                        style={focusedVehicleColor ? { color: focusedVehicleColor, fontWeight: 'bold' } : undefined}
                      >
                        <option value="">-- Free Camera --</option>
                        {vehicleList.map((vehicle) => (
                          <option
                            key={vehicle.id}
                            value={vehicle.id}
                            style={vehicle.colorCode ? { color: vehicle.colorCode } : undefined}
                          >
                            {`Vehicle ${vehicle.id.substring(0, 8)}`}
                          </option>
                        ))}
                      </Form.Select>
                      {focusedVehicleId && (
                        <>
                          <div
                            className="mt-2 text-start"
                            style={{ fontSize: '0.8rem', borderTop: '1px solid #eee', paddingTop: '8px' }}
                          >
                            <div className="fw-bold mb-1" style={{ fontSize: '0.75rem', color: '#666' }}>Camera Mode</div>
                            <Form.Check
                              type="radio"
                              label="Chase View (Relative)"
                              name="cameraMode"
                              id="modeChase"
                              checked={cameraMode === 'chase'}
                              onChange={() => handleCameraModeChange('chase')}
                              style={{ cursor: 'pointer' }}
                            />
                            <Form.Check
                              type="radio"
                              label="Fixed Compass"
                              name="cameraMode"
                              id="modeFixed"
                              checked={cameraMode === 'fixed'}
                              onChange={() => handleCameraModeChange('fixed')}
                              style={{ cursor: 'pointer' }}
                            />
                          </div>
                          <Button
                            variant="outline-danger"
                            size="sm"
                            className="mt-2"
                            onClick={() => setFocusedVehicleId("")}
                            style={{ fontSize: '0.75rem', padding: '2px 8px', width: '100%' }}
                          >
                            Release Lock
                          </Button>
                        </>
                      )}
                    </>
                  )}
                </Card.Body>
              </Card>
            </div>

            {/* --- Float-Right Toolbar --- */}
            <div className="map-tools-container-3d">
              <Button
                variant="light"
                className="shadow-sm"
                onClick={fitAllOnScreen}
                title="Fit All Vehicles"
              >
                <FontAwesomeIcon icon={faUpRightAndDownLeftFromCenter} />
              </Button>

              <Button
                variant="light"
                className="shadow-sm"
                onClick={toggleMapStyle}
                title={mapStyle === 'satellite' ? "Hybrid Display" : "Satellite Display"}
              >
                <FontAwesomeIcon icon={mapStyle === 'satellite' ? faMap : faSatellite} />
              </Button>

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
                vehicleId={focusedVehicleId || undefined}
              />
            )}
        </div>
      ) : (
        <SpinnerLoading />
      )}
      </div>
  );
};
