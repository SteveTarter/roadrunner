# Roadrunner View

Provides an interface to visualize Vehicles travelling in the roadrunner system.

## Description

Provides an interface to visualize Vehicles travelling in the roadrunner system.

## Setup environment

The application has integrations with OAuth for authentication and MapBox for map displays and georeferencing.  Signup for an Auth0 account at https://auth0.com/.  Signup for a MapBox account at https://account.mapbox.com/auth/signup/ .

The following environment variables need to be set for the application to startup:

* REACT_APP_ROADRUNNER_REST_URL_BASE : Base REST URL of the Roadrunner application (ex:"http://localhost:8080")
* REACT_APP_PUBLIC_URL : (ex:"http://localhost:3000")
* REACT_APP_AUTH0_DOMAIN : Auth0 Domain
* REACT_APP_AUTH0_CLIENT_ID : Auth0 Client ID
* REACT_APP_AUTH0_CLIENT_SECRET : Auth0 Client Secret 
* REACT_APP_AUTH0_CALLBACK_URL : "http://localhost:3000"
* REACT_APP_AUTH0_AUDIENCE : "https://auth.tarterware.com/"
* REACT_APP_MAPBOX_TOKEN :  Token from your Mapbox account
* REACT_APP_MAPBOX_MAP_STYLE : Mapbox style (ex: "mapbox://styles/mapbox/streets-v12")
* REACT_APP_MAPBOX_API_URL : URL to MapBox API (should be "https://api.mapbox.com/")

### Run it

    git clone https://github.com/SteveTarter/roadrunner-view.git

    cd roadrunner-view
    npm install
    npm start

    open http://localhost:3000/

