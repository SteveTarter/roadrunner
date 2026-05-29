Roadrunner is a portfolio vehicle-simulation application built to demonstrate full-stack, cloud, mapping, and distributed-system development. This project was started in part to gain real life experience with Kafka and Redis.

The application uses Google sign-in and Amazon Cognito for authentication. When you sign in, Roadrunner may receive basic account information such as your name, email address, and user identifier. Roadrunner does not receive or store your Google or Cognito password.

Roadrunner may use cookies, browser storage, or authentication tokens to maintain login sessions and secure API requests. Third-party providers such as Google, Amazon Cognito, AWS, and Mapbox may process limited technical data needed to provide authentication, hosting, mapping, routing, logging, or API functionality.

Roadrunner may collect limited technical logs, API request information, and simulation data for debugging, monitoring, security, and demonstration purposes. It is not intended to collect sensitive personal information, and you should not enter confidential or sensitive data into the application.

Roadrunner is a portfolio/demo project, not a production transportation, dispatch, navigation, or safety system. It may change over time, contain bugs, or experience downtime. Roadrunner does not sell user data.

Roadrunner's code is hosted on Github in three repos:
- [Roadrunner](https://github.com/SteveTarter/roadrunner): Spring Boot-based Backend
- [Roadrunner Viewer](https://github.com/SteveTarter/roadrunner-view): React-based viewer
- [Roadrunner K8S Orchestration](https://github.com/SteveTarter/roadrunner-k8s-orchestration): Install to minikube or AWS kubernetes cluster