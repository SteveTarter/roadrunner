import { fetchAuthSession } from "aws-amplify/auth";
import { useMemo, useState, useEffect } from 'react';
import { MaterialReactTable } from 'material-react-table';
import { CONFIG } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { Button } from "react-bootstrap";
import { useNavigate } from "react-router-dom";

const timeFormatOptions: Intl.DateTimeFormatOptions = {
    timeZone: 'UTC',
    month: 'numeric',
    day: 'numeric',
    year: '2-digit',
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
};

export const SimulationTable = (props: {
  toggleSimTable: any,
  returnToNow: any,
}) => {
  const navigate = useNavigate();

  const [data, setData] = useState([]);
  const [rowCount, setRowCount] = useState(0);

  const { playbackOffset } = usePlayback();

  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10
  });

  useEffect(() => {
    const controller = new AbortController();

    async function loadPage() {
      try {
        // Get the latest session right before the call
        const session = await fetchAuthSession();
        const accessToken = session.tokens?.accessToken?.toString();

        if (!accessToken) {
          console.error("Session expired");
          return;
        }

        const url =
          `${CONFIG.ROADRUNNER_REST_URL_BASE}` +
          `/api/vehicle/simulation-sessions?page=${pagination.pageIndex}&pageSize=${pagination.pageSize}`

        const res = await fetch(url, {
          method: 'GET',
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
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
  }, [pagination.pageIndex, pagination.pageSize]);

  const columns = useMemo(() => [
    {
      accessorKey: 'id',
      header: 'Vehicle',
      size: 150,
      Cell: ({ cell }: any) => cell.getValue()
    },
    {
      accessorKey: 'username',
      header: 'Username',
      size: 200,
      // Fallback to "unknown-user" if username is missing or empty
      Cell: ({ cell }: any) => cell.getValue() || "unknown-user"    },
    {
      accessorKey: 'start',
      header: 'Start Time',
      size: 200,
      Cell: ({ cell }: any) => new Date(cell.getValue()).toLocaleTimeString([], timeFormatOptions) + 'Z'
    },
    {
      header: 'Actions',
      size: 120,
      Cell: ({ row }: any) => {
        const { setPlaybackSession } = usePlayback();
        return (
          <Button
           size="sm"
           onClick={() => {
            setPlaybackSession(row.original.start);
            navigate(`/driver-view/${row.original.id}`);
          }}>
            ▶️ Playback
          </Button>
        );
      }
    }
  ], [navigate]);

  return (
    <div
      className="simulation-table-container"
      style={{
        position: 'relative',
        zIndex: 1000, // Ensure it's above the Map layers
        background: 'white',
        padding: '10px',
        borderRadius: '8px',
        paddingBottom: '65px',
        width: '94%',
        maxWidth: 'fit-content',
        margin: '10px auto',
        boxShadow: '0 4px 15px rgba(0,0,0,0.2)'
      }}
    >
      <MaterialReactTable
        columns={columns}
        data={data}
        manualPagination // Tells MRT NOT to do client-side paging
        rowCount={rowCount}
        onPaginationChange={setPagination}
        state={{ pagination }}
        layoutMode="grid" // Important for allowing columns to respect 'size'
        displayColumnDefOptions={{
          'mrt-row-actions': {
            size: 100,
          },
        }}
        muiTablePaperProps={{
          sx: {
            width: '100%',
            overflow: 'hidden',
          }
        }}
        muiTableContainerProps={{
          sx: {
            maxHeight: '400px',
            maxWidth: '100%',
            overflowX: 'auto',
          }
        }}
        initialState={{ density: 'compact' }}
      />
      {playbackOffset !== 0 && (
        <Button
          variant="success"
          className="mb-2"
          onClick={() => {
            props.returnToNow();
            props.toggleSimTable();
          }}
          style={{
            position: 'absolute',
            bottom: '10px',
            right: '85px',
            zIndex: 1001,
          }}
        >
          Return to Now
        </Button>
      )}
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