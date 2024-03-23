package com.tarterware.roadrunner.utilities;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

public class TopologyUtilities
{
	public static double FEET_PER_METER = 3.280839895;
	public static double METERS_PER_FEET = 1.0 / FEET_PER_METER;
	public static double FEET_PER_STATUTE_MILE = 5280.0;
	public static double FEET_PER_NAUTICAL_MILE = 6076.12;

	/**
	 * Get a WGS84 coordinate system that uses geodetic coordinates (latitude, longitude, and elevation).
	 * @return A WGS84 geodetic coordinate system.
	 */
	static public CoordinateReferenceSystem getWgs84CoordinateSystem()
	{
		CRSFactory crsFactory = new CRSFactory();
		CoordinateReferenceSystem wgs1984;

		wgs1984 = crsFactory.createFromParameters(null, "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs");

		return wgs1984;
	}
	
	/**
	 * Return a UTM coordinate system appropriate for the given geodetic coordinate.
	 * @param latitude Location in degrees latitude.
	 * @param longitude Location in degrees longitude.
	 * @return A UTM coordinate system.
	 */
	static public CoordinateReferenceSystem getUtmCoordinateSystem(double latitude, double longitude)
	{
		if( Math.abs(latitude) > 90.0)
		{
			throw new IllegalArgumentException("Not a valid latitude: " + latitude);
		}
		if( Math.abs(longitude) > 180.0)
		{
			throw new IllegalArgumentException("Not a valid longitude: " + longitude);
		}
		
		boolean zoneIsSouth = latitude < 0.0;
		int zone = (int) Math.ceil((longitude + 180.0) / 6.0);
		
		StringBuilder sb = new StringBuilder();
		sb.append("+proj=utm +zone=");
		sb.append(zone);
		if(zoneIsSouth)
		{
			sb.append(" +south");
		}
		sb.append(" +datum=WGS84 +units=m +no_defs");
		
		CRSFactory crsFactory = new CRSFactory();
		CoordinateReferenceSystem crs = crsFactory.createFromParameters(null, sb.toString());
		return crs;
	}
	
	/**
	 * Return a UTM coordinate system appropriate for the given geodetic coordinate.
	 * @param coord Location.
	 * @return A UTM coordinate system.
	 */
	static public CoordinateReferenceSystem getUtmCoordinateSystem(Coordinate coord)
	{
		if(coord == null)
		{
			throw new IllegalArgumentException("coord cannot be null!");
		}
		
		return getUtmCoordinateSystem(coord.getY(), coord.getX());
	}
	
	/**
	 * Get a coordinate transformer that converts coordinates from geodetic to UTM.
	 * @param latitude Representative latitude coordinate.
	 * @param longitude Representative longitude coordinate.
	 * @return A Geodetic to UTM coordinate transform.
	 */
	static public CoordinateTransform getWgs84ToUtmCoordinateTransformer(double latitude, double longitude)
	{
		CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
		CoordinateReferenceSystem wgsCoordSys = getWgs84CoordinateSystem();
		CoordinateReferenceSystem utmCoordinateSystem = getUtmCoordinateSystem(latitude, longitude);
		
		return ctFactory.createTransform(wgsCoordSys, utmCoordinateSystem);
	}
	
	/**
	 * Get a coordinate transformer that converts coordinates from geodetic to UTM.
	 * @param coord Representative coordinate
	 * @return  A Geodetic to UTM coordinate transform.
	 */
	static public CoordinateTransform getWgs84ToUtmCoordinateTransformer(Coordinate coord)
	{
		return getWgs84ToUtmCoordinateTransformer(coord.getY(), coord.getX());
	}
	
	/**
	 * Get a coordinate transformer that converts coordinates from UTM to geodetic.
	 * @param latitude Representative latitude coordinate.
	 * @param longitude Representative longitude coordinate.
	 * @return A UTM to Geodetic coordinate transform.
	 */
	static public CoordinateTransform getUtmToWgs84CoordinateTransformer(double latitude, double longitude)
	{
		CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
		CoordinateReferenceSystem wgsCoordSys = getWgs84CoordinateSystem();
		CoordinateReferenceSystem utmCoordinateSystem = getUtmCoordinateSystem(latitude, longitude);
		
		return ctFactory.createTransform(utmCoordinateSystem, wgsCoordSys);
	}
	
	/**
	 * Get a coordinate transformer that converts coordinates from UTM to geodetic
	 * @param coord Representative coordinate.
	 * @return A UTM to Geodetic coordinate transform.
	 */
	static public CoordinateTransform getUtmToWgs84CoordinateTransformer(Coordinate coord)
	{
		return getUtmToWgs84CoordinateTransformer(coord.getY(), coord.getX());
	}
	
	/**
	 * Convert speed in miles per hour to meters per second.
	 * @param mph Speed in miles per hour
	 * @return Speed in meters per second.
	 */
	static public double convertMilesPerHourToMetersPerSecond(double mph)
	{
		return mph * FEET_PER_STATUTE_MILE * METERS_PER_FEET / 60.0 / 60.0;
	}
		
	/**
	 * Convert speed in knots to speed in meters per second.
	 * @param knots Speed in knots.
	 * @return Speed in meters per second.
	 */
	static public double convertKnotsToMetersPerSecond(double knots)
	{
		return knots * FEET_PER_NAUTICAL_MILE / METERS_PER_FEET / 60.0 / 60.0;
	}
	
	/**
	 * Convert speed in meters per second to knots.
	 * @param mps Speed in meters per second.
	 * @return Speed in knots.
	 */
	static public double convertMetersPerSecondToKnots(double mps)
	{
		return mps * FEET_PER_METER / FEET_PER_NAUTICAL_MILE * 60.0 * 60.0;
	}
	
	/**
	 * Convert distance in meters to statute miles.
	 * @param meters Distance in meters.
	 * @return Distance in statute miles.
	 */
	static public double convertMetersToStatuteMiles(double meters)
	{
		return meters * FEET_PER_METER / FEET_PER_STATUTE_MILE;
	}
	
	/**
	 * Convert distance in statute miles to meters.
	 * @param nauticalMiles Distance in statute miles.
	 * @return Distance in meters.
	 */
	static public double convertStatuteMilesToMeters(double statuteMiles)
	{
		return statuteMiles * FEET_PER_STATUTE_MILE * METERS_PER_FEET;
	}
	
	static double _defaultBufferDistance = 10.0;
	
	/**
	 * Get the default distance in meters to use as a buffer around another geometry.
	 * @return Buffer distance in meters.
	 */
	static public double getDefaultBufferDistance()
	{
		return _defaultBufferDistance;
	}
	
	/**
	 * Create a Coordinate that has the same properties as the given ProjCoordinate.
	 * @param p ProjCoordinate to base new Coordinate on. 
	 * @return Coordinate with same location values as given ProjCoordinate.
	 */
	static public Coordinate projCoordToCoord(ProjCoordinate p)
	{
		return new Coordinate(p.x, p.y, p.z);
	}
	
	/**
	 * Create a ProjCoordinate that has the same properties as the given Coordinate.
	 * @param c Coordinate to base new ProjCoordinate on.
	 * @return ProjCoordinate with same location as given Coordinate.
	 */
	static public ProjCoordinate coordToProjCoord(Coordinate c)
	{
		return new ProjCoordinate(c.x, c.y, c.z);
	}
}
