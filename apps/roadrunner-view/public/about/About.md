About Roadrunner
================

Roadrunner is a full-stack, cloud-native vehicle simulation application built to demonstrate real-time telemetry streaming, interactive 2D/3D geospatial visualization, and distributed systems architecture.

Architecture Overview
---------------------
- **Backend Engine** (`apps/roadrunner`): Java 17 / Spring Boot service performing vehicle trajectory generation, route interpolation, REST control plane, and real-time Kafka & Redis telemetry streaming.
- **Frontend Viewer** (`apps/roadrunner-view`): React / TypeScript application with dual Mapbox GL JS & Google Maps 3D WebGL rendering, 1st-person driver view perspective, and contextual help documentation.
- **Infrastructure & Cloud** (`orchestration/`): Terraform automation, AWS Aurora Serverless PostGIS, S3 + CloudFront CDN, Amazon Cognito auth, and Kubernetes / Minikube manifests.

GitHub Repository
-----------------
The project is consolidated in a single monorepo on GitHub:
- [Roadrunner Monorepo](https://github.com/SteveTarter/roadrunner)

Privacy Notice & Disclaimer
---------------------------
- **Authentication**: Roadrunner uses Google Sign-In and Amazon Cognito for authentication. When you sign in, Roadrunner receives basic account information (name, email address, user ID). Passwords are managed directly by Cognito/Google and are never received or stored by Roadrunner.
- **Cookies & Storage**: Tokens, cookies, or browser storage are used solely to maintain authenticated sessions and secure API requests.
- **Data & Logging**: Limited technical logs and simulation telemetry data are collected for monitoring, security, and demo purposes. Do not submit sensitive personal information.
- **Disclaimer**: Roadrunner is a portfolio demonstration application, not a production transportation, dispatch, navigation, or safety system.