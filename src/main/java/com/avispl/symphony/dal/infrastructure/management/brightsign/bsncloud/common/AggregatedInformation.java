/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common;

/**
 * Enum AggregatedInformation represents various pieces of aggregated information about a device.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 6/19/2024
 * @since 1.0.0
 */
public enum AggregatedInformation {
	PLAYER_ID("PlayerID", ""),
	DESCRIPTION("Description", ""),
	PRESENTATION("Presentation", ""),
	SETUP_TYPE("SetupType", ""),
	TIME_ZONE("Timezone", ""),
	FIRMWARE_VERSION("BrightSignOSVersion", ""),
	UPTIME("DeviceUptime", ""),
	DEVICE_STATUS("DeviceStatus", ""),
	LAST_CONNECTED("LastConnected", ""),
	IP_ADDRESS("ExternalIPAddress", ""),
	ID("GroupID", ""),
	NAME("GroupName", ""),


	NETWORK_INTERFACE("NetworkInterface", ""),
	STORAGE("Storage", ""),

	LATITUDE("Latitude", "Location#"),
	LONGITUDE("Longitude", "Location#"),
	COUNTRY("Country", "Location#"),
	LOCALITY("Locality", "Location#"),

	DIAGNOSTIC_LOG("DiagnosticLog", "Logging#"),
	EVENT_LOG("EventLog", "Logging#"),
	PLAYBACK_LOG("PlaybackLog", "Logging#"),
	STATE_LOG("StateLog", "Logging#"),
	VARIABLE_LOG("VariableLog", "Logging#"),
	UPLOAD_AT_BOOT("UploadAtBoot", "Logging#"),
	UPLOAD_TIME("UploadTime", "Logging#"),
	;
	private final String name;
	private final String group;

	/**
	 * Constructor for AggregatedInformation.
	 *
	 * @param name The name representing the system information category.
	 * @param group The group associated with the category.
	 */
	AggregatedInformation(String name, String group) {
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
