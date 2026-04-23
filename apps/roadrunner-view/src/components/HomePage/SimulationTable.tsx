import { fetchAuthSession } from "aws-amplify/auth";
import { useMemo, useState, useEffect } from 'react';
import { MaterialReactTable } from 'material-react-table';
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { Button } from "react-bootstrap";

export const SimulationTable = (props: {
  toggleSimTable: any,
  returnToNow: any,
}) => {
  const [token, setToken] = useState("");
  const [data, setData] = useState([]);
  const [rowCount, setRowCount] = useState(0);

  const { playbackOffset } = usePlayback();

  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10
  });

  // Load (and silently refresh) an access token
  useEffect(() => {
    let cancelled = false;

    async function loadToken() {
      if (token) return;

      try {
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("No access token available. Route guard should have redirected to login.");
          return;
        }

        if (!cancelled) setToken(accessToken);
      } catch (error: any) {
        console.error("Error fetching token:", error?.message ?? error);
      }
    }

    loadToken();
    return () => { cancelled = true; };
  }, [token]);

  useEffect(() => {
    if (!token) return;

    const controller = new AbortController();

    async function loadPage() {
      try {
        const url =
          `${CONFIG.ROADRUNNER_REST_URL_BASE}` +
          `/api/vehicle/simulation-sessions?page=${pagination.pageIndex}&pageSize=${pagination.pageSize}`

        const res = await fetch(url, {
          method: 'GET',
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          signal: controller.signal,
        });

        const pagedModel = await res.json();
        const sessions = pagedModel._embedded?.simulationSessions ?? [];

        setData(sessions);
        setRowCount(pagedModel.page?.totalElements ?? 0);
      } catch (err:any) {
        if (err.name !== "AbortError") {
          console.error("Fetch error:", err);
        }
      }
    }

    loadPage();

    return () => controller.abort();
  }, [token, pagination.pageIndex, pagination.pageSize]);

  const columns = useMemo(() => [
    { accessorKey: 'id', header: 'Session ID' },
    { accessorKey: 'start', header: 'Start Time' },
    {
      header: 'Actions',
      Cell: ({ row }: any) => {
        const { setPlaybackSession } = usePlayback();
        return (
          <Button
           size="sm"
           onClick={() => {
            setPlaybackSession(row.original.start);
            props.toggleSimTable();
          }}>
            ▶️ Playback
          </Button>
        );
      }
    }
  ], [props]);

  return (
    <div
      className="simulation-table-container"
      style={{
        position: 'relative',
        zIndex: 1000, // Ensure it's above the Map layers
        background: 'white',
        padding: '10px',
        borderRadius: '8px',
        paddingBottom: '65px'
      }}
    >
      {/* Show the button only if we are currently in playback mode */}
      {playbackOffset !== 0 && (
        <Button
          variant="warning"
          className="mb-2"
          onClick={() => {
            props.returnToNow();
            props.toggleSimTable();
          }}
          style={{
            position: 'relative',
            zIndex: 1001,
            display: 'block', // Ensure it takes up its own line
            width: '100%'
          }}
        >
          Return to Current Time (Live)
        </Button>
      )}
      <MaterialReactTable
        columns={columns}
        data={data}
        manualPagination // Tells MRT NOT to do client-side paging
        rowCount={rowCount}
        onPaginationChange={setPagination}
        state={{
          pagination,
         }}
        muiTableContainerProps={{ sx: { maxHeight: '400px' } }} // Sets the scrollable area
        initialState={{ density: 'compact' }}
      />
      <Button
        variant="warning"
        className="mb-2"
        onClick={() => {
          props.toggleSimTable();
        }}
        style={{
          position: 'absolute',
          bottom: '10px',
          right: '10px',
          zIndex: 1001,
        }}
      >
        Close
      </Button>
    </div>
  );
};