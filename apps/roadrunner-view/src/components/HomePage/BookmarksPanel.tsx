import React, { useState, useEffect } from 'react';
import { fetchAuthSession } from "aws-amplify/auth";
import { CONFIG } from "../../config";
import { Card, CardHeader, CardBody, Button, ListGroup, ListGroupItem, Spinner } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlay, faTimes } from '@fortawesome/free-solid-svg-icons';

export type Bookmark = {
  vehicleId: string;
  start: number;
  title: string;
  description: string;
};

interface BookmarksPanelProps {
  onClose: () => void;
  // This prop allows the parent HomePage to handle the actual map/playback transition
  onSelectBookmark: (vehicleId: string, startTime: number) => void;
}

export const BookmarksPanel: React.FC<BookmarksPanelProps> = ({ onClose, onSelectBookmark }) => {
  const [bookmarks, setBookmarks] = useState<Bookmark[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchBookmarks = async () => {
      try {
        // Get the latest session right before the call
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("Session expired");
          return;
        }

        const url = `${CONFIG.ROADRUNNER_REST_URL_BASE}/api/bookmarks`;

        const response = await fetch(url, {
          method: 'get',
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          credentials: 'include'
        });

        if (!response.ok) {
          throw new Error(`Error fetching bookmarks: ${response.statusText}`);
        }
        const data = await response.json();
        setBookmarks(data);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchBookmarks();
  }, []);

  return (
    <Card style={{ width: '20rem', alignSelf: 'end', top: 10 }}>

      <CardHeader className="d-flex justify-content-between align-items-center bg-dark text-white py-2">
        <h6 className="mb-0">Bookmarks</h6>
        <Button onClick={onClose} variant="white" className="text-white">
          <FontAwesomeIcon icon={faTimes} />
        </Button>
      </CardHeader>

      <CardBody style={{ maxHeight: '60vh', overflowY: 'auto' }} className="p-0">
        {loading && (
          <div className="text-center p-4">
            <Spinner color="primary" />
          </div>
        )}

        {error && (
          <div className="text-danger text-center p-4">
            Failed to load bookmarks: {error}
          </div>
        )}

        {!loading && !error && bookmarks.length === 0 && (
          <div className="text-muted text-center p-4">
            No curated scenarios available.
          </div>
        )}

        {!loading && !error && bookmarks.length > 0 && (
          <ListGroup flush>
            {bookmarks.map((bookmark) => (
              <ListGroupItem key={bookmark.vehicleId} className="p-3 border-bottom">
                <div className="d-flex justify-content-between align-items-start">
                  <div className="pe-3">
                    <h6 className="font-weight-bold mb-1 text-primary">{bookmark.title}</h6>
                    <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
                      {bookmark.description}
                    </p>
                  </div>
                  <Button
                    color="success"
                    size="sm"
                    className="flex-shrink-0 mt-1 shadow-sm"
                    onClick={() => onSelectBookmark(bookmark.vehicleId, bookmark.start)}
                    title="Jump to Scenario"
                  >
                    <FontAwesomeIcon icon={faPlay} /> Jump
                  </Button>
                </div>
              </ListGroupItem>
            ))}
          </ListGroup>
        )}
      </CardBody>
    </Card>
  );
};