package com.tarterware.roadrunner.components;

import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.proj4j.CoordinateTransform;

import lombok.Data;

/**
 * Represents data associated with a line segment in a route, including its UTM
 * transformations and offset information. This class encapsulates information
 * necessary to manage and transform coordinates for a specific segment of a
 * route.
 * 
 * <p>
 * Each instance of this class corresponds to a line segment within a UTM zone.
 * It includes:
 * <ul>
 * <li>The cumulative offset in meters from the start of the route.</li>
 * <li>A {@link LengthIndexedLine} representing the geometry of the
 * segment.</li>
 * <li>Coordinate transformation utilities for converting between geodetic
 * (WGS84) and UTM coordinates.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * This data structure is used to manage the routing logic and spatial
 * transformations required to track a vehicle's position along a route.
 * </p>
 * 
 * @see LengthIndexedLine
 * @see CoordinateTransform
 */
@Data
public class LineSegmentData
{
    /**
     * The cumulative offset, in meters, from the start of the route to the start of
     * this line segment.
     */
    private double metersOffset;

    /**
     * The line geometry of this segment, indexed by length for efficient spatial
     * queries.
     */
    private LengthIndexedLine lengthIndexedLine;

    /**
     * The coordinate transformer for converting from geodetic (WGS84) coordinates
     * to UTM coordinates.
     */
    private CoordinateTransform wgs84ToUtmCoordinatetransformer;

    /**
     * The coordinate transformer for converting from UTM coordinates to geodetic
     * (WGS84) coordinates.
     */
    private CoordinateTransform utmToWgs84Coordinatetransformer;
}
