package com.tarterware.roadrunner.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.DirectionsCache;
import com.tarterware.roadrunner.utilities.StringUtilities;

@Service
public class DirectionsService
{
    @Value("${mapbox.api.url}")
    private String _mapBoxApiUrl;

    @Value("${mapbox.api.key}")
    private String _mapBoxApiKey;

    private final RestTemplate restTemplate;
    private final GeocodingService geocodingService;
    private final DirectionsCache directionsCache;

    private static final Logger logger = LoggerFactory.getLogger(DirectionsService.class);

    public DirectionsService(
            RestTemplate restTemplate,
            GeocodingService geocodingService,
            DirectionsCache directionsCache)
    {
        this.restTemplate = restTemplate;
        this.geocodingService = geocodingService;
        this.directionsCache = directionsCache;
    }

    public Directions getDirections(List<Address> listAddresses)
    {
        StringBuilder sb = new StringBuilder(_mapBoxApiUrl);

        if (!_mapBoxApiUrl.endsWith("/"))
        {
            sb.append("/");
        }

        String directionsCacheKey = getCacheKey(listAddresses);
        sb.append(directionsCacheKey);

        return directionsCache.get(directionsCacheKey)
                .map(cached ->
                {
                    logger.info("Direction via cache: {}", directionsCacheKey);
                    return cached;
                })
                .orElseGet(() ->
                {
                    logger.info("Direction via REST: {}", sb);

                    sb.append("&access_token=");
                    sb.append(_mapBoxApiKey);

                    Directions directions = null;
                    try
                    {
                        ResponseEntity<Directions> response = restTemplate.getForEntity(sb.toString(),
                                Directions.class);
                        directions = response.getBody();
                    }
                    catch (Exception e)
                    {
                        logger.error("Unable to create Directions", e);
                    }

                    directionsCache.put(directionsCacheKey, directions);
                    return directions;
                });
    }

    public Directions getDirectionsForTripPlan(TripPlan tripPlan)
    {
        // Check to see a valid TripPlan has been provided before proceeding.
        if (tripPlan == null)
        {
            throw new IllegalArgumentException("tripPlan cannot be null!");
        }

        if ((tripPlan.getListStops() == null) || (tripPlan.getListStops().size() < 2))
        {
            throw new IllegalArgumentException("There must be at least 2 stops in tripPlan!");
        }

        // Get the geodetic position of the given Address.
        for (Address address : tripPlan.getListStops())
        {
            // If the Address record is NumericEntry, don't bother looking it up.
            if (StringUtilities.isNullEmptyOrBlank(address.getSource()) || !address.getSource().equals("NumericEntry"))
            {
                geocodingService.setPositionFromAddress(address);
            }
        }

        // Pass the list of addresses to obtain Directions to travel between them
        Directions directions = getDirections(tripPlan.getListStops());

        return directions;
    }

    public void reset()
    {
        directionsCache.reset();
    }

    public String getCacheKey(List<Address> listAddresses)
    {
        // Begin with start of GeoCoding endpoint
        StringBuilder sb = new StringBuilder("directions/v5/mapbox/driving/");

        for (Address address : listAddresses)
        {
            sb.append(address.getLongitude());
            sb.append(",");
            sb.append(address.getLatitude());
            sb.append(";");
        }

        // Remove the last comma that was added as a part of the address components
        sb.deleteCharAt(sb.length() - 1);

        // Tell the API that we want to match address features
        sb.append("?alternatives=false");
        sb.append("&annotations=speed,distance");
        sb.append("&geometries=geojson");
        sb.append("&language=en");
        sb.append("&overview=full");
        sb.append("&steps=true");

        // This subset of the path constitutes the key name.
        String directionsCacheKey = sb.toString();

        return directionsCacheKey;
    }

    public String getCacheKey(TripPlan tripPlan)
    {
        // Check to see a valid TripPlan has been provided before proceeding.
        if (tripPlan == null)
        {
            throw new IllegalArgumentException("tripPlan cannot be null!");
        }

        if ((tripPlan.getListStops() == null) || (tripPlan.getListStops().size() < 2))
        {
            throw new IllegalArgumentException("There must be at least 2 stops in tripPlan!");
        }

        // Pass the stops list to the List of Addresses method.
        String directionsCacheKey = getCacheKey(tripPlan.getListStops());
        return directionsCacheKey;
    }
}
