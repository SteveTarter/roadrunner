package com.tarterware.roadrunner.configs;

import java.net.URI;
import java.net.URISyntaxException;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

public class RedisIAMTokenGenerator
{

    private static final String SERVICE_NAME = "memorydb"; // Service name for MemoryDB
    private static final String REGION = System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable());

    public String generateIAMAuthToken(String host, int port) throws URISyntaxException
    {
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

        // Build the Redis URI
        URI redisUri = new URI(String.format("redis://%s:%d", host, port));

        // Create the unsigned HTTP request (dummy GET request)
        SdkHttpFullRequest unsignedRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).uri(redisUri)
                .putHeader("host", redisUri.getHost()).build();

        // Create the Aws4Signer
        Aws4Signer signer = Aws4Signer.create();

        // Set up the signing parameters
        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentialsProvider.resolveCredentials()).signingName(SERVICE_NAME) // MemoryDB uses
                                                                                                    // "memorydb"
                .signingRegion(Region.of(REGION)) // Use the AWS region
                .build();

        // Sign the request
        SdkHttpFullRequest signedRequest = signer.sign(unsignedRequest, signerParams);

        // The IAM token is in the "Authorization" header
        return signedRequest.firstMatchingHeader("Authorization")
                .orElseThrow(() -> new RuntimeException("Unable to generate IAM token for Redis"));
    }
}
