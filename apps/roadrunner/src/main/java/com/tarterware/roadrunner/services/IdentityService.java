package com.tarterware.roadrunner.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

@Service
public class IdentityService
{
    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    @Value("${com.tarterware.roadrunner.aws.cognito.user-pool-id}")
    private String userPoolId;

    public String getEmailBySub(String sub)
    {
        // The .create() method uses the default AWS credential provider chain
        try (CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create())
        {

            // sub is a standard attribute we can filter on
            String filter = String.format("sub = \"%s\"", sub);

            ListUsersRequest request = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .filter(filter)
                    .limit(1)
                    .build();

            ListUsersResponse response = cognitoClient.listUsers(request);

            if (response.hasUsers() && !response.users().isEmpty())
            {
                UserType user = response.users().get(0);

                // Find the email attribute in the list of user attributes
                return user.attributes().stream()
                        .filter(attr -> attr.name().equals("email"))
                        .findFirst()
                        .map(attr -> attr.value())
                        .orElse("unknown-email");
            }
        }
        catch (Exception e)
        {
            log.error("Failed to fetch email from Cognito for sub: {}", sub, e);
        }
        return "unknown-user";
    }
}