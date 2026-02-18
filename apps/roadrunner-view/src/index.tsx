import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import { App } from './App';
import { BrowserRouter } from 'react-router-dom';
import { MapProvider } from 'react-map-gl';

import { configureAmplify } from "./amplify-config";

configureAmplify();

const root = ReactDOM.createRoot(
  document.getElementById('root')!
);

root.render(
  <MapProvider>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </MapProvider>
);
