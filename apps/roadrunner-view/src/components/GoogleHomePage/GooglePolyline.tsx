import { useEffect } from 'react';
import { useMap } from '@vis.gl/react-google-maps';

export const GooglePolyline = (props: {
  path: google.maps.LatLngLiteral[];
  strokeColor?: string;
  strokeWeight?: number;
  strokeOpacity?: number;
}) => {
  const map = useMap();

  useEffect(() => {
    if (!map || !props.path || props.path.length === 0) return;

    const polyline = new google.maps.Polyline({
      map,
      path: props.path,
      strokeColor: props.strokeColor || '#FF0000',
      strokeWeight: props.strokeWeight || 3,
      strokeOpacity: props.strokeOpacity || 0.8,
    });

    return () => {
      polyline.setMap(null);
    };
  }, [map, props.path, props.strokeColor, props.strokeWeight, props.strokeOpacity]);

  return null;
};
