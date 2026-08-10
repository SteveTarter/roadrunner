import { useMemo, useState } from 'react';
import { MaterialReactTable } from 'material-react-table';
import { usePlayback } from "../../context/PlaybackContext";
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
  const { playbackOffset, setPlaybackSession } = usePlayback();
  const { simulationSessionMap, activeCountData } = useSimulationSessionData();

  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10
  });

  // Extract all sessions directly from simulationSessionMap (same data source as ActiveVehiclePlot)
  const allSessions = useMemo(() => {
    if (!simulationSessionMap || simulationSessionMap.size() === 0) return [];
    return Array.from(simulationSessionMap.values()).sort((a: any, b: any) => {
      const startA = new Date(a.start).getTime() || 0;
      const startB = new Date(b.start).getTime() || 0;
      return startB - startA; // Sort newest sessions first
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simulationSessionMap, activeCountData]);

  const rowCount = allSessions.length;

  // Paginate sessions locally for MaterialReactTable
  const paginatedData = useMemo(() => {
    const startIdx = pagination.pageIndex * pagination.pageSize;
    const endIdx = startIdx + pagination.pageSize;
    return allSessions.slice(startIdx, endIdx);
  }, [allSessions, pagination.pageIndex, pagination.pageSize]);

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
        data={paginatedData}
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