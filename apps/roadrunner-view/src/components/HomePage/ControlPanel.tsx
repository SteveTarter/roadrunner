import { Button, Card, Form, FormLabel } from "react-bootstrap";
import { VehicleState } from "../../models/VehicleState";
import { useEffect, useState } from "react";
import { Input } from "reactstrap";
import { useAuth0 } from "@auth0/auth0-react";
// eslint-disable-next-line
import { AddressAutofill } from '@mapbox/search-js-react';
import { useNavigate } from "react-router-dom";

export const ControlPanel = (props: {
  vehicleStateList: VehicleState[],
  hideAllRoutes: any,
  showAllRoutes: any,
  fitAllOnScreen: any,
  toggleMapStyle: any
}) => {
  const { getAccessTokenSilently } = useAuth0();
  const [token, setToken] = useState("");

  const [isActive, setIsActive] = useState(false);
  const [isActionPanelActive, setIsActionPanelActive] = useState(false);
  const [isCreateVehicleActive, setIsCreateVehicleActive] = useState(false);

  const navigate = useNavigate();
  
  const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN!;

  const AddressAutofill = require('@mapbox/search-js-react').AddressAutofill;

  useEffect(() => {
  if (token) {
    return;
  }

  const audience = process.env.REACT_APP_AUTH0_AUDIENCE;
  getAccessTokenSilently({
    authorizationParams: {
    audience: audience,
    }
  })
    .then(async token => {
    setToken(token);
    });
  }, [token, getAccessTokenSilently]);

  function toggleIsActive() {
  setIsActive(!isActive);
  }

  function toggleIsActionPanelActive() {
  setIsActionPanelActive(!isActionPanelActive);
  }

  function toggleIsCreateVehicleActive() {
  setIsCreateVehicleActive(!isCreateVehicleActive);
  }

  function cancelCreateVehicleActive() {
  setIsCreateVehicleActive(false);
  }

  const createVehicle = async (): Promise<void> => {
  const inputs = document.querySelectorAll("form input");
  const inputValues = Array.from(inputs).map(input => {
    const element = input as HTMLInputElement;
    return { name: element.name, value: element.value };
  });

  const formattedData = {
    listStops: [
    {
      address1: inputValues.find(input => input.name.startsWith("originAddress"))?.value || "",
      address2: inputValues.find(input => input.name.startsWith("originApartment"))?.value || "",
      city: inputValues.find(input => input.name.startsWith("originCity"))?.value || "",
      state: inputValues.find(input => input.name.startsWith("originState"))?.value || "",
      zipCode: parseInt(inputValues.find(input => input.name.startsWith("originZip"))?.value || "0")
    },
    {
      address1: inputValues.find(input => input.name.startsWith("destinationAddress"))?.value || "",
      address2: inputValues.find(input => input.name.startsWith("destinationApartment"))?.value || "",
      city: inputValues.find(input => input.name.startsWith("destinationCity"))?.value || "",
      state: inputValues.find(input => input.name.startsWith("destinationState"))?.value || "",
      zipCode: parseInt(inputValues.find(input => input.name.startsWith("destinationZip"))?.value || "0")
    }
    ]
  };

  console.log(formattedData);

  const url = `${process.env.REACT_APP_ROADRUNNER_REST_URL_BASE}/api/vehicle/create-new`;
  try {
    const response = await fetch(url, {
    method: 'post',
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(formattedData),
    });

    if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`);
    }

    const data = await response.json();
    console.log("Success:", data);
    setIsCreateVehicleActive(false);

    // Hop into the vehicle
    navigate(`/driver-view/${data.id}`);
  } catch (error) {
    console.error("Error creating new vehicle:", error);
    alert("Failed to create new vehicle.");
  }
  };

  function hideAllRoutes() {
  props.hideAllRoutes();
  setIsActionPanelActive(false);
  }

  function showAllRoutes() {
  props.showAllRoutes();
  setIsActionPanelActive(false);
  }

  function fitAllOnScreen() {
  props.fitAllOnScreen();
  setIsActionPanelActive(false);
  }

  function toggleMapStyle() {
  props.toggleMapStyle();
  setIsActionPanelActive(false);
  }

  let activeCount = 0;
  props.vehicleStateList.forEach((vehicleState) => {
  if (!vehicleState.positionLimited) {
    ++activeCount;
  }
  })
  return (
  <Card style={{ width: '18rem', alignSelf: 'end' }}>
    <Card.Body>
    <Card.Title>Control Panel</Card.Title>
    {!isActive && (
      <>
      <Card.Title></Card.Title><Form>
        <Button onClick={toggleIsActive} value="Show">Show</Button>
      </Form>
      </>
    )}
    {isActive && (
      <>
      <Card.Text>Vehicles: {activeCount} active / {props.vehicleStateList.length} total</Card.Text>
      {!isActionPanelActive && !isCreateVehicleActive && (
        <Form>
        <div>
          <Button onClick={toggleIsActionPanelActive} value="ShowActions">Show Actions</Button>
        </div>
        <div>
          <Button onClick={toggleIsCreateVehicleActive} value="Create Vehicle">Create Vehicle</Button>
        </div>
        </Form>
      )}
      {isActionPanelActive && (
        <Form>
        <Button onClick={hideAllRoutes} value="Hide All Routes">Hide All Routes</Button>
        <Button onClick={showAllRoutes} value="Show All Routes">Show All Routes</Button>
        <Button onClick={fitAllOnScreen} value="Fit All On Screen">Fit All On Screen</Button>
        <Button onClick={toggleMapStyle} value="Toggle Map Style">Toggle Map Style</Button>
        </Form>
      )}
      {isCreateVehicleActive && (
        <>
        <Form>
          <AddressAutofill accessToken={mapboxToken}>
          <div>
            <FormLabel>Origin Address</FormLabel>
            <Input
            name="originAddress"
            autoComplete="address-line1"
            placeholder="Address" />
            <Input
            name="originApartment"
            autoComplete="address-line2"
            placeholder="Apartment" />
            <Input
            name="originCity"
            autoComplete="address-level2"
            placeholder="City" />
            <Input
            name="originState"
            autoComplete="address-level1"
            placeholder="State" />
            <Input
            name="originZip"
            autoComplete="postal-code"
            placeholder="ZIP" />
          </div>
          </AddressAutofill>
          <AddressAutofill accessToken={mapboxToken}>
          <div>
            <FormLabel>Destination Address</FormLabel>
            <Input
            name="destinationAddress"
            autoComplete="address-line1"
            placeholder="Address" />
            <Input
            name="destinationApartment"
            autoComplete="address-line2"
            placeholder="Apartment" />
            <Input
            name="destinationCity"
            autoComplete="address-level2"
            placeholder="City" />
            <Input
            name="destinationState"
            autoComplete="address-level1"
            placeholder="State" />
            <Input
            name="destinationZip"
            autoComplete="postal-code"
            placeholder="ZIP" />
          </div>
          </AddressAutofill>
          <div style={{ marginTop: '10px' }}>
          <Button onClick={cancelCreateVehicleActive} value="CancelCreateVehicle">Cancel</Button>
          <Button onClick={createVehicle} value="EnterRoute">Create</Button>
          </div>
        </Form>
        </>
      )}
      <Form>
        <div style={{ marginTop: '10px' }}>
        <Button onClick={toggleIsActive} value="Hide">Hide</Button>
        </div>
      </Form>
      </>
    )}
    </Card.Body>
  </Card>
  );
}
