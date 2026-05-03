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
  CartesianGrid
} from 'recharts';

import { MapWrapper } from '../Utils/MapWrapper';

export const ActiveVehiclePlot = (props: {
  toggleShowActiveVehiclePlot: any
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

  // Track the current time under the mouse for the zoom anchor
  //const [hoveredTime, setHoveredTime] = useState<number | null>(null);

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

    // Define the chart margins (Recharts defaults + Y-Axis width)
    // Usually, the Y-Axis takes about 60px, and there's a 5px margin.
    // You can inspect your specific chart to tune these numbers.
    const chartMarginLeft = 65;
    const chartMarginRight = 5;
    const chartWidth = rect.width - chartMarginLeft - chartMarginRight;

    // Calculate where the mouse is relative to the start of the line area
    const xInChart = midX - rect.left - chartMarginLeft;

    // Calculate the percentage across the X-axis (clamped between 0 and 1)
    const percentage = Math.max(0, Math.min(1, xInChart / chartWidth));

    // Map that percentage to your current time domain
    const timeSpan = currentEnd - currentStart;
    const exactMsTime = currentStart + (timeSpan * percentage);
    setMsXPoint(exactMsTime);
  }, [chartRef, midX, domain]);

  // Generate Chart Data
  const chartData = useMemo(() => {
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
          hour: '2-digit',
          minute: '2-digit',
          month: 'short',
          day: 'numeric'
        }),
        msTime: dateObj.getTime(),
        count: activeCount
      });
    });
    return data;
  }, [sessions]);

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

    if (span > 24 * 60 * 60 * 1000 * 2) { // Greater than 2 days
      return `Vehicle Activity: ${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`;
    } else if (span > 60 * 60 * 1000) { // Greater than 1 hour
      return `Vehicle Activity: ${startDate.toLocaleDateString()} ${startDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} to ${endDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }
    return `Vehicle Activity: ${startDate.toLocaleString()}`;
  };

  // Helper for X-Axis ticks
  const formatXAxis = (unixTime: number, domain: [number, number]) => {
    const span = domain[1] - domain[0];
    const date = new Date(unixTime);

    if (span < 60 * 60 * 1000 * 2) { // Less than 2 hours: show minutes/seconds
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } else if (span < 24 * 60 * 60 * 1000) { // Less than a day: show hours
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' }); // Default: Date
  };

  const handleChartClick = (state: any) => {
    if (!msXPoint) return;

    const strExactMsTime = new Date(msXPoint).toISOString();

    // Update the playback session
    setPlaybackSession(strExactMsTime);

    console.log("Warping to calculated time:", strExactMsTime);
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
        background: 'rgba(255, 255, 255, 0.6)',
        backdropFilter: 'blur(2px)',
        padding: '20px',
        borderRadius: '8px',
        zIndex: 1000,
        paddingBottom: '65px',
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
              strokeOpacity={0.3}
            />

            <XAxis
              dataKey="msTime"
              type="number"
              allowDataOverflow={true}
              domain={domain}
              tickFormatter={(unixTime) => formatXAxis(unixTime, domain)}
            />
            <YAxis />

            {/* Sweeping Line: Only show if it falls within the current visible domain */}
            {currentPlaybackTime >= domain[0] && currentPlaybackTime <= domain[1] && (
              <ReferenceLine
                x={currentPlaybackTime}
                stroke="red"
                strokeWidth={2}
                zIndex={1001}
                label={{ position: 'top', value: 'Live', fill: 'red', fontSize: 10 }}
              />
            )}

            <Tooltip
              labelFormatter={(label, payload) =>
                payload?.[0]?.payload?.fullLocalTime || label
              }
            />
            <Line
              type="stepAfter"
              dataKey="count"
              stroke="#8884d8"
              fill="#8884d8"
              strokeWidth={2}
              fillOpacity={0.8}
              dot={false}
              connectNulls={true}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="mt-3 d-flex gap-2">
        <Button variant="warning" onClick={clearPlayback}>Return to Live</Button>
        <Button variant="secondary" onClick={props.toggleShowActiveVehiclePlot}>Close</Button>
      </div>
    </div>
  );
};