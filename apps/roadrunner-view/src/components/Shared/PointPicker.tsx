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

  const AddressAutofill = MapboxAddressAutofill as any;

  return (
    <div className="mb-4">
      <FormLabel style={{ fontSize: "1.1rem" }}>Origin Address</FormLabel>
      <Button
        variant={isSelecting ? "warning" : "outline-primary"}
        size="sm"
        className="mb-2 w-100"
        onClick={() => setIsSelecting(!isSelecting)}
      >
        {isSelecting ? "Click a point on the map..." : `Choose ${label} on map`}
      </Button>

      {(isSelecting || selectedPoint) ? (
        <div>
          <Input
            name={`${addressPrefix}Point`}
            placeholder="Lat, Lng"
            value={selectedPoint ? `${selectedPoint.lat.toFixed(6)}, ${selectedPoint.lng.toFixed(6)}` : ""}
            readOnly={!!selectedPoint}
            onChange={() => {}}
            className="mb-2"
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
      ) : (
        <AddressAutofill accessToken={mapboxToken}>
          <input type="hidden" name={`${addressPrefix}AddressSource`} value="Mapbox" />
          <div>
            <Input
              name={`${addressPrefix}Address`}
              autoComplete="address-line1"
              placeholder={`${label} Street Address`}
              style={{ marginBottom: "10px", width: "100%" }}
              className="mb-2"
            />
            <Input
              name={`${addressPrefix}Apartment`}
              autoComplete="address-line2"
              placeholder={`${label} Apartment`}
              style={{ marginBottom: "10px", width: "100%" }}
              className="mb-2"
            />
          </div>
          <div className="d-flex justify-content-between">
            <Input
              name={`${addressPrefix}City`}
              autoComplete="address-level2"
              placeholder={`${label} City`}
              style={{ flex: 2, marginRight: "5px" }}
            />
            <Input
              name={`${addressPrefix}State`}
              autoComplete="address-level1"
              placeholder={`${label} State`}
              style={{ flex: 1, marginRight: "5px" }} />
            <Input
              name={`${addressPrefix}Zip`}
              autoComplete="postal-code"
              placeholder={`${label} ZIP`}
              style={{ flex: 1 }} />
          </div>
        </AddressAutofill>
      )}
    </div>
  );
};