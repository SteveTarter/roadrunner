package com.tarterware.roadrunner.security;

import java.util.Collections;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Creates application-level user principals from authenticated JWTs.
 *
 * <p>
 * This component translates token-specific claim data into a
 * {@link UserPrincipal} that can be used by Roadrunner services. Keeping this
 * mapping in one place avoids spreading Cognito claim names and JWT parsing
 * logic throughout the application.
 * </p>
 */
@Component
public class UserPrincipalFactory
{
    /**
     * Builds a {@link UserPrincipal} from a validated Spring Security JWT.
     *
     * <p>
     * The user's stable identifier is read from the token subject. The email
     * address is read from the {@code email} claim, and group memberships are read
     * from the Cognito {@code cognito:groups} claim. If the token does not contain
     * any groups, the returned principal uses an empty group list.
     * </p>
     *
     * @param jwt validated JWT for the authenticated request
     * @return application-level principal containing the user's identity and group
     *         memberships
     */
    public UserPrincipal fromJwt(Jwt jwt)
    {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups == null)
        {
            groups = Collections.emptyList();
        }

        return new UserPrincipal(sub, email, groups);
    }
}