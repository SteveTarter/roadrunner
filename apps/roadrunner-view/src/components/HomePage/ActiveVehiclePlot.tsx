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

  const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
  const ABSOLUTE_END = Date.now();
  const ABSOLUTE_START = ABSOLUTE_END - ONE_WEEK_MS;

  const [domain, setDomain] = useState<[number, number]>([ABSOLUTE_START, ABSOLUTE_END]);

  // Track the current time under the mouse for the zoom anchor
  const [hoveredTime, setHoveredTime] = useState<number | null>(null);

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
    // Check if activePayload exists (this contains the data for the clicked point)
    if (state && state.activeLabel) {
      setPlaybackSession(state.activeLabel); // This updates the global playback state
      console.log("Warping to:", state.activeLabel);
      props.toggleShowActiveVehiclePlot();
    }
  };

  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (hoveredTime === null) return;

    const [currentStart, currentEnd] = domain;
    const zoomFactor = 0.1; // 10% zoom per scroll notch
    const direction = e.deltaY < 0 ? 1 : -1; // Negative deltaY is scroll up (zoom in)

    if (direction === -1) {
      let newStart = currentStart - (hoveredTime - currentStart) * zoomFactor;
      let newEnd = currentEnd + (currentEnd - hoveredTime) * zoomFactor;

      newStart = Math.max(ABSOLUTE_START, newStart);
      newEnd = Math.min(ABSOLUTE_END, newEnd);

      setDomain([newStart, newEnd]);
      return;
    }

    // Zoom In: Contract start and end around hoveredTime
    const newStart = currentStart + (hoveredTime - currentStart) * zoomFactor;
    const newEnd = currentEnd - (currentEnd - hoveredTime) * zoomFactor;

    // Prevent zooming in too far (e.g., closer than 1 minute)

    if (newEnd - newStart > 60000) {
      setDomain([newStart, newEnd]);
    }
  }, [domain, hoveredTime, ABSOLUTE_START, ABSOLUTE_END]);

  const currentPlaybackTime = Date.now() - playbackOffset;

  return (
    <div
      className="active-vehicle-plot-container"
      onWheel={handleWheel}
      style={{
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
            onMouseMove={(state) => {
              // Recharts activeLabel can be string | number.
              // We check if it is a number before setting our state.
              if (state && typeof state.activeLabel === 'number') {
                setHoveredTime(state.activeLabel);
              } else {
                setHoveredTime(null);
              }
            }}
            onMouseLeave={() => setHoveredTime(null)}
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