import React from 'react';
import 'mapbox-gl/dist/mapbox-gl.css';
import './App.css';
import { Route, Routes, Navigate } from 'react-router-dom';
import { APIProvider } from '@vis.gl/react-google-maps';
import { CONFIG } from './config';
import { HomePage } from './components/HomePage/HomePage';
import { AuthenticationGuard } from './AuthenticationGuard';
import { ProfilePage } from './components/ProfilePage/ProfilePage';
import { DriverViewPage } from './components/DriverViewPage/DriverViewPage';
import { library } from '@fortawesome/fontawesome-svg-core';
import fontawesome from '@fortawesome/fontawesome'
import { faHome, faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash, faArrowLeft, faArrowRight, fa0, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faPowerOff, faUser } from '@fortawesome/fontawesome-free-solid'
import { AboutPage } from './components/AboutPage/AboutPage';
import { GuidePage } from './components/GuidePage/GuidePage';
import { Vehicle3DMapPage } from './components/Vehicle3DMapPage/Vehicle3DMapPage';
import { GoogleHomePage } from './components/GoogleHomePage/GoogleHomePage';
import { GoogleVehicle3DMapPage } from './components/GoogleVehicle3DMapPage/GoogleVehicle3DMapPage';
import { GoogleDriverViewPage } from './components/GoogleDriverViewPage/GoogleDriverViewPage';
import { HelpProvider } from './context/HelpContext';
import { HelpDrawer } from './components/Shared/HelpDrawer';

library.add(faHome, faSatellite, faMap, faUpRightAndDownLeftFromCenter, faEye, faEyeSlash, faArrowLeft, faArrowRight, fa0, faQuestionCircle );
fontawesome.library.add(faPowerOff, faUser);

const HomeRedirect = () => {
  const provider = localStorage.getItem('roadrunner_map_provider') || 'google';
  return <Navigate to={provider === 'google' ? '/google/home' : '/home'} replace />;
};

const ProviderEnforcedRoute = ({ requiredProvider, component: Component }: { requiredProvider: 'google' | 'mapbox'; component: React.ComponentType }) => {
  const provider = (localStorage.getItem('roadrunner_map_provider') as 'google' | 'mapbox') || 'google';
  if (provider !== requiredProvider) {
    const search = window.location.search;
    const path = window.location.pathname;
    let newPath = '';
    if (requiredProvider === 'mapbox') {
      if (path === '/home') newPath = '/google/home';
      else if (path === '/3d-view') newPath = '/google/3d-view';
      else if (path.startsWith('/driver-view/')) newPath = `/google/driver-view/${path.substring('/driver-view/'.length)}`;
      else newPath = '/google/home';
    } else {
      if (path === '/google/home') newPath = '/home';
      else if (path === '/google/3d-view') newPath = '/3d-view';
      else if (path.startsWith('/google/driver-view/')) newPath = `/driver-view/${path.substring('/google/driver-view/'.length)}`;
      else newPath = '/home';
    }
    return <Navigate to={`${newPath}${search}`} replace />;
  }
  return <Component />;
};

const WrappedGoogleHomePage = () => (
  <APIProvider apiKey={CONFIG.GOOGLE_MAPS_API_KEY || ''} version="weekly">
    <GoogleHomePage />
  </APIProvider>
);

const WrappedGoogleVehicle3DMapPage = () => (
  <APIProvider apiKey={CONFIG.GOOGLE_MAPS_API_KEY || ''} version="beta">
    <GoogleVehicle3DMapPage />
  </APIProvider>
);

const WrappedGoogleDriverViewPage = () => (
  <APIProvider apiKey={CONFIG.GOOGLE_MAPS_API_KEY || ''} version="beta">
    <GoogleDriverViewPage />
  </APIProvider>
);

export const App = () => {

  return (
    <HelpProvider>
      <div className='d-flex flex-column min-vh-100'>
        <div className='flex-grow-1'>
        <Routes>
          <Route path='/' element={<HomeRedirect />} />
          <Route
            path='/home'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="mapbox" component={HomePage} />} />}
          />
          <Route
            path='/google/home'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="google" component={WrappedGoogleHomePage} />} />}
          />
          <Route
            path='/google/3d-view'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="google" component={WrappedGoogleVehicle3DMapPage} />} />}
          />
          <Route
            path='/google/driver-view/:vehicleId'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="google" component={WrappedGoogleDriverViewPage} />} />}
          />
          <Route
            path='/about'
            element={<AuthenticationGuard component={AboutPage} />}
          />
          <Route
            path='/guide/:guideId'
            element={<AuthenticationGuard component={GuidePage} />}
          />
          <Route
            path="/profile"
            element={<AuthenticationGuard component={ProfilePage} />}
          />
          <Route
            path='/driver-view/:vehicleId'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="mapbox" component={DriverViewPage} />} />}
          />
          <Route
            path='/3d-view'
            element={<AuthenticationGuard component={() => <ProviderEnforcedRoute requiredProvider="mapbox" component={Vehicle3DMapPage} />} />}
          />
        </Routes>
        </div>
        <HelpDrawer />
      </div>
    </HelpProvider>
  );
}
