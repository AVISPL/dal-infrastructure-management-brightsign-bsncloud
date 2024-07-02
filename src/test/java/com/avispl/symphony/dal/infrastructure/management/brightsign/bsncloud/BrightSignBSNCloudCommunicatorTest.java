/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;

/**
 * BrightSignBSNCloudCommunicatorTest
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 7/1/2024
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

	@Test
	void testGetAggregatorData() throws Exception {
		extendedStatistic = (ExtendedStatistics) brightSignBSNCloudCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllableProperties = extendedStatistic.getControllableProperties();
		Assert.assertEquals(11, statistics.size());
	}

	/**
	 * Test case for getting the number of devices from Dante Director.
	 * Verifies the number of devices retrieved.
	 */
	@Test
	void testGetNumberOfDevices() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		List<AggregatedDevice> aggregatedDeviceList = brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(1, aggregatedDeviceList.size());
	}
}
