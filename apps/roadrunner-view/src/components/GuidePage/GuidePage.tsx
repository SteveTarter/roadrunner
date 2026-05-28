import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MarkdownPageLayout } from '../Shared/MarkdownPageLayout';

export function GuidePage() {
  // Get the Guide ID from the URL in the window
  const guideId = (window.location.pathname).split('/')[2];

  const [title, setTitle] = useState('');
  const [markdownUrl, setMarkdownUrl] = useState('');
  const [returnPath, setReturnPath] = useState('/home');

  const navigate = useNavigate();

  useEffect(() => {
    if (!guideId) return;

    switch (guideId) {
      case 'overview':
        setTitle('User Guide: Overview');
        setMarkdownUrl('/guide/UserGuide.md');
        setReturnPath('/home');
        break;

      case 'bookmarks':
        setTitle('User Guide: Bookmarks');
        setMarkdownUrl('/guide/BookmarksPanel.md');
        setReturnPath('/guide/overview')
        break;

      case 'sim-table':
        setTitle('User Guide: Simulation Table');
        setMarkdownUrl('/guide/SimTable.md');
        setReturnPath('/guide/overview')
        break;

      case 'active-vehicle-plot':
        setTitle('User Guide: Active Vehicle Plot');
        setMarkdownUrl('/guide/ActiveVehiclePlot.md');
        setReturnPath('/guide/overview')
        break;

      case 'create-criss-cross-panel':
        setTitle('User Guide: Create Criss-Cross Panel');
        setMarkdownUrl('/guide/CreateCrissCrossPanel.md');
        setReturnPath('/guide/overview')
        break;

      case 'create-vehicle-panel':
        setTitle('User Guide: Create Vehicle Panel');
        setMarkdownUrl('/guide/CreateVehiclePanel.md');
        setReturnPath('/guide/overview')
        break;

      default:
        setTitle('UserGuide: No Such Guide');
        setMarkdownUrl('/guide/NoSuchGuide.md');
        setReturnPath('/home');
        break;
    }
  }, [guideId]);

  const closePage = useCallback(() => {
    navigate(returnPath);
  },[navigate, returnPath]);

    return <MarkdownPageLayout
      title={title}
      markdownUrl={markdownUrl}
      onClose={closePage}
    />
}