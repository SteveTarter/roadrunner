import { useState, useEffect, useCallback, useRef } from 'react';
import { getCachedAuthToken } from '../components/Utils/AuthUtils';
import { getRestUrl } from "../config";
import { SimulationSession } from '../models/SimulationSession';
import { MapWrapper } from '../components/Utils/MapWrapper';
import { usePlayback } from '../context/PlaybackContext';

export const useSimulationSessionData = () => {
  const { dataSource } = usePlayback();
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const isFetchingRef = useRef(false);
  const [bufferNum, setBufferNum] = useState(0);

  const simulationSessionMapRef = useRef(new MapWrapper<string, SimulationSession>());
  const activeCountMapRef = useRef(new MapWrapper<number, number>());
  const sortedCountKeysRef = useRef(new Array<number>());
  const [activeCountData, setActiveCountData] = useState({
    activeCountMap: new MapWrapper<number, number>(),
    sortedCountKeys: new Array<number>(),
  });

  const fetchBatch = useCallback(async () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;

    try {
      const accessToken = await getCachedAuthToken();
      if (!accessToken) return;

      // We start at page 0 for every batch
      let currentPage = 0;
      let totalPages = 1;
      let pageSize = 200;

      // Loop until we have swallowed every page for the current timeAnchor
      while (currentPage < totalPages) {
        const apiPath = dataSource === 'postgis' ? '/api/db-vehicle' : '/api/vehicle';
        let url = getRestUrl(`${apiPath}/simulation-sessions?page=${currentPage}`);

        url += `&pageSize=${pageSize}`;

        const response = await fetch(url, {
          headers: {
            Authorization: `Bearer ${accessToken}`
          },
          credentials: 'include'
        });
        if (!response.ok) break; // Exit loop on error

        const result = await response.json();

        if (result._embedded?.simulationSessions) {
          // Put latest batch of sessions in map
          result._embedded?.simulationSessions.forEach((s: SimulationSession) => {
            simulationSessionMapRef.current.set(s.id, s);
          })
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
      }

      setIsDataLoaded(true);
      setBufferNum(prev => prev + 1);
    } catch (error) {
      console.error("Fetch error:", error);
    } finally {
      isFetchingRef.current = false;
    }
  }, [dataSource]);

  useEffect(() => {
    if (!isDataLoaded || (simulationSessionMapRef.current.size() === 0)) return;

    // Generate a Map of session events
    const eventMap = new MapWrapper<number, number>();
    const msNow = new Date().getTime();
    simulationSessionMapRef.current.forEach((s: SimulationSession) => {
      const msStart = new Date(s.start).getTime();
      const msEnd = s.end ? new Date(s.end).getTime() : msNow;

      // A start event increments action count for this timestamp
      let startCount = eventMap.get(msStart) || 0;
      eventMap.set(msStart, startCount + 1);

      // An end event decrements action count for this timestamp
      let endCount = eventMap.get(msEnd) || 0;
      eventMap.set(msEnd, endCount - 1);
    })

    // Now, iterate through the event list and maintain running activeCount.
    let activeCount = 0;

    const sortedKeys = Array.from(eventMap.keys()).sort((a, b) => a - b);
    activeCountMapRef.current.clear();

    sortedKeys.forEach((key) => {
      const countAdjustment = eventMap.get(key) || 0;
      activeCount += countAdjustment;

      activeCountMapRef.current.set(key, activeCount);
    })

    sortedCountKeysRef.current = Array.from(activeCountMapRef.current.keys()).sort((a, b) => a - b);

    setActiveCountData({
      activeCountMap: activeCountMapRef.current,
      sortedCountKeys: sortedCountKeysRef.current,
    })
  }, [isDataLoaded, bufferNum]);

  useEffect(() => {
    const doFetch = () => {
      if (!document.hidden) {
        fetchBatch();
      }
    };

    doFetch();

    const fetchTimer = window.setInterval(doFetch, 2500);

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        doFetch();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.clearInterval(fetchTimer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [fetchBatch]);

  // Clear map cache when data source toggles
  useEffect(() => {
    simulationSessionMapRef.current.clear();
    setIsDataLoaded(false);
    if (!document.hidden) {
      fetchBatch();
    }
  }, [dataSource, fetchBatch]);

  return {
    simulationSessionMap: simulationSessionMapRef.current,
    activeCountData,
  }
}