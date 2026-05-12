import { useState, useEffect, useRef } from 'react';
import mapboxgl from 'mapbox-gl';
import { Button, Card, Form, FormLabel } from "react-bootstrap";
import { Input } from "reactstrap";
import { fetchAuthSession } from "aws-amplify/auth";
// eslint-disable-next-line
import { AddressAutofill as MapboxAddressAutofill } from '@mapbox/search-js-react';
import { useNavigate } from "react-router-dom";
import { CONFIG } from "../../config";
import { Address } from '../../models/Address';
import { TripPlan } from '../../models/TripPlan';

export const CreateVehiclePanel = (props: {
  setIsCreateVehicleActive: any,
  returnToNow: any,
  mapRef: any,
}) => {
  const navigate = useNavigate();

  const mapboxToken = CONFIG.MAPBOX_TOKEN;

  const originMarkerRef = useRef<mapboxgl.Marker | null>(null);
  const destinationMarkerRef = useRef<mapboxgl.Marker | null>(null);

  const [isSelectingOriginOnMap, setIsSelectingOriginOnMap] = useState(false);
  const [isSelectingDestinationOnMap, setIsSelectingDestinationOnMap] = useState(false);
  const [selectedOrigin, setSelectedOrigin] = useState<{lat: number, lng: number} | null>(null);
  const [selectedDestination, setSelectedDestination] = useState<{lat: number, lng: number} | null>(null);

  // Logic to handle map clicks for origin when selection mode is active
  useEffect(() => {
    if (!props.mapRef?.current || !isSelectingOriginOnMap) return;

    const map = props.mapRef.current.getMap();

    const handleMapClick = (e: any) => {
      const { lng, lat } = e.lngLat;
      setSelectedOrigin({ lat, lng });
      setIsSelectingOriginOnMap(false); // Turn off selection mode after click
    };

    map.on('click', handleMapClick);
    return () => map.off('click', handleMapClick);
  }, [isSelectingOriginOnMap, props.mapRef]);

  // Logic to handle map clicks for destination when selection mode is active
  useEffect(() => {
    if (!props.mapRef?.current || !isSelectingDestinationOnMap) return;

    const map = props.mapRef.current.getMap();

    const handleMapClick = (e: any) => {
      const { lng, lat } = e.lngLat;
      setSelectedDestination({ lat, lng });
      setIsSelectingDestinationOnMap(false); // Turn off selection mode after click
    };

    map.on('click', handleMapClick);
    return () => map.off('click', handleMapClick);
  }, [isSelectingDestinationOnMap, props.mapRef]);

  // Logic to handle map cursor changes when selecting on map
  useEffect(() => {
    if (!props.mapRef?.current) return;

    const map = props.mapRef.current.getMap();

    if (isSelectingOriginOnMap || isSelectingDestinationOnMap) {
      map.getCanvas().style.cursor = 'crosshair'; //
    } else {
      map.getCanvas().style.cursor = ''; // Reset to default
    }
  }, [isSelectingOriginOnMap, isSelectingDestinationOnMap, props.mapRef]);

  // Effect for Origin Marker
  useEffect(() => {
    if (!props.mapRef?.current || !selectedOrigin) return;

    const map = props.mapRef.current.getMap();

    // Remove existing marker if it exists
    if (originMarkerRef.current) originMarkerRef.current.remove();

    // Create a popup to serve as a label
    const originPopup = new mapboxgl.Popup({
      offset: 25,
      closeButton: false,
      closeOnClick: false,
      className: 'marker-label'
    })
    .setText('Origin') //
    .addTo(map); //

    // Create new green marker for Origin
    const marker = new mapboxgl.Marker({ color: '#28a745' })
      .setLngLat([selectedOrigin.lng, selectedOrigin.lat])
      .setPopup(originPopup)
      .addTo(map);

    // Manually trigger the popup to stay open
    marker.togglePopup();

    originMarkerRef.current = marker;

    // Cleanup on unmount or change
    return () => { if (originMarkerRef.current) originMarkerRef.current.remove(); };
  }, [selectedOrigin, props.mapRef]);

  // Effect for Destination Marker
  useEffect(() => {
    if (!props.mapRef?.current || !selectedDestination) return;

    const map = props.mapRef.current.getMap();

    if (destinationMarkerRef.current) destinationMarkerRef.current.remove();

    // Create a popup to serve as a label
    const destinationPopup = new mapboxgl.Popup({
      offset: 25,
      closeButton: false,
      closeOnClick: false,
      className: 'marker-label'
    })
    .setText('Destination') //
    .addTo(map); //

    // Create new red marker for Destination
    const marker = new mapboxgl.Marker({ color: '#dc3545' })
      .setLngLat([selectedDestination.lng, selectedDestination.lat])
      .setPopup(destinationPopup)
      .addTo(map);

      // Manually trigger the popup to stay open
      marker.togglePopup();

    destinationMarkerRef.current = marker;

    return () => { if (destinationMarkerRef.current) destinationMarkerRef.current.remove(); };
  }, [selectedDestination, props.mapRef]);

  // Cast AddressAutofill so TypeScript stops complaining about JSX compatibility
  const AddressAutofill = MapboxAddressAutofill as any;

  type ApiErrorResponse = {
    message?: string;
    status?: number;
    timestamp?: string;
  };

  async function readApiError(response: Response): Promise<string> {
    const contentType = response.headers.get("content-type") ?? "";

    try {
      if (contentType.includes("application/json")) {
        const body = (await response.json()) as ApiErrorResponse;
        return body.message || `Request failed with status ${response.status}`;
      }

      const text = await response.text();
      return text || `Request failed with status ${response.status}`;
    } catch {
      return `Request failed with status ${response.status}`;
    }
  }

  const createVehicle = async (): Promise<void> => {
    const form = document.querySelector("form");
    if (!form) return;
    const formData = new FormData(form as HTMLFormElement);

    // Determine source and location
    const sourceOrigin = formData.get("originAddressSource") as string;
    const latOrigin = selectedOrigin ? selectedOrigin.lat : 0; // Default or parsed from form
    const lngOrigin = selectedOrigin ? selectedOrigin.lng : 0;
    const sourceDestination = formData.get("destinationAddressSource") as string;
    const latDestination = selectedDestination ? selectedDestination.lat : 0; // Default or parsed from form
    const lngDestination = selectedDestination ? selectedDestination.lng : 0;

    // Build the Address object per the model
    const origin = new Address(
      sourceOrigin, // Sets to "Numeric" if chosen via map
      formData.get("originAddress") as string,
      formData.get("originApartment") as string,
      formData.get("originCity") as string,
      formData.get("originState") as string,
      formData.get("originZip") as string,
      latOrigin,
      lngOrigin
    );
    const destination = new Address(
      sourceDestination, // Sets to "Numeric" if chosen via map
      formData.get("destinationAddress") as string,
      formData.get("destinationApartment") as string,
      formData.get("destinationCity") as string,
      formData.get("destinationState") as string,
      formData.get("destinationZip") as string,
      latDestination,
      lngDestination
    );

    const tripPlan = new TripPlan([origin, destination]); //

    const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/create-new`;
    props.returnToNow();
    try {
      // Get the latest session right before the call
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();

      if (!accessToken) {
        console.error("Session expired");
        return;
      }

      const response = await fetch(url, {
        method: 'post',
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(tripPlan),
      });

      if (!response.ok) {
        const message = await readApiError(response);
        throw new Error(message);
      }

      const data = await response.json();

      // Hop into the vehicle.  For now, since all new vehicles created through
      // the "/api/vehicle" API are created in the present time.
      navigate(`/driver-view/${data.id}`);
    } catch (error) {
      console.error(error);
      alert(error);
    }
  };

  function cancelCreateVehicle() {
    props.setIsCreateVehicleActive(false);
  }

  return (
<Card style={{ width: '20rem', alignSelf: 'end', top: 60 }}>
  <Card.Body>
    <Card.Title className="text-center">Create Vehicle</Card.Title>
    <>
      <Form>
        <FormLabel style={{ fontSize: "1.1rem" }}>Origin Address</FormLabel>
        <Button
          variant={isSelectingOriginOnMap ? "warning" : "outline-primary"}
          size="sm"
          className="mb-2 w-100"
          onClick={() => setIsSelectingOriginOnMap(!isSelectingOriginOnMap)}
        >
          {isSelectingOriginOnMap ? "Click a point on the map..." : "Choose point on map"}
        </Button>
        <AddressAutofill accessToken={mapboxToken}>
          <div>
            <Input
              name="originPoint"
              placeholder="Address"
              value={selectedOrigin ? `${selectedOrigin.lat.toFixed(6)}, ${selectedOrigin.lng.toFixed(6)}` : ""}
              readOnly={!!selectedOrigin}
              onChange={() => {}}
              style={{ marginBottom: "10px", width: "100%" }}
            />
            {/* Hidden field to track the source */}
            <input
              type="hidden"
              name="originAddressSource"
              value={selectedOrigin ? "NumericEntry" : "Mapbox"}
            />
          </div>
          <div>
            <Input
              name="originAddress"
              autoComplete="address-line1"
              placeholder="Address"
              style={{ marginBottom: "10px", width: "100%" }} />
            <Input
              name="originApartment"
              autoComplete="address-line2"
              placeholder="Apartment"
              style={{ marginBottom: "10px", width: "100%" }} />
          </div>
          <div className="d-flex justify-content-between">
            <Input
              name="originCity"
              autoComplete="address-level2"
              placeholder="City"
              style={{ flex: 2, marginRight: "5px" }} />
            <Input
              name="originState"
              autoComplete="address-level1"
              placeholder="State"
              style={{ flex: 1, marginRight: "5px" }} />
            <Input
              name="originZip"
              autoComplete="postal-code"
              placeholder="ZIP"
              style={{ flex: 1 }} />
          </div>
        </AddressAutofill>
        <AddressAutofill accessToken={mapboxToken}>
          <FormLabel style={{ fontSize: "1.1rem", marginTop: "20px" }}>Destination Address</FormLabel>
          <Button
            variant={isSelectingDestinationOnMap ? "warning" : "outline-primary"}
            size="sm"
            className="mb-2 w-100"
            onClick={() => setIsSelectingDestinationOnMap(!isSelectingDestinationOnMap)}
          >
            {isSelectingDestinationOnMap ? "Click a point on the map..." : "Choose point on map"}
          </Button>
          <div>
            <Input
              name="destinationPoint"
              placeholder="Address"
              value={selectedDestination ? `${selectedDestination.lat.toFixed(6)}, ${selectedDestination.lng.toFixed(6)}` : ""}
              readOnly={!!selectedDestination}
              onChange={() => {}}
              style={{ marginBottom: "10px", width: "100%" }}
            />
            {/* Hidden field to track the source */}
            <input
              type="hidden"
              name="destinationAddressSource"
              value={selectedDestination ? "NumericEntry" : "Mapbox"}
            />
          </div>
          <div>
            <Input
              name="destinationAddress"
              autoComplete="address-line1"
              placeholder="Address"
              style={{ marginBottom: "10px", width: "100%" }} />
            <Input
              name="destinationApartment"
              autoComplete="address-line2"
              placeholder="Apartment"
              style={{ marginBottom: "10px", width: "100%" }} />
          </div>
          <div className="d-flex justify-content-between">
            <Input
              name="destinationCity"
              autoComplete="address-level2"
              placeholder="City"
              style={{ flex: 2, marginRight: "5px" }} />
            <Input
              name="destinationState"
              autoComplete="address-level1"
              placeholder="State"
              style={{ flex: 1, marginRight: "5px" }} />
            <Input
              name="destinationZip"
              autoComplete="postal-code"
              placeholder="ZIP"
              style={{ flex: 1 }} />
          </div>
        </AddressAutofill>
        <div style={{ marginTop: '20px', display: "flex", justifyContent: "space-between" }}>
          <Button onClick={cancelCreateVehicle} value="CancelCreateVehicle" variant="secondary">
            Cancel
          </Button>
          <Button onClick={createVehicle} value="EnterRoute" variant="primary">
            Create
          </Button>
        </div>
      </Form>
    </>
  </Card.Body>
</Card>
  );
}
