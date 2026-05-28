import { Button, Container } from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import { useEffect, useState } from "react";

interface MarkdownPageLayoutProps {
  title: string,
  markdownUrl: string;
  onClose?: () => void;
}

export function MarkdownPageLayout({ title, markdownUrl, onClose }: MarkdownPageLayoutProps) {
  const navigate = useNavigate();
  const [content, setContent] = useState<string>("");

  // Fetch the markdown text when the component mounts or the URL changes
  useEffect(() => {
    fetch(markdownUrl)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to load markdown: ${response.statusText}`);
        }
        return response.text();
      })
      .then((text) => {
        setContent(text);
      })
      .catch((err) => {
        console.error(err);
        setContent(`Error!  Could not load the page at {markdownUrl}!`);
      });
  }, [markdownUrl]);

  const handleClose = () => {
    if (onClose) {
      onClose();
    } else {
      navigate(`/home`);
    }
  };

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
        overflowY: 'auto',
        backgroundColor: '#f8f9fa', // Ensures parent backdrop textures do not bleed through
        zIndex: 2000 // Forces the viewport container over any background map configurations
      }}
    >
      {/* Top Header - Fixed */}
      <div className="d-flex justify-content-between align-items-center px-4 py-3 border-bottom shadow-sm">
        <h4 className="mb-0">{title}</h4>
        <Button
          id="qsCloseBtn"
          variant="primary"
          onClick={handleClose}
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
            <ReactMarkdown
              components={{
                // This tells react-markdown: "Whenever you see a markdown image,
                // render it with these styles applied."
                img: ({node, ...props}) => (
                  <img
                    style={{
                      maxWidth: '100%',
                      height: 'auto',
                      borderRadius: '8px',
                      border: '1px solid #dee2e6', /* Light gray Bootstrap border */
                      boxShadow: '0 4px 6px rgba(0,0,0,0.1)', /* Subtle shadow */
                      marginTop: '10px',
                      marginBottom: '20px'
                    }}
                    {...props}
                    alt={props.alt || "Page image"}
                  />
                )
              }}
            >
              {content}
            </ReactMarkdown>
          </Container>
        </main>
      </div>
    </div>
  );
}