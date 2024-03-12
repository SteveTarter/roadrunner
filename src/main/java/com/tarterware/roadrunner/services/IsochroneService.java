package com.tarterware.roadrunner.services;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.mapbox.Isochrone;

@Service
public class IsochroneService
{
	@Value("${mapbox.api.url}")
	private String _restUrlBase;
	
	@Value("${mapbox.api.key}")
	private String _mapBoxApiKey;
    
    @Value("${com.tarterware.data-dir}")
    private String _tarterwareDataDir;
	
	@Autowired
	RestTemplate restTemplate;
	
	private static final Logger logger = LoggerFactory.getLogger(IsochroneService.class);

    public Isochrone setIsochroneFromAddress(
            Address address,
            String isochroneType,
            int isochroneParameterValue)
   {
        if(address == null)
        {
            throw new IllegalArgumentException("Address location must be set!");
        }
    	if((address.getLatitude() == 0.0) && (address.getLongitude() == 0.0))
    	{
    		throw new IllegalArgumentException("Address location must be set!");
    	}
    	
    	return setIsochrone(
    	        address.getLatitude(), 
    	        address.getLongitude(), 
    	        isochroneType, 
    	        isochroneParameterValue);
    }
    
    public Isochrone setIsochrone(
            double latitude,
            double longitude,
            String isochroneType,
            @PathVariable int isochroneParameterValue)
   {
    	String isochroneParameterName = "";
        switch(isochroneType)
        {
            case "time":
            {
                isochroneParameterName = "contours_minutes";
                break;
            }
            
            case "distance":
            {
                isochroneParameterName = "contours_meters";
                break;
            }
            
            default:
            {
                logger.error("Isochrone Type \"" + isochroneType + "\" is not found!");
                throw new RuntimeException("Isochrone Type \"" + isochroneType + "\" is not found!");
            }
        }
        
        if(isochroneParameterValue <= 0)
        {
            logger.error("Isochrone Parameter must be greater than 0, not \"" + isochroneParameterValue + "\"");
            throw new RuntimeException("Isochrone Parameter must be greater than 0, not \"" + isochroneParameterValue + "\"");
        }
        
		StringBuilder sb = new StringBuilder(_restUrlBase);
		
		// Add path separator if it is needed.
		if( !_restUrlBase.endsWith("/"))
		{
			sb.append("/");
		}
		
		// Add start of GeoCoding endpoint
		sb.append("isochrone/v1/mapbox/driving/");
		
    	// Add the geographic location to center the isochrome on.
		sb.append(longitude);
		sb.append(",");
		sb.append(latitude);
		
		// Add the type and limit at which to draw the isochrome polygon.
		sb.append("?");
		sb.append(isochroneParameterName);
		sb.append("=");
		sb.append(isochroneParameterValue);
        
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
		
		Isochrone isochrone = null;
        ObjectMapper objectMapper = new ObjectMapper();
        File cacheFile = new File(cacheFileName);
        if(cacheFile.exists())
        {
            logger.info("Isochrone via cache: " + cacheFileName);
            
            try
            {
                isochrone = objectMapper.readValue(cacheFile, Isochrone.class);
            }
            catch(IOException ex)
            {
                logger.error("Unable to read FeatureCollection from " + cacheFileName, ex);
            }
        }
        else
        {
            logger.info("Isochrone via REST: " + sb.toString());

            ResponseEntity<Isochrone> respIsochrome =
                    restTemplate.getForEntity(sb.toString(), Isochrone.class);
            
            isochrone = respIsochrome.getBody();
            
            // Persist the newly read Object to the cache
            try
            {
                objectMapper.writeValue(cacheFile, isochrone);
            }
            catch(IOException ex)
            {
                logger.error("Unable to write Isochrone to " + cacheFileName, ex);
            }
        }
        
		return isochrone;
    }
}
