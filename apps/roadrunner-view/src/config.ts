/**
 * Utility to get environment variables.
 * Prioritizes window._env_ (injected at runtime by Kubernetes)
 * falls back to process.env (available during local npm start)
 */
const getEnv = (key: string, defaultValue: string = ""): string => {
  const runtimeValue = (window as any)._env_?.[key];
  const buildTimeValue = process.env[key];

  return runtimeValue || buildTimeValue || defaultValue;
};

export const CONFIG = {
  // Cognito
  COGNITO_AUTHORITY: getEnv("REACT_APP_COGNITO_AUTHORITY"),
  COGNITO_USER_POOL_ID: getEnv("REACT_APP_COGNITO_USER_POOL_ID"),
  COGNITO_USER_POOL_CLIENT_ID: getEnv("REACT_APP_COGNITO_USER_POOL_CLIENT_ID"),
  COGNITO_CLIENT_ID: getEnv("REACT_APP_COGNITO_USER_POOL_CLIENT_ID"),
  COGNITO_DOMAIN: getEnv("REACT_APP_COGNITO_DOMAIN"),
  COGNITO_REDIRECT_URI: getEnv("REACT_APP_COGNITO_REDIRECT_URI"),
  COGNITO_REDIRECT_SIGN_IN: getEnv("REACT_APP_COGNITO_REDIRECT_SIGN_IN"),
  COGNITO_REDIRECT_SIGN_OUT: getEnv("REACT_APP_COGNITO_REDIRECT_SIGN_OUT"),

  // Mapbox
  MAPBOX_TOKEN: getEnv("REACT_APP_MAPBOX_TOKEN"),
  MAPBOX_API_URL: getEnv("REACT_APP_MAPBOX_API_URL"),

  // Google Maps
  GOOGLE_MAPS_API_KEY: getEnv("REACT_APP_GOOGLE_MAPS_API_KEY"),

  // API URLs
  ROADRUNNER_REST_URL_BASE: getEnv("REACT_APP_ROADRUNNER_REST_URL_BASE"),

  // Misc
  LANDING_PAGE_URL: getEnv("REACT_APP_LANDING_PAGE_URL"),
  PUBLIC_URL: getEnv("REACT_APP_PUBLIC_URL", "/"),
  IS_PRODUCTION: getEnv("NODE_ENV") === "production",
};

/**
 * Dynamically resolves the full URL based on the API path.
 * - Database paths (`/api/db-*`) route to the AWS API Gateway (if configured).
 * - Control plane/simulation paths (like `/api/vehicle/*` or `/api/playback/*`)
 *   route to the local Minikube cluster (`https://roadrunner.tarterware.info`) when deployed to CloudFront.
 */
export const getRestUrl = (apiPath: string): string => {
  const base = CONFIG.ROADRUNNER_REST_URL_BASE || "";
  const isDbPath = apiPath.startsWith('/api/db-');

  if (isDbPath) {
    return `${base}${apiPath}`;
  }

  // If REST URL base points to the AWS custom domain (.com), route control plane requests to local Minikube (.info)
  if (base.includes('.tarterware.com')) {
    return `https://roadrunner.tarterware.info${apiPath}`;
  }

  return `${base}${apiPath}`;
};

// Validate critical configs on load
if (!CONFIG.COGNITO_USER_POOL_ID && !CONFIG.IS_PRODUCTION) {
    console.error("CRITICAL ERROR: Cognito User Pool ID is missing!");
}