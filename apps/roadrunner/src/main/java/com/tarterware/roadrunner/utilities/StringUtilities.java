package com.tarterware.roadrunner.utilities;

public class StringUtilities {
	/**
	 * Test if string is null, empty, or blank.
	 * @param str String to be evaluated
	 * @return true if the string is null, empty, or blank.
	 */
	public static boolean isNullEmptyOrBlank(String str) {
		if(str == null) {
			return true;
		}
		if(str.trim().isEmpty()) {
			return true;
		}
		
		return false;
	}
}
