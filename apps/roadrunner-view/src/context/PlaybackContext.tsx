import React, { createContext, useContext, useMemo, useState } from 'react';

interface PlaybackContextType {
  playbackOffset: number; // Offset in milliseconds
  setPlaybackOffset: (offset: number) => void;
  setPlaybackSession: (startTime: string) => void;
  clearPlayback: () => void;
}

const PlaybackContext = createContext<PlaybackContextType | undefined>(undefined);

export const PlaybackProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const tabId = useMemo(() => {
    if (!window.name) {
      window.name = `tab_${Math.random().toString(36).substr(2, 9)}`;
    }
    return window.name;
  }, []);

  const storageKey = `roadrunner_offset_${tabId}`;

  const [playbackOffset, setPlaybackOffset] = useState<number>(() => {
    const saved = sessionStorage.getItem(storageKey);
    return saved ? parseInt(saved, 10) : 0;
  });

  const setPlaybackSession = (startTime: string) => {
    const offset = Date.now() - new Date(startTime).getTime();
    setPlaybackOffset(offset);
    sessionStorage.setItem(storageKey, offset.toString());
  };

  const clearPlayback = () => {
    setPlaybackOffset(0);
    sessionStorage.removeItem(storageKey);
  }

  return (
    <PlaybackContext.Provider value={{ playbackOffset, setPlaybackOffset, setPlaybackSession, clearPlayback }}>
      {children}
    </PlaybackContext.Provider>
  );
};

export const usePlayback = () => {
  const context = useContext(PlaybackContext);
  if (!context) throw new Error("usePlayback must be used within PlaybackProvider");
  return context;
};