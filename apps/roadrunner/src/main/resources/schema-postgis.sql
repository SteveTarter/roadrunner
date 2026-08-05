CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

CREATE TABLE IF NOT EXISTS simulation_sessions (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    color_code VARCHAR(10) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS trip_plans (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    stops TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bookmarks (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100),
    title VARCHAR(200),
    description TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vehicle_routes (
    vehicle_id VARCHAR(50) PRIMARY KEY REFERENCES trip_plans(vehicle_id) ON DELETE CASCADE,
    distance_meters DOUBLE PRECISION NOT NULL,
    duration_seconds DOUBLE PRECISION NOT NULL,
    route_path GEOMETRY(LineString, 4326) NOT NULL,
    waypoints TEXT NOT NULL,
    speed_annotations TEXT
);
