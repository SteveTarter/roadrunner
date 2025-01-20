package com.tarterware.roadrunner.configs;

import java.net.URI;

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

    public String generateIAMAuthToken(String host, int port)
    {
        try
        {
            AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
            Aws4Signer signer = Aws4Signer.create();

            // Construct a URI with http:// to satisfy the AWS SDK
            URI signingUri = new URI(String.format("http://%s:%d", host, port));

            // Create an unsigned HTTP GET request
            SdkHttpFullRequest unsignedRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).uri(signingUri)
                    .putHeader("host", signingUri.getHost()).build();

            // Configure the signing parameters
            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .awsCredentials(credentialsProvider.resolveCredentials()).signingName(SERVICE_NAME)
                    .signingRegion(Region.of(REGION)).build();

            // Sign the request and extract the IAM token
            SdkHttpFullRequest signedRequest = signer.sign(unsignedRequest, signerParams);
            return signedRequest.firstMatchingHeader("Authorization")
                    .orElseThrow(() -> new RuntimeException("Unable to generate IAM token"));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error generating IAM token for Redis", e);
        }
    }
}
