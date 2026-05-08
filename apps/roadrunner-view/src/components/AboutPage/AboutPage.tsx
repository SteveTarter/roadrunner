import { Button } from "react-bootstrap";
import { useNavigate } from "react-router-dom";

export function AboutPage() {
  const navigate = useNavigate();

  const goHome = () => {
    navigate(`/home`);
  }

  return (
    <main className="container py-4">
      <h1>About Roadrunner / Privacy Notice</h1>

      <p>
        Roadrunner is a portfolio vehicle-simulation application built to
        demonstrate full-stack, cloud, mapping, and distributed-system
        development.  This project was started in part to gain real life
        experience with Kafka and Redis.
      </p>

      <p>
        The application uses Google sign-in and Amazon Cognito for
        authentication. When you sign in, Roadrunner may receive basic account
        information such as your name, email address, and user identifier.
        Roadrunner does not receive or store your Google or Cognito password.
      </p>

      <p>
        Roadrunner may use cookies, browser storage, or authentication tokens to
        maintain login sessions and secure API requests. Third-party providers
        such as Google, Amazon Cognito, AWS, and Mapbox may process limited
        technical data needed to provide authentication, hosting, mapping,
        routing, logging, or API functionality.
      </p>

      <p>
        Roadrunner may collect limited technical logs, API request information,
        and simulation data for debugging, monitoring, security, and
        demonstration purposes. It is not intended to collect sensitive personal
        information, and you should not enter confidential or sensitive data into
        the application.
      </p>

      <p>
        Roadrunner is a portfolio/demo project, not a production transportation,
        dispatch, navigation, or safety system. It may change over time, contain
        bugs, or experience downtime. Roadrunner does not sell user data.
      </p>

      <p>
        Roadrunner's code is hosted on Github in three repos:<br/>
        <ul>
          <li><a href="https://github.com/SteveTarter/roadrunner">Roadrunner</a>: Spring Boot-based Backend</li>
          <li><a href="https://github.com/SteveTarter/roadrunner-view">Roadrunner Viewer</a>: React-based viewer</li>
          <li><a href="https://github.com/SteveTarter/roadrunner-k8s-orchestration">Roadrunner K8S Orchestration</a>: Install to minikube or AWS kubernetes cluster</li>
        </ul>
      </p>
      <Button
        id="qsLoginBtn"
        color="primary"
        className="btn-margin"
        onClick={goHome}
      >
        OK
      </Button>

    </main>
  );
}