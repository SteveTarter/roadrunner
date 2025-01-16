package com.tarterware.roadrunner.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;

@Service
public class DirectionsService
{
    @Value("${mapbox.api.url}")
    private String _mapBoxApiUrl;
    
    @Value("${mapbox.api.key}")
    private String _mapBoxApiKey;
    
    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    GeocodingService geocodingService;
    
    private static final Logger logger = LoggerFactory.getLogger(DirectionsService.class);

    public Directions getDirections(List<Address> listAddresses)
    {                
        Directions directions = null;

        StringBuilder sb = new StringBuilder(_mapBoxApiUrl);
        
        // Add path separator if it is needed.
        if( !_mapBoxApiUrl.endsWith("/"))
        {
            sb.append("/");
        }
        
        // Add start of GeoCoding endpoint
        sb.append("directions/v5/mapbox/driving/");
        
        for(Address address : listAddresses)
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
        
        // Create the key name, removing the URL portion in front.
        String directionsCacheKey = 
                sb.toString()
                .substring(_mapBoxApiUrl.length());

        Directions redisDirections = (Directions) redisTemplate.opsForValue().get(directionsCacheKey);
        
        if(redisDirections != null)
        {
            logger.info("Direction via redis cache: " + directionsCacheKey);
            
            directions = redisDirections;
        }
        else
        {
            // Log the URL used to get the Direction record, without token
            logger.info("Direction via REST: " + sb.toString());

            // Now, add the MapBox API key, or the endpoint will tell us to frig off.
            sb.append("&access_token=");
            sb.append(_mapBoxApiKey);

            try
            {
                ResponseEntity<Directions> respDirections =
                        restTemplate.getForEntity(sb.toString(), Directions.class);
                
                directions = respDirections.getBody();
            }
            catch (Exception e)
            {
                logger.error("Unable to create Directions", e);
            }
            
            // Persist the newly read Object to the cache
            redisTemplate.opsForValue().set(directionsCacheKey, directions);
        }
        
        return directions;
    }
    
    public Directions getDirectionsForTripPlan(TripPlan tripPlan)
    {
        // Check to see a valid TripPlan has been provided before proceeding.
        if(tripPlan == null)
        {
            throw new IllegalArgumentException("tripPlan cannot be null!");
        }
        
        if((tripPlan.getListStops() == null) || (tripPlan.getListStops().size() < 2))
        {
            throw new IllegalArgumentException("There must be at least 2 stops in tripPlan!");
        }
        
        // Get the geodetic position of the given Address.
        for(Address address : tripPlan.getListStops()) 
        {
            geocodingService.setPositionFromAddress(address);
        }
        
        // Pass the list of addresses to obtain Directions to travel between them
        Directions directions = getDirections(tripPlan.getListStops());
        
        return directions;
    }
}
