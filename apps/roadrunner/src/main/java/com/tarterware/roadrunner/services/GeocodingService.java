package com.tarterware.roadrunner.services;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.mapbox.Feature;
import com.tarterware.roadrunner.models.mapbox.FeatureCollection;
import com.tarterware.roadrunner.utilities.StringUtilities;

@Service
public class GeocodingService
{
    @Value("${mapbox.api.url}")
    private String _mapBoxApiUrl;

    @Value("${mapbox.api.key}")
    private String _mapBoxApiKey;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);

    public void setPositionFromAddress(Address address)
    {
        StringBuilder sb = new StringBuilder();

        // For the address elements, add them to the URL if each element is non-null and
        // not empty.
        if (!StringUtilities.isNullEmptyOrBlank(address.getAddress1()))
        {
            sb.append(address.getAddress1().trim());
            sb.append(",");
        }
        if (!StringUtilities.isNullEmptyOrBlank(address.getAddress2()))
        {
            sb.append(address.getAddress2().trim());
            sb.append(",");
        }
        if (!StringUtilities.isNullEmptyOrBlank(address.getCity()))
        {
            sb.append(address.getCity().trim());
            sb.append(",");
        }
        if (!StringUtilities.isNullEmptyOrBlank(address.getState()))
        {
            sb.append(address.getState().trim());
            sb.append(",");
        }
        if (!StringUtilities.isNullEmptyOrBlank(address.getZipCode()))
        {
            sb.append(address.getZipCode().trim());
            sb.append(",");
        }

        // If there weren't any address elements, don't bother. There's nothing to look
        // up.
        if (sb.length() == 0)
        {
            throw new IllegalArgumentException("No Address parameters set!  Unable to locate \"anywhere\"!");
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

        sb = new StringBuilder(_mapBoxApiUrl);

        // Add path separator if it is needed.
        if (!_mapBoxApiUrl.endsWith("/"))
        {
            sb.append("/");
        }

        // Add start of GeoCoding endpoint
        sb.append("geocoding/v5/mapbox.places/");
        sb.append(parameterString);

        // Tell the API that we want to match address features
        sb.append(".json?proximity=ip&types=address,postcode");

        // Create the key name, removing the URL portion in front.
        String geocodingCacheKey = sb.toString().substring(_mapBoxApiUrl.length());

        FeatureCollection featureCollection = null;
        FeatureCollection redisFeatureCollection = (FeatureCollection) redisTemplate.opsForValue()
                .get(geocodingCacheKey);

        if (redisFeatureCollection != null)
        {
            // FeatureCollection found in cache. Report it and set return featureCollection
            // to the cached value
            logger.info("FeatureCollection via cache: " + geocodingCacheKey);

            featureCollection = redisFeatureCollection;
        }
        else
        {
            // Nothing found in cache
            // Log the URL used to get the FeatureCollection record, without token
            logger.info("FeatureCollection via REST: " + sb.toString());

            // Add the MapBox API key, or the endpoint will tell us to frig off.
            sb.append("&access_token=");
            sb.append(_mapBoxApiKey);

            ResponseEntity<FeatureCollection> respFc = restTemplate.getForEntity(sb.toString(),
                    FeatureCollection.class);

            featureCollection = respFc.getBody();

            // Persist the newly read Object to the cache
            redisTemplate.opsForValue().set(geocodingCacheKey, featureCollection, 100, TimeUnit.HOURS);
        }

        if (featureCollection != null)
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
