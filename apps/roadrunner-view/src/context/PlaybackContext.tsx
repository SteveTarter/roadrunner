import React, { createContext, useContext, useState } from 'react';

interface PlaybackContextType {
  playbackOffset: number; // Offset in milliseconds
  setPlaybackSession: (startTime: string) => void;
  clearPlayback: () => void;
}

const PlaybackContext = createContext<PlaybackContextType | undefined>(undefined);

export const PlaybackProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [playbackOffset, setPlaybackOffset] = useState<number>(() => {
    const saved = localStorage.getItem('roadrunner_offset');
    return saved ? parseInt(saved, 10) : 0;
  });

  const setPlaybackSession = (startTime: string) => {
    const offset = Date.now() - new Date(startTime).getTime();
    setPlaybackOffset(offset);
    localStorage.setItem('roadrunner_offset', offset.toString());
  };

  const clearPlayback = () => {
    setPlaybackOffset(0);
    localStorage.removeItem('roadrunner_offset');
  }

  return (
    <PlaybackContext.Provider value={{ playbackOffset, setPlaybackSession, clearPlayback }}>
      {children}
    </PlaybackContext.Provider>
  );
};

export const usePlayback = () => {
  const context = useContext(PlaybackContext);
  if (!context) throw new Error("usePlayback must be used within PlaybackProvider");
  return context;
};