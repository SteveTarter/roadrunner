Roadrunner: How to Use the Map Interface
========================================

Welcome to the Roadrunner vehicle-simulation viewer! The application provides a real-time, interactive map to monitor and manage simulated vehicle fleets. Below is a guide to the tools and panels available on the Home Page.

Map View Controls
-----------------

Pinned to the right side of the screen, you will find a floating toolbar to customize your map view.

*   ![Fit All Button](./images/FitAll.png) Click this button to automatically adjust the map's zoom and pan to fit all currently active vehicles within your viewing area.

*   ![Street/Satellite Button](./images/Satellite.png) Use this button to seamlessly switch the map background between a standard street layout and a satellite imagery view.

*   ![Show All Routes Button](./images/ShowAllRoutes.png) ![Hide All Routes Button](./images/HideAllRoutes.png)These buttons allow you to globally toggle the visibility of the route lines drawn behind the vehicles. _(Note: You can also click directly near an individual vehicle on the map to toggle its specific route and popup.)_

*   ![Interpolation Button](./images/Interpolation.png) This tool toggles vehicle movement interpolation on and off. Enable smoothing for fluid vehicle animations, or disable it to see the raw, un-interpolated data points coming from the backend.

*   ![Active Vehicle Chart Button](./images/VehicleChart.png) Clicking this opens a detailed data chart/plot for active vehicles directly over the map view.


Application Menu (Manage Panels)
--------------------------------

The top navigation bar contains additional management panels for running and monitoring simulations.

### Standard Features

*   **Bookmarks:** Opens a panel where you can select from a curated list of previously saved simulation sessions. Selecting a bookmark will adjust the application clock and transport you to the Driver View for that specific vehicle's historical run.

*   **Sim Table:** Opens a tabular layout displaying simulation sessions that have occured within the retention period.

*   **Active Vehicle Plot:** Opens a chart displaying vehicle activity over the retention period.  Allows an alternate way to select a timeline to view past or current simulations.


### Creator Features

_Note: The following features require your user account to be assigned to the 'creator' group in Amazon Cognito._

*   **Create Vehicle:** Opens the creation panel, allowing you to establish a starting point, destination, and parameters for a new vehicle simulation.

*   **Create criss-cross:** Opens a specialized panel designed to generate a "criss-cross" simulation scenario, ideal for testing intersection routing or specific demonstration paths.
