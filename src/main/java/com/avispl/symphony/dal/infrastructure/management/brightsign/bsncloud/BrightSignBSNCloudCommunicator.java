/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.AggregatedInformation;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.AggregatorInformation;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.BrightSignBSNCloudCommand;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.BrightSignBSNCloudConstant;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.LoginInfo;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.PingMode;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric.NetworkInformation;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric.StatusEnum;
import com.avispl.symphony.dal.infrastructure.management.brightsign.bsncloud.common.metric.StorageInformation;
import com.avispl.symphony.dal.util.StringUtils;


public class BrightSignBSNCloudCommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {
	/**
	 * Process that is running constantly and triggers collecting data from BrightSign BSNCloud SE API endpoints, based on the given timeouts and thresholds.
	 *
	 * @author Harry
	 * @since 1.0.0
	 */
	class BrightSignBSNCloudDataLoader implements Runnable {
		private volatile boolean inProgress;
		private volatile boolean flag = false;

		public BrightSignBSNCloudDataLoader() {
			inProgress = true;
		}

		@Override
		public void run() {
			loop:
			while (inProgress) {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
					logger.info(String.format("Sleep for 0.5 second was interrupted with error message: %s", e.getMessage()));
				}

				if (!inProgress) {
					break loop;
				}

				// next line will determine whether BrightSign BSNCloud monitoring was paused
				updateAggregatorStatus();
				if (devicePaused) {
					continue loop;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Fetching other than aggregated device list");
				}
				long currentTimestamp = System.currentTimeMillis();
				if (!flag && nextDevicesCollectionIterationTimestamp <= currentTimestamp) {
					populateDeviceDetails();
					flag = true;
				}

				while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
					} catch (InterruptedException e) {
						logger.info(String.format("Sleep for 1 second was interrupted with error message: %s", e.getMessage()));
					}
				}

