/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric;

import java.util.Arrays;

/**
 * StatusEnum
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 7/3/2024
 * @since 1.0.0
 */
public enum StatusEnum {
	MAC("Healthy", "Normal"),
	NAME("Idle", "Warning"),
	TYPE("Inactive", "Error"),
	;
	private final String name;
	private final String value;

	/**
	 * Constructor for StatusEnum.
	 *
	 * @param name The name representing the system information category.
	 * @param value The corresponding value associated with the category.
	 */
	StatusEnum(String name, String value) {
		this.name = name;
		this.value = value;
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
	 * Retrieves {@link #value}
	 *
	 * @return value of {@link #value}
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Retrieves the name associated with a given value from the {@code StatusEnum} enum.
	 * If the value does not match any enum constant, returns the input value.
	 *
	 * @param value the value to match against the enum constants.
	 * @return the name associated with the matching enum constant, or the input value if no match is found.
	 */
	public static String getNameByValue(String value) {
		return Arrays.stream(values()).filter(item -> item.getValue().equalsIgnoreCase(value)).
				map(item -> item.name).findFirst().orElse(value);
	}
}
