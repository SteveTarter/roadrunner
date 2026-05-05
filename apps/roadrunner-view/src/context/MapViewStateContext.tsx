import React, { createContext, Dispatch, ReactNode, SetStateAction, useContext, useMemo, useState } from 'react';

import type { ViewState } from "react-map-gl";

interface MapViewStateContextType {
  homeMapViewState: ViewState;
  setHomeMapViewState: Dispatch<SetStateAction<ViewState>>;
}

const MapViewStateContext = createContext<MapViewStateContextType | undefined>(undefined);

export const MapViewStateProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const tabId = useMemo(() => {
    if (!window.name) {
      window.name = `tab_${Math.random().toString(36).substr(2, 9)}`;
    }
    return window.name;
  }, []);

  const storageKey = `roadrunner_viewstate_${tabId}`;

  const [homeMapViewState, setViewState] = useState<ViewState>(() => {
    const saved = sessionStorage.getItem(storageKey);
    return saved ? JSON.parse(saved) : {
      longitude: -97.5,
      latitude: 32.75,
      zoom: 10,
      bearing: 0,
      pitch: 0,
      padding: { top: 0, bottom: 0,left: 0,right: 0 }
    }
  });

  const setHomeMapViewState = ((vs:any) => {
    setViewState(vs);
    sessionStorage.setItem(storageKey, JSON.stringify(vs));
  })

  return (
    <MapViewStateContext.Provider value={{ homeMapViewState, setHomeMapViewState }}>
      {children}
    </MapViewStateContext.Provider>
  );
};

export const useMapViewState = () => {
  const context = useContext(MapViewStateContext);
  if (!context) throw new Error("useMapViewState must be used within MapViewStateProvider");
  return context;
};