import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import { App } from './App';
import { BrowserRouter } from 'react-router-dom';
import { MapProvider } from 'react-map-gl';
import { PlaybackProvider } from './context/PlaybackContext';

import { configureAmplify } from "./amplify-config";
import { MapViewStateProvider } from './context/MapViewStateContext';

configureAmplify();

const root = ReactDOM.createRoot(
  document.getElementById('root')!
);

root.render(
  <MapProvider>
    <MapViewStateProvider>
      <BrowserRouter>
        <PlaybackProvider>
          <App />
        </PlaybackProvider>
      </BrowserRouter>
    </MapViewStateProvider>
  </MapProvider>
);
