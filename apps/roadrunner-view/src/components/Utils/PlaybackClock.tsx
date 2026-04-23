import { useState, useEffect } from 'react';
import { usePlayback } from '../../context/PlaybackContext';
import { Badge } from 'react-bootstrap';

export const PlaybackClock = () => {
  const { playbackOffset } = usePlayback();
  const [displayTime, setDisplayTime] = useState<string>("");

  useEffect(() => {
    const updateClock = () => {
      // Calculate time: Current wall clock - the historical offset
      const targetDate = new Date(Date.now() - playbackOffset);
      setDisplayTime(targetDate.toISOString());
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
      right: '20px',
      zIndex: 1000,
      fontFamily: 'monospace'
    }}>
      <Badge bg={playbackOffset === 0 ? "success" : "warning"} style={{ fontSize: '1rem', padding: '10px' }}>
        {playbackOffset === 0 ? "LIVE: " : "PLAYBACK: "}
        {displayTime}
      </Badge>
    </div>
  );
};