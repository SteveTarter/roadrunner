import { useState, useEffect } from 'react';
import { Button, Card, Form, FormGroup, FormLabel } from "react-bootstrap";
import { Input } from "reactstrap";
import { fetchAuthSession } from "aws-amplify/auth";
import { CONFIG } from "../../config";
import { PointPicker } from '../Shared/PointPicker';

export const CrissCrossPanel = (props: {
  setIsCrissCrossActive: any,
  returnToNow: any,
  mapRef: any,
}) => {
  const [selectedCenter, setSelectedCenter] = useState<{lat: number, lng: number} | null>(null);
  const [vehicleCount, setVehicleCount] = useState<number>(15);
  const [kmRadius, setKmRadius] = useState<number>(10.0);
  const [inProgress, setInProgress] = useState(false);

  // Helper to create a GeoJSON Circle Polygon
  const createGeoJSONCircle = (center: {lat: number, lng: number}, radiusInKm: number, points = 64) => {
    const coords = { latitude: center.lat, longitude: center.lng };
    const km = radiusInKm;
    const ret = [];
    const distanceX = km / (111.32 * Math.cos(coords.latitude * Math.PI / 180));
    const distanceY = km / 110.574;

    for (let i = 0; i < points; i++) {
      const theta = (i / points) * (2 * Math.PI);
      const x = distanceX * Math.cos(theta);
      const y = distanceY * Math.sin(theta);
      ret.push([coords.longitude + x, coords.latitude + y]);
    }
    ret.push(ret[0]); // Close the polygon

    return {
      type: 'Feature',
      geometry: { type: 'Polygon', coordinates: [ret] }
    };
  };

  // Helper to calculate a single coordinate based on bearing and distance
  const getPointAtBearing = (center: {lat: number, lng: number}, distanceKm: number, bearingDeg: number) => {
    const radiusEarth = 6371; // Earth's radius in km
    const dist = distanceKm / radiusEarth;
    const brng = (bearingDeg * Math.PI) / 180;
    const lat1 = (center.lat * Math.PI) / 180;
    const lon1 = (center.lng * Math.PI) / 180;

    const lat2 = Math.asin(Math.sin(lat1) * Math.cos(dist) +
                Math.cos(lat1) * Math.sin(dist) * Math.cos(brng));
    const lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(dist) * Math.cos(lat1),
                Math.cos(dist) - Math.sin(lat1) * Math.sin(lat2));

    return [((lon2 * 180) / Math.PI), ((lat2 * 180) / Math.PI)];
  };

  // Manage Map Circle Visualization
  useEffect(() => {
    if (!props.mapRef?.current || !selectedCenter) return;
    const map = props.mapRef.current.getMap();

    const sourceId = 'criss-cross-radius-source';
    const layerId = 'criss-cross-radius-layer';
    const outlineId = 'criss-cross-radius-outline';
    const pointsSourceId = 'criss-cross-points-source';
    const pointsLayerId = 'criss-cross-points-layer';

    const circleData = createGeoJSONCircle(selectedCenter, kmRadius);

    // Vehicle Start Points Logic (Mirroring Server Calculation)
    const degIncrement = 360.0 / vehicleCount;
    const degStartBearing = degIncrement / 2.0;

    const pointsFeatures = [];
    for (let i = 0; i < vehicleCount; i++) {
      const bearing = degStartBearing + (i * degIncrement);
      const coords = getPointAtBearing(selectedCenter, kmRadius, bearing);
      pointsFeatures.push({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: coords }
      });
    }

    const pointsData = { type: 'FeatureCollection', features: pointsFeatures };

    if (map.getSource(sourceId)) {
      (map.getSource(sourceId) as mapboxgl.GeoJSONSource).setData(circleData as any);
    } else {
      map.addSource(sourceId, { type: 'geojson', data: circleData as any });

      // Add Fill Layer
      map.addLayer({
        id: layerId,
        type: 'fill',
        source: sourceId,
        paint: { 'fill-color': '#007bff', 'fill-opacity': 0.2 }
      });

      // Add Outline Layer
      map.addLayer({
        id: outlineId,
        type: 'line',
        source: sourceId,
        paint: { 'line-color': '#007bff', 'line-width': 2, 'line-dasharray': [2, 1] }
      });
    }

    if (map.getSource(pointsSourceId)) {
      (map.getSource(pointsSourceId) as mapboxgl.GeoJSONSource).setData(pointsData as any);
    } else {
      map.addSource(pointsSourceId, { type: 'geojson', data: pointsData as any });

      // Add Layer for the vehicle markers (small filled circles)
      map.addLayer({
        id: pointsLayerId,
        type: 'circle',
        source: pointsSourceId,
        paint: {
          'circle-radius': 4,
          'circle-color': '#007bff',
          'circle-stroke-width': 1,
          'circle-stroke-color': '#ffffff'
        }
      });
    }

    // Cleanup: Remove layers and source when data or component is cleared
    return () => {
      if (map.getLayer(layerId)) map.removeLayer(layerId);
      if (map.getLayer(outlineId)) map.removeLayer(outlineId);
      if (map.getSource(sourceId)) map.removeSource(sourceId);
      if (map.getLayer(pointsLayerId)) map.removeLayer(pointsLayerId);
      if (map.getSource(pointsSourceId)) map.removeSource(pointsSourceId);    };
  }, [selectedCenter, kmRadius, vehicleCount, props.mapRef]);

  const createCrissCross = async (): Promise<void> => {
    setInProgress(true);

    const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/create-crisscross`;

    const payload = {
      degLatitude: selectedCenter?.lat || 0,
      degLongitude: selectedCenter?.lng || 0,
      kmRadius: kmRadius,
      vehicleCount: vehicleCount,
    };

    props.returnToNow();

    try {
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();

      if (!accessToken) {
        console.error("Session expired");
        setInProgress(false);
        return;
      }

      const response = await fetch(url, {
        method: 'post',
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        setInProgress(false);
        throw new Error(`Request failed with status ${response.status}`);
      }

      setInProgress(false);
    } catch (error) {
      console.error(error);
      setInProgress(false);
      alert(error);
    }
  };

  return (
    <Card style={{ width: '20rem', alignSelf: 'end', top: 60 }}>
      <Card.Body>
        <Card.Title className="text-center mb-4">Create Criss-Cross</Card.Title>
        <Form>
          <PointPicker
            label="Center"
            color="#007bff"
            mapRef={props.mapRef}
            mapboxToken={CONFIG.MAPBOX_TOKEN}
            selectedPoint={selectedCenter}
            onPointChange={setSelectedCenter}
            addressPrefix="center"
          />

          <FormGroup className="mb-3">
            <FormLabel>Vehicle Count</FormLabel>
            <Input
              type="number"
              value={vehicleCount}
              onChange={(e) => setVehicleCount(parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup className="mb-4">
            <FormLabel>Radius (km)</FormLabel>
            <Input
              type="number"
              step="0.1"
              value={kmRadius}
              onChange={(e) => setKmRadius(parseFloat(e.target.value))}
            />
          </FormGroup>

          <div className="d-flex justify-content-between mt-3">
            <Button onClick={() => props.setIsCrissCrossActive(false)} variant="secondary">
              Cancel
            </Button>
            <Button
              onClick={createCrissCross}
              variant="primary"
              disabled={!selectedCenter || inProgress}
            >
              Generate
            </Button>
          </div>
        </Form>
      </Card.Body>
    </Card>
  );
};