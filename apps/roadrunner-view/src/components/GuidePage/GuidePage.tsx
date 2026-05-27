import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Button, Container } from "react-bootstrap";
import { useNavigate } from "react-router-dom";

export function GuidePage() {
  const [content, setContent] = useState("");

  const navigate = useNavigate();

  const goHome = () => {
    navigate(`/home`);
  }

  useEffect(() => {
    // Fetch the markdown file from the public directory
    fetch("/guide/guide.md")
      .then((res) => res.text())
      .then((text) => setContent(text));
  }, []);

  return (
<div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#ffffff', // Ensures parent backdrop textures do not bleed through
        zIndex: 2000 // Forces the viewport container over any background map configurations
      }}
    >
      {/* Top Header - Fixed */}
      <div className="d-flex justify-content-between align-items-center px-4 py-3 border-bottom shadow-sm">
        <h4 className="mb-0">Roadrunner Guide</h4>
        <Button
          id="qsLoginBtn"
          variant="primary"
          onClick={goHome}
        >
          OK
        </Button>
      </div>

      {/* Markdown Content - Scrollable */}
      <div
        style={{
          flex: 1, // Takes up remaining vertical space
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch', // Fluid inertial scrolling acceleration for iOS Safari
        }}
      >
        <main className="container-fluid px-4 py-4" style={{ paddingBottom: "100px" }}>
          <Container>
            <ReactMarkdown>{content}</ReactMarkdown>
          </Container>
        </main>
      </div>
    </div>
  );
}