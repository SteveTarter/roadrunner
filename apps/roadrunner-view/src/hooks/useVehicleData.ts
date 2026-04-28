import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { fetchAuthSession } from "aws-amplify/auth";
import { CONFIG } from "../config";
import { usePlayback } from "../context/PlaybackContext";
import { VehicleState } from '../models/VehicleState';
import { VehicleDisplay } from '../models/VehicleDisplay';
import { MapWrapper } from '../components/Utils/MapWrapper';

interface UseVehicleDataProps {
  vehicleSize: number;
  intervalMs?: number;
}

export const useVehicleData = ({
  vehicleSize,
  intervalMs = 100
}: UseVehicleDataProps) => {
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const isFetchingRef = useRef(false);
  const { playbackOffset } = usePlayback();

  // The master storage of every state we've fetched
  const masterBuffer = useRef<VehicleState[]>([]);

  // The active maps used by the UI
  const vehicleStateMapRef = useRef(new MapWrapper<string, VehicleState>());
  const vehicleDisplayMapRef = useRef(new MapWrapper<string, VehicleDisplay>());
  const [version, setVersion] = useState(0);
  const [bufferNum, setBufferNum] = useState(0);
  /**
   * FIX: Create a stable time tag anchored to the moment the offset changes.
   * This ensures all page requests for this "batch" use the same server cache.
   */
  const timeAnchor = useMemo(() => {
    if (playbackOffset === 0) return null;
    const bufferLeadTime = 10000; // 10 seconds ahead
    return new Date(Date.now() - playbackOffset + bufferLeadTime).toISOString();
// eslint-disable-next-line
  }, [playbackOffset, bufferNum]); // Only changes if the user scrubs playback

  /**
   * THE FETCHER: Background process to swallow pages of data.
   */
  const fetchBatch = useCallback(async () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;

    try {
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();
      if (!accessToken) return;

      // We start at page 0 for every batch
      let currentPage = 0;
      let totalPages = 1;

      // Loop until we have swallowed every page for the current timeAnchor
      while (currentPage < totalPages) {
        let url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/playback/state?page=${currentPage}`;

        if (timeAnchor) {
          url += `&timestamp=${encodeURIComponent(timeAnchor)}`;
          url += "&windowPeriod=5s";
        }

        const response = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        if (!response.ok) break; // Exit loop on error

        const result = await response.json();

        if (result._embedded?.vehicleStates) {
          // USE A SET TO PREVENT DUPLICATES
          // When overlapping batches, we might fetch the same state twice.
          // We filter the incoming data to only include things we don't have.
          const existingIds = new Set(masterBuffer.current.map(s => `${s.id}-${s.msEpochLastRun}`));
          const newStates = result._embedded.vehicleStates.filter(
            (s: VehicleState) => !existingIds.has(`${s.id}-${s.msEpochLastRun}`)
          );

          // Append new states to master buffer
          masterBuffer.current = [...masterBuffer.current, ...newStates];
        }

        totalPages = result.page?.totalPages || 1;
        currentPage++;

        setIsDataLoaded(true);
      }

      // Once the entire loop is done, we've exhausted this anchor
      setBufferNum(prev => prev + 1);
    } catch (error) {
      console.error("Fetch error:", error);
    } finally {
      isFetchingRef.current = false;
    }
  // eslint-disable-next-line
  }, [timeAnchor]);

  /**
   * THE PLAYBACK ENGINE: Syncs the UI maps to the specific playback time.
   */
  const syncMapsToPlaybackTime = useCallback(() => {
    const currentTime = Date.now() - playbackOffset;
    const SECS_VEHICLE_TIMEOUT = 30;

    // Filter buffer for states that match our current playback window
    // We want the most recent state for each vehicle that is NOT in the future
    const activeStates = masterBuffer.current.filter(s => s.msEpochLastRun <= currentTime);

    // Group by ID to get the latest state for each vehicle
    const latestPerVehicle = new Map<string, VehicleState>();
    activeStates.forEach(state => {
      const existing = latestPerVehicle.get(state.id);
      if (!existing || state.msEpochLastRun > existing.msEpochLastRun) {
        latestPerVehicle.set(state.id, state);
      }
    });

    // Update the UI MapWrapper
    latestPerVehicle.forEach((state, id) => {
      vehicleStateMapRef.current.set(id, state);

      if (!vehicleDisplayMapRef.current.get(id)) {
        vehicleDisplayMapRef.current.set(id, new VehicleDisplay(vehicleSize, false, false));
      } else {
        const display = vehicleDisplayMapRef.current.get(id);
        if (display) display.size = vehicleSize;
      }
    });

    // Housekeeping: Remove old states from UI and Master Buffer
    const timeoutThreshold = currentTime - (SECS_VEHICLE_TIMEOUT * 1000);

    vehicleStateMapRef.current = vehicleStateMapRef.current.filter(
      state => state.msEpochLastRun > timeoutThreshold
    );

    // Keep masterBuffer from growing infinitely (remove data older than timeout)
    masterBuffer.current = masterBuffer.current.filter(
        state => state.msEpochLastRun > timeoutThreshold
    );

    setVersion(v => v + 1);
  }, [playbackOffset, vehicleSize]);

  // Run the Fetcher (Background)
  useEffect(() => {
    var fetchInterval = 2500; // Fetch pages every 2.5s usually
    if (playbackOffset ===0) {
      fetchInterval = 250;
    }

    const fetchTimer = window.setInterval(fetchBatch, fetchInterval);
    return () => window.clearInterval(fetchTimer);
  }, [fetchBatch, playbackOffset]);

  // Run the Playback Engine (High Frequency)
  useEffect(() => {
    const playbackTimer = window.setInterval(syncMapsToPlaybackTime, intervalMs);
    return () => window.clearInterval(playbackTimer);
  }, [syncMapsToPlaybackTime, intervalMs]);

  // Provide helper functions to modify display state from the UI
  const clearData = useCallback(() => {
    masterBuffer.current = [];
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
    version,
    clearData,
    setAllRoutesVisibility
  };
};