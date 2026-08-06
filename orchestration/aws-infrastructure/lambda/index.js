import pg from 'pg';
const { Pool } = pg;

const pool = new Pool({
  host: process.env.DB_HOST,
  port: parseInt(process.env.DB_PORT || '5432'),
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  ssl: {
    rejectUnauthorized: false
  }
});

const corsHeaders = {
  'Access-Control-Allow-Origin': 'https://roadrunner-view.tarterware.com',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Credentials': 'true',
  'Access-Control-Max-Age': '3600'
};

const UNSET_VALUE = 'Unset';

// Helper to parse duration strings like "5s", "10m" to milliseconds
function parseDuration(d, defaultMs = 5000) {
  if (!d || d === UNSET_VALUE) return defaultMs;
  const match = d.match(/^(\d+)(ms|s|m|h)$/);
  if (!match) return defaultMs;
  const value = parseInt(match[1]);
  const unit = match[2];
  switch (unit) {
    case 'ms': return value;
    case 's': return value * 1000;
    case 'm': return value * 60000;
    case 'h': return value * 3600000;
    default: return defaultMs;
  }
}

// Helper to verify if user claims contain the "superuser" group
function isSuperuser(event) {
  const claims = event.requestContext?.authorizer?.jwt?.claims;
  if (!claims) return false;
  const groups = claims['cognito:groups'];
  if (!groups) return false;
  if (Array.isArray(groups)) {
    return groups.includes('superuser');
  }
  // Try parsing in case it is a JSON string or comma-separated list
  try {
    const parsed = JSON.parse(groups);
    if (Array.isArray(parsed)) return parsed.includes('superuser');
  } catch (e) {
    // Ignore and fallback
  }
  return groups.split(/[\s,]+/).includes('superuser');
}

// Helper to extract user email
function getUserEmail(event) {
  return event.requestContext?.authorizer?.jwt?.claims?.email || 'unknown-user';
}

