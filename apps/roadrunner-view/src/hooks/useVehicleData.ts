import { useState, useEffect, useCallback, useRef } from 'react';
import { fetchAuthSession } from "aws-amplify/auth";
import { CONFIG } from "../config";
import { usePlayback } from "../context/PlaybackContext";
import { VehicleState } from '../models/VehicleState';
import { VehicleDisplay } from '../models/VehicleDisplay';
import { MapWrapper } from '../components/Utils/MapWrapper';

interface UseVehicleDataProps {
  pageNumber?: number;
  setPageNumber?: (page: number) => void;
  vehicleSize: number;
  intervalMs?: number;
}

export const useVehicleData = ({
  pageNumber = 0,
  setPageNumber,
  vehicleSize,
  intervalMs = 100
}: UseVehicleDataProps) => {
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const isFetchingRef = useRef(false);
  const { playbackOffset } = usePlayback();

  // Keep the refs inside the hook
  const vehicleStateMapRef = useRef(new MapWrapper<string, VehicleState>());
  const vehicleDisplayMapRef = useRef(new MapWrapper<string, VehicleDisplay>());
  const [version, setVersion] = useState(0);

  const SECS_VEHICLE_TIMEOUT = 30;

  const fetchData = useCallback(async () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;

    try {
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();
      if (!accessToken) return;

      const restUrlBase = CONFIG.ROADRUNNER_REST_URL_BASE;

      let url = `${restUrlBase}/api/playback/state?page=${pageNumber}`;

      // Calculate the target timestamp, if needed
      if (playbackOffset !== 0) {
        const targetDate = new Date(Date.now() - playbackOffset);
        const isoTimestamp = targetDate.toISOString();
        url += `&timestamp=${encodeURIComponent(isoTimestamp)}`;
      }

      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      if (response.ok) {
        const result = await response.json();

        // Handle pagination increment
        if (setPageNumber) {
          const totalPages = result.page?.totalPages || 1;
          setPageNumber((pageNumber + 1) % totalPages);
        }

        if (result._embedded?.vehicleStates) {
          result._embedded.vehicleStates.forEach((state: VehicleState) => {
            vehicleStateMapRef.current.set(state.id, state);

            // Manage Display objects inside the hook
            if (!vehicleDisplayMapRef.current.get(state.id)) {
              vehicleDisplayMapRef.current.set(state.id, new VehicleDisplay(vehicleSize, false, false));
            } else {
              // Update size for existing vehicles
              const display = vehicleDisplayMapRef.current.get(state.id);
              if (display) display.size = vehicleSize;
            }
          });
        }

        // Cleanup timed-out vehicles
        const timeoutThreshold = Date.now() - (SECS_VEHICLE_TIMEOUT * 1000) - playbackOffset;
        vehicleStateMapRef.current = vehicleStateMapRef.current.filter(
          state => state.msEpochLastRun > timeoutThreshold
        );

        setVersion(v => v + 1);
        setIsDataLoaded(true);
      }
    } catch (error) {
      console.error("Fetch error:", error);
    } finally {
      isFetchingRef.current = false;
    }
  }, [playbackOffset, pageNumber, setPageNumber, vehicleSize]);

  useEffect(() => {
    const timer = window.setInterval(fetchData, intervalMs);
    return () => window.clearInterval(timer);
  }, [fetchData, intervalMs]);

// Provide helper functions to modify display state from the UI
  const clearData = useCallback(() => {
    vehicleStateMapRef.current.clear();
    vehicleDisplayMapRef.current.clear();
    setVersion(v => v + 1);
  }, []);

  const setAllRoutesVisibility = useCallback((visible: boolean) => {
    vehicleDisplayMapRef.current.forEach((display: { routeVisible: boolean; }) => {
      display.routeVisible = visible;
    });
    setVersion(v => v + 1);
  }, []);

  return {
    vehicleStateMap: vehicleStateMapRef.current,
    vehicleDisplayMap: vehicleDisplayMapRef.current,
    isDataLoaded,
    version, // Used for useMemo dependencies in components
    clearData,
    setAllRoutesVisibility
  };
};