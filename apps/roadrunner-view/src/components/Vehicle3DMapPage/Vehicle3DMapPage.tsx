import './Vehicle3DMapPage.css';
import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { useNavigate, useSearchParams } from 'react-router-dom';
import Map, { useMap, ViewState } from "react-map-gl";
import { PlaybackClock } from '../Utils/PlaybackClock';
import { SpinnerLoading } from "../Utils/SpinnerLoading";
import { Button, Card, Form } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { 
  faSatellite, 
  faMap, 
  faMagic, 
  faBars, 
  faUpRightAndDownLeftFromCenter, 
  faCompass,
  faChartLine,
  faChevronDown,
  faChevronUp,
  faExchangeAlt,
  faHome
} from '@fortawesome/free-solid-svg-icons';
import { CONFIG } from "../../config";
import { useVehicleData } from '../../hooks/useVehicleData';
import { usePlayback } from "../../context/PlaybackContext";
import { ActiveVehiclePlot } from '../Shared/ActiveVehiclePlot';
import mapboxgl from 'mapbox-gl';

// Configure Mapbox performance settings globally
mapboxgl.workerCount = 4;
mapboxgl.maxParallelImageRequests = 32;

export const Vehicle3DMapPage = () => {
  const navigate = useNavigate();
  const { threeDMap } = useMap();
  const mapboxToken = CONFIG.MAPBOX_TOKEN;
  usePlayback();

  // Constants
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md?optimize=true";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";

  // States
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_SATELLITE);
  const [isMapReady, setIsMapReady] = useState(false);
  const [searchParams] = useSearchParams();
  const initialVehicleId = searchParams.get('vehicleId') || "";
  const [focusedVehicleId, setFocusedVehicleId] = useState<string>(initialVehicleId);
  const [hasCenteredInitially, setHasCenteredInitially] = useState(false);
  const missingTimestampRef = useRef<number | null>(null);
  const [cameraMode, setCameraMode] = useState<'chase' | 'fixed'>('chase');
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);
  const [isFocusMinimized, setIsFocusMinimized] = useState(false);

  // Camera state variables matching Google version
  const [cameraDistance, setCameraDistance] = useState<number>(60.96); // 200 feet in meters
  const [cameraYaw, setCameraYaw] = useState<number>(0);
  const [cameraElevation, setCameraElevation] = useState<number>(15);

  const toggleShowActiveVehiclePlot = useCallback(() => {
    setShowActiveVehiclePlot(prev => !prev);
  }, []);

  // View state for Mapbox camera control
  const [mapViewState, setMapViewState] = useState<ViewState>(() => {
    const saved = sessionStorage.getItem('roadrunner_3d_viewstate');
    return saved ? JSON.parse(saved) : {
      longitude: -97.5,
      latitude: 32.75,
      zoom: 16,
      pitch: 75,
      bearing: -20,
      padding: { top: 0, bottom: 0, left: 0, right: 0 }
    };
  });

  // Save viewState to sessionStorage
  useEffect(() => {
    sessionStorage.setItem('roadrunner_3d_viewstate', JSON.stringify(mapViewState));
  }, [mapViewState]);

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
    const currentHeading = mapViewState.bearing || 0;
    if (mode === 'chase' && focusedVehicleId) {
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      if (vehicle) {
        const rawYaw = (currentHeading - vehicle.degBearing + 720) % 360;
        const roundedYaw = Math.round(rawYaw / 5) * 5 % 360;
        setCameraYaw(roundedYaw > 180 ? roundedYaw - 360 : roundedYaw);
      }
    } else {
      setCameraYaw(currentHeading);
    }
  }, [focusedVehicleId, vehicleStateMap, mapViewState.bearing]);

  // Derived list of active vehicles
  const vehicleList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  // Redirect to Home Page if the focused target vehicle goes invalid or its coordinates go to 0,0 (with 10-second leniency)
  useEffect(() => {
    if (!focusedVehicleId || !isDataLoaded) {
      missingTimestampRef.current = null;
      return;
    }

    const vehicle = vehicleStateMap.get(focusedVehicleId);
    if (!vehicle || (vehicle.degLatitude === 0 && vehicle.degLongitude === 0)) {
      if (missingTimestampRef.current === null) {
        missingTimestampRef.current = Date.now();
      } else if (Date.now() - missingTimestampRef.current > 10000) {
        console.log(`Focused vehicle ${focusedVehicleId} coordinates invalid/missing for 10s. Returning home.`);
        navigate('/home');
      }
    } else {
      missingTimestampRef.current = null; // Reset when vehicle is valid
    }
  }, [focusedVehicleId, vehicleStateMap, isDataLoaded, version, navigate]);

  const lastFocusedIdRef = useRef<string>("");

  // Reset camera settings on new vehicle focus
  useEffect(() => {
    if (!focusedVehicleId) {
      lastFocusedIdRef.current = "";
      return;
    }

    if (focusedVehicleId !== lastFocusedIdRef.current) {
      lastFocusedIdRef.current = focusedVehicleId;
      setCameraDistance(60.96); // Reset to 200 feet (60.96m)
      setCameraYaw(0); // Reset to 0 degrees
      setCameraElevation(15); // Reset to 15 degrees (75 pitch)
    }
  }, [focusedVehicleId]);

  // Handle camera positioning when a vehicle is focused, maintaining bearing/pitch/zoom relative to state variables
  useEffect(() => {
    if (!focusedVehicleId) return;
    const vehicle = vehicleStateMap.get(focusedVehicleId);
    if (vehicle) {
      const calculatedZoom = 24.5 - Math.log2(cameraDistance);
      const calculatedPitch = 90 - cameraElevation;
      const calculatedBearing = cameraMode === 'chase' 
        ? (vehicle.degBearing + cameraYaw) 
        : cameraYaw;
        
      setMapViewState(prev => ({
        ...prev,
        longitude: vehicle.degLongitude,
        latitude: vehicle.degLatitude,
        zoom: calculatedZoom,
        pitch: calculatedPitch,
        bearing: (calculatedBearing + 360) % 360
      }));
    }
  }, [focusedVehicleId, vehicleStateMap, version, cameraMode, cameraDistance, cameraYaw, cameraElevation]);

  // Centering camera initially on the first active vehicle if available
  useEffect(() => {
    if (isDataLoaded && !hasCenteredInitially && vehicleList.length > 0) {
      const firstVehicle = vehicleList[0];
      setMapViewState(prev => ({
        ...prev,
        longitude: firstVehicle.degLongitude,
        latitude: firstVehicle.degLatitude,
        zoom: 16
      }));
      setHasCenteredInitially(true);
    }
  }, [isDataLoaded, vehicleList, hasCenteredInitially]);

  // Setup Mapbox custom 3D model, DEM terrain, and sky/fog layers
  useEffect(() => {
    const map = threeDMap?.getMap();
    if (!map) return;

    const setupLayers = () => {
      if (!map.isStyleLoaded()) {
        setTimeout(setupLayers, 200);
        return;
      }

      try {
        // 1. Add the 3D Mitsubishi Car Model
        if (!map.hasModel('mitsubishi-car')) {
          map.addModel('mitsubishi-car', '/models/mitsubishi/source/Untitled.glb');
        }

        // 2. Add GeoJSON source for position tracking
        if (!map.getSource('vehicle-positions')) {
          map.addSource('vehicle-positions', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] }
          });
        }

        // 3. Add Custom 3D Model Layer mapping vehicle entities to coordinates
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
              'model-color': ['get', 'vehicleColor'],
              'model-color-mix-intensity': 0.7
            }
          });
        }

        // 4. Add Raster DEM Terrain for 3D topology
        if (!map.getSource('mapbox-dem')) {
          map.addSource('mapbox-dem', {
            type: 'raster-dem',
            url: 'mapbox://mapbox.mapbox-terrain-dem-v1',
            tileSize: 512,
            maxzoom: 14
          });
          map.setTerrain({ source: 'mapbox-dem', exaggeration: 1.0 });
        }

        // 5. Add Sky Layer for realistic atmospheric horizon scattering
        if (!map.getLayer('sky-layer')) {
          map.addLayer({
            id: 'sky-layer',
            type: 'sky',
            paint: {
              'sky-type': 'atmosphere',
              'sky-atmosphere-sun': [0.0, 90.0],
              'sky-atmosphere-sun-intensity': 15
            }
          });
        }

        // 6. Set Fog for performance on pitched views
        map.setFog({});

        // 7. Disable raster fade-in transition animation to save GPU/CPU cycles
        const style = map.getStyle();
        if (style && style.layers) {
          style.layers.forEach((layer) => {
            if (layer.type === 'raster') {
              map.setPaintProperty(layer.id, 'raster-fade-duration', 0);
            }
          });
        }

        setIsMapReady(true);
      } catch (e) {
        console.error("Error building 3D layers:", e);
      }
    };

    map.on('style.load', setupLayers);
    setupLayers();

    return () => {
      map.off('style.load', setupLayers);
    };
  }, [threeDMap, mapStyle]);

  // Feed active vehicle position updates to the Mapbox source
  useEffect(() => {
    const map = threeDMap?.getMap();
    if (!map || !isMapReady) return;

    const features = vehicleList.map((vState) => ({
      type: 'Feature',
      properties: {
        id: vState.id,
        bearing: vState.degBearing,
        vehicleColor: vState.colorCode || '#FFFFFF'
      },
      geometry: {
        type: 'Point',
        coordinates: [vState.degLongitude, vState.degLatitude]
      }
    }));

    const source: any = map.getSource('vehicle-positions');
    if (source) {
      source.setData({ type: 'FeatureCollection', features });
    }
  }, [version, vehicleList, threeDMap, isMapReady]);

  // Handle map movement and user camera interactions
  const onMove = useCallback((evt: any) => {
    setMapViewState(evt.viewState);
    
    if (focusedVehicleId && evt.originalEvent) {
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      if (vehicle) {
        // 1. Calculate cameraDistance from zoom
        const calculatedDistance = Math.pow(2, 24.5 - evt.viewState.zoom);
        setCameraDistance(calculatedDistance);
        
        // 2. Calculate cameraElevation from pitch
        setCameraElevation(90 - evt.viewState.pitch);
        
        // 3. Calculate cameraYaw from bearing
        if (cameraMode === 'chase') {
          const yaw = (evt.viewState.bearing - vehicle.degBearing + 360) % 360;
          setCameraYaw(yaw > 180 ? yaw - 360 : yaw);
        } else {
          setCameraYaw(evt.viewState.bearing);
        }
      }
    }
  }, [focusedVehicleId, vehicleStateMap, cameraMode]);

  // Detect vehicle clicks to change focus target
  const onClick = useCallback((event: any) => {
    const map = threeDMap?.getMap();
    if (!map) return;

    let closestVehicleId = "";
    let minDistance = 40; // Max click radius in pixels

    vehicleList.forEach((vehicle) => {
      const point = threeDMap.project([vehicle.degLongitude, vehicle.degLatitude]);
      if (point) {
        const dx = point.x - event.point.x;
        const dy = point.y - event.point.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < minDistance) {
          minDistance = dist;
          closestVehicleId = vehicle.id;
        }
      }
    });

    if (closestVehicleId) {
      setFocusedVehicleId(closestVehicleId);
    } else {
      setFocusedVehicleId("");
    }
  }, [threeDMap, vehicleList]);

  // Fit view bounds to contain all active vehicles
  const fitAllOnScreen = useCallback(() => {
    if (!isDataLoaded || vehicleList.length === 0) return;

    let minLongitude = 360.0;
    let minLatitude = 360.0;
    let maxLongitude = -360.0;
    let maxLatitude = -360.0;

    vehicleList.forEach((vehicle) => {
      minLatitude = Math.min(minLatitude, vehicle.degLatitude);
      minLongitude = Math.min(minLongitude, vehicle.degLongitude);
      maxLatitude = Math.max(maxLatitude, vehicle.degLatitude);
      maxLongitude = Math.max(maxLongitude, vehicle.degLongitude);
    });

    // Expand bounds buffer slightly
    const deltaLng = Math.max(0.005, maxLongitude - minLongitude);
    const deltaLat = Math.max(0.005, maxLatitude - minLatitude);

    threeDMap?.fitBounds([
      [minLongitude - deltaLng * 0.1, minLatitude - deltaLat * 0.1],
      [maxLongitude + deltaLng * 0.1, maxLatitude + deltaLat * 0.1]
    ], {
      pitch: mapViewState.pitch,
      bearing: mapViewState.bearing,
      duration: 1000
    });
  }, [isDataLoaded, vehicleList, threeDMap, mapViewState.pitch, mapViewState.bearing]);

  // Toggle Street vs Satellite maps
  const toggleMapStyle = useCallback(() => {
    setIsMapReady(false);
    setMapStyle(prev => (prev === MAP_STYLE_STREET ? MAP_STYLE_SATELLITE : MAP_STYLE_STREET));
  }, []);

  // Reset view to default perspective
  const resetCamera = useCallback(() => {
    if (focusedVehicleId) {
      setCameraDistance(60.96);
      setCameraYaw(0);
      setCameraElevation(15);
    } else {
      setMapViewState(prev => ({
        ...prev,
        zoom: 16,
        pitch: 75,
        bearing: -20
      }));
    }
  }, [focusedVehicleId]);

  const focusedVehicle = vehicleStateMap.get(focusedVehicleId);
  const focusedVehicleColor = focusedVehicle?.colorCode;

  const shouldShowMap = isDataLoaded || vehicleList.length > 0;

  return (
    <div className="body row scroll-y">
      {shouldShowMap ? (
        <div className="map-container-3d">
          <Map
            id="threeDMap"
            {...mapViewState}
            onMove={onMove}
            onDragStart={() => setFocusedVehicleId("")}
            onClick={onClick}
            mapStyle={mapStyle}
            mapboxAccessToken={mapboxToken}
            maxPitch={85}
          >

            {/* Timeline playback control */}
            <PlaybackClock />

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
                              <span className="fw-bold" style={{ fontSize: '0.75rem', color: '#666' }}>{cameraMode === 'chase' ? 'Yaw' : 'Yaw (Compass)'}</span>
                              <span style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>{Math.round(cameraYaw)}°</span>
                            </div>
                            <div className="d-flex gap-1 mb-2">
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraYaw(prev => (prev - 5 + 360) % 360)}
                              >
                                -5°
                              </Button>
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraYaw(prev => (prev + 5) % 360)}
                              >
                                +5°
                              </Button>
                            </div>

                            <div className="d-flex justify-content-between align-items-center mb-1">
                              <span className="fw-bold" style={{ fontSize: '0.75rem', color: '#666' }}>Elevation</span>
                              <span style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>{Math.round(cameraElevation)}°</span>
                            </div>
                            <div className="d-flex gap-1 mb-2">
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraElevation(prev => Math.max(0, prev - 5))}
                              >
                                -5°
                              </Button>
                              <Button
                                variant="outline-secondary"
                                size="sm"
                                style={{ flex: 1, padding: '2px 0', fontSize: '0.75rem' }}
                                onClick={() => setCameraElevation(prev => Math.min(85, prev + 5))}
                              >
                                +5°
                              </Button>
                            </div>
                          </div>

                          <Button 
                            variant="outline-danger" 
                            size="sm" 
                            className="mt-2"
                            onClick={() => {
                              if (window.confirm("Are you sure?")) {
                                setFocusedVehicleId("");
                              }
                            }}
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
                onClick={() => {
                  const provider = localStorage.getItem('roadrunner_map_provider') || 'google';
                  navigate(provider === 'google' ? '/google/home' : '/home');
                }}
                title="Home"
              >
                <FontAwesomeIcon icon={faHome} />
              </Button>
              {focusedVehicleId && (
                <Button
                  variant="light"
                  className="shadow-sm border border-primary animate-pulse"
                  onClick={() => {
                    const provider = localStorage.getItem('roadrunner_map_provider') || 'google';
                    navigate(provider === 'google' ? `/google/driver-view/${focusedVehicleId}` : `/driver-view/${focusedVehicleId}`);
                  }}
                  title="Switch to Driver View"
                >
                  <FontAwesomeIcon icon={faExchangeAlt} className="text-primary" />
                </Button>
              )}
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
                title={mapStyle === MAP_STYLE_STREET ? "Satellite Display" : "Street Display"}
              >
                <FontAwesomeIcon icon={mapStyle === MAP_STYLE_STREET ? faSatellite : faMap} />
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

              <Button
                variant="light"
                className="shadow-sm"
                onClick={resetCamera}
                title="Reset Camera Angle"
              >
                <FontAwesomeIcon icon={faCompass} />
              </Button>
            </div>
            {showActiveVehiclePlot && (
              <ActiveVehiclePlot
                toggleShowActiveVehiclePlot={toggleShowActiveVehiclePlot}
                vehicleId={focusedVehicleId || undefined}
              />
            )}
          </Map>
        </div>
      ) : (
        <SpinnerLoading />
      )}
    </div>
  );
};
