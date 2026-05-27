import { useCallback, useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Button, Container } from "react-bootstrap";
import { useNavigate } from "react-router-dom";

export function GuidePage() {
  // Get the Guide ID from the URL in the window
  const guideId = (window.location.pathname).split('/')[2];

  const [content, setContent] = useState("");

  const [returnPath, setReturnPath] = useState("/home");

  const navigate = useNavigate();

  useEffect(() => {
    if (!guideId) return;

    // Determine the file to display.
    let docPath = '/guide/NoSuchGuide.md';

    switch (guideId) {
      case 'overview':
        docPath = '/guide/UserGuide.md';
        setReturnPath('/home');
        break;

      case 'bookmarks':
        docPath = '/guide/Bookmarks.md';
        setReturnPath('/guide/overview')
        break;

      case 'sim-table':
        docPath = '/guide/SimTable.md';
        setReturnPath('/guide/overview')
        break;

      case 'active-vehicle-plot':
        docPath = '/guide/ActiveVehiclePlot.md';
        setReturnPath('/guide/overview')
        break;

      case 'criss-cross-panel':
        docPath = '/guide/CreateCrissCrossPanel.md';
        setReturnPath('/guide/overview')
        break;

      case 'create-vehicle-panel':
        docPath = '/guide/CreateVehiclePanel.md';
        setReturnPath('/guide/overview')
        break;
    }

    // Fetch the markdown file from the public directory
    fetch(docPath)
      .then((res) => res.text())
      .then((text) => setContent(text));
  }, [guideId]);

  const closePage = useCallback(() => {
    navigate(returnPath);
  },[navigate, returnPath]);

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
          onClick={closePage}
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