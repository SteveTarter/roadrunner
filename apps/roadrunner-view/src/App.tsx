import React from 'react';
import './App.css';
import { Route, Routes } from 'react-router-dom';
import { HomePage } from './components/HomePage/HomePage';
import { useAuth0 } from '@auth0/auth0-react';
import { SpinnerLoading } from './components/Utils/SpinnerLoading';
import { AuthenticationGuard } from './AuthenticationGuard';
import { AppNavBar } from './components/NavBar/AppNavBar';
import { ProfilePage } from './components/ProfilePage/ProfilePage';

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
        <AppNavBar />
        <Routes>
          <Route path='/'
            element={<AuthenticationGuard
              component={HomePage} />} />
          <Route
            path='/home'
            element={<AuthenticationGuard
              component={HomePage} />} />
          <Route
            path="/profile"
            element={<AuthenticationGuard component={ProfilePage} />}
          />
        </Routes>
      </div>
    </div>
  );
}
