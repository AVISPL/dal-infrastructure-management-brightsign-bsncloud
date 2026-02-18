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
	public static final String TOKEN = "/realms/bsncloud/protocol/openid-connect/token";
	public static final String NETWORK = "2022/06/REST/Self/Session/Network";
	public static final String DEVICES_COUNT = "2022/06/REST/Devices/Count"; //scope bsn.api.main.devices.retrieve
	public static final String NETWORK_INFO = "2022/06/REST/Self/Networks"; //scope bsn.api.self.networks.retrieve
	public static final String LIST_DEVICES = "2022/06/REST/Devices"; //scope bsn.api.main.devices.retrieve
	public static final String REBOOT = "https://ws.bsn.cloud/rest/v1/control/reboot/?destinationType=player&destinationName=%s";
}
