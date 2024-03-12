package com.tarterware.roadrunner.services;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.mapbox.Feature;
import com.tarterware.roadrunner.models.mapbox.FeatureCollection;
import com.tarterware.roadrunner.utilities.StringUtilities;

@Service
public class GeocodingService
{
	@Value("${mapbox.api.url}")
	private String _restUrlBase;
	
	@Value("${mapbox.api.key}")
	private String _mapBoxApiKey;
	
	@Value("${com.tarterware.data-dir}")
    private String _tarterwareDataDir;
    
	@Autowired
	RestTemplate restTemplate;
	
	private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);

	public void setPositionFromAddress(Address address)
	{
	    StringBuilder sb = new StringBuilder();
	    
        // For the address elements, add them to the URL if each element is non-null and not empty.
        if(!StringUtilities.isNullEmptyOrBlank(address.getAddress1()))
        {
            sb.append(address.getAddress1().trim());
            sb.append(",");
        }
        if(!StringUtilities.isNullEmptyOrBlank(address.getAddress2()))
        {
            sb.append(address.getAddress2().trim());
            sb.append(",");
        }
        if(!StringUtilities.isNullEmptyOrBlank(address.getCity()))
        {
            sb.append(address.getCity().trim());
            sb.append(",");
        }
        if(!StringUtilities.isNullEmptyOrBlank(address.getState()))
        {
            sb.append(address.getState().trim());
            sb.append(",");
        }
        if(!StringUtilities.isNullEmptyOrBlank(address.getZipCode()))
        {
            sb.append(address.getZipCode().trim());
            sb.append(",");
        }
        
        // If there weren't any address elements, don't bother.  There's nothing to look up.
        if(sb.length() == 0)
        {
            return;
        }
        
        // Remove the last comma that was added as a part of the address components
        sb.deleteCharAt(sb.length() - 1);
        
        // Create the parameter string for the Geocoding call, and also
        // replace problematic characters before calling.
        String parameterString = sb.toString();
        parameterString = parameterString.replace("#", "%23");
        parameterString = parameterString.replace("$", "%24");
        parameterString = parameterString.replace("&", "%26");
        parameterString = parameterString.replace("+", "%2B");
        parameterString = parameterString.replace("/", "%2F");
        parameterString = parameterString.replace("@", "%40");

		sb = new StringBuilder(_restUrlBase);
		
		// Add path separator if it is needed.
		if( !_restUrlBase.endsWith("/"))
		{
			sb.append("/");
		}
		
		// Add start of GeoCoding endpoint
		sb.append("geocoding/v5/mapbox.places/");
		sb.append(parameterString);
		
		// Tell the API that we want to match address features
		sb.append(".json?proximity=ip&types=address,postcode");
		
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
		
		// If file exists, read it from there instead of hitting MapBox REST API.
		FeatureCollection featureCollection = null;
        ObjectMapper objectMapper = new ObjectMapper();
		File cacheFile = new File(cacheFileName);
		if(cacheFile.exists())
		{
		    logger.info("FeatureCollection via cache: " + cacheFileName);
		    
		    try
		    {
		        featureCollection = objectMapper.readValue(cacheFile, FeatureCollection.class);
		    }
		    catch(IOException ex)
		    {
		        logger.error("Unable to read FeatureCollection from " + cacheFileName, ex);
		    }
		}
		else
		{
		    logger.info("FeatureCollection via REST: " + sb.toString());
		
		    ResponseEntity<FeatureCollection> respFc =
		            restTemplate.getForEntity(sb.toString(), FeatureCollection.class);
		    
		    featureCollection = respFc.getBody();
		    
		    // Persist the newly read Object to the cache
		    try
		    {
		        objectMapper.writeValue(cacheFile, featureCollection);
		    }
		    catch(IOException ex)
            {
                logger.error("Unable to write FeatureCollection to " + cacheFileName, ex);
            }
		}
		
		if(featureCollection != null)
		{
			Feature feature = featureCollection.getFeatures().get(0);
			address.setLongitude(feature.getCenter().get(0));
			address.setLatitude(feature.getCenter().get(1));
			address.setSource("GeocodingService");
		}
		else
		{
			logger.warn("Unable to get coordinate data for: " + address.toString());
		}
	}
}
