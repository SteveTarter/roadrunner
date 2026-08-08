export interface HelpTopic {
  id: string;
  title: string;
  markdownUrl: string;
  summary?: string;
}

export const HELP_TOPICS: Record<string, HelpTopic> = {
  'overview': {
    id: 'overview',
    title: 'RoadRunner Help & Overview',
    markdownUrl: '/guide/UserGuide.md',
    summary: 'Overview of RoadRunner features and navigation.'
  },
  'sim-table': {
    id: 'sim-table',
    title: 'Simulation Table Guide',
    markdownUrl: '/guide/SimTable.md',
    summary: 'Search, filter, and inspect simulated vehicle sessions.'
  },
  'active-vehicle-plot': {
    id: 'active-vehicle-plot',
    title: 'Active Vehicle Plot Guide',
    markdownUrl: '/guide/ActiveVehiclePlot.md',
    summary: 'Analyze vehicle concurrency over time and scrub playback.'
  },
  'bookmarks': {
    id: 'bookmarks',
    title: 'Bookmarks Panel Guide',
    markdownUrl: '/guide/BookmarksPanel.md',
    summary: 'Quickly access curated simulation scenarios.'
  },
  'create-vehicle': {
    id: 'create-vehicle',
    title: 'Create Vehicle Panel Guide',
    markdownUrl: '/guide/CreateVehiclePanel.md',
    summary: 'Configure and spawn individual simulated vehicles.'
  },
  'create-criss-cross': {
    id: 'create-criss-cross',
    title: 'Criss-Cross Pattern Panel Guide',
    markdownUrl: '/guide/CreateCrissCrossPanel.md',
    summary: 'Generate complex intersection and criss-cross vehicle patterns.'
  }
};
