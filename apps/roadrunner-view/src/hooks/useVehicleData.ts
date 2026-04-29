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
  const [isInterpolationEnabled, setIsInterpolationEnabled] = useState(true);
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
    const bufferBackfillTime = 5000; // 5 seconds ahead
    return new Date(Date.now() - playbackOffset + bufferBackfillTime).toISOString();
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
      let pageSize = 200;

      // Loop until we have swallowed every page for the current timeAnchor
      while (currentPage < totalPages) {
        let url =
          `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/playback/state?page=${currentPage}`;

        url += `&pageSize=${pageSize}`;

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

        // If this is the first page, determine how big each remaining
        // request needs to be so we can get the data in 5 bites.
        if (result.page?.number === 0) {
          const remainingElements =
            result.page?.totalElements - result.page?.size;

          pageSize = Math.ceil(remainingElements / 5.0);
          pageSize = Math.max(pageSize, 200);
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

  // Helper for bearing interpolation
  const interpolateBearing = (start: number, end: number, ratio: number) => {
    let delta = end - start;
    if (delta > 180) delta -= 360;
    if (delta < -180) delta += 360;
    return (start + delta * ratio + 360) % 360;
  };

  const LIVE_INTERPOLATION_DELAY_MS = 2000;

  /**
   * THE PLAYBACK ENGINE: Syncs the UI maps to the specific playback time.
   */
  const syncMapsToPlaybackTime = useCallback(() => {
    // If offset is 0 (live), subtract a delay to allow for look-ahead data
    const effectiveOffset =
      playbackOffset === 0 ? LIVE_INTERPOLATION_DELAY_MS : playbackOffset;
    const currentTime = Date.now() - effectiveOffset;

    const vehicleIds = new Set(masterBuffer.current.map(s => s.id));

    vehicleIds.forEach(id => {
      const states = masterBuffer.current
        .filter(s => s.id === id)
        .sort((a, b) => a.msEpochLastRun - b.msEpochLastRun);

      if (states.length === 0) return;

      let displayState: VehicleState;

      // Logic for Smooth vs. Raw movement
      if (isInterpolationEnabled) {

        // Find the two states surrounding currentTime
        const nextIndex = states.findIndex(s => s.msEpochLastRun > currentTime);

        if (nextIndex === -1) {
          // We only have past data, use the latest one (raw)
          displayState = states[states.length - 1];
        } else if (nextIndex === 0) {
          // We only have future data, use the first one
          displayState = states[0];
        } else {
          // Interpolate between states[nextIndex - 1] and states[nextIndex]
          const s0 = states[nextIndex - 1];
          const s1 = states[nextIndex];

          const ratio = (currentTime - s0.msEpochLastRun) / (s1.msEpochLastRun - s0.msEpochLastRun);

          displayState = {
            ...s0,
            degLatitude: s0.degLatitude + (s1.degLatitude - s0.degLatitude) * ratio,
            degLongitude: s0.degLongitude + (s1.degLongitude - s0.degLongitude) * ratio,
            // Handle bearing wrap-around (e.g., 350 to 10 degrees)
            degBearing: interpolateBearing(s0.degBearing, s1.degBearing, ratio)
          };
        }
      } else {
        // In Raw mode, we use the real "now" if live, or the playback time
        const rawCurrentTime = Date.now() - playbackOffset;
        displayState = states.filter(s => s.msEpochLastRun <= rawCurrentTime).pop() || states[0];        // Raw mode: Get the latest state that is not in the future
      }

      vehicleStateMapRef.current.set(id, displayState);

      // Update Display metadata (size and visibility)
      if (!vehicleDisplayMapRef.current.get(id)) {
        vehicleDisplayMapRef.current.set(id, new VehicleDisplay(vehicleSize, false, false));
      } else {
        const display = vehicleDisplayMapRef.current.get(id);
        if (display) display.size = vehicleSize;
      }
    });

    // Housekeeping: Remove old states from UI and Master Buffer
    const timeoutPastThreshold = currentTime - (30 * 1000);
    const timeoutFutureThreshold = currentTime + (30 * 1000);

    vehicleStateMapRef.current = vehicleStateMapRef.current.filter(
      state =>
        state.msEpochLastRun > timeoutPastThreshold &&
        state.msEpochLastRun < timeoutFutureThreshold
    );

    // Keep masterBuffer from growing infinitely (remove data older than timeout)
    masterBuffer.current = masterBuffer.current.filter(
      state =>
        state.msEpochLastRun > timeoutPastThreshold &&
        state.msEpochLastRun < timeoutFutureThreshold
    );

    setVersion(v => v + 1);
  }, [playbackOffset, masterBuffer, vehicleSize, isInterpolationEnabled]);

  // Run the Fetcher (Background)
  useEffect(() => {
     // If in playback mode, fetch pages every 2.5s. Live is fetched every 250 ms.
    var fetchInterval = playbackOffset === 0 ? 250 : 2500;

    fetchBatch();

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
    vehicleDisplayMapRef.current.forEach((display: any) => {
      display.routeVisible = visible;
    });
    setVersion(v => v + 1);
  }, []);

  return {
    vehicleStateMap: vehicleStateMapRef.current,
    vehicleDisplayMap: vehicleDisplayMapRef.current,
    isDataLoaded,
    isInterpolationEnabled,
    setIsInterpolationEnabled,
    version,
    clearData,
    setAllRoutesVisibility
  };
};