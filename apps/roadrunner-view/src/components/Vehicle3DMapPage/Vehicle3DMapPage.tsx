import './Vehicle3DMapPage.css';
import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import Map, { useMap, ViewState } from "react-map-gl";
import { AppNavBar } from '../NavBar/AppNavBar';
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
  faInfoCircle,
  faChartLine
} from '@fortawesome/free-solid-svg-icons';
import { CONFIG } from "../../config";
import { useVehicleData } from '../../hooks/useVehicleData';
import { usePlayback } from "../../context/PlaybackContext";
import { ActiveVehiclePlot } from '../Shared/ActiveVehiclePlot';

export const Vehicle3DMapPage = () => {
  const { threeDMap } = useMap();
  const mapboxToken = CONFIG.MAPBOX_TOKEN;
  usePlayback();

  // Constants
  const MAP_STYLE_SATELLITE = "mapbox://styles/tarterwaresteve/cm518rzmq00fr01qpfkvcd4md";
  const MAP_STYLE_STREET = "mapbox://styles/mapbox/standard";

  // States
  const [mapStyle, setMapStyle] = useState(MAP_STYLE_SATELLITE);
  const [isMapReady, setIsMapReady] = useState(false);
  const [focusedVehicleId, setFocusedVehicleId] = useState<string>("");
  const [hasCenteredInitially, setHasCenteredInitially] = useState(false);
  const userBearingOffsetRef = useRef<number>(0);
  const [cameraMode, setCameraMode] = useState<'chase' | 'fixed'>('chase');
  const [showActiveVehiclePlot, setShowActiveVehiclePlot] = useState(false);

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
      pitch: 65,
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
    if (mode === 'chase' && focusedVehicleId) {
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      if (vehicle) {
        userBearingOffsetRef.current = mapViewState.bearing - vehicle.degBearing;
      }
    }
  }, [focusedVehicleId, vehicleStateMap, mapViewState.bearing]);

  // Derived list of active vehicles
  const vehicleList = useMemo(() => {
    return Array.from(vehicleStateMap.values());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, vehicleStateMap]);

  // Handle camera positioning when a vehicle is focused, maintaining bearing relative to vehicle's heading (if in chase mode)
  useEffect(() => {
    if (!focusedVehicleId) return;
    const vehicle = vehicleStateMap.get(focusedVehicleId);
    if (vehicle) {
      setMapViewState(prev => ({
        ...prev,
        longitude: vehicle.degLongitude,
        latitude: vehicle.degLatitude,
        ...(cameraMode === 'chase' ? { bearing: vehicle.degBearing + userBearingOffsetRef.current } : {})
      }));
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
      if (vehicle) {
        userBearingOffsetRef.current = 0; // Reset offset on new focus
        setMapViewState(prev => ({
          ...prev,
          longitude: vehicle.degLongitude,
          latitude: vehicle.degLatitude,
          zoom: 21.5, // Zoom in close to see the vehicle model
          ...(cameraMode === 'chase' ? { bearing: vehicle.degBearing } : {})
        }));
      }
    }
  }, [focusedVehicleId, vehicleStateMap, cameraMode]);

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
    // If the movement was triggered manually by user mouse or touch interaction,
    // update our relative bearing offset so the camera continues rotating with the vehicle (only in chase mode)
    if (focusedVehicleId && evt.originalEvent) {
      const vehicle = vehicleStateMap.get(focusedVehicleId);
      if (vehicle && cameraMode === 'chase') {
        userBearingOffsetRef.current = evt.viewState.bearing - vehicle.degBearing;
      }
    }
    setMapViewState(evt.viewState);
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
    setMapViewState(prev => ({
      ...prev,
      zoom: 16,
      pitch: 65,
      bearing: -20
    }));
  }, []);

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
            {/* Standard shared navigation bar */}
            <AppNavBar />

            {/* Timeline playback control */}
            <PlaybackClock />

            {/* Google Earth Navigation Guide */}
            <div className="controls-guide-card">
              <div className="controls-guide-title">
                <FontAwesomeIcon icon={faInfoCircle} />
                <span>3D Camera Controls</span>
              </div>
              <ul className="controls-guide-list">
                <li><strong>Left-Click + Drag</strong>: Pan map</li>
                <li><strong>Right-Click + Drag</strong>: Orbit / Tilt perspective</li>
                <li><strong>Ctrl + Drag</strong>: Tilt perspective</li>
                <li><strong>Scroll / Pinch</strong>: Zoom in/out</li>
              </ul>
            </div>

            {/* Focus Panel */}
            <div className="focus-panel-container">
              <Card className="focus-card">
                <Card.Body className="focus-card-body">
                  <div className="focus-title">Focus Target</div>
                  <Form.Select
                    size="sm"
                    className="focus-select"
                    value={focusedVehicleId}
                    onChange={(e) => setFocusedVehicleId(e.target.value)}
                  >
                    <option value="">-- Free Camera --</option>
                    {vehicleList.map((vehicle) => (
                      <option key={vehicle.id} value={vehicle.id}>
                        {`Vehicle ${vehicle.id.substring(0, 8)} (${vehicle.colorCode || 'Gray'})`}
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
