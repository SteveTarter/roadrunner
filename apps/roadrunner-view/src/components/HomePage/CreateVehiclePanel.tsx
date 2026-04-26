import { Button, Card, Form, FormLabel } from "react-bootstrap";
import { Input } from "reactstrap";
import { fetchAuthSession } from "aws-amplify/auth";
// eslint-disable-next-line
import { AddressAutofill as MapboxAddressAutofill } from '@mapbox/search-js-react';
import { useNavigate } from "react-router-dom";
import { CONFIG } from "../../config";

export const CreateVehiclePanel = (props: {
  setIsCreateVehicleActive: any,
  returnToNow: any,
}) => {
  const navigate = useNavigate();

  const mapboxToken = CONFIG.MAPBOX_TOKEN;

  // Cast AddressAutofill so TypeScript stops complaining about JSX compatibility
  const AddressAutofill = MapboxAddressAutofill as any;

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
        body: JSON.stringify(formattedData),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }

      const data = await response.json();

      // Hop into the vehicle.  For now, since all new vehicles created through
      // the "/api/vehicle" API are created in the present time.
      navigate(`/driver-view/${data.id}`);
    } catch (error) {
      console.error("Error creating new vehicle:", error);
      alert("Failed to create new vehicle.");
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
        <AddressAutofill accessToken={mapboxToken}>
          <FormLabel style={{ fontSize: "1.1rem" }}>Origin Address</FormLabel>
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
