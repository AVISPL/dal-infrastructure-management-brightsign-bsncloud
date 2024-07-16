/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common;

/**
 * SystemInformation class represents information about the aggregator.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 6/18/2024
 * @since 1.0.0
 */
public enum AggregatorInformation {
	NAME("name", ""),
	CREATE_DATE("creationDate", ""),
	LAST_MODIFIED_DATE("lastModifiedDate", ""),
	LOCKOUT_DATE("lockoutDate", ""),
	IS_LOCKED_OUT("isLockedOut", ""),
	LAST_LOCKOUT_DATE("lastLockoutDate", ""),
	LEVEL("level", "subscription"),
	SUB_CREATE_DATE("creationDate", "subscription"),
	SUB_LAST_MODIFIED_DATE("lastModifiedDate", "subscription"),
	EXPIRE_DATE("expireDate", "subscription"),
			;
	private final String name;
	private final String group;

	/**
	 * Constructor for SystemNetworkInformation.
	 *
	 * @param name The name representing the system information category.
	 * @param group The corresponding value associated with the category.
	 */
	AggregatorInformation(String name, String group) {
		this.name = name;
		this.group = group;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Retrieves {@link #group}
	 *
	 * @return value of {@link #group}
	 */
	public String getGroup() {
		return group;
	}
}