export const handler = async (event) => {
  const method = event.requestContext.http.method;
  const path = event.requestContext.http.path;

  console.log(`Received request: ${method} ${path}`);

  // Handle preflight OPTIONS requests
  if (method === 'OPTIONS') {
    return {
      statusCode: 200,
      headers: corsHeaders,
      body: ''
    };
  }

  try {
    /* -------------------------------------------------------------------------
     * 1. GET /api/db-playback/state
     * ------------------------------------------------------------------------- */
    if (method === 'GET' && path === '/api/db-playback/state') {
      const qParams = event.queryStringParameters || {};
      const timestampStr = qParams.timestamp || UNSET_VALUE;
      const windowPeriodStr = qParams.windowPeriod || '5s';
      const page = parseInt(qParams.page || '0');
      const pageSize = parseInt(qParams.pageSize || '10');

      let endTime;
      if (timestampStr === UNSET_VALUE) {
        endTime = new Date();
      } else {
        endTime = new Date(timestampStr);
      }

      let msWindowPeriod = parseDuration(windowPeriodStr, 5000);
      if (timestampStr === UNSET_VALUE) {
        msWindowPeriod = Math.max(msWindowPeriod, 10000); // minimum 10s window for live query
      }

      const startTime = new Date(endTime.getTime() - msWindowPeriod);

      const query = `
        SELECT DISTINCT ON (vehicle_id) id, vehicle_id, ST_Y(position) as latitude, ST_X(position) as longitude, heading, speed, sequence_number, ns_last_exec, position_valid, position_limited, color_code, manager_host, status, timestamp
        FROM vehicle_telemetry
        WHERE timestamp >= $1 AND timestamp <= $2
        ORDER BY vehicle_id, id DESC
      `;

      const result = await pool.query(query, [startTime.toISOString(), endTime.toISOString()]);

      const states = result.rows.map(r => ({
        id: r.vehicle_id,
        metersOffset: 0,
        positionLimited: r.position_limited,
        positionValid: r.position_valid,
        degLatitude: r.latitude,
        degLongitude: r.longitude,
        metersPerSecondDesired: 0,
        metersPerSecond: r.speed,
        mssAcceleration: 0,
        degBearing: r.heading,
        colorCode: r.color_code,
        managerHost: r.manager_host,
        msEpochLastRun: new Date(r.timestamp).getTime(),
        nsLastExec: parseInt(r.ns_last_exec || '0')
      }));

      // Pagination
      const listSize = states.length;
      const start = Math.min(page * pageSize, listSize);
      const end = Math.min(start + pageSize, listSize);
      const pageContent = states.slice(start, end);
      const totalPages = Math.ceil(listSize / pageSize) || 1;

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify({
          _embedded: {
            vehicleStates: pageContent
          },
          page: {
            size: pageSize,
            totalElements: listSize,
            totalPages: totalPages,
            number: page
          }
        })
      };
    }

    /* -------------------------------------------------------------------------
     * 2. GET /api/db-playback/get-vehicle-state
     * ------------------------------------------------------------------------- */
    if (method === 'GET' && path === '/api/db-playback/get-vehicle-state') {
      const qParams = event.queryStringParameters || {};
      const vehicleId = qParams.vehicleId || UNSET_VALUE;
      const timestampStr = qParams.timestamp || UNSET_VALUE;
      const windowPeriodStr = qParams.windowPeriod || '5s';

      if (vehicleId === UNSET_VALUE) {
        return {
          statusCode: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
          body: JSON.stringify({ message: "Missing vehicleId parameter" })
        };
      }

      let endTime;
      if (timestampStr === UNSET_VALUE) {
        endTime = new Date();
      } else {
        endTime = new Date(timestampStr);
      }

      let msWindowPeriod = parseDuration(windowPeriodStr, 5000);
      if (timestampStr === UNSET_VALUE) {
        msWindowPeriod = Math.max(msWindowPeriod, 10000);
      }

      const startTime = new Date(endTime.getTime() - msWindowPeriod);

      const query = `
        SELECT id, vehicle_id, ST_Y(position) as latitude, ST_X(position) as longitude, heading, speed, sequence_number, ns_last_exec, position_valid, position_limited, color_code, manager_host, status, timestamp
        FROM vehicle_telemetry
        WHERE vehicle_id = $1 AND timestamp >= $2 AND timestamp <= $3
        ORDER BY id DESC
        LIMIT 1
      `;

      const result = await pool.query(query, [vehicleId, startTime.toISOString(), endTime.toISOString()]);

      if (result.rows.length === 0) {
        return {
          statusCode: 404,
          headers: corsHeaders,
          body: ''
        };
      }

      const r = result.rows[0];
      const state = {
        id: r.vehicle_id,
        metersOffset: 0,
        positionLimited: r.position_limited,
        positionValid: r.position_valid,
        degLatitude: r.latitude,
        degLongitude: r.longitude,
        metersPerSecondDesired: 0,
        metersPerSecond: r.speed,
        mssAcceleration: 0,
        degBearing: r.heading,
        colorCode: r.color_code,
        managerHost: r.manager_host,
        msEpochLastRun: new Date(r.timestamp).getTime(),
        nsLastExec: parseInt(r.ns_last_exec || '0')
      };

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(state)
      };
    }

    /* -------------------------------------------------------------------------
     * 3. GET /api/db-vehicle/simulation-sessions
     * ------------------------------------------------------------------------- */
    if (method === 'GET' && path === '/api/db-vehicle/simulation-sessions') {
      const qParams = event.queryStringParameters || {};
      const page = parseInt(qParams.page || '0');
      const pageSize = parseInt(qParams.pageSize || '10');

      const query = `
        SELECT vehicle_id, username, color_code, start_time, end_time
        FROM simulation_sessions
        ORDER BY start_time DESC
      `;

      const result = await pool.query(query);

      const sessions = result.rows.map(r => ({
        id: r.vehicle_id,
        username: r.username,
        colorCode: r.color_code,
        start: new Date(r.start_time).getTime(),
        end: r.end_time ? new Date(r.end_time).getTime() : null
      }));

      // Pagination
      const listSize = sessions.length;
      const start = Math.min(page * pageSize, listSize);
      const end = Math.min(start + pageSize, listSize);
      const pageContent = sessions.slice(start, end);
      const totalPages = Math.ceil(listSize / pageSize) || 1;

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify({
          _embedded: {
            simulationSessions: pageContent
          },
          page: {
            size: pageSize,
            totalElements: listSize,
            totalPages: totalPages,
            number: page
          }
        })
      };
    }

    /* -------------------------------------------------------------------------
     * 4. GET /api/db-vehicle/get-vehicle-session/{vehicleId}
     * ------------------------------------------------------------------------- */
    const sessionMatch = path.match(/^\/api\/db-vehicle\/get-vehicle-session\/([^/]+)$/);
    if (method === 'GET' && sessionMatch) {
      const vehicleId = decodeURIComponent(sessionMatch[1]);

      const query = `
        SELECT vehicle_id, username, color_code, start_time, end_time
        FROM simulation_sessions
        WHERE vehicle_id = $1
      `;

      const result = await pool.query(query, [vehicleId]);

      if (result.rows.length === 0) {
        return {
          statusCode: 404,
          headers: corsHeaders,
          body: ''
        };
      }

      const r = result.rows[0];
      const session = {
        id: r.vehicle_id,
        username: r.username,
        colorCode: r.color_code,
        start: new Date(r.start_time).getTime(),
        end: r.end_time ? new Date(r.end_time).getTime() : null
      };

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(session)
      };
    }

    /* -------------------------------------------------------------------------
     * 5. GET /api/db-vehicle/get-vehicle-directions/{vehicleId}
     * ------------------------------------------------------------------------- */
    const directionsMatch = path.match(/^\/api\/db-vehicle\/get-vehicle-directions\/([^/]+)$/);
    if (method === 'GET' && directionsMatch) {
      const vehicleId = decodeURIComponent(directionsMatch[1]);

      const query = `
        SELECT vehicle_id, distance_meters, duration_seconds, ST_AsGeoJSON(route_path) as path_json, waypoints, speed_annotations
        FROM vehicle_routes
        WHERE vehicle_id = $1
      `;

      const result = await pool.query(query, [vehicleId]);

      if (result.rows.length === 0) {
        return {
          statusCode: 404,
          headers: corsHeaders,
          body: ''
        };
      }

      const r = result.rows[0];
      const pathGeo = JSON.parse(r.path_json);
      const coords = pathGeo.coordinates;

      // Reconstruct Mapbox-compatible nested JSON response
      const responseMap = {
        routes: [
          {
            legs: [
              {
                steps: [
                  {
                    geometry: {
                      coordinates: coords,
                      type: 'LineString'
                    }
                  }
                ]
              }
            ]
          }
        ]
      };

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(responseMap)
      };
    }

    /* -------------------------------------------------------------------------
     * 6. GET /api/db-bookmarks
     * ------------------------------------------------------------------------- */
    if (method === 'GET' && path === '/api/db-bookmarks') {
      const query = `
        SELECT vehicle_id, username, title, description, start_time
        FROM bookmarks
      `;

      const result = await pool.query(query);

      const bookmarks = result.rows.map(r => ({
        vehicleId: r.vehicle_id,
        start: new Date(r.start_time).getTime(),
        title: r.title,
        description: r.description
      }));

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(bookmarks)
      };
    }

    /* -------------------------------------------------------------------------
     * 7. GET /api/db-bookmarks/{vehicleId}
     * ------------------------------------------------------------------------- */
    const singleBookmarkMatch = path.match(/^\/api\/db-bookmarks\/([^/]+)$/);
    if (method === 'GET' && singleBookmarkMatch) {
      const vehicleId = decodeURIComponent(singleBookmarkMatch[1]);

      const query = `
        SELECT vehicle_id, username, title, description, start_time
        FROM bookmarks
        WHERE vehicle_id = $1
      `;

      const result = await pool.query(query, [vehicleId]);

      if (result.rows.length === 0) {
        return {
          statusCode: 404,
          headers: corsHeaders,
          body: ''
        };
      }

      const r = result.rows[0];
      const bookmark = {
        vehicleId: r.vehicle_id,
        start: new Date(r.start_time).getTime(),
        title: r.title,
        description: r.description
      };

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(bookmark)
      };
    }

    /* -------------------------------------------------------------------------
     * 8. POST /api/db-bookmarks (Auth check required)
     * ------------------------------------------------------------------------- */
    if (method === 'POST' && path === '/api/db-bookmarks') {
      if (!isSuperuser(event)) {
        return {
          statusCode: 403,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
          body: JSON.stringify({ message: `Access Denied: User must be superuser to create bookmarks!` })
        };
      }

      const body = JSON.parse(event.body || '{}');
      const email = getUserEmail(event);

      const query = `
        INSERT INTO bookmarks (vehicle_id, username, title, description, start_time, created_at)
        VALUES ($1, $2, $3, $4, $5, NOW())
        ON CONFLICT (vehicle_id) 
        DO UPDATE SET username = $2, title = $3, description = $4, start_time = $5
      `;

      await pool.query(query, [
        body.vehicleId,
        email,
        body.title,
        body.description,
        new Date(body.start).toISOString()
      ]);

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(body)
      };
    }

    /* -------------------------------------------------------------------------
     * 9. PUT /api/db-bookmarks (Auth check required)
     * ------------------------------------------------------------------------- */
    if (method === 'PUT' && path === '/api/db-bookmarks') {
      if (!isSuperuser(event)) {
        return {
          statusCode: 403,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
          body: JSON.stringify({ message: `Access Denied: User must be superuser to update bookmarks!` })
        };
      }

      const body = JSON.parse(event.body || '{}');

      const query = `
        UPDATE bookmarks
        SET title = $2, description = $3, start_time = $4
        WHERE vehicle_id = $1
      `;

      const result = await pool.query(query, [
        body.vehicleId,
        body.title,
        body.description,
        new Date(body.start).toISOString()
      ]);

      if (result.rowCount === 0) {
        return {
          statusCode: 404,
          headers: corsHeaders,
          body: ''
        };
      }

      return {
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
        body: JSON.stringify(body)
      };
    }

    /* -------------------------------------------------------------------------
     * 10. DELETE /api/db-bookmarks/{vehicleId} (Auth check required)
     * ------------------------------------------------------------------------- */
    if (method === 'DELETE' && singleBookmarkMatch) {
      const vehicleId = decodeURIComponent(singleBookmarkMatch[1]);

      if (!isSuperuser(event)) {
        return {
          statusCode: 403,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
          body: JSON.stringify({ message: `Access Denied: User must be superuser to delete bookmarks!` })
        };
      }

      const query = `
        DELETE FROM bookmarks WHERE vehicle_id = $1
      `;

      await pool.query(query, [vehicleId]);

      return {
        statusCode: 200,
        headers: corsHeaders,
        body: ''
      };
    }

    // Default route fallback: 404
    return {
      statusCode: 404,
      headers: corsHeaders,
      body: ''
    };

  } catch (err) {
    console.error('Lambda handler error:', err);
    return {
      statusCode: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
      body: JSON.stringify({ message: err.message || String(err) })
    };
  }
};
