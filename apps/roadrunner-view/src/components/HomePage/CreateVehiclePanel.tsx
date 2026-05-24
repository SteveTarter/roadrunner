import { Button, Card, Form, CardHeader, CardBody } from "react-bootstrap";
import { fetchAuthSession } from "aws-amplify/auth";
// eslint-disable-next-line
import { useNavigate } from "react-router-dom";
import { CONFIG } from "../../config";
import { Address } from '../../models/Address';
import { TripPlan } from '../../models/TripPlan';
import { PointPicker } from '../Shared/PointPicker';
import { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTimes } from "@fortawesome/free-solid-svg-icons";

export const CreateVehiclePanel = (props: {
  setIsCreateVehicleActive: any,
  returnToNow: any,
  mapRef: any,
}) => {
  const navigate = useNavigate();

  const [selectedOrigin, setSelectedOrigin] = useState<{lat: number, lng: number} | null>(null);
  const [selectedDestination, setSelectedDestination] = useState<{lat: number, lng: number} | null>(null);
  const [isPickingOriginCenter, setIsPickingOriginCenter] = useState(false);
  const [isPickingDestinationCenter, setIsPickingDestinationCenter] = useState(false);

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

    // Helper to build Address object from FormData
    const buildAddress = (prefix: string, point: {lat: number, lng: number} | null) => {
      return new Address(
        formData.get(`${prefix}AddressSource`) as string,
        formData.get(`${prefix}Address address-search`) as string || "",
        formData.get(`${prefix}Apartment`) as string || "",
        formData.get(`${prefix}City`) as string || "",
        formData.get(`${prefix}State`) as string || "",
        formData.get(`${prefix}ZIP`) as string || "",
        point?.lat || 0,
        point?.lng || 0
      );
    };

    const tripPlan = new TripPlan([
      buildAddress("origin", selectedOrigin),
      buildAddress("destination", selectedDestination)
    ]);

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
        credentials: 'include'
      });

      if (!response.ok) {
        const message = await readApiError(response);
        throw new Error(message);
      }

      const data = await response.json();

      // Hop into the vehicle.
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
<Card style={{ width: '20rem', alignSelf: 'end', top: 10 }}>

  <CardHeader className="d-flex justify-content-between align-items-center bg-dark text-white py-2">
    <h6 className="mb-0">Create Vehicle</h6>
    <Button onClick={cancelCreateVehicle} variant="white" className="text-white">
      <FontAwesomeIcon icon={faTimes} />
    </Button>
  </CardHeader>
  <CardBody>
    <>
      <Form>
        {!isPickingDestinationCenter && (
          <PointPicker
            label="Origin"
            color="#28a745"
            mapRef={props.mapRef}
            mapboxToken={CONFIG.MAPBOX_TOKEN}
            selectedPoint={selectedOrigin}
            onPointChange={setSelectedOrigin}
            addressPrefix="origin"
            isSelecting={isPickingOriginCenter}
            setIsSelecting={setIsPickingOriginCenter}
          />
        )}
        {!isPickingOriginCenter && (
          <PointPicker
            label="Destination"
            color="#dc3545"
            mapRef={props.mapRef}
            mapboxToken={CONFIG.MAPBOX_TOKEN}
            selectedPoint={selectedDestination}
            onPointChange={setSelectedDestination}
            addressPrefix="destination"
            isSelecting={isPickingDestinationCenter}
            setIsSelecting={setIsPickingDestinationCenter}
          />
        )}
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
  </CardBody>
</Card>
  );
}
