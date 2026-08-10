import { useMemo, useState, useEffect } from 'react';
import { MaterialReactTable } from 'material-react-table';
import { getRestUrl } from "../../config";
import { usePlayback } from "../../context/PlaybackContext";
import { getCachedAuthToken } from "../Utils/AuthUtils";
import { useSimulationSessionData } from "../../hooks/useSimulationSessionData";
import { Button } from "react-bootstrap";
import { HelpIconButton } from "../Shared/HelpIconButton";

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
  const [data, setData] = useState<any[]>([]);
  const [rowCount, setRowCount] = useState(0);

  const { playbackOffset, setPlaybackSession, dataSource } = usePlayback();
  const { simulationSessionMap } = useSimulationSessionData();

  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10
  });

  useEffect(() => {
    const controller = new AbortController();

    async function loadPage() {
      try {
        const accessToken = await getCachedAuthToken();

        const apiPath = dataSource === 'postgis' ? '/api/db-vehicle' : '/api/vehicle';
        const url = getRestUrl(
          `${apiPath}/simulation-sessions?page=${pagination.pageIndex}&pageSize=${pagination.pageSize}`
        );

        const headers: Record<string, string> = {
          "Content-Type": "application/json"
        };
        if (accessToken) {
          headers["Authorization"] = `Bearer ${accessToken}`;
        }

        const res = await fetch(url, {
          method: 'GET',
          headers,
          credentials: 'include',
          signal: controller.signal
        });

        if (!res.ok) {
          console.warn(`SimulationTable fetch returned status ${res.status}`);
          throw new Error(`HTTP ${res.status}`);
        }

        const result = await res.json();

        // Support HATEOAS (_embedded.simulationSessions), custom sessions property, or raw array
        const sessions =
          result._embedded?.simulationSessions ||
          result._embedded?.simulationSessionList ||
          result.sessions ||
          (Array.isArray(result) ? result : []);

        const total =
          result.page?.totalElements ??
          result.totalCount ??
          sessions.length;

        if (sessions.length > 0) {
          setData(sessions);
          setRowCount(total);
          return;
        }
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          console.error("Failed to load simulation sessions directly", err);
        }
      }

      // Fallback: If direct fetch failed or returned no items, use cached simulationSessionMap from useSimulationSessionData
      if (simulationSessionMap && simulationSessionMap.size() > 0) {
        const fallbackSessions = simulationSessionMap.values();
        const startIdx = pagination.pageIndex * pagination.pageSize;
        const endIdx = startIdx + pagination.pageSize;
        setData(fallbackSessions.slice(startIdx, endIdx));
        setRowCount(fallbackSessions.length);
      }
    }

    loadPage();
    return () => controller.abort();
  }, [pagination.pageIndex, pagination.pageSize, dataSource, simulationSessionMap]);

  const columns = useMemo(
    () => [
      {
        accessorFn: (row: any) => row.vehicleId || row.id,
        id: 'vehicleId',
        header: 'Vehicle ID',
        size: 150,
      },
      {
        accessorFn: (row: any) => {
          if (!row.start) return 'N/A';
          const date = new Date(row.start);
          return isNaN(date.getTime()) ? 'N/A' : date.toLocaleString('en-US', timeFormatOptions);
        },
        id: 'start',
        header: 'Start Time (UTC)',
        size: 180,
      },
      {
        accessorFn: (row: any) => {
          if (!row.end) return 'Active / In Progress';
          const date = new Date(row.end);
          return isNaN(date.getTime()) ? 'Active / In Progress' : date.toLocaleString('en-US', timeFormatOptions);
        },
        id: 'end',
        header: 'End Time (UTC)',
        size: 180,
      },
      {
        accessorFn: (row: any) => `${row.pointCount ?? 0} pts`,
        id: 'pointCount',
        header: 'Data Points',
        size: 120,
      },
      {
        id: 'actions',
        header: 'Actions',
        size: 120,
        Cell: ({ row }: { row: any }) => (
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setPlaybackSession(row.original.start);
              props.toggleSimTable();
            }}
          >
            Jump to
          </Button>
        ),
      },
    ],
    [props, setPlaybackSession]
  );

  return (
    <div
      className="simulation-table-container"
      style={{
        position: 'relative',
        top: 20,
        zIndex: 1000, // Ensure it's above the Map layers
        background: 'white',
        padding: '10px 14px',
        borderRadius: '8px',
        width: '94%',
        maxWidth: 'fit-content',
        margin: '10px auto',
        boxShadow: '0 4px 15px rgba(0,0,0,0.2)'
      }}
    >
      <div className="d-flex justify-content-between align-items-center mb-2 px-1">
        <h6 className="mb-0 fw-bold d-flex align-items-center text-dark">
          Simulation Sessions
          <HelpIconButton topicId="sim-table" title="Simulation Table Help" className="ms-2" />
        </h6>
      </div>
      <MaterialReactTable
        columns={columns}
        data={data}
        manualPagination // Tells MRT NOT to do client-side paging
        rowCount={rowCount}
        onPaginationChange={setPagination}
        state={{ pagination }}
        layoutMode="grid"
        enableStickyHeader
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
      <div className="d-flex justify-content-end gap-2 mt-3">
      {playbackOffset !== 0 && (
        <Button
          variant="success"
          onClick={() => {
            props.returnToNow();
            props.toggleSimTable();
          }}
        >
          Return to Now
        </Button>
      )}
      <Button
        variant="warning"
        onClick={() => {
          props.toggleSimTable();
        }}
      >
        Close
      </Button>
      </div>
    </div>
  );
};