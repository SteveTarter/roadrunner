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

  const [cameraDistance, setCameraDistance] = useState<number>(60.96); // 200 feet in meters
  const [cameraAzimuth, setCameraAzimuth] = useState<number>(45); // Degrees offset behind vehicle
  const [cameraTilt, setCameraTilt] = useState<number>(45); // Tilt angle (defaults to 45)

  const mapRef = useRef<any>(null);
  const isProgrammaticUpdateRef = useRef<boolean>(false);
  const lastProgrammaticHeadingRef = useRef<number | null>(null);
  const lastProgrammaticRangeRef = useRef<number | null>(null);
  const lastProgrammaticTiltRef = useRef<number | null>(null);
  const isUserInteractingRef = useRef<boolean>(false);
  const wheelTimeoutRef = useRef<any>(null);

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
    if (mapEl) {
      const currentHeading = mapEl.heading || 0;
      if (mode === 'chase' && focusedVehicleId) {
        const vehicle = vehicleStateMap.get(focusedVehicleId);
        if (vehicle) {
          setCameraAzimuth((currentHeading - vehicle.degBearing + 720) % 360);
        }
      } else {
        setCameraAzimuth(currentHeading);
      }
    }
  }, [focusedVehicleId, vehicleStateMap]);

  // Derived list of active vehicles
  const vehicleList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  // Redirect to Google Home Page if the focused target vehicle goes invalid or its coordinates go to 0,0
  useEffect(() => {
    if (!focusedVehicleId || !isDataLoaded) return;

    const vehicle = vehicleStateMap.get(focusedVehicleId);
    if (!vehicle) {
      console.log(`Focused vehicle ${focusedVehicleId} went invalid. Returning home.`);
      navigate('/google/home');
    } else if (vehicle.degLatitude === 0 && vehicle.degLongitude === 0) {
      console.log(`Focused vehicle ${focusedVehicleId} coordinates went to 0,0. Returning home.`);
      navigate('/google/home');
    }
  }, [focusedVehicleId, vehicleStateMap, isDataLoaded, navigate]);

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

  // Handle user interaction detection (mousedown, touchstart, wheel)
  useEffect(() => {
    const mapEl = mapRef.current;
    if (!mapEl) return;

    const handleInteractionStart = () => {
      isUserInteractingRef.current = true;
    };

    const handleInteractionEnd = () => {
      isUserInteractingRef.current = false;
    };

    const handleWheel = () => {
      isUserInteractingRef.current = true;
      if (wheelTimeoutRef.current) clearTimeout(wheelTimeoutRef.current);
      wheelTimeoutRef.current = setTimeout(() => {
        isUserInteractingRef.current = false;
      }, 500);
    };

    mapEl.addEventListener('mousedown', handleInteractionStart);
    mapEl.addEventListener('touchstart', handleInteractionStart);
    window.addEventListener('mouseup', handleInteractionEnd);
    window.addEventListener('touchend', handleInteractionEnd);
    mapEl.addEventListener('wheel', handleWheel);

    return () => {
      mapEl.removeEventListener('mousedown', handleInteractionStart);
      mapEl.removeEventListener('touchstart', handleInteractionStart);
      window.removeEventListener('mouseup', handleInteractionEnd);
      window.removeEventListener('touchend', handleInteractionEnd);
      mapEl.removeEventListener('wheel', handleWheel);
      if (wheelTimeoutRef.current) clearTimeout(wheelTimeoutRef.current);
    };
  }, [isMapReady]);

  // Handle manual range, heading, and tilt adjustments by the user
  useEffect(() => {
    const mapEl = mapRef.current;
    if (!mapEl) return;

    const handleHeadingChange = () => {
      if (isProgrammaticUpdateRef.current || !isUserInteractingRef.current) return;
      const newHeading = mapEl.heading || 0;
      
      if (lastProgrammaticHeadingRef.current !== null && Math.abs(newHeading - lastProgrammaticHeadingRef.current) < 0.1) {
        lastProgrammaticHeadingRef.current = null;
        return;
      }

      if (focusedVehicleId) {
        const vehicle = vehicleStateMap.get(focusedVehicleId);
        if (vehicle) {
          if (cameraMode === 'chase') {
            setCameraAzimuth((newHeading - vehicle.degBearing + 720) % 360);
          } else {
            setCameraAzimuth(newHeading);
          }
        }
      } else {
        setCameraAzimuth(newHeading);
      }
    };

    const handleRangeChange = () => {
      if (isProgrammaticUpdateRef.current || !isUserInteractingRef.current) return;
      const newRange = mapEl.range;
      if (newRange === undefined || newRange <= 0) return;

      if (lastProgrammaticRangeRef.current !== null && Math.abs(newRange - lastProgrammaticRangeRef.current) < 0.1) {
        lastProgrammaticRangeRef.current = null;
        return;
      }

      setCameraDistance(newRange);
    };

    const handleTiltChange = () => {
      if (isProgrammaticUpdateRef.current || !isUserInteractingRef.current) return;
      const newTilt = mapEl.tilt;
      if (newTilt === undefined) return;

      if (lastProgrammaticTiltRef.current !== null && Math.abs(newTilt - lastProgrammaticTiltRef.current) < 0.1) {
        lastProgrammaticTiltRef.current = null;
        return;
      }

      setCameraTilt(newTilt);
    };

    mapEl.addEventListener('gmp-headingchange', handleHeadingChange);
    mapEl.addEventListener('gmp-rangechange', handleRangeChange);
    mapEl.addEventListener('gmp-tiltchange', handleTiltChange);
    return () => {
      mapEl.removeEventListener('gmp-headingchange', handleHeadingChange);
      mapEl.removeEventListener('gmp-rangechange', handleRangeChange);
      mapEl.removeEventListener('gmp-tiltchange', handleTiltChange);
    };
  }, [focusedVehicleId, cameraMode, vehicleStateMap, version]);

  // Handle camera positioning when a vehicle is focused
  useEffect(() => {
    if (!focusedVehicleId) return;
    const vehicle = vehicleStateMap.get(focusedVehicleId);
    const mapEl = mapRef.current;
    if (vehicle && mapEl) {
      isProgrammaticUpdateRef.current = true;
      const targetHeading = cameraMode === 'chase'
        ? (vehicle.degBearing + cameraAzimuth + 360) % 360
        : cameraAzimuth;
      
      lastProgrammaticHeadingRef.current = targetHeading;
      lastProgrammaticRangeRef.current = cameraDistance;
      lastProgrammaticTiltRef.current = cameraTilt;

      const currentCenter = mapEl.center;
      if (!currentCenter || Math.abs(currentCenter.lat - vehicle.degLatitude) > 0.000001 || Math.abs(currentCenter.lng - vehicle.degLongitude) > 0.000001) {
        mapEl.center = { lat: vehicle.degLatitude, lng: vehicle.degLongitude, altitude: 0 };
      }
      
      if (mapEl.range !== cameraDistance) {
        mapEl.range = cameraDistance;
      }
      
      if (mapEl.tilt !== cameraTilt) {
        mapEl.tilt = cameraTilt;
      }
      
      if (Math.abs((mapEl.heading || 0) - targetHeading) > 0.01) {
        mapEl.heading = targetHeading;
      }
      
      isProgrammaticUpdateRef.current = false;
    }
  }, [focusedVehicleId, vehicleStateMap, version, cameraMode, cameraDistance, cameraAzimuth, cameraTilt]);

  // Handle camera positioning when untethered (free camera)
  useEffect(() => {
    if (focusedVehicleId) return;
    const mapEl = mapRef.current;
    if (mapEl) {
      isProgrammaticUpdateRef.current = true;
      lastProgrammaticRangeRef.current = cameraDistance;
      lastProgrammaticTiltRef.current = cameraTilt;
      lastProgrammaticHeadingRef.current = cameraAzimuth;

      if (mapEl.range !== cameraDistance) {
        mapEl.range = cameraDistance;
      }
      if (mapEl.tilt !== cameraTilt) {
        mapEl.tilt = cameraTilt;
      }
      if (Math.abs((mapEl.heading || 0) - cameraAzimuth) > 0.01) {
        mapEl.heading = cameraAzimuth;
      }
      isProgrammaticUpdateRef.current = false;
    }
  }, [focusedVehicleId, cameraDistance, cameraAzimuth, cameraTilt]);

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
        isProgrammaticUpdateRef.current = true;
        setCameraDistance(60.96); // Reset to 200 feet
        setCameraAzimuth(45); // Reset to 45 degrees
        setCameraTilt(45); // Reset to 45 degrees
        
        mapEl.center = { lat: vehicle.degLatitude, lng: vehicle.degLongitude, altitude: 0 };
        mapEl.range = 60.96;
        mapEl.tilt = 45;
        if (cameraMode === 'chase') {
          mapEl.heading = (vehicle.degBearing + 45) % 360;
        } else {
          mapEl.heading = 45;
        }
        isProgrammaticUpdateRef.current = false;
      }
    }
  }, [focusedVehicleId, vehicleStateMap, cameraMode]);

  // Handle keypress controls for camera range, tilt, and yaw
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const activeEl = document.activeElement;
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'SELECT' || activeEl.tagName === 'TEXTAREA')) {
        return;
      }

      const key = e.key.toLowerCase();
      let handled = false;

      // I / O: follow distance (range)
      if (key === 'i') {
        setCameraDistance(prev => Math.max(10, prev - 10));
        handled = true;
      } else if (key === 'o') {
        setCameraDistance(prev => Math.min(3000, prev + 10));
        handled = true;
      }
      // K / L: pitch angle (tilt)
      else if (key === 'k') {
        setCameraTilt(prev => Math.max(0, Math.min(85, prev - 3)));
        handled = true;
      } else if (key === 'l') {
        setCameraTilt(prev => Math.max(0, Math.min(85, prev + 3)));
        handled = true;
      }
      // A / D: yaw offset (heading)
      else if (key === 'a') {
        setCameraAzimuth(prev => (prev - 5 + 360) % 360);
        handled = true;
      } else if (key === 'd') {
        setCameraAzimuth(prev => (prev + 5) % 360);
        handled = true;
      }

      if (handled) {
        e.preventDefault();
        e.stopPropagation();
      }
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  }, []);

  // Centering camera initially on active vehicles
  useEffect(() => {
    if (isDataLoaded && isMapReady && !hasCenteredInitially && vehicleList.length > 0) {
      const validVehicles = vehicleList.filter(v => v.degLatitude !== 0 || v.degLongitude !== 0);
      if (validVehicles.length > 0) {
        const mapEl = mapRef.current;
        if (mapEl) {
          const avgLat = validVehicles.reduce((sum, v) => sum + v.degLatitude, 0) / validVehicles.length;
          const avgLng = validVehicles.reduce((sum, v) => sum + v.degLongitude, 0) / validVehicles.length;

          isProgrammaticUpdateRef.current = true;
          mapEl.center = {
            lat: avgLat,
            lng: avgLng,
            altitude: 0
          };
          mapEl.range = 1000;
          mapEl.tilt = 65;
          mapEl.heading = -20;
          isProgrammaticUpdateRef.current = false;
          setHasCenteredInitially(true);
        }
      }
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

                          <div className="mt-2 text-start" style={{ fontSize: '0.8rem', borderTop: '1px solid #eee', paddingTop: '8px' }}>
                            <div className="d-flex justify-content-between align-items-center mb-1">
                              <span className="fw-bold" style={{ fontSize: '0.75rem', color: '#666' }}>Distance</span>
                              <span style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>{Math.round(cameraDistance / 0.3048)} ft</span>
                            </div>
                            <div className="d-flex gap-1 mb-2">
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraDistance(prev => Math.max(3.048, prev - 3.048))}
                              >
                                -10ft
                              </Button>
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraDistance(prev => Math.min(914.4, prev + 3.048))}
                              >
                                +10ft
                              </Button>
                            </div>

                            <div className="d-flex justify-content-between align-items-center mb-1">
                              <span className="fw-bold" style={{ fontSize: '0.75rem', color: '#666' }}>Azimuth</span>
                              <span style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>{Math.round(cameraAzimuth)}°</span>
                            </div>
                            <div className="d-flex gap-1 mb-2">
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraAzimuth(prev => (prev - 5 + 360) % 360)}
                              >
                                -5°
                              </Button>
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraAzimuth(prev => (prev + 5) % 360)}
                              >
                                +5°
                              </Button>
                            </div>
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
