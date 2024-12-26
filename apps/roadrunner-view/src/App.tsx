import React from 'react';
import 'mapbox-gl/dist/mapbox-gl.css';
import './App.css';
import { Route, Routes } from 'react-router-dom';
import { HomePage } from './components/HomePage/HomePage';
import { useAuth0 } from '@auth0/auth0-react';
import { SpinnerLoading } from './components/Utils/SpinnerLoading';
import { AuthenticationGuard } from './AuthenticationGuard';
import { ProfilePage } from './components/ProfilePage/ProfilePage';
import { DriverViewPage } from './components/DriverViewPage/DriverViewPage';

export const App = () => {
  const { isLoading } = useAuth0();

  if (isLoading) {
    return (
      <div className="page-layout">
        <SpinnerLoading />
      </div>
    );
  }

  return (
    <div className='d-flex flex-column min-vh-100'>
      <div className='flex-grow-1'>
        <Routes>
          <Route path='/'
            element={<AuthenticationGuard component={HomePage} />} />
          <Route
            path='/home'
            element={<AuthenticationGuard component={HomePage} />} />
          <Route
            path="/profile"
            element={<AuthenticationGuard component={ProfilePage} />}
          />
          <Route
            path='/driver-view/:vehicleId'
            element={<AuthenticationGuard component={DriverViewPage} />}
          />
        </Routes>
      </div>
    </div>
  );
}
