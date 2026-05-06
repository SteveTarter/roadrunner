import React, { useMemo, useState, useEffect, useCallback } from 'react';
import { fetchAuthSession } from "aws-amplify/auth";
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { Button } from "react-bootstrap";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
  CartesianGrid,
  ReferenceDot
} from 'recharts';

import { MapWrapper } from '../Utils/MapWrapper';

export const ActiveVehiclePlot = (props: {
  toggleShowActiveVehiclePlot: any,
  vehicleId?: any | null
}) => {
  const { playbackOffset, setPlaybackSession, clearPlayback } = usePlayback();
  const [sessions, setSessions] = useState<any[]>([]);

  const [touchStartDist, setTouchStartDist] = useState<number | null>(null);
  const [midX, setMidX] = useState<number | null>(null);
  const [msXPoint, setMsXPoint] = useState<number | null>(null);

  const chartRef = React.useRef<HTMLDivElement>(null);

  const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
  const INITIAL_END = Date.now();
  const INITIAL_START = INITIAL_END - ONE_WEEK_MS;

  const [domain, setDomain] = useState<[number, number]>([INITIAL_START, INITIAL_END]);

  // Fetch all session data for the week
  useEffect(() => {
    async function loadAllSessions() {
      const session = await fetchAuthSession();
      const accessToken = session.tokens?.accessToken?.toString();
      // Fetch logic similar to SimulationTable, but iterating through all pages
      // to capture the full week's worth of start/end timestamps
      const res = await fetch(`${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/simulation-sessions?pageSize=1000`, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      const data = await res.json();
      setSessions(data._embedded?.simulationSessions ?? []);
    }
    loadAllSessions();
  }, []);

  useEffect(() => {
    if(!chartRef || !chartRef.current || !midX) return;

    // Get the bounding box of the chart container
    const rect = chartRef.current.getBoundingClientRect();
    const [currentStart, currentEnd] = domain;

    const chartMarginLeft = 85;
    const chartMarginRight = 24;

    // If the mouse is outside the LineChart area, the msXPoint is invalid.
    if((midX < chartMarginLeft + rect.x) || (midX > rect.width + rect.x - chartMarginRight)) {
      setMsXPoint(null);
      return;
    }

    // Define the chart margins (Recharts defaults + Y-Axis width)
    const chartWidth = rect.width - chartMarginLeft - chartMarginRight;

    // Calculate where the mouse is relative to the start of the line area
    const xInChart = midX - chartMarginLeft - rect.x;

    const timeSpan = currentEnd - currentStart;

    // Calculate the percentage across the X-axis (clamped between 0 and 1)
    const percentage = Math.max(0, Math.min(1, xInChart / chartWidth));

    // Map that percentage to your current time domain
    const exactMsTime = currentStart + (timeSpan * percentage);

    setMsXPoint(exactMsTime);
  }, [chartRef, midX, domain]);

  // Generate Chart Data
  const chartData = useMemo(() => {
    if (!sessions || sessions.length === 0) return;

    const data: any[] = [];

    // Generate a Map of session events
    const eventMap = new MapWrapper<number, number>();
    sessions.forEach(s => {
      const msStart = new Date(s.start).getTime();
      const msEnd = s.end ? new Date(s.end).getTime() : msStart;

      // A start event increments action count for this timestamp
      var startCount = eventMap.get(msStart) || 0;
      eventMap.set(msStart, startCount + 1);

      // An end event decrements action count for this timestamp
      var endCount = eventMap.get(msEnd) || 0;
      eventMap.set(msEnd, endCount - 1);
    })

    // Now, iterate through the event list and maintain running activeCount.
    var activeCount = 0;
    const activeCountMap = new MapWrapper<number, number>();
    const sortedKeys = Array.from(eventMap.keys()).sort((a, b) => a - b);
    sortedKeys.forEach((key) => {
      const countAdjustment = eventMap.get(key) || 0;
      activeCount += countAdjustment;

      activeCountMap.set(key, activeCount);
    })

    var sortedCountKeys = Array.from(activeCountMap.keys()).sort((a, b) => a - b);
    sortedCountKeys.forEach((key) => {
      const dateObj = new Date(key);
      const activeCount = activeCountMap.get(key);

      data.push({
        time: dateObj,
        // X-Axis usually looks better with just the date or hour
        displayTime: dateObj.toLocaleDateString([], { month: 'short', day: 'numeric' }),
        // Create a detailed string specifically for the tooltip
        fullLocalTime: dateObj.toLocaleString([], {
          timeZone: 'UTC',
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          month: 'short',
          day: 'numeric'
        }),
        msTime: dateObj.getTime(),
        count: activeCount
      });
    });

    // If a vehicle ID was provided, restrict the window to that vehicle's timespan.
    if (props.vehicleId) {
      const driverSession = sessions.find((s) => (props.vehicleId === s.id));
      if (driverSession) {
        const startDate = new Date(driverSession.start);
        const endDate = driverSession.end ? new Date(driverSession.end) : new Date();
        setDomain([startDate.getTime(), endDate.getTime()]);
      }
      else {
        console.error("Didn't find session for vehicle ID", props.vehicleId);
      }
    }

    return data;

  }, [sessions, props.vehicleId]);

  const getMidnightTicks = (start: number, end: number) => {
    const ticks = [];
    // Create a date object starting at the beginning of the domain
    let current = new Date(start);

    // Normalize to the start of the next UTC day
    current.setUTCHours(24, 0, 0, 0);

    while (current.getTime() <= end) {
      ticks.push(current.getTime());
      // Advance by exactly 24 hours
      current.setUTCDate(current.getUTCDate() + 1);
    }
    return ticks;
  };

  const isMultiDay = useMemo(() => {
    const startDate = new Date(domain[0]);
    const endDate = new Date(domain[1]);

    // Compare UTC components to ignore local timezone offsets
    return (
      startDate.getUTCFullYear() !== endDate.getUTCFullYear() ||
      startDate.getUTCMonth() !== endDate.getUTCMonth() ||
      startDate.getUTCDate() !== endDate.getUTCDate()
    );
  }, [domain]);

  const isRightOfCenter = useMemo(() => {
    if (!chartRef.current || midX === null) return false;
    const rect = chartRef.current.getBoundingClientRect();
    return (midX - rect.left) > (rect.width / 2);
  }, [midX]);

  const midnightTicks = useMemo(() => {
    const span = domain[1] - domain[0];
    if (span > 2 * 24 * 60 * 60 * 1000) {
      return getMidnightTicks(domain[0], domain[1]);
    }
    return undefined; // Fall back to automatic even spacing for small time spans
  }, [domain]);

  const hoveredCount = useMemo(() => {
    if (!msXPoint || !chartData || chartData.length === 0) return 0;
    // Find the last data point that happened before or exactly at the mouse point
    const point = chartData.reduce((prev, curr) =>
      (curr.msTime <= msXPoint ? curr : prev),
      chartData[0]
    );
    return point?.count || 0;
  }, [msXPoint, chartData]);

  const hoveredTimestamp = useMemo(() => {
    if (!msXPoint || !chartData || chartData.length === 0) return undefined;
    const span = domain[1] - domain[0];

    const options: Intl.DateTimeFormatOptions = {
      timeZone: 'UTC',
      month: 'numeric',
      day: 'numeric',
      year: 'numeric',
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
    };
    const date = new Date(msXPoint);
    if (span < 60 * 60 * 1000) {
      return `${date.toLocaleTimeString([], { ...options, second: '2-digit' })}Z`;
    }
    return `${date.toLocaleTimeString([], options)}Z`;

  }, [domain, msXPoint, chartData]);

  // Extracted zoom logic for reuse between Wheel and Touch
  const performZoom = useCallback((isZoomIn: boolean, anchor: number) => {
    const [currentStart, currentEnd] = domain;
    const zoomFactor = 0.1;

    if (isZoomIn) {
      let newStart = currentStart + (anchor - currentStart) * zoomFactor;
      let newEnd = currentEnd - (currentEnd - anchor) * zoomFactor;

      newStart = Math.max(INITIAL_START, newStart);
      newEnd = Math.min(INITIAL_END, newEnd);

      // Prevent zooming in too far (e.g., closer than 1 minute)
      if (newEnd - newStart > 60000) {
        setDomain([newStart, newEnd]);
      }
    } else {
      let newStart = currentStart - (anchor - currentStart) * zoomFactor;
      let newEnd = currentEnd + (currentEnd - anchor) * zoomFactor;

      newStart = Math.max(INITIAL_START, newStart);
      newEnd = Math.min(INITIAL_END, newEnd);

      setDomain([newStart, newEnd]);
    }
  }, [domain, INITIAL_END, INITIAL_START]);

  // Helper to format the title based on the viewable span
  const getDynamicTitle = (start: number, end: number) => {
    const span = end - start;
    const startDate = new Date(start);
    const endDate = new Date(end);

  const options: Intl.DateTimeFormatOptions = {
      timeZone: 'UTC',
      hour12: false
    };

    if (span > 24 * 60 * 60 * 1000 * 2) {
      return `Vehicle Activity: ${startDate.toLocaleDateString([], options)} to ${endDate.toLocaleDateString([], options)}`;
    } else if (span > 60 * 60 * 1000) {
      if (isMultiDay) {
        return `Vehicle Activity: ${startDate.toLocaleDateString([], options)}, ${startDate.toLocaleTimeString([], { ...options, hour: '2-digit', minute: '2-digit' })}Z to ${endDate.toLocaleDateString([], options)}, ${endDate.toLocaleTimeString([], { ...options, hour: '2-digit', minute: '2-digit' })}Z`;
      } else {
        return `Vehicle Activity: ${startDate.toLocaleDateString([], options)}, ${startDate.toLocaleTimeString([], { ...options, hour: '2-digit', minute: '2-digit' })}Z to ${endDate.toLocaleTimeString([], { ...options, hour: '2-digit', minute: '2-digit' })}Z`;
      }
    }
    return `Vehicle Activity: ${startDate.toLocaleString([], options)}Z to ${endDate.toLocaleString([], options)}Z`;
  };

  // Helper for X-Axis ticks
  const formatXAxis = (unixTime: number, domain: [number, number]) => {
    const span = domain[1] - domain[0];
    const date = new Date(unixTime);

    const options: Intl.DateTimeFormatOptions = {
      timeZone: 'UTC',
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
    };

    if (span > 24 * 60 * 60 * 1000 * 2) {
      return date.toLocaleDateString([], {
        timeZone: 'UTC',
        month: 'short',
        day: 'numeric',
      });
    } else if (span > 60 * 60 * 1000) {
      return `${date.toLocaleTimeString([], options)}Z`;
    }
    return `${date.toLocaleTimeString([], { ...options, second: '2-digit' })}Z`;
  };

  const handleChartClick = (state: any) => {
    if (!msXPoint) return;

    const strExactMsTime = new Date(msXPoint).toISOString();

    // Update the playback session
    setPlaybackSession(strExactMsTime);

    props.toggleShowActiveVehiclePlot();
  };

  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (msXPoint === null) return;

    performZoom(e.deltaY < 0, msXPoint);
  }, [performZoom, msXPoint]);

  const getTouchDist = (e: React.TouchEvent) => {
    if (e.touches.length !== 2) return null;
    const dx = e.touches[0].pageX - e.touches[1].pageX;
    const dy = e.touches[0].pageY - e.touches[1].pageY;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 2) {
      const dist = getTouchDist(e);
      setTouchStartDist(dist);
      // Use the midpoint between two fingers as the zoom anchor
      setMidX((e.touches[0].pageX + e.touches[1].pageX) / 2);
    }
    if ((e.touches.length === 1) && !touchStartDist) {
      if (!msXPoint) return;

      const strExactMsTime = new Date(msXPoint).toISOString();

      // Update the playback session
      setPlaybackSession(strExactMsTime);

      console.log("Warping to calculated time:", strExactMsTime);
      props.toggleShowActiveVehiclePlot();
    }
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (e.touches.length === 2 && touchStartDist !== null) {
      const currentDist = getTouchDist(e);
      if (!currentDist) return;
      if (!msXPoint) return;

      const zoomThreshold = 2; // Pixels
      const diff = currentDist - touchStartDist;

      if (Math.abs(diff) > zoomThreshold) {
        const isZoomIn = diff > 0;

        performZoom(isZoomIn, msXPoint);

        // Update starting distance to allow continuous zooming
        setTouchStartDist(currentDist);
      }
    }
  };

  const currentPlaybackTime = Date.now() - playbackOffset;

  return (
    <div
      ref={chartRef}
      className="active-vehicle-plot-container"
      onWheel={handleWheel}
      onMouseMove={(state) => {
        if (state && state.clientX) {
          setMidX(state.clientX);
        }
      }}
      onMouseLeave={() => {
        setMidX(null);
        setMsXPoint(null);
      }}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={() => {
        setMidX(null);
        setMsXPoint(null);
        setTouchStartDist(null);
      }}
      style={{
        touchAction: 'none',
        position: 'relative',
        background: 'rgba(255, 255, 255, 0.8)',
        backdropFilter: 'blur(2px)',
        padding: '20px',
        borderRadius: '8px',
        zIndex: 1000,
        paddingBottom: '20px',
        width: '94%',
        margin: '20px auto',
        boxShadow: '0 4px 15px rgba(0,0,0,0.2)'
      }}
    >
      <h5>{getDynamicTitle(domain[0], domain[1])}</h5>

      <div style={{ width: '100%', height: 300 }}>
        <ResponsiveContainer>
          <LineChart
            data={chartData}
            onMouseDown={handleChartClick}
            style={{ cursor: 'crosshair' }}
          >
            <CartesianGrid
              strokeDasharray="3 3"
              vertical={false}
              strokeOpacity={0.8}
              fill="rgba(0, 0, 0, 0.07)" // Light gray fill for the interior
              fillOpacity={1}
            />

            <XAxis
              dataKey="msTime"
              type="number"
              allowDataOverflow={true}
              domain={domain}
              ticks={midnightTicks}
              interval={0}
              tickFormatter={(unixTime) => formatXAxis(unixTime, domain)}
            />
            <YAxis />

            {/* Sweeping Line: Only show if it falls within the current visible domain */}
            {currentPlaybackTime >= domain[0] && currentPlaybackTime <= domain[1] && (
              <ReferenceLine
                x={playbackOffset === 0 ? domain[1] : currentPlaybackTime}
                stroke="red"
                strokeWidth={2}
                zIndex={1001}
                label={{ position: 'top', value: 'Live', fill: 'red', fontSize: 10 }}
              />
            )}
            {/* Red Dot and vertical line following the mouse exactly on the line */}
            {msXPoint && chartData && (
              <>
                <ReferenceDot
                  x={msXPoint}
                  // Logic to find the Y value: find the last data point that happened BEFORE the mouse X
                  y={chartData.reduce((prev, curr) => (curr.msTime <= msXPoint ? curr : prev), chartData[0])?.count}
                  r={4}
                  fill="red"
                  stroke="none"
                />
                <ReferenceLine
                  x={msXPoint}
                  stroke="rgba(0,0,0,0.1)"
                  strokeDasharray="3 3"
                />
              </>
            )}
            <Tooltip active={false}/> {/* Disable default snapping tooltip */}
            <Line
              type="stepAfter"
              dataKey="count"
              stroke="#8884d8"
              fill="#8884d8"
              strokeWidth={2}
              fillOpacity={0.8}
              dot={false}
              activeDot={false}
              connectNulls={true}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
        {/* Tooltip tied to the ReferencePoint at the cursor X position */}
        {msXPoint && midX && (
          <div
            style={{
              position: 'fixed',
              // Dynamic anchoring logic:
              left: isRightOfCenter ? 'auto' : midX + 15,
              right: isRightOfCenter ? (window.innerWidth - midX) + 15 : 'auto',
              top: '50%',
              pointerEvents: 'none',
              background: 'white',
              border: '1px solid #ccc',
              padding: '8px',
              borderRadius: '4px',
              zIndex: 1002,
              fontSize: '12px',
              boxShadow: '0 2px 5px rgba(0,0,0,0.2)'
            }}
          >
            <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>
              {hoveredTimestamp}
            </div>
            <div style={{ color: '#8884d8' }}>
              Active Vehicles: {hoveredCount}
            </div>
          </div>
        )}
      </div>

      <div className="mt-3 d-flex justify-content-end gap-2">
        {playbackOffset !== 0 && (
          <Button variant="success" onClick={clearPlayback}>Return to Live</Button>
        )}
        <Button variant="secondary" onClick={props.toggleShowActiveVehiclePlot}>Close</Button>
      </div>
    </div>
  );
};