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
  isSelecting: boolean;
  setIsSelecting: (selecting: boolean) => void;
}

export const PointPicker = ({
  label,
  color,
  mapRef,
  mapboxToken,
  selectedPoint,
  onPointChange,
  addressPrefix,
  isSelecting,
  setIsSelecting
}: PointPickerProps) => {
  const [autofillFired, setAutofillFired] = useState(false);
  const markerRef = useRef<mapboxgl.Marker | null>(null);
  const googleMarkerRef = useRef<any>(null);
  const googleInfoWindowRef = useRef<any>(null);

  // Handle Map Clicks
  useEffect(() => {
    if (!mapRef?.current || !isSelecting) return;
    const map = mapRef.current.getMap();
    if (!map) return;

    const isMapbox = typeof map.on === 'function';

    if (isMapbox) {
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
    } else {
      // Google Maps
      const listener = map.addListener('click', (e: any) => {
        if (e.latLng) {
          const lat = e.latLng.lat();
          const lng = e.latLng.lng();
          onPointChange({ lat, lng });
          setIsSelecting(false);
        }
      });

      const div = map.getDiv ? map.getDiv() : null;
      if (div) {
        div.style.cursor = 'crosshair';
      }

      return () => {
        if (typeof google !== 'undefined' && google.maps?.event) {
          google.maps.event.removeListener(listener);
        }
        if (div) {
          div.style.cursor = '';
        }
      };
    }
  }, [isSelecting, setIsSelecting, mapRef, onPointChange]);

  const selectedLat = selectedPoint?.lat;
  const selectedLng = selectedPoint?.lng;

  // Handle Marker and Label rendering
  useEffect(() => {
    // 1. If map or selectedPoint is missing, remove everything
    if (!mapRef?.current || selectedLat === undefined || selectedLng === undefined) {
      if (markerRef.current) {
        markerRef.current.remove();
        markerRef.current = null;
      }
      if (googleMarkerRef.current) {
        googleMarkerRef.current.map = null;
        googleMarkerRef.current = null;
      }
      if (googleInfoWindowRef.current) {
        googleInfoWindowRef.current.close();
        googleInfoWindowRef.current = null;
      }
      return;
    }

    const map = mapRef.current.getMap();
    if (!map) return;

    const isMapbox = typeof map.on === 'function';

    if (isMapbox) {
      // Cleanup Google elements if we are switching engines
      if (googleMarkerRef.current) {
        googleMarkerRef.current.map = null;
        googleMarkerRef.current = null;
      }
      if (googleInfoWindowRef.current) {
        googleInfoWindowRef.current.close();
        googleInfoWindowRef.current = null;
      }

      // Reuse/update Mapbox marker
      if (markerRef.current) {
        markerRef.current.setLngLat([selectedLng, selectedLat]);
      } else {
        const popup = new mapboxgl.Popup({
          offset: 25,
          closeButton: false,
          closeOnClick: false,
          className: 'marker-label'
        })
        .setText(label)
        .addTo(map);

        const marker = new mapboxgl.Marker({ color })
          .setLngLat([selectedLng, selectedLat])
          .setPopup(popup)
          .addTo(map);

        marker.togglePopup();
        markerRef.current = marker;
      }
    } else {
      // Cleanup Mapbox elements if we are switching engines
      if (markerRef.current) {
        markerRef.current.remove();
        markerRef.current = null;
      }

      // Reuse/update Google AdvancedMarkerElement
      if (typeof google !== 'undefined' && google.maps && google.maps.marker?.AdvancedMarkerElement) {
        const position = { lat: selectedLat, lng: selectedLng };

        if (googleMarkerRef.current) {
          googleMarkerRef.current.position = position;
          if (googleInfoWindowRef.current) {
            googleInfoWindowRef.current.setPosition(position);
          }
        } else {
          const marker = new google.maps.marker.AdvancedMarkerElement({
            position: position,
            map: map,
            title: label
          });

          const infoWindow = new google.maps.InfoWindow({
            content: `<div style="color: #000; font-weight: bold; padding: 2px 5px;">${label}</div>`,
            disableAutoPan: true
          });

          infoWindow.open(map, marker);

          googleMarkerRef.current = marker;
          googleInfoWindowRef.current = infoWindow;
        }
      }
    }
  }, [selectedLat, selectedLng, mapRef, color, label]);

  // Clean up all markers on absolute component unmount
  useEffect(() => {
    return () => {
      if (markerRef.current) {
        markerRef.current.remove();
        markerRef.current = null;
      }
      if (googleMarkerRef.current) {
        googleMarkerRef.current.map = null;
        googleMarkerRef.current = null;
      }
      if (googleInfoWindowRef.current) {
        googleInfoWindowRef.current.close();
        googleInfoWindowRef.current = null;
      }
    };
  }, []);

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