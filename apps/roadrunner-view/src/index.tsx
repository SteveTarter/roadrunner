import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import { App } from './App';
import { BrowserRouter } from 'react-router-dom';
import { Auth0ProviderWithNavigate } from "./Auth0ProviderWithNavigate";
import { MapProvider } from 'react-map-gl';

const root = ReactDOM.createRoot(
  document.getElementById('root')!
);

root.render(
  <MapProvider>
    <BrowserRouter>
      <Auth0ProviderWithNavigate>
        <App />
      </Auth0ProviderWithNavigate>
    </BrowserRouter>
  </MapProvider>
);
