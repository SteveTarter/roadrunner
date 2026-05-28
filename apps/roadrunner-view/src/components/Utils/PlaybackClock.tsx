import { useState, useEffect } from 'react';
import { usePlayback } from '../../context/PlaybackContext';
import { Badge } from 'react-bootstrap';

export const PlaybackClock = () => {
  const { playbackOffset } = usePlayback();
  const [displayTime, setDisplayTime] = useState<string>("");

  useEffect(() => {
    const updateClock = () => {
      const options: Intl.DateTimeFormatOptions = {
        timeZone: 'UTC',
        month: 'numeric',
        day: 'numeric',
        year: '2-digit',
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      };

      // Calculate time: Current wall clock - the historical offset
      const targetDate = new Date(Date.now() - playbackOffset);
      setDisplayTime(`${targetDate.toLocaleTimeString([], options)}Z`);
    };

    // Initialize and set interval
    updateClock();
    const interval = setInterval(updateClock, 1000);

    return () => clearInterval(interval);
  }, [playbackOffset]);

  return (
    <div style={{
      position: 'fixed',
      bottom: '20px',
      right: '5px',
      zIndex: 1000,
      fontFamily: 'monospace'
    }}>
      <Badge bg={playbackOffset === 0 ? "success" : "warning"} style={{ fontSize: '0.85rem', padding: '8px' }}>
        {playbackOffset === 0 ? "LIVE: " : "PLAYBACK: "}
        {displayTime}
      </Badge>
    </div>
  );
};