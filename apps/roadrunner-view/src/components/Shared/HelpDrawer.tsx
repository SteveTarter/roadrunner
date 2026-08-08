import React, { useEffect, useState } from 'react';
import { Offcanvas, Form, Spinner, Alert } from 'react-bootstrap';
import ReactMarkdown from 'react-markdown';
import { useHelp } from '../../context/HelpContext';
import { HELP_TOPICS } from '../../config/helpTopics';

export const HelpDrawer: React.FC = () => {
  const { activeTopicId, isOpen, closeHelp, openHelp } = useHelp();
  const [content, setContent] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const topicKey = activeTopicId && HELP_TOPICS[activeTopicId] ? activeTopicId : 'overview';
  const topic = HELP_TOPICS[topicKey];

  useEffect(() => {
    if (!isOpen || !topic) return;

    setLoading(true);
    setError(null);

    fetch(topic.markdownUrl)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`Failed to load documentation (${res.status})`);
        }
        return res.text();
      })
      .then((text) => {
        setContent(text);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Help loading error:', err);
        setError(`Unable to load guide content for "${topic.title}".`);
        setLoading(false);
      });
  }, [isOpen, topic]);

  return (
    <Offcanvas
      show={isOpen}
      onHide={closeHelp}
      placement="end"
      backdrop={false}
      style={{
        width: '420px',
        maxWidth: '90vw',
        zIndex: 2100,
        boxShadow: '-4px 0 20px rgba(0, 0, 0, 0.25)'
      }}
    >
      <Offcanvas.Header closeButton className="border-bottom bg-light">
        <div className="w-100 me-3">
          <Form.Select
            size="sm"
            value={topicKey}
            onChange={(e) => openHelp(e.target.value)}
            className="fw-bold"
          >
            {Object.entries(HELP_TOPICS).map(([key, t]) => (
              <option key={key} value={key}>
                {t.title}
              </option>
            ))}
          </Form.Select>
        </div>
      </Offcanvas.Header>

      <Offcanvas.Body className="p-3" style={{ overflowY: 'auto' }}>
        {loading && (
          <div className="text-center py-5">
            <Spinner animation="border" variant="primary" role="status" />
            <div className="text-muted mt-2">Loading guide...</div>
          </div>
        )}

        {error && (
          <Alert variant="warning" className="my-3">
            {error}
          </Alert>
        )}

        {!loading && !error && (
          <div className="markdown-help-body">
            <ReactMarkdown
              components={{
                img: ({ node, ...props }) => (
                  <img
                    style={{
                      maxWidth: '100%',
                      height: 'auto',
                      borderRadius: '6px',
                      border: '1px solid #dee2e6',
                      boxShadow: '0 2px 5px rgba(0,0,0,0.1)',
                      marginTop: '8px',
                      marginBottom: '16px'
                    }}
                    {...props}
                    alt={props.alt || 'Help graphic'}
                  />
                )
              }}
            >
              {content}
            </ReactMarkdown>
          </div>
        )}
      </Offcanvas.Body>
    </Offcanvas>
  );
};
