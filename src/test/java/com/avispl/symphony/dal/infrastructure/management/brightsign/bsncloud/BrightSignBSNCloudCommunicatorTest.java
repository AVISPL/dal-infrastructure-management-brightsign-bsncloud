/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;

/**
 * NaViSetAdministrator2SECommunicatorTest
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 9/8/2023
 * @since 1.0.0
 */
public class BrightSignBSNCloudCommunicatorTest {
	private ExtendedStatistics extendedStatistic;
	private BrightSignBSNCloudCommunicator brightSignBSNCloudCommunicator;

	@BeforeEach
	void setUp() throws Exception {
		brightSignBSNCloudCommunicator = new BrightSignBSNCloudCommunicator();
		brightSignBSNCloudCommunicator.setHost("");
		brightSignBSNCloudCommunicator.setLogin("");
		brightSignBSNCloudCommunicator.setPassword("");
		brightSignBSNCloudCommunicator.setPort(443);
		brightSignBSNCloudCommunicator.init();
		brightSignBSNCloudCommunicator.connect();
	}

	@AfterEach
	void destroy() throws Exception {
		brightSignBSNCloudCommunicator.disconnect();
		brightSignBSNCloudCommunicator.destroy();
	}
}
