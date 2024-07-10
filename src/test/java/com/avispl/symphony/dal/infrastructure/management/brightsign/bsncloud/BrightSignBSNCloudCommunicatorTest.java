/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.BrightSignBSNCloudConstant;

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

	@Test
	void testGetAggregatorInformation() throws Exception {
		extendedStatistic = (ExtendedStatistics) brightSignBSNCloudCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllableProperties = extendedStatistic.getControllableProperties();
		Assert.assertEquals("AVISPL_Symphony_Dev", statistics.get("Name"));
		Assert.assertEquals("1", statistics.get("NumberOfDevices"));
	}

	@Test
	void testGetAggregatorDataWhenFiltering() throws Exception {
		brightSignBSNCloudCommunicator.setFilterByModel("");
		brightSignBSNCloudCommunicator.setFilterByGroupName("");
		brightSignBSNCloudCommunicator.setFilterByGroupID("373011");
		extendedStatistic = (ExtendedStatistics) brightSignBSNCloudCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllableProperties = extendedStatistic.getControllableProperties();
		Assert.assertEquals(6, statistics.size());
		Assert.assertEquals(1, advancedControllableProperties.size());
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


	@Test
	void testAggregatedDeviceInfo() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		List<AggregatedDevice> aggregatedDeviceList = brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		String deviceId = "1233489";
		Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
		if (aggregatedDevice.isPresent()) {
			Map<String, String> stats = aggregatedDevice.get().getProperties();
			Assert.assertEquals("PST", stats.get("TimeZone"));
			Assert.assertEquals("Default", stats.get("GroupName"));
			Assert.assertEquals("M4E33N001426", stats.get("PlayerID"));
			Assert.assertEquals("373011", stats.get("GroupID"));
			Assert.assertEquals("None", stats.get("Hostname"));
			Assert.assertEquals("Symphony_Dev", stats.get("Presentation"));
			Assert.assertEquals("9.0.145.1", stats.get("BrightSignOSVersion"));
			Assert.assertEquals("Normal", stats.get("DeviceStatus"));
		}
	}

	@Test
	void testLoggingInfo() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		List<AggregatedDevice> aggregatedDeviceList = brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		String deviceId = "1233489";
		Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
		if (aggregatedDevice.isPresent()) {
			Map<String, String> stats = aggregatedDevice.get().getProperties();
			Assert.assertEquals("Disabled", stats.get("Logging#VariableLog"));
			Assert.assertEquals("Enabled", stats.get("Logging#PlaybackLog"));
			Assert.assertEquals("Enabled", stats.get("Logging#StateLog"));
			Assert.assertEquals("Disabled", stats.get("Logging#UploadAtBoot"));
			Assert.assertEquals("Enabled", stats.get("Logging#EventLog"));
			Assert.assertEquals("Enabled", stats.get("Logging#DiagnosticLog"));
		}
	}

	@Test
	void testLocationInfo() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		List<AggregatedDevice> aggregatedDeviceList = brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		String deviceId = "1233489";
		Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
		if (aggregatedDevice.isPresent()) {
			Map<String, String> stats = aggregatedDevice.get().getProperties();
			Assert.assertEquals("United States", stats.get("Location#Country"));
			Assert.assertEquals("Raleigh", stats.get("Location#Locality"));
		}
	}


	@Test
	void testRebootControl() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Reboot Player";
		String value = "1";
		String deviceId = "1233489";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		brightSignBSNCloudCommunicator.controlProperty(controllableProperty);
	}

	@Test
	void testRebootWithCrashReportControl() throws Exception {
		brightSignBSNCloudCommunicator.getMultipleStatistics();
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		brightSignBSNCloudCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = BrightSignBSNCloudConstant.REBOOT_WITH_CRASH_REPORT;
		String value = "1";
		String deviceId = "1233489";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		brightSignBSNCloudCommunicator.controlProperty(controllableProperty);
	}
}
