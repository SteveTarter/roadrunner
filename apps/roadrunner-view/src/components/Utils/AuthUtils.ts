import { fetchAuthSession } from "aws-amplify/auth";

let cachedAccessToken: string | null = null;
let lastTokenFetchTime = 0;

/**
 * Retrieves a cached access token or fetches a new one if expired.
 * Token is cached in memory for up to 5 minutes to prevent CPU/memory overhead
 * from frequent AWS Amplify fetchAuthSession calls.
 */
export async function getCachedAuthToken(): Promise<string | null> {
  const now = Date.now();
  if (cachedAccessToken && (now - lastTokenFetchTime < 5 * 60 * 1000)) {
    return cachedAccessToken;
  }
  try {
    const session = await fetchAuthSession();
    cachedAccessToken = session.tokens?.accessToken?.toString() || null;
    lastTokenFetchTime = now;
    return cachedAccessToken;
  } catch (e) {
    console.error("Error fetching auth session:", e);
    if (cachedAccessToken) {
      return cachedAccessToken;
    }
    return null;
  }
}
