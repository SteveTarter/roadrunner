# Roadrunner View

# Roadrunner Vehicle Simulation

Roadrunner is a portfolio vehicle-simulation application built to demonstrate full-stack, cloud, mapping, and distributed-system development. The project was created in part to gain practical, real-life experience with Kafka and Redis. The application provides a real-time, interactive map to monitor and manage simulated vehicle fleets.

## Project Repositories
Roadrunner's codebase is distributed across three GitHub repositories:
* **[Roadrunner Backend](https://github.com/SteveTarter/roadrunner)**: The Spring Boot-based backend.
* **[Roadrunner Viewer](https://github.com/SteveTarter/roadrunner-viewer)**: The React-based frontend viewer **(THIS PROJECT)**.
* **[Roadrunner K8S Orchestration](https://github.com/SteveTarter/roadrunner-k8s-orchestration)**: Kubernetes orchestration for deployment to Minikube or an AWS Kubernetes cluster.

## Features & Usage
Below is an example of a "criss-cross" pattern of 15 vehicles centered around The Elipse in Washington, DC. The vehicles are spaced apart by 24 degrees of bearing, at a distance of 5 kilometers from The Elipse. Each vehicle's route takes it to the opposite side of the circle. Here's a view with the routes turned on for all vehicles:
![Roadrunner Home Page example](./Resources/img/RoadrunnerViewer-2026-05-28-1.png)

### Interactive Map Controls
The frontend features a comprehensive map view with a floating toolbar pinned to the right side of the screen to customize your experience:
* ![Fit All](./Resources/img/FitAll.png) **Fit All**: Automatically adjusts the map's zoom and pan to fit all currently active vehicles within your viewing area.
* ![Map Layers](./Resources/img/Satellite.png) **Map Layers**: Seamlessly switch the map background between a standard street layout and a satellite imagery view.
* ![Show/Hide All Routes](./Resources/img/ShowAllRoutes.png) **Route Visibility**: Globally toggle the visibility of route lines drawn behind the vehicles. Alternatively, you can click directly near an individual vehicle to toggle its specific route and popup.
* ![Interpolation](./Resources/img/Interpolation.png) **Movement Interpolation**: Toggle vehicle movement interpolation on for fluid vehicle animations, or off to observe the raw, un-interpolated data points streaming from the backend.
* ![Active Vehicle Chart](./Resources/img/VehicleChart.png) **Active Vehicle Chart**: Opens a detailed data chart for active vehicles directly over the map view.

### Driver's View
The frontend allows a user to "jump" into a vehicle on the map, and get a first person view of what the driver would see.  Note that the Active Vehicle Chart is available to warp within the vehicle's lifetime.
![Roadrunner Driver Page example](./Resources/img/RoadrunnerViewer-2026-05-28-2.png)
In the driver's view, other vehicles in the simulation are visible.
![Roadrunner Driver Page with other vehicles](./Resources/img/RoadrunnerViewer-2026-05-28-3.png)
### Playback and Monitoring (Standard Features)
Users can track and review vehicle simulations utilizing several top-navigation panels:

#### Bookmarks
Displays a curated list of past simulation sessions, provided by the administrator, with descriptions of the scenario. Selecting a bookmark adjusts the application clock and transports you to the Driver View for that specific vehicle's historical run.
  
![Bookmarks Panel](./Resources/img/BookmarksPanel.png)

#### Sim Table
Provides a paginated tabular layout of all simulations that have occurred within the retention period. For each session, it lists the vehicle ID, initiating user, and start time. Users can launch playback of a specific session or use the "Now" button to jump to the current time.
  
![Sim Table Panel](./Resources/img/SimTable.png)

#### Active Vehicle Plot
An interactive chart showing the number of concurrent sessions over the retention period. Users can zoom in via mouse wheel or pinch gestures, and click/double-tap to set the playback to a specific time.
  
![Active Vehicle Plot](./Resources/img/VehiclePlot-7Day.png)
  
When accessed from the Driver View, this plot automatically zooms into the selected vehicle's lifetime to easily jump back or forward in its journey.
![Zoomed Active Vehicle Plot](./Resources/img/VehiclePlot-DriverView.png)

### Creator Features
Users whose accounts are assigned to the 'creator' group in Amazon Cognito (requested via the administrator) can generate new simulations up to 30 times a day:

#### Create Vehicle
Allows users to start a simulation by establishing a starting point, destination, and vehicle parameters. Origins and destinations can be specified via address autocomplete or by clicking directly on the map.
![Create Vehicle Panel](./Resources/img/CreateVehiclePanel.png)

#### Create Criss-Cross
A specialized panel to generate intersection routing or demonstration paths. By selecting a center point on the map, a radius, and a number of vehicles, users can spawn multiple vehicles that travel from the edge of the circle, through the center point, to the opposite side.  Once the center point has been chosen, a circle appears showing where vehicles will be created
![Create Criss-Cross: Selection](./Resources/img/CrissCross-Select.png)
  
After the Generate button has been depressed, vehicles are created as close as possible to the requested position
![Create Criss-Cross: Selection](./Resources/img/CrissCross-Generate.png)

## Authentication and Privacy
Authentication is managed via Google Sign-in and Amazon Cognito. Roadrunner may receive basic account information (name, email, user ID) but does not receive or store passwords. To maintain login sessions and secure APIs, the application uses cookies, browser storage, and authentication tokens. Third-party providers such as Google, Amazon Cognito, AWS, and Mapbox may process limited technical data needed for hosting, mapping, routing, and logging. 

**Disclaimer**: Roadrunner is a demo project, not a production transportation, dispatch, navigation, or safety system. It may change over time, contain bugs, or experience downtime. The application is not intended to collect sensitive personal information, and you should not enter confidential data into it. Roadrunner does not sell user data.
Provides an interface to visualize Vehicles travelling in a Roadrunner system.

## Setup environment

The application has integrations with Amazon Cognito for authentication and MapBox for map displays and georeferencing.  Signup for an Amazon AWS at https://aws.amazon.com/.  Signup for a MapBox account at https://account.mapbox.com/auth/signup/ .

The following environment variables need to be set for the application to startup:

* REACT_APP_ROADRUNNER_REST_URL_BASE : Base REST URL of the Roadrunner application (ex:"http://localhost:8080")
* REACT_APP_PUBLIC_URL : (ex:"http://localhost:3000")
* REACT_APP_MAPBOX_API_URL : URL to MapBox API (should be "https://api.mapbox.com/")
* REACT_APP_LANDING_PAGE_URL : URL to go to when logging out ("https://tarterware.com/")
* REACT_APP_COGNITO_REDIRECT_SIGN_IN: (ex:"http://localhost:3000")
* REACT_APP_COGNITO_REDIRECT_SIGN_OUT: (ex:"http://localhost:3000")
* REACT_APP_MAPBOX_TOKEN : "secret_mapbox_token"
* REACT_APP_COGNITO_AUTHORITY : "Amazon Cognito Authority URL"
* REACT_APP_COGNITO_CLIENT_ID : "Amazon Cognito Client ID"
* REACT_APP_COGNITO_REDIRECT_URI : "Amazon Cognito Redirect URL"
* REACT_APP_COGNITO_USER_POOL_ID : "Amazon Cognito User Pool ID"
* REACT_APP_COGNITO_USER_POOL_CLIENT_ID : "Amazon Cognito User Pool Client ID"
* REACT_APP_COGNITO_DOMAIN : "Amazon Cognito Domain"

In my development environement, the first six reside in the .env file in the root directory of the project.  The sensitive keys appearing in the last seven lines above are included via a .env.local file during development.  (Note that this file is explicitly excluded from git in the .gitignore file).

## Run it

First, make sure that you have an instance of Roadrunner executing [SteveTarter/roadrunner](https://github.com/SteveTarter/roadrunner).
Next, obtain the repository and descend into the directory:

```bash
    git clone https://github.com/SteveTarter/roadrunner-view.git
    cd roadrunner-view
```

Update the .env and .env.local files to point to your Mapbox keys, Auth0 resources, and local resources.  Next, compile and run:

```bash
    npm install
    npm start
```

The browser will most likely open on npm start, if not, then execute:

```bash
    open http://localhost:3000/
```
