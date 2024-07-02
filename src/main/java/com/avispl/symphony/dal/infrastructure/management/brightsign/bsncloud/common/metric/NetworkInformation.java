/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric;

import java.util.Arrays;
import java.util.Optional;

import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.BrightSignBSNCloudConstant;

/**
 * NetworkInformation class represents information about a network.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 6/19/2024
 * @since 1.0.0
 */
public enum NetworkInformation {
	IP("IP", "ip"),
	ENABLED("Enabled", "enabled"),
	DNS("DNS", "dns"),
	GATEWAY("Gateway", "gateway"),
	PROTOCOL("Protocol", "proto"),
	CONTENT_DOWNLOAD("ContentDownload", "contentDownloadEnabled"),
	TEXT_FEED("TextFeedsDownload", "textFeedsDownloadEnabled"),
	MEDIA_FEED("MediaFeedsDownload", "mediaFeedsDownloadEnabled"),
	HEALTH("HealthReporting", "healthReportingEnabled"),
	LOGS("LogsUpload", "logsUploadEnabled"),
	MAC("MAC", "mac"),
	NAME("Name", "name"),
	TYPE("Type", "type"),
	;
	private final String name;
	private final String value;

	/**
	 * Constructor for AggregatedInformation.
	 *
	 * @param name The name representing the system information category.
	 * @param value The corresponding value associated with the category.
	 */
	NetworkInformation(String name, String value) {
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
	 * Retrieves the name associated with a given value from the {@code NetworkInformation} enum.
	 * If the value does not match any enum constant, returns a default constant {@code BrightSignBSNCloudConstant.NONE}.
	 *
	 * @param value the value to match against the enum constants.
	 * @return the name associated with the matching enum constant, or {@code BrightSignBSNCloudConstant.NONE} if no match is found.
	 */
	public static String getByDefaultName(String value) {
		Optional<NetworkInformation> property = Arrays.stream(values()).filter(item -> item.getValue().equalsIgnoreCase(value)).findFirst();
		return property.isPresent() ? property.get().name : BrightSignBSNCloudConstant.NONE;
	}
}
