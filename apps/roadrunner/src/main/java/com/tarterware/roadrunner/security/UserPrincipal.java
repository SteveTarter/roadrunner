package com.tarterware.roadrunner.security;

import java.util.List;

/**
 * Application-level representation of an authenticated Roadrunner user.
 *
 * <p>
 * This record contains the user identity and authorization information
 * extracted from the authenticated security token. It is intended to give
 * application services a small, stable object to work with instead of depending
 * directly on token-specific types such as a JWT.
 * </p>
 *
 * @param sub    stable subject identifier for the authenticated user, typically
 *               taken from the token's {@code sub} claim
 * @param email  email address associated with the authenticated user, when
 *               available
 * @param groups authorization groups associated with the user, such as Cognito
 *               groups from the {@code cognito:groups} claim
 */
public record UserPrincipal(
        String sub,
        String email,
        List<String> groups
)
{
    /**
     * Indicates whether the user belongs to the Roadrunner superuser group.
     *
     * <p>
     * Superusers are treated as privileged demo users and may be exempt from normal
     * usage limits, such as the daily vehicle-start limit, or running maintenance
     * commands.
     * </p>
     *
     * @return {@code true} if the user belongs to the {@code superuser} group;
     *         otherwise {@code false}
     */
    public boolean isSuperuser()
    {
        return groups != null && groups.contains("superuser");
    }

    /**
     * Indicates whether the user belongs to the Roadrunner creator group.
     *
     * <p>
     * Creators are treated as privileged demo users that are allowed to create
     * vehicles. Note that unless a user also has superuser group membership, daily
     * vehicle-start limits will be enforced.
     * </p>
     *
     * @return {@code true} if the user belongs to the {@code creator} group;
     *         otherwise {@code false}
     */
    public boolean isCreator()
    {
        return groups != null && groups.contains("creator");
    }

}