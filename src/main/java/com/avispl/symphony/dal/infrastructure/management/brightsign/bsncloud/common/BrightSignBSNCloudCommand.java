/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common;

/**
 * BrightSignBSNCloudCommand
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 6/18/2024
 * @since 1.0.0
 */
public class BrightSignBSNCloudCommand {
	public static final String REST_TOKEN = "2022/06/REST/Token";
	public static final String GET_NUMBER_OF_DEVICES = "2022/06/REST/Devices/Count";
	public static final String NETWORK_INFO = "2022/06/REST/Self/Networks";
	public static final String GET_ALL_DEVICES = "2022/06/REST/Devices";
	public static final String REBOOT_ENDPOINT = "https://ws.bsn.cloud/rest/v1/control/reboot/?destinationType=player&destinationName=%s";
}
