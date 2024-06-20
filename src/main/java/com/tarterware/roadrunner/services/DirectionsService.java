package com.tarterware.roadrunner.services;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;

@Service
public class DirectionsService
{
    @Value("${mapbox.api.url}")
    private String _restUrlBase;
    
    @Value("${mapbox.api.key}")
    private String _mapBoxApiKey;
    
    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
    GeocodingService geocodingService;
    
    @Value("${com.tarterware.data-dir}")
    private String _tarterwareDataDir;
    
    private static final Logger logger = LoggerFactory.getLogger(DirectionsService.class);

    public Directions getDirections(List<Address> listAddresses)
    {                
        StringBuilder sb = new StringBuilder(_restUrlBase);
        
        // Add path separator if it is needed.
        if( !_restUrlBase.endsWith("/"))
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
        
        String cacheFileName = 
                _tarterwareDataDir +
                File.separatorChar +
                sb.toString()
                .substring(_restUrlBase.length())
                .replace("/","$")
                .replace("&", "\\&")
                .replace("$", "\\$")
                .replace(";", "\\;");
        
        // Add the MapBox API key, or the endpoint will tell us to frig off.
        sb.append("&access_token=");
        sb.append(_mapBoxApiKey);
        
        Directions directions = null;
        ObjectMapper objectMapper = new ObjectMapper();
        File cacheFile = new File(cacheFileName);
        if(cacheFile.exists())
        {
            logger.info("Direction via cache: " + cacheFileName);
            
            try
            {
                directions = objectMapper.readValue(cacheFile, Directions.class);
            }
            catch(IOException ex)
            {
                logger.error("Unable to read Directions from " + cacheFileName, ex);
            }
        }
        else
        {
            logger.info("Direction via REST: " + sb.toString());
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
            try
            {
                objectMapper.writeValue(cacheFile, directions);
            }
            catch(IOException ex)
            {
                logger.error("Unable to write Directions to " + cacheFileName, ex);
            }
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
