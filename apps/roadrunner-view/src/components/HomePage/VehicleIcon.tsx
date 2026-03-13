import { Layer, Marker, Popup, Source } from "react-map-gl";
import { fetchAuthSession } from "aws-amplify/auth";
import type { LayerProps } from "react-map-gl";
import type { Feature } from "geojson";
import { useEffect, useState } from "react";
import { VehicleDisplay } from "../../models/VehicleDisplay";
import { VehicleState } from "../../models/VehicleState";
import { Button, Card } from "react-bootstrap";
import { CONFIG } from "../../config";

export const VehicleIcon: React.FC<{
  vehicleState: VehicleState,
  vehicleDisplay: VehicleDisplay
}> = (props) => {

  const [token, setToken] = useState("");
  const [directionsGeometry, setDirectionsGeometry] = useState([]);

  const MPS_TO_MPH = 2.236936;

  // Load (and silently refresh) an access token
  useEffect(() => {
    let cancelled = false;

    async function loadToken() {
      if (token) return;

      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("No access token available. Route guard should have redirected to login.");
          return;
        }

        if (!cancelled) setToken(accessToken);
      } catch (error: any) {
        console.error("Error fetching token:", error?.message ?? error);
      }
    }

    loadToken();
    return () => { cancelled = true; };
  }, [token]);

  useEffect(() => {

    function fetchVehicleDirections() {
      if (!token || token.length === 0) {
        return;
      }

      try {
        // Get the latest VehicleStates
        const restUrlBase = CONFIG.ROADRUNNER_REST_URL_BASE;
        const getStatesUrl: string = `${restUrlBase}/api/vehicle/get-vehicle-directions/${props.vehicleState.id}`;
        fetch(getStatesUrl, {
          method: 'get',
          headers: {
            Authorization: `Bearer ${token}`,
          }
        })
          .then(async response => response.json())
          .then(data => {
            let dg: any = [];
            for (let i = 0; i < data.routes[0].legs[0].steps.length; ++i) {
              dg.push(data.routes[0].legs[0].steps[i].geometry.coordinates);
            }

            setDirectionsGeometry(dg);
          });

      }
      catch (error: any) {
        console.log(`Error caught in fetchVehicleDirections: ${error.message}`);
      }
    };
    fetchVehicleDirections();
  }, [props.vehicleState.id, token]);

  function getLineLayer(id: string, colorCode: string, vehicleSize: number): LayerProps {
    return {
      id: `line-${id}`,
      type: 'line',
      paint: {
        'line-width': vehicleSize / 2.0,
        'line-color': `${colorCode}`,
        'line-opacity': 0.5
      }
    };
  }

  function getLineData(id: string, directionsGeometry: any): Feature {
    return {
      type: 'Feature',
      properties: {
        title: `line-${id}`
      },
      geometry: {
        type: 'MultiLineString',
        coordinates: directionsGeometry
      }
    };
  }

  let lineData = getLineData(props.vehicleState.id, directionsGeometry);
  let lineLayer = getLineLayer(props.vehicleState.id, props.vehicleState.colorCode, props.vehicleDisplay.size);
  let lineVisible = props.vehicleDisplay.routeVisible;
  let popupVisible = props.vehicleDisplay.popupVisible;
  let pxSize = Math.round(2.5 * props.vehicleDisplay.size) + "px";

  return (
    <>
      <Marker key={`point-{id}`}
        longitude={props.vehicleState.degLongitude}
        latitude={props.vehicleState.degLatitude}
        rotation={(props.vehicleState.degBearing + 180.0) % 360}>
        <svg id="triangle" viewBox="0 0 100 100" height={pxSize}>
          <polygon points="0 0 50 20 100 0 50 100" fill={props.vehicleState.colorCode}/>
        </svg>
      </Marker>
      {directionsGeometry && lineVisible && (
        <Source type='geojson' data={lineData}>
          <Layer {...lineLayer} />
        </Source>
      )}
      {popupVisible && (
        <Popup
          longitude={props.vehicleState.degLongitude}
          latitude={props.vehicleState.degLatitude}
          anchor="bottom-left">
          <Card.Body>
            <Card.Text>
            Speed: {(Math.round(MPS_TO_MPH * props.vehicleState.metersPerSecond * 10) / 10).toFixed(1)} MPH<br/>
            Bearing: {(Math.round(props.vehicleState.degBearing * 10) / 10).toFixed(1)}&deg;
            </Card.Text>
            <Button variant="primary" href={`/driver-view/${props.vehicleState.id}`}>
              Jump into vehicle
            </Button>
          </Card.Body>
        </Popup>
      )}
    </>
  );
}
