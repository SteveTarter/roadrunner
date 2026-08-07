import React, { useEffect, useState } from "react";
import { AdvancedMarker, InfoWindow } from "@vis.gl/react-google-maps";
import { getCachedAuthToken } from "../Utils/AuthUtils";
import { VehicleDisplay } from "../../models/VehicleDisplay";
import { VehicleState } from "../../models/VehicleState";
import { Button, Card } from "react-bootstrap";
import { getRestUrl } from "../../config";
import { GooglePolyline } from "./GooglePolyline";
import { useNavigate } from "react-router-dom";
import { usePlayback } from "../../context/PlaybackContext";

export const GoogleVehicleIcon: React.FC<{
  vehicleState: VehicleState,
  vehicleDisplay: VehicleDisplay
}> = (props) => {
  const navigate = useNavigate();
  const { dataSource } = usePlayback();
  const [token, setToken] = useState("");
  const [directionsGeometry, setDirectionsGeometry] = useState<any[]>([]);
  const [popupVisible, setPopupVisible] = useState(props.vehicleDisplay.popupVisible);
  const [routeVisible, setRouteVisible] = useState(props.vehicleDisplay.routeVisible);
  const [popupPosition, setPopupPosition] = useState<{ lat: number, lng: number } | null>(null);

  // Sync state if props change (e.g. from parent/telemetry)
  useEffect(() => {
    setPopupVisible(props.vehicleDisplay.popupVisible);
    setRouteVisible(props.vehicleDisplay.routeVisible);
  }, [props.vehicleDisplay.popupVisible, props.vehicleDisplay.routeVisible]);

  // Freeze popup position when visible to prevent flickering and allow clicking the button while vehicle is moving
  useEffect(() => {
    if (popupVisible) {
      if (!popupPosition) {
        setPopupPosition({
          lat: props.vehicleState.degLatitude,
          lng: props.vehicleState.degLongitude
        });
      }
    } else {
      setPopupPosition(null);
    }
  }, [popupVisible, props.vehicleState.degLatitude, props.vehicleState.degLongitude, popupPosition]);

  const MPS_TO_MPH = 2.236936;

  // Load (and silently refresh) an access token
  useEffect(() => {
    let cancelled = false;

    async function loadToken() {
      if (token) return;

      try {
        const accessToken = await getCachedAuthToken();

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
        const apiPath = dataSource === 'postgis' ? '/api/db-vehicle' : '/api/vehicle';
        const getStatesUrl = getRestUrl(`${apiPath}/get-vehicle-directions/${props.vehicleState.id}`);
        fetch(getStatesUrl, {
          method: 'get',
          headers: {
            Authorization: `Bearer ${token}`,
          },
          credentials: 'include'
        })
          .then(async response => response.json())
          .then(data => {
            const dg = data.routes[0].legs.flatMap((leg: any) =>
              leg.steps.map((step: any) => step.geometry.coordinates)
            );
            setDirectionsGeometry(dg);
          });
      } catch (error: any) {
        console.log(`Error caught in fetchVehicleDirections: ${error.message}`);
      }
    }
    fetchVehicleDirections();
  }, [props.vehicleState.id, token, dataSource]);

  const pxSize = Math.round(2.5 * props.vehicleDisplay.size) + "px";

  // Map directions geometry [lng, lat] to google { lat, lng }
  const googlePath = React.useMemo(() => {
    const flatCoords = directionsGeometry.flat();
    return flatCoords.map((coord: any) => ({
      lat: coord[1],
      lng: coord[0]
    })).filter((pt: any) => pt.lat !== undefined && pt.lng !== undefined && !isNaN(pt.lat) && !isNaN(pt.lng));
  }, [directionsGeometry]);

  return (
    <>
      <AdvancedMarker
        position={{ lat: props.vehicleState.degLatitude, lng: props.vehicleState.degLongitude }}
        title={`Vehicle ${props.vehicleState.id.substring(0, 8)}`}
        onClick={() => {
          const nextPopup = !popupVisible;
          const nextRoute = !routeVisible;
          props.vehicleDisplay.popupVisible = nextPopup;
          props.vehicleDisplay.routeVisible = nextRoute;
          setPopupVisible(nextPopup);
          setRouteVisible(nextRoute);
        }}
      >
        <div style={{ 
          width: pxSize, 
          height: pxSize, 
          transform: `rotate(${(props.vehicleState.degBearing + 180.0) % 360}deg)`, 
          transformOrigin: 'center',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <svg id="triangle" viewBox="0 0 100 100" height="100%" width="100%">
            <polygon points="0 0 50 20 100 0 50 100" fill={props.vehicleState.colorCode} />
          </svg>
        </div>
      </AdvancedMarker>

      {googlePath.length > 0 && routeVisible && (
        <GooglePolyline
          path={googlePath}
          strokeColor={props.vehicleState.colorCode}
          strokeWeight={props.vehicleDisplay.size / 2.0}
          strokeOpacity={0.5}
        />
      )}

      {popupVisible && popupPosition && (
        <InfoWindow
          position={popupPosition}
          onCloseClick={() => {
            props.vehicleDisplay.popupVisible = false;
            setPopupVisible(false);
          }}
        >
          <div 
            style={{ color: '#000' }}
            onMouseDown={(e) => e.stopPropagation()}
            onMouseUp={(e) => e.stopPropagation()}
            onPointerDown={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
            onClick={(e) => e.stopPropagation()}
            onDoubleClick={(e) => e.stopPropagation()}
          >
            <Card.Body style={{ padding: '0px' }}>
              <Card.Text style={{ margin: '0 0 10px 0' }}>
                Vehicle: {props.vehicleState.id.substring(0, 8)}<br />
                Speed: {(Math.round(MPS_TO_MPH * props.vehicleState.metersPerSecond * 10) / 10).toFixed(1)} MPH<br />
                Bearing: {(Math.round(props.vehicleState.degBearing * 10) / 10).toFixed(1)}&deg;
              </Card.Text>
              <Button
                variant="primary"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  navigate(`/google/driver-view/${props.vehicleState.id}`);
                }}
              >
                Jump into vehicle
              </Button>
            </Card.Body>
          </div>
        </InfoWindow>
      )}
    </>
  );
};
