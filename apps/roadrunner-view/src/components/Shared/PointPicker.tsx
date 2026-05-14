import { useState, useEffect, useRef } from 'react';
import mapboxgl from 'mapbox-gl';
import { Button, FormLabel } from "react-bootstrap";
import { Input } from "reactstrap";
// @ts-ignore
import { AddressAutofill as MapboxAddressAutofill } from '@mapbox/search-js-react';

interface PointPickerProps {
  label: string;
  color: string;
  mapRef: any;
  mapboxToken: string;
  selectedPoint: { lat: number; lng: number } | null;
  onPointChange: (point: { lat: number; lng: number } | null) => void;
  addressPrefix: string; // e.g., "origin" or "destination"}
}

export const PointPicker = ({
 label,
  color,
  mapRef,
  mapboxToken,
  selectedPoint,
  onPointChange,
  addressPrefix
}: PointPickerProps) => {
  const [isSelecting, setIsSelecting] = useState(false);
  const [autofillFired, setAutofillFired] = useState(false);
  const markerRef = useRef<mapboxgl.Marker | null>(null);

  // Handle Map Clicks
  useEffect(() => {
    if (!mapRef?.current || !isSelecting) return;
    const map = mapRef.current.getMap();

    const handleMapClick = (e: any) => {
      const { lng, lat } = e.lngLat;
      onPointChange({ lat, lng });
      setIsSelecting(false);
    };

    map.on('click', handleMapClick);
    map.getCanvas().style.cursor = 'crosshair';

    return () => {
      map.off('click', handleMapClick);
      map.getCanvas().style.cursor = '';
    };
  }, [isSelecting, mapRef, onPointChange]);

  // Handle Marker and Label rendering
  useEffect(() => {
    if (!mapRef?.current || !selectedPoint) {
      // Remove existing marker if it exists
      if (markerRef.current) markerRef.current.remove();
      return;
    }

    const map = mapRef.current.getMap();

    // Remove existing marker if it exists
    if (markerRef.current) markerRef.current.remove();

    // Create a popup to serve as a label
    const popup = new mapboxgl.Popup({
      offset: 25,
      closeButton: false,
      closeOnClick: false,
      className: 'marker-label'
    })
    .setText(label)
    .addTo(map);

    // Create new marker for point.
    const marker = new mapboxgl.Marker({ color })
      .setLngLat([selectedPoint.lng, selectedPoint.lat])
      .setPopup(popup)
      .addTo(map);

    // Manually trigger the popup to stay open
    marker.togglePopup();

    markerRef.current = marker;

    // Cleanup on unmount or change
    return () => { if (markerRef.current) markerRef.current.remove(); };
  }, [selectedPoint, mapRef, color, label]);

  // Handler to snap marker to address location
  const handleRetrieve = (res: any) => {
    const feature = res.features[0];
    if (feature && feature.geometry && feature.geometry.coordinates) {
      const [lng, lat] = feature.geometry.coordinates;
      // This updates the state, which triggers the marker useEffect
      onPointChange({ lat, lng });
      setAutofillFired(true);
    }
  };

  const AddressAutofill = MapboxAddressAutofill as any;

  return (
    <AddressAutofill
      accessToken={mapboxToken}
      onRetrieve={handleRetrieve}
    >
      <div className="mb-4">
        <FormLabel style={{ fontSize: "1.1rem" }}>{label} Address</FormLabel>
        <Button
          variant={isSelecting ? "warning" : "outline-primary"}
          size="sm"
          className="mb-2 w-100"
          onClick={() => setIsSelecting(!isSelecting)}
        >
          {isSelecting ? "Click a point on the map..." : `Choose ${label} on map`}
        </Button>

        {/* Always render the Point display when a point is selected */}
        {(!autofillFired && (isSelecting || selectedPoint)) && (
          <div>
            <Input
              name={`${addressPrefix}Point`}
              placeholder="Lat, Lng"
              value={selectedPoint ? `${selectedPoint.lat.toFixed(6)}, ${selectedPoint.lng.toFixed(6)}` : ""}
              readOnly={!!selectedPoint}
              onChange={() => {}}
            />
            {/* Hidden field to track the source */}
            <input type="hidden" name={`${addressPrefix}AddressSource`} value="NumericEntry" />

            {selectedPoint && (
              <Button
                variant="link"
                size="sm"
                className="p-0 text-danger"
                onClick={() => onPointChange(null)}
              >
                Clear {label}
              </Button>
            )}
          </div>
        )}
        <div style={{ display: (!autofillFired && (isSelecting || selectedPoint)) ? 'none' : 'block' }}>
          <div>
            <Input
              name={`${addressPrefix}Address`}
              autoComplete="address-line1"
              placeholder="Address"
              style={{ marginBottom: "10px", width: "100%" }}
            />
            <Input
              name={`${addressPrefix}Apartment`}
              autoComplete="address-line2"
              placeholder="Apartment"
              style={{ marginBottom: "10px", width: "100%" }}
            />
          </div>
          <div className="d-flex justify-content-between">
            <Input
              name={`${addressPrefix}City`}
              autoComplete="address-level2"
              placeholder="City"
              style={{ flex: 2, marginRight: "5px" }}
            />
            <Input
              name={`${addressPrefix}State`}
              autoComplete="address-level1"
              placeholder="State"
              style={{ flex: 1, marginRight: "5px" }} />
            <Input
              name={`${addressPrefix}ZIP`}
              autoComplete="postal-code"
              placeholder="ZIP"
              style={{ flex: 1 }} />
          </div>
          <Input type="hidden" name={`${addressPrefix}AddressSource`} value="Mapbox" />
        </div>
      </div>
    </AddressAutofill>
  );
};