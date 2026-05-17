package com.tarterware.roadrunner.utilities;

import java.util.UUID;

public class StringUtilities
{
    /**
     * Test if string is null, empty, or blank.
     * 
     * @param str String to be evaluated
     * @return true if the string is null, empty, or blank.
     */
    public static boolean isNullEmptyOrBlank(String str)
    {
        if (str == null)
        {
            return true;
        }
        if (str.trim().isEmpty())
        {
            return true;
        }

        return false;
    }

    /**
     * Return the shortened String form of a UUID, the first 8 characters.
     * 
     * @param uuid UUID to shorten
     * @return
     */
    public static String shortenedUUID(UUID uuid)
    {
        if (uuid == null)
        {
            throw new IllegalArgumentException("uuid cannot be null");
        }

        return uuid.toString().substring(0, 8);
    }
}