				if (!inProgress) {
					break loop;
				}
				if (flag) {
					nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;
					flag = false;
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Finished collecting devices statistics cycle at " + new Date());
				}
			}
			// Finished collecting
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}

	/**
	 * Indicates whether a device is considered as paused.
	 * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
	 * collection unless the {@link BrightSignBSNCloudCommunicator#retrieveMultipleStatistics()} method is called which will change it
	 * to a correct value
	 */
	private volatile boolean devicePaused = true;

	/**
	 * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
	 * new devices' statistics loop will be launched before the next monitoring iteration. To avoid that -
	 * this variable stores a timestamp which validates it, so when the devices' statistics is done collecting, variable
	 * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
	 */
	private long nextDevicesCollectionIterationTimestamp;

	/**
	 * This parameter holds timestamp of when we need to stop performing API calls
	 * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
	 */
	private volatile long validRetrieveStatisticsTimestamp;

	/**
	 * Aggregator inactivity timeout. If the {@link BrightSignBSNCloudCommunicator#retrieveMultipleStatistics()}  method is not
	 * called during this period of time - device is considered to be paused, thus the Cloud API
	 * is not supposed to be called
	 */
	private static final long retrieveStatisticsTimeOut = 3 * 60 * 1000;

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link BrightSignBSNCloudCommunicator}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Uptime time stamp to valid one
	 */
	private synchronized void updateValidRetrieveStatisticsTimestamp() {
		validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
		updateAggregatorStatus();
	}

	/**
	 * A mapper for reading and writing JSON using Jackson library.
	 * ObjectMapper provides functionality for converting between Java objects and JSON.
	 * It can be used to serialize objects to JSON format, and deserialize JSON data to objects.
	 */
	ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Executor that runs all the async operations, that is posting and
	 */
	private ExecutorService executorService;

	/**
	 * the login info
	 */
	private LoginInfo loginInfo;

	/**
	 * the network name
	 */
	private String networkName;

	/**
	 * A private field that represents an instance of the BrightSignBSNCloudLoader class, which is responsible for loading device data for BrightSign BSNCloud
	 */
	private BrightSignBSNCloudDataLoader deviceDataLoader;

	/**
	 * A private final ReentrantLock instance used to provide exclusive access to a shared resource
	 * that can be accessed by multiple threads concurrently. This lock allows multiple reentrant
	 * locks on the same shared resource by the same thread.
	 */
	private final ReentrantLock reentrantLock = new ReentrantLock();

	/**
	 * Private variable representing the local extended statistics.
	 */
	private ExtendedStatistics localExtendedStatistics;

	/**
	 * An instance of the AggregatedDeviceProcessor class used to process and aggregate device-related data.
	 */
	private AggregatedDeviceProcessor aggregatedDeviceProcessor;

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * cache data for aggregated
	 */
	private List<AggregatedDevice> cachedData = Collections.synchronizedList(new ArrayList<>());

	/**
	 * ping mode
	 */
	private PingMode pingMode = PingMode.ICMP;

	/**
	 * number of devices
	 */
	private int numberOfDevices;

	/**
	 * Next Marker
	 */
	private String nextMarker;

	/**
	 * filter by group ID
	 */
	private String filterByGroupID;

	/**
	 * filter by group name
	 */
	private String filterByGroupName;

	/**
	 * filter by model
	 */
	private String filterByModel;

	/**
	 * Retrieves {@link #filterByGroupID}
	 *
	 * @return value of {@link #filterByGroupID}
	 */
	public String getFilterByGroupID() {
		return filterByGroupID;
	}

	/**
	 * Sets {@link #filterByGroupID} value
	 *
	 * @param filterByStatus new value of {@link #filterByGroupID}
	 */
	public void setFilterByGroupID(String filterByStatus) {
		this.filterByGroupID = filterByStatus;
	}

	/**
	 * Retrieves {@link #filterByGroupName}
	 *
	 * @return value of {@link #filterByGroupName}
	 */
	public String getFilterByGroupName() {
		return filterByGroupName;
	}

	/**
	 * Sets {@link #filterByGroupName} value
	 *
	 * @param filterByGroupName new value of {@link #filterByGroupName}
	 */
	public void setFilterByGroupName(String filterByGroupName) {
		this.filterByGroupName = filterByGroupName;
	}

	/**
	 * Retrieves {@link #filterByModel}
	 *
	 * @return value of {@link #filterByModel}
	 */
	public String getFilterByModel() {
		return filterByModel;
	}

	/**
	 * Sets {@link #filterByModel} value
	 *
	 * @param filterByModel new value of {@link #filterByModel}
	 */
	public void setFilterByModel(String filterByModel) {
		this.filterByModel = filterByModel;
	}

	/**
	 * Retrieves {@link #pingMode}
	 *
	 * @return value of {@link #pingMode}
	 */
	public String getPingMode() {
		return pingMode.name();
	}

	/**
	 * Sets {@link #pingMode} value
	 *
	 * @param pingMode new value of {@link #pingMode}
	 */
	public void setPingMode(String pingMode) {
		this.pingMode = PingMode.ofString(pingMode);
	}

	/**
	 * Constructs a new instance of BrightSignBSNCloudCommunicator.
	 *
	 * @throws IOException If an I/O error occurs while loading the properties mapping YAML file.
	 */
	public BrightSignBSNCloudCommunicator() throws IOException {
		Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML(BrightSignBSNCloudConstant.MODEL_MAPPING_AGGREGATED_DEVICE, getClass());
		aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
		this.setTrustAllCertificates(true);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * Check for available devices before retrieving the value
	 * ping latency information to Symphony
	 */
	@Override
	public int ping() throws Exception {
		if (this.pingMode == PingMode.ICMP) {
			return super.ping();
		} else if (this.pingMode == PingMode.TCP) {
			if (isInitialized()) {
				long pingResultTotal = 0L;

				for (int i = 0; i < this.getPingAttempts(); i++) {
					long startTime = System.currentTimeMillis();

					try (Socket puSocketConnection = new Socket(this.host, this.getPort())) {
						puSocketConnection.setSoTimeout(this.getPingTimeout());
						if (puSocketConnection.isConnected()) {
							long pingResult = System.currentTimeMillis() - startTime;
							pingResultTotal += pingResult;
							if (this.logger.isTraceEnabled()) {
								this.logger.trace(String.format("PING OK: Attempt #%s to connect to %s on port %s succeeded in %s ms", i + 1, host, this.getPort(), pingResult));
							}
						} else {
							if (this.logger.isDebugEnabled()) {
								this.logger.debug(String.format("PING DISCONNECTED: Connection to %s did not succeed within the timeout period of %sms", host, this.getPingTimeout()));
							}
							return this.getPingTimeout();
						}
					} catch (SocketTimeoutException | ConnectException tex) {
						throw new RuntimeException("Socket connection timed out", tex);
					} catch (UnknownHostException ex) {
						throw new UnknownHostException(String.format("Connection timed out, UNKNOWN host %s", host));
					} catch (Exception e) {
						if (this.logger.isWarnEnabled()) {
							this.logger.warn(String.format("PING TIMEOUT: Connection to %s did not succeed, UNKNOWN ERROR %s: ", host, e.getMessage()));
						}
						return this.getPingTimeout();
					}
				}
				return Math.max(1, Math.toIntExact(pingResultTotal / this.getPingAttempts()));
			} else {
				throw new IllegalStateException("Cannot use device class without calling init() first");
			}
		} else {
			throw new IllegalArgumentException("Unknown PING Mode: " + pingMode);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		reentrantLock.lock();
		try {
			if (loginInfo == null) {
				loginInfo = new LoginInfo();
			}
			checkAuthentication();
			Map<String, String> statistics = new HashMap<>();
			ExtendedStatistics extendedStatistics = new ExtendedStatistics();
			populateNetworkInfo(statistics);
			populateNumberOfDevice(statistics);
			extendedStatistics.setStatistics(statistics);
			localExtendedStatistics = extendedStatistics;
		} finally {
			reentrantLock.unlock();
		}
		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		reentrantLock.lock();
		try {
			String property = controllableProperty.getProperty();
			String deviceId = controllableProperty.getDeviceId();

			String[] propertyList = property.split(BrightSignBSNCloudConstant.HASH);
			String propertyName = property;
			if (property.contains(BrightSignBSNCloudConstant.HASH)) {
				propertyName = propertyList[1];
			}
			Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
			if (aggregatedDevice.isPresent()) {
				String deviceSerial = aggregatedDevice.get().getProperties().get(AggregatedInformation.PLAYER_ID.getName());
				if (StringUtils.isNullOrEmpty(deviceSerial)) {
					throw new IllegalArgumentException(String.format("Unable to control property: %s as the device serial not found.", property));
				}
				switch (propertyName) {
					case BrightSignBSNCloudConstant.REBOOT_PLAYER:
						String request = String.format(BrightSignBSNCloudCommand.REBOOT_ENDPOINT, deviceSerial);
						JsonNode response = doPut(request, new HashMap<>(), JsonNode.class);

						if (checkFailedResponse(response)) {
							throw new IllegalArgumentException(String.format("Unable to control property: %s", property));
						}
						break;
					case BrightSignBSNCloudConstant.REBOOT_WITH_CRASH_REPORT:
						request = String.format(BrightSignBSNCloudCommand.REBOOT_ENDPOINT, deviceSerial);
						ObjectNode rootNode = objectMapper.createObjectNode();
						ObjectNode dataNode = objectMapper.createObjectNode();
						dataNode.put("crash_report", true);
						rootNode.set("data", dataNode);

						response = doPut(request, rootNode, JsonNode.class);
						if (checkFailedResponse(response)) {
							throw new IllegalArgumentException(String.format("Unable to control property: %s", property));
						}
						break;
					default:
						if (logger.isWarnEnabled()) {
							logger.warn(String.format("Unable to execute %s command on device %s: Not Supported", property, deviceId));
						}
						break;
				}
			} else {
				throw new IllegalArgumentException(String.format("Unable to control property: %s as the device does not exist.", property));
			}
		} finally {
			reentrantLock.unlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> controllableProperties) throws Exception {
		if (CollectionUtils.isEmpty(controllableProperties)) {
			throw new IllegalArgumentException("ControllableProperties can not be null or empty");
		}
		for (ControllableProperty p : controllableProperties) {
			try {
				controlProperty(p);
			} catch (Exception e) {
				logger.error(String.format("Error when control property %s", p.getProperty()), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		if (executorService == null) {
			executorService = Executors.newFixedThreadPool(1);
			executorService.submit(deviceDataLoader = new BrightSignBSNCloudDataLoader());
		}
		nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
		updateValidRetrieveStatisticsTimestamp();
		if (cachedData.isEmpty()) {
			return Collections.emptyList();
		}
		return cloneAndPopulateAggregatedDeviceList();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
		return retrieveMultipleStatistics().stream().filter(aggregatedDevice -> list.contains(aggregatedDevice.getDeviceId())).collect(Collectors.toList());
	}

	/**
	 * {@inheritDoc}
	 * set API Key into Header of Request
	 */
	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) {
		if (loginInfo.getToken() != null && !uri.contains(BrightSignBSNCloudCommand.REST_TOKEN)) {
			headers.setBearerAuth(loginInfo.getToken());
		}
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		if (uri.contains("v1/control/reboot")) {
			headers.setContentType(MediaType.APPLICATION_JSON);
		}
		return headers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() throws Exception {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalInit() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		executorService = Executors.newFixedThreadPool(1);
		executorService.submit(deviceDataLoader = new BrightSignBSNCloudDataLoader());
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal destroy is called.");
		}
		if (deviceDataLoader != null) {
			deviceDataLoader.stop();
			deviceDataLoader = null;
		}
		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}
		if (localExtendedStatistics != null && localExtendedStatistics.getStatistics() != null && localExtendedStatistics.getControllableProperties() != null) {
			localExtendedStatistics.getStatistics().clear();
			localExtendedStatistics.getControllableProperties().clear();
		}
		loginInfo = null;
		nextDevicesCollectionIterationTimestamp = 0;
		aggregatedDeviceList.clear();
		cachedData.clear();
		super.internalDestroy();
	}

	/**
	 * Checks user authentication by verifying the login and password fields.
	 * If the login or password fields are empty, or if the session has timed out, it attempts to retrieve a new token.
	 *
	 * @throws FailedLoginException if the username or password fields are empty or if their format is incorrect.
	 * @throws Exception if the authentication process fails.
	 */
	private void checkAuthentication() throws Exception {
		if (StringUtils.isNullOrEmpty(this.getLogin()) || StringUtils.isNullOrEmpty(this.getPassword())) {
			throw new FailedLoginException("Username or Password field is empty. Please check device credentials");
		}
		if (this.loginInfo.isTimeout() || this.loginInfo.getToken() == null) {
			String username;
			String password;
			String clientId;
			String clientSecret;
			String[] loginField = this.getLogin().split(BrightSignBSNCloudConstant.SPACE);
			if (loginField.length == 2) {
				username = loginField[0];
				clientId = loginField[1];
				String[] user = username.split("/");
				if (user.length == 2) {
					networkName = user[0];
				}
			} else {
				throw new FailedLoginException("The format of Username field is incorrect. Please check again");
			}

			String[] passwordField = this.getPassword().split(BrightSignBSNCloudConstant.SPACE);
			if (passwordField.length == 2) {
				password = passwordField[0];
				clientSecret = passwordField[1];
			} else {
				throw new FailedLoginException("The format of Password field is incorrect. Please check again");
			}
			retrieveToken(username, password, clientId, clientSecret);
		}
	}

	/**
	 * Retrieves an authorization token using the provided credentials.
	 *
	 * @param username the username for authentication
	 * @param password the password for authentication
	 * @param clientId the client ID for the application
	 * @param clientSecret the client secret for the application
	 * @throws FailedLoginException if login fails due to incorrect credentials
	 * @throws ResourceNotReachableException if the endpoint is unreachable
	 * @throws Exception for other unforeseen errors
	 */
	private void retrieveToken(String username, String password, String clientId, String clientSecret) throws Exception {
		try {
			MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
			request.add("client_id", clientId);
			request.add("client_secret", clientSecret);
			request.add("grant_type", "password");
			request.add("username", username);
			request.add("password", password);
			JsonNode response = this.doPost(BrightSignBSNCloudCommand.REST_TOKEN, request, JsonNode.class);
			if (response != null && response.has(BrightSignBSNCloudConstant.ACCESS_TOKEN)) {
				this.loginInfo.setToken(response.get(BrightSignBSNCloudConstant.ACCESS_TOKEN).asText());
				this.loginInfo.setLoginDateTime(System.currentTimeMillis());
			} else {
				loginInfo = null;
				throw new ResourceNotReachableException("Unable to retrieve the authorization token, endpoint not reachable");
			}
		} catch (CommandFailureException e) {
			if (e.getStatusCode() == 400) {
				JsonNode response = objectMapper.readTree(e.getResponse());
				if (response.has("error") && "invalid_grant".equalsIgnoreCase(response.get("error").asText())) {
					throw new FailedLoginException("Unable to login. Please check device credentials");
				}
			}
			throw new ResourceNotReachableException("Unable to retrieve the authorization token, endpoint not reachable", e);
		} catch (Exception ex) {
			throw new ResourceNotReachableException("Unable to retrieve the authorization token, endpoint not reachable", ex);
		}
	}

	/**
	 * Populates network information into the provided stats map by retrieving data from the network info endpoint.
	 *
	 * @param stats a map to store network information as key-value pairs
	 * @throws ResourceNotReachableException if the network information cannot be retrieved
	 */
	private void populateNetworkInfo(Map<String, String> stats) {
		try {
			JsonNode response = this.doGet(BrightSignBSNCloudCommand.NETWORK_INFO, JsonNode.class);
			for (JsonNode item : response) {
				if (networkName.equals(item.get(BrightSignBSNCloudConstant.NAME).asText())) {
					for (AggregatorInformation property : AggregatorInformation.values()) {
						if (checkNode(property, item)) {
							String group = property.getGroup();
							switch (property) {
								case CREATE_DATE:
								case LAST_MODIFIED_DATE:
								case LAST_LOCKOUT_DATE:
								case LOCKOUT_DATE:
									stats.put(uppercaseFirstCharacter(property.getName()),
											convertDateTimeFormat(getDefaultValueForNullData(item.get(property.getName()).asText()), BrightSignBSNCloudConstant.DEFAULT_FORMAT_DATETIME_WITH_MILLIS));
									break;
								case SUB_LAST_MODIFIED_DATE:
								case SUB_CREATE_DATE:
									stats.put(uppercaseFirstCharacter(property.getGroup()) + "#" + uppercaseFirstCharacter(property.getName()),
											convertDateTimeFormat(getDefaultValueForNullData(item.get(property.getGroup()).get(property.getName()).asText()),
													BrightSignBSNCloudConstant.DEFAULT_FORMAT_DATETIME_WITH_MILLIS));
									break;
								case IS_LOCKED_OUT:
									stats.put("LockedOut", getDefaultValueForNullData(item.get(property.getName()).asText()));
									break;
								default:
									if (BrightSignBSNCloudConstant.EMPTY.equals(group)) {
										stats.put(uppercaseFirstCharacter(property.getName()), getDefaultValueForNullData(item.get(property.getName()).asText()));
									} else {
										stats.put(uppercaseFirstCharacter(property.getGroup()) + "#" + uppercaseFirstCharacter(property.getName()),
												getDefaultValueForNullData(item.get(property.getGroup()).get(property.getName()).asText()));
									}
									break;
							}
						}
					}
					break;
				}
			}
		} catch (Exception e) {
			throw new ResourceNotReachableException("Unable to retrieve network information.", e);
		}
	}

	/**
	 * Checks if the specified property exists in the given JSON node.
	 *
	 * @param property the property to check for existence
	 * @param node the JSON node to check
	 * @return true if the property exists in the node, false otherwise
	 */
	private boolean checkNode(AggregatorInformation property, JsonNode node) {
		if (BrightSignBSNCloudConstant.EMPTY.equals(property.getGroup()) && node.has(property.getName())) {
			return true;
		}

		if (!BrightSignBSNCloudConstant.EMPTY.equals(property.getGroup()) && node.has(property.getGroup()) && node.get(property.getGroup()).has(property.getName())) {
			return true;
		}
		return false;
	}

	/**
	 * Populates number of device on network into the provided stats map by retrieving data from the COUNT endpoint.
	 *
	 * @param stats a map to store network information as key-value pairs
	 * @throws ResourceNotReachableException if the network information cannot be retrieved
	 */
	private void populateNumberOfDevice(Map<String, String> stats) {
		try {
			String response = this.doGet(BrightSignBSNCloudCommand.GET_NUMBER_OF_DEVICES + createParamFilter());
			stats.put("NumberOfDevices", response);
			numberOfDevices = Integer.parseInt(response);
		} catch (CommandFailureException ex) {
			if (!ex.getResponse().contains("Unsupported value")) {
				throw new ResourceNotReachableException("Unable to retrieve get number of devices on network.", ex);
			}
			stats.put("NumberOfDevices", "0");
		} catch (Exception e) {
			throw new ResourceNotReachableException("Unable to retrieve get number of devices on network.", e);
		}
	}

	/**
	 * Creates a parameter filter string for querying based on provided status, model, and group name filters.
	 * The filters are concatenated with "AND" if more than one is present.
	 *
	 * @return a filter string in the format "?filter=[Status].[Health] IS IN ('value1', 'value2') AND [Model] IS IN ('value1', 'value2')..."
	 */
	private String createParamFilter() {
		StringBuilder param = new StringBuilder("?filter=");
		boolean isFirstFilterAdded = false;

		if (StringUtils.isNotNullOrEmpty(filterByGroupID)) {
			param.append("[Status].[Group].[ID] IS IN (")
					.append(filterByGroupID)
					.append(")");
			isFirstFilterAdded = true;
		}

		if (StringUtils.isNotNullOrEmpty(filterByModel)) {
			if (isFirstFilterAdded) {
				param.append(" AND ");
			}
			param.append("[Model] IS IN (")
					.append(convertToQuotedCSV(filterByModel))
					.append(")");
			isFirstFilterAdded = true;
		}

		if (StringUtils.isNotNullOrEmpty(filterByGroupName)) {
			if (isFirstFilterAdded) {
				param.append(" AND ");
			}
			param.append("[Status].[Group].[Name] IS IN (")
					.append(convertToQuotedCSV(filterByGroupName))
					.append(")");
		}
		return param.toString();
	}

	/**
	 * Converts a comma-separated string into a quoted comma-separated string.
	 * For example, "A,B,C" becomes "'A','B','C'".
	 *
	 * @param input the input string to convert.
	 * @return a string where each element is surrounded by single quotes and separated by commas.
	 */
	private String convertToQuotedCSV(String input) {
		String[] items = input.split(",");
		return Arrays.stream(items).map(String::trim)
				.map(item -> "'" + item + "'").collect(Collectors.joining(","));
	}

	/**
	 * Populates device details by making a POST request to retrieve information from Bright Sign.
	 * The method clears the existing aggregated device list, processes the response, and updates the list accordingly.
	 * Any error during the process is logged.
	 */
	private void populateDeviceDetails() {
		try {
			JsonNode response = this.doGet(BrightSignBSNCloudCommand.GET_ALL_DEVICES + createParamFilter() + createPageSizeParam(), JsonNode.class);
			if (response != null && response.has(BrightSignBSNCloudConstant.ITEMS)) {
				for (JsonNode jsonNode : response.get(BrightSignBSNCloudConstant.ITEMS)) {
					JsonNode node = objectMapper.createArrayNode().add(jsonNode);
					String id = jsonNode.get("id").asText();
					cachedData.removeIf(item -> item.getDeviceId().equals(id));
					cachedData.addAll(aggregatedDeviceProcessor.extractDevices(node));
				}
				nextMarker = BrightSignBSNCloudConstant.EMPTY;
				if (response.has("isTruncated") && BrightSignBSNCloudConstant.TRUE.equalsIgnoreCase(response.get("isTruncated").asText())) {
					nextMarker = response.get("nextMarker").asText();
				}
			}
		} catch (CommandFailureException ex) {
			cachedData.clear();
			logger.error(ex.getResponse(), ex);
		} catch (Exception e) {
			logger.error("Error while populate aggregated device", e);
		}
	}

	/**
	 * Creates a query parameter string for pagination based on the number of devices and the next marker.
	 *
	 * @return A string representing the pagination parameters for a query.
	 */
	private String createPageSizeParam() {
		String result = "&pageSize=" + numberOfDevices;
		if (StringUtils.isNotNullOrEmpty(nextMarker)) {
			result += "&nextMarker=" + nextMarker;
		}
		return result;
	}

	/**
	 * Clones and populates a new list of aggregated devices with mapped monitoring properties.
	 *
	 * @return A new list of {@link AggregatedDevice} objects with mapped monitoring properties.
	 */
	private List<AggregatedDevice> cloneAndPopulateAggregatedDeviceList() {
		aggregatedDeviceList.clear();
		synchronized (cachedData) {
			for (AggregatedDevice item : cachedData) {
				AggregatedDevice aggregatedDevice = new AggregatedDevice();
				Map<String, String> cachedValue = item.getProperties();
				aggregatedDevice.setDeviceId(item.getDeviceId());
				aggregatedDevice.setDeviceModel(item.getDeviceModel());
				aggregatedDevice.setDeviceName(item.getDeviceName());
				aggregatedDevice.setDeviceOnline(item.getDeviceOnline());

				Map<String, String> stats = new HashMap<>();
				List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();
				mapMonitoringProperty(cachedValue, stats);
				mapControllableProperty(stats, advancedControllableProperties);
				aggregatedDevice.setProperties(stats);
				aggregatedDevice.setControllableProperties(advancedControllableProperties);
				aggregatedDeviceList.add(aggregatedDevice);
			}
		}
		return aggregatedDeviceList;
	}

	/**
	 * Maps controllable properties to the provided stats and advancedControllableProperties lists.
	 * This method adds buttons for "Reboot Player" and "Reboot with Crash Report" to the advanced controllable properties.
	 *
	 * @param stats A map containing the statistics to be populated with controllable properties.
	 * @param advancedControllableProperties A list of AdvancedControllableProperty objects to be populated with controllable properties.
	 */
	private void mapControllableProperty(Map<String, String> stats, List<AdvancedControllableProperty> advancedControllableProperties) {
		addAdvancedControlProperties(advancedControllableProperties, stats, createButton(BrightSignBSNCloudConstant.REBOOT_PLAYER, "Apply", "Applying", 0), BrightSignBSNCloudConstant.NONE);
		addAdvancedControlProperties(advancedControllableProperties, stats, createButton(BrightSignBSNCloudConstant.REBOOT_WITH_CRASH_REPORT, "Apply", "Applying", 0), BrightSignBSNCloudConstant.NONE);
	}

	/**
	 * Maps monitoring properties from cached values to statistics and advanced control properties.
	 *
	 * @param cachedValue The cached values map containing raw monitoring data.
	 * @param stats The statistics map to store mapped monitoring properties.
	 */
	private void mapMonitoringProperty(Map<String, String> cachedValue, Map<String, String> stats) {
		for (AggregatedInformation property : AggregatedInformation.values()) {
			String name = property.getName();
			String propertyName = property.getGroup() + name;
			String value = getDefaultValueForNullData(cachedValue.get(name));
			switch (property) {
				case DEVICE_STATUS:
					stats.put(propertyName, StatusEnum.getNameByValue(value));
					break;
				case LAST_CONNECTED:
					stats.put(propertyName, convertDateTimeFormat(value, BrightSignBSNCloudConstant.DEFAULT_FORMAT_DATETIME_WITHOUT_MILLIS));
					break;
				case UPTIME:
					stats.put(propertyName, formatUpTime(value));
					break;
				case NETWORK_INTERFACE:
					populateNetworkInterface(value, stats);
					break;
				case STORAGE:
					populateStorageInformation(value, stats);
					break;
				case DIAGNOSTIC_LOG:
				case EVENT_LOG:
				case PLAYBACK_LOG:
				case STATE_LOG:
				case VARIABLE_LOG:
				case UPLOAD_AT_BOOT:
					if (BrightSignBSNCloudConstant.TRUE.equalsIgnoreCase(value)) {
						value = BrightSignBSNCloudConstant.ENABLED;
					}
					if (BrightSignBSNCloudConstant.FALSE.equalsIgnoreCase(value)) {
						value = BrightSignBSNCloudConstant.DISABLED;
					}
					stats.put(propertyName, value);
					break;
				default:
					stats.put(propertyName, value);
			}
		}
	}

	/**
	 * Populates storage information into the provided stats map based on the JSON data string.
	 *
	 * @param value the JSON data string containing storage information
	 * @param stats a map to store storage information as key-value pairs
	 */
	private void populateStorageInformation(String value, Map<String, String> stats) {
		try {
			JsonNode jsonNode = objectMapper.readTree(value);
			ArrayNode filteredNodes = objectMapper.createArrayNode();
			jsonNode.forEach(node -> {
				if (!(node.has(BrightSignBSNCloudConstant.INTERFACE) &&
						("Tmp".equalsIgnoreCase(node.get(BrightSignBSNCloudConstant.INTERFACE).asText()) ||
								"Flash".equalsIgnoreCase(node.get(BrightSignBSNCloudConstant.INTERFACE).asText())))) {
					filteredNodes.add(node);
				}
			});
			int index = 0;
			for (JsonNode node : filteredNodes) {
				index++;
				String group = "Storage" + (filteredNodes.size() == 1 ? BrightSignBSNCloudConstant.EMPTY : index) + "#";
				for (StorageInformation item : StorageInformation.values()) {
					switch (item) {
						case SIZE_FREE:
						case SIZE_TOTAL:
							if (node.has(BrightSignBSNCloudConstant.STATS) && node.get(BrightSignBSNCloudConstant.STATS).has(item.getName())) {
								stats.put(group + uppercaseFirstCharacter(item.getName()) + "(GB)",
										convertBytesToGigabytes(getDefaultValueForNullData(node.get(BrightSignBSNCloudConstant.STATS).get(item.getName()).asText())));
							}
							break;
						default:
							if (node.has(item.getName())) {
								stats.put(group + uppercaseFirstCharacter(item.getName()), getDefaultValueForNullData(node.get(item.getName()).asText()));
							}
							break;
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error while populate Storage Information", e);
		}
	}

	/**
	 * Populates network interface information into the provided stats map based on the JSON data string.
	 *
	 * @param value the JSON data string containing network interface information
	 * @param stats a map to store network interface information as key-value pairs
	 */
	private void populateNetworkInterface(String value, Map<String, String> stats) {
		try {
			JsonNode jsonNode = objectMapper.readTree(value);
			int index = 0;
			for (JsonNode item : jsonNode) {
				index++;
				String group = "NetworkInterface" + (jsonNode.size() == 1 ? BrightSignBSNCloudConstant.EMPTY : index) + "#";
				Iterator<Entry<String, JsonNode>> fields = item.fields();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> field = fields.next();
					String key = field.getKey();
					JsonNode valueItem = field.getValue();

					if (valueItem.isObject()) {
						continue;
					}
					String valueNode;
					if (valueItem.isArray()) {
						valueNode = valueItem.toString().replace("[\"", BrightSignBSNCloudConstant.EMPTY).replace("\"]", BrightSignBSNCloudConstant.EMPTY);
					} else if (valueItem.isNull()) {
						valueNode = BrightSignBSNCloudConstant.NONE;
					} else if (BrightSignBSNCloudConstant.TRUE.equalsIgnoreCase(valueItem.asText()) && !"enabled".equalsIgnoreCase(key)) {
						valueNode = BrightSignBSNCloudConstant.ENABLED;
					} else if (BrightSignBSNCloudConstant.FALSE.equalsIgnoreCase(valueItem.asText())) {
						valueNode = BrightSignBSNCloudConstant.DISABLED;
					} else {
						valueNode = valueItem.asText();
					}
					if (!BrightSignBSNCloudConstant.NONE.equalsIgnoreCase(NetworkInformation.getByDefaultName(key))) {
						stats.put(group + NetworkInformation.getByDefaultName(key), getDefaultValueForNullData(valueNode));
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error while populate Network Interfaces", e);
		}
	}

	/**
	 * Checks if the given JSON response indicates a failed operation.
	 *
	 * @param response The JSON response to be checked.
	 * @return {@code true} if the response is null, does not contain the required data,
	 *         or the success flag is not set to "true"; {@code false} otherwise.
	 */
	private boolean checkFailedResponse(JsonNode response){
		if (response == null || !response.has(BrightSignBSNCloudConstant.DATA) || !response.get(BrightSignBSNCloudConstant.DATA).has(BrightSignBSNCloudConstant.RESULT) ||
				!response.get(BrightSignBSNCloudConstant.DATA).get(BrightSignBSNCloudConstant.RESULT).has(BrightSignBSNCloudConstant.SUCCESS)
				|| !BrightSignBSNCloudConstant.TRUE.equalsIgnoreCase(
				response.get(BrightSignBSNCloudConstant.DATA).get(BrightSignBSNCloudConstant.RESULT).get(BrightSignBSNCloudConstant.SUCCESS).asText())) {
			return true;
		}
		return false;
	}

	/**
	 * capitalize the first character of the string
	 *
	 * @param input input string
	 * @return string after fix
	 */
	private String uppercaseFirstCharacter(String input) {
		char firstChar = input.charAt(0);
		return Character.toUpperCase(firstChar) + input.substring(1);
	}

	/**
	 * Converts a date-time string from the default format to the target format with GMT timezone.
	 *
	 * @param inputDateTime The input date-time string in the default format.
	 * @return The date-time string after conversion to the target format with GMT timezone.
	 * Returns {@link BrightSignBSNCloudConstant#NONE} if there is an error during conversion.
	 * @throws Exception If there is an error parsing the input date-time string.
	 */
	private String convertDateTimeFormat(String inputDateTime, String format) {
		if (BrightSignBSNCloudConstant.NONE.equals(inputDateTime)) {
			return inputDateTime;
		}
		try {
			SimpleDateFormat inputFormat = new SimpleDateFormat(format);
			inputFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

			SimpleDateFormat outputFormat = new SimpleDateFormat(BrightSignBSNCloudConstant.TARGET_FORMAT_DATETIME);
			outputFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

			Date date = inputFormat.parse(inputDateTime);
			return outputFormat.format(date);
		} catch (Exception e) {
			logger.warn("Can't convert the date time value");
			return BrightSignBSNCloudConstant.NONE;
		}
	}

	/**
	 * Formats uptime from a string representation "hh:mm:ss" into "X hour(s) Y minute(s)" format.
	 *
	 * @param time the uptime string to format
	 * @return formatted uptime string or "None" if input is invalid
	 */
	private String formatUpTime(String time) {
		if (BrightSignBSNCloudConstant.NONE.equalsIgnoreCase(time)) {
			return time;
		}
		String[] timeParts = time.split(":");
		if (timeParts.length != 3) {
			return BrightSignBSNCloudConstant.NONE;
		}
		int hours;
		int days = 0;
		if (timeParts[0].contains(".")) {
			String[] dayTimeParts = timeParts[0].split("\\.");
			if (dayTimeParts.length != 2) {
				return BrightSignBSNCloudConstant.NONE;
			}
			days = Integer.parseInt(dayTimeParts[0]);
			hours = Integer.parseInt(dayTimeParts[1]);
		} else {
			hours = Integer.parseInt(timeParts[0]);
		}
		int minutes = Integer.parseInt(timeParts[1]);
		if (days != 0) {
			return days + " day(s) " + hours + " hour(s) " + minutes + " minute(s) ";
		} else if (hours != 0) {
			return hours + " hour(s) " + minutes + " minute(s) ";
		} else {
			return minutes + " minute(s) ";
		}
	}

	/**
	 * Converts a byte value to gigabytes.
	 *
	 * @param value the byte value to convert
	 * @return the value converted to gigabytes, rounded to two decimal places, or "None" if conversion fails
	 */
	private String convertBytesToGigabytes(String value) {
		try {
			long bytes = Long.parseLong(value);
			return round((double) bytes / (1024 * 1024 * 1024), 2);
		} catch (Exception e) {
			logger.warn("Can't convert the value");
			return BrightSignBSNCloudConstant.NONE;
		}
	}

	/**
	 * Rounds a double value to the specified number of decimal places.
	 *
	 * @param value the value to round
	 * @param places the number of decimal places to round to
	 * @return the rounded value as a string
	 * @throws IllegalArgumentException if places is negative
	 */
	private String round(double value, int places) {
		if (places < 0) {
			throw new IllegalArgumentException();
		}

		BigDecimal bd = new BigDecimal(Double.toString(value));
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return String.valueOf(bd.doubleValue());
	}

	/**
	 * check value is null or empty
	 *
	 * @param value input value
	 * @return value after checking
	 */
	private String getDefaultValueForNullData(String value) {
		return StringUtils.isNotNullOrEmpty(value) && !"null".equalsIgnoreCase(value) ? uppercaseFirstCharacter(value) : BrightSignBSNCloudConstant.NONE;
	}

	/**
	 * Create a button.
	 *
	 * @param name name of the button
	 * @param label label of the button
	 * @param labelPressed label of the button after pressing it
	 * @param gracePeriod grace period of button
	 * @return This returns the instance of {@link AdvancedControllableProperty} type Button.
	 */
	private AdvancedControllableProperty createButton(String name, String label, String labelPressed, long gracePeriod) {
		AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
		button.setLabel(label);
		button.setLabelPressed(labelPressed);
		button.setGracePeriod(gracePeriod);
		return new AdvancedControllableProperty(name, new Date(), button, BrightSignBSNCloudConstant.EMPTY);
	}

	/**
	 * Add addAdvancedControlProperties if advancedControllableProperties different empty
	 *
	 * @param advancedControllableProperties advancedControllableProperties is the list that store all controllable properties
	 * @param stats store all statistics
	 * @param property the property is item advancedControllableProperties
	 * @throws IllegalStateException when exception occur
	 */
	private void addAdvancedControlProperties(List<AdvancedControllableProperty> advancedControllableProperties, Map<String, String> stats, AdvancedControllableProperty property, String value) {
		if (property != null) {
			advancedControllableProperties.removeIf(controllableProperty -> controllableProperty.getName().equals(property.getName()));

			String propertyValue = StringUtils.isNotNullOrEmpty(value) ? value : BrightSignBSNCloudConstant.EMPTY;
			stats.put(property.getName(), propertyValue);

			advancedControllableProperties.add(property);
		}
	}
}
