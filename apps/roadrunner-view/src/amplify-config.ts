import { Amplify } from "aws-amplify";

export function configureAmplify() {
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: process.env.REACT_APP_COGNITO_USER_POOL_ID!,
        userPoolClientId: process.env.REACT_APP_COGNITO_USER_POOL_CLIENT_ID!,
        loginWith: {
          oauth: {
            domain: process.env.REACT_APP_COGNITO_DOMAIN!, // e.g. your-domain.auth.us-east-1.amazoncognito.com
            scopes: ["openid", "email", "profile", "aws.cognito.signin.user.admin"], // add "offline_access" if you want refresh tokens
            redirectSignIn: [process.env.REACT_APP_COGNITO_REDIRECT_SIGN_IN!],
            redirectSignOut: [process.env.REACT_APP_COGNITO_REDIRECT_SIGN_OUT!],
            responseType: "code", // Auth Code + PKCE
          },
        },
      },
    },
  });
}
