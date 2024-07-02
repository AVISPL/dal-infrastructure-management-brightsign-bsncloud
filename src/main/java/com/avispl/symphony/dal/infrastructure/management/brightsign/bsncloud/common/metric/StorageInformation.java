/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric;

/**
 * StorageInformation class represents information about a storage.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 6/20/2024
 * @since 1.0.0
 */
public enum StorageInformation {
	INTERFACE( "interface"),
	SYSTEM( "system"),
	ACCESS( "access"),
	SIZE_TOTAL( "sizeTotal"),
	SIZE_FREE( "sizeFree"),
	;
	private final String name;


	/**
	 * Constructor for StorageInformation.
	 *
	 * @param name The name representing the system information category.
	 */
	StorageInformation(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}
}
