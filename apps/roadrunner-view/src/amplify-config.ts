import { Amplify } from "aws-amplify";
import { CONFIG } from "./config"

export function configureAmplify() {
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: CONFIG.COGNITO_USER_POOL_ID,
        userPoolClientId: CONFIG.COGNITO_USER_POOL_CLIENT_ID,
        loginWith: {
          oauth: {
            domain: CONFIG.COGNITO_DOMAIN, // e.g. your-domain.auth.us-east-1.amazoncognito.com
            scopes: ["openid", "email", "profile", "aws.cognito.signin.user.admin"], // add "offline_access" if you want refresh tokens
            redirectSignIn: [CONFIG.COGNITO_REDIRECT_SIGN_IN],
            redirectSignOut: [CONFIG.COGNITO_REDIRECT_SIGN_OUT],
            responseType: "code", // Auth Code + PKCE
          },
        },
      },
    },
  });
}
