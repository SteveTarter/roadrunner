import { fetchAuthSession } from "aws-amplify/auth";
import { useMemo, useState, useEffect } from 'react';
import { MaterialReactTable } from 'material-react-table';
import { CONFIG } from "../../config";

export const SimulationTable = () => {
  const [token, setToken] = useState("");
  const [data, setData] = useState([]);
  const [rowCount, setRowCount] = useState(0);
  const [pagination, setPagination] = useState({ pageIndex: 0, pageSize: 10 });

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

    const url=`${CONFIG.ROADRUNNER_REST_URL_BASE}/api/vehicle/simulation-sessions?page=${pagination.pageIndex}&size=${pagination.pageSize}`
    fetch(url,{
        method: 'get',
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        }
      })
      .then(res => res.json())
      .then(pagedModel => {
        // Extract data from the HAL structure we fixed in the tests!
        setData(pagedModel._embedded?.simulationSessions ?? []);
        setRowCount(pagedModel.page.totalElements);
      });
  }, [pagination.pageIndex, pagination.pageSize, token]);

  const columns = useMemo(() => [
    { accessorKey: 'id', header: 'Session ID' },
    { accessorKey: 'start', header: 'Start Time' },
    { accessorKey: 'end', header: 'End Time' },
  ], []);

  return (
    <MaterialReactTable
      columns={columns}
      data={data}
      manualPagination // Tells MRT NOT to do client-side paging
      onPaginationChange={setPagination}
      rowCount={rowCount}
      state={{ pagination }}
      enableRowVirtualization // Enables the high-performance scroll
      muiTableContainerProps={{ sx: { maxHeight: '500px' } }} // Sets the scrollable area
    />
  );
};