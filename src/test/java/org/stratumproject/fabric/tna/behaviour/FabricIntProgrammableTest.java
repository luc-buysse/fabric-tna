// Copyright 2020-present Open Networking Foundation
// SPDX-License-Identifier: Apache-2.0
package org.stratumproject.fabric.tna.behaviour;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.onlab.junit.TestUtils;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.util.HexString;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.TestApplicationId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DefaultHost;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostLocation;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.driver.DriverData;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.flow.DefaultFlowEntry;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperation;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.stratumproject.fabric.tna.Constants;
import org.stratumproject.fabric.tna.inbandtelemetry.IntReportConfig;
import org.stratumproject.fabric.tna.INTDeviceConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.partialMockBuilder;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.onosproject.net.group.DefaultGroupBucket.createCloneGroupBucket;
import static org.slf4j.LoggerFactory.getLogger;
import static org.stratumproject.fabric.tna.behaviour.FabricUtils.KRYO;
import static org.stratumproject.fabric.tna.behaviour.FabricUtils.doCareRangeMatch;
import static org.stratumproject.fabric.tna.utils.TestUtils.getIntReportConfig;
import static org.stratumproject.fabric.tna.utils.TestUtils.getINTConfig;

import static org.stratumproject.fabric.tna.Constants.V1MODEL_RECIRC_PORT;
import static org.stratumproject.fabric.tna.Constants.V1MODEL_INT_REPORT_MIRROR_ID;

/**
 * Tests for fabric INT programmable behaviour.
 */
@RunWith(Parameterized.class)
public class FabricIntProgrammableTest {
    private static final int NODE_SID_IPV4 = 100;
    private static final IpAddress ROUTER_IP = IpAddress.valueOf("10.200.0.100");

    private static final ApplicationId APP_ID =
            TestApplicationId.create(Constants.APP_NAME);
    private static final DeviceId LEAF_DEVICE_ID = DeviceId.deviceId("device:1");
    private static final DeviceId SPINE_DEVICE_ID = DeviceId.deviceId("device:2");
    private static final IpPrefix SUBNET_1 = IpPrefix.valueOf("10.0.0.0/24");
    private static final IpPrefix SUBNET_2 = IpPrefix.valueOf("192.168.0.0/24");
    private static final int DEFAULT_PRIORITY = 10000;
    private static final IpAddress COLLECTOR_IP = IpAddress.valueOf("10.128.0.1");
    private static final TpPort COLLECTOR_PORT = TpPort.tpPort(32766);
    private static final short BMD_TYPE_EGRESS_MIRROR = 2;
    private static final short BMD_TYPE_DEFLECTED = 5;
    private static final short BMD_TYPE_INT_INGRESS_DROP = 4;
    private static final short MIRROR_TYPE_INVALID = 0;
    private static final short MIRROR_TYPE_INT_REPORT = 1;
    private static final short INT_REPORT_TYPE_FLOW = 1;
    private static final short INT_REPORT_TYPE_QUEUE = 2;
    private static final short INT_REPORT_TYPE_DROP = 4;
    private static final HostLocation COLLECTOR_LOCATION = new HostLocation(LEAF_DEVICE_ID, PortNumber.P0, 0);
    private static final Host COLLECTOR_HOST =
            new DefaultHost(null, null, null, null, COLLECTOR_LOCATION, Sets.newHashSet());
    private static final ImmutableByteSequence DEFAULT_TIMESTAMP_MASK =
            ImmutableByteSequence.copyFrom(
                    HexString.fromHexString("ffffc0000000", ""));
    private static final ImmutableByteSequence DEFAULT_QMASK = ImmutableByteSequence.copyFrom(
            HexString.fromHexString("00000000ffffff00", ""));
    private static final Map<Integer, Long> QUAD_PIPE_MIRROR_SESS_TO_RECIRC_PORTS =
            ImmutableMap.<Integer, Long>builder()
                    .put(0x200, 0xFFFFFF00L)
                    .put(0x201, 0xFFFFFF01L)
                    .put(0x202, 0xFFFFFF02L)
                    .put(0x203, 0xFFFFFF03L).build();
    private static final Map<Integer, Long> V1MODEL_MIRROR_SESS_TO_RECIRC_PORTS =
            ImmutableMap.<Integer, Long>builder()
                    .put(V1MODEL_INT_REPORT_MIRROR_ID, V1MODEL_RECIRC_PORT.get(0)).build();
    private static final long DEFAULT_QUEUE_REPORT_TRIGGER_LATENCY_THRESHOLD = 0xffffffffL;
    private static final long DEFAULT_QUEUE_REPORT_RESET_LATENCY_THRESHOLD = 0;
    private static final byte MAX_QUEUES = 32;
    private static final int INT_MIRROR_TRUNCATE_MAX_LEN = 128;
    private static final FlowRuleOperations EMPTY_FLOW_RULE_OPS = FlowRuleOperations.builder().build();

    private FabricIntProgrammable intProgrammable;
    private FlowRuleService flowRuleService;
    private GroupService groupService;
    private NetworkConfigService netcfgService;
    private CoreService coreService;
    private HostService hostService;
    private DriverData driverData;

    private boolean isArchV1model;

    public FabricIntProgrammableTest(boolean isV1model) {
        // Needed for JUnit parameterized test.
        this.isArchV1model = isV1model;
    }

    @Parameterized.Parameters(name = "Test - {index}, isV1model: {0}")
    public static Collection values() {
        return Arrays.asList(new Object[][] {
                {true},
                {false}
        });
    }

    @Before
    public void setup() {
        FabricCapabilities capabilities = createMock(FabricCapabilities.class);
        expect(capabilities.isArchTna()).andReturn(!this.isArchV1model).anyTimes();
        expect(capabilities.isArchV1model()).andReturn(this.isArchV1model).anyTimes();
        expect(capabilities.hasHashedTable()).andReturn(true).anyTimes();
        expect(capabilities.supportDoubleVlanTerm()).andReturn(false).anyTimes();
        expect(capabilities.hwPipeCount()).andReturn(4).anyTimes();
        replay(capabilities);

        // Services mock
        flowRuleService = createMock(FlowRuleService.class);
        groupService = createMock(GroupService.class);
        netcfgService = createMock(NetworkConfigService.class);
        coreService = createMock(CoreService.class);
        hostService = createMock(HostService.class);
        expect(coreService.getAppId(anyString())).andReturn(APP_ID).anyTimes();

        expect(netcfgService.getConfig(LEAF_DEVICE_ID, INTDeviceConfig.class))
                .andReturn(getINTConfig(LEAF_DEVICE_ID, "/sr.json")).anyTimes();
        expect(hostService.getHostsByIp(COLLECTOR_IP)).andReturn(ImmutableSet.of(COLLECTOR_HOST)).anyTimes();
        replay(coreService, netcfgService, hostService);

        DriverHandler driverHandler = createMock(DriverHandler.class);
        expect(driverHandler.get(FlowRuleService.class)).andReturn(flowRuleService).anyTimes();
        expect(driverHandler.get(GroupService.class)).andReturn(groupService).anyTimes();
        expect(driverHandler.get(NetworkConfigService.class)).andReturn(netcfgService).anyTimes();
        expect(driverHandler.get(CoreService.class)).andReturn(coreService).anyTimes();
        expect(driverHandler.get(HostService.class)).andReturn(hostService).anyTimes();
        replay(driverHandler);

        driverData = createMock(DriverData.class);
        expect(driverData.deviceId()).andReturn(LEAF_DEVICE_ID).anyTimes();
        replay(driverData);

        intProgrammable = partialMockBuilder(FabricIntProgrammable.class)
                .addMockedMethod("getFieldSize").createMock();
        expect(intProgrammable.getFieldSize(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_QUEUE_LATENCY_THRESHOLDS,
                P4InfoConstants.HDR_HOP_LATENCY_UPPER)).andReturn(16).anyTimes();
        expect(intProgrammable.getFieldSize(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_QUEUE_LATENCY_THRESHOLDS,
                P4InfoConstants.HDR_HOP_LATENCY_LOWER)).andReturn(16).anyTimes();
        replay(intProgrammable);
        TestUtils.setField(intProgrammable, "capabilities", capabilities);
        TestUtils.setField(intProgrammable, "handler", driverHandler);
        TestUtils.setField(intProgrammable, "data", driverData);
        TestUtils.setField(intProgrammable, "log", getLogger(""));

        testInit();
    }

    @After
    public void teardown() {
        reset(flowRuleService, groupService, netcfgService, coreService);
    }

    /**
     * Test "setUpIntConfig" function of IntProgrammable and the config contains
     * subnets to be watched.
     * And there exists some entries in the watchlist table.
     */
    @Test
    public void testSetupIntConfigWithWatchedSubnet() {
        final List<FlowEntry> existsEntries = ImmutableList.of(
            buildFlowEntry(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_SRC)),
            buildFlowEntry(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_DST))
        );
        final FlowRuleOperations expectedOpsForCollector = FlowRuleOperations.builder()
            .add(buildCollectorWatchlistRule(LEAF_DEVICE_ID))
            .build();
        final FlowRuleOperations expectedOpsForSubnet = FlowRuleOperations.builder()
            .remove(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_SRC))
            .remove(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_DST))
            .newStage()
            .add(buildWatchlistRule(null, Criterion.Type.IPV4_SRC))
            .add(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_SRC))
            .add(buildWatchlistRule(SUBNET_1, Criterion.Type.IPV4_DST))
            .add(buildWatchlistRule(SUBNET_2, Criterion.Type.IPV4_SRC))
            .add(buildWatchlistRule(SUBNET_2, Criterion.Type.IPV4_DST))
            .build();
        final IntReportConfig intConfig = getIntReportConfig(APP_ID, "/int-report-with-subnets.json");
        final Capture<FlowRuleOperations> capturedOpsForCollector = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForSubnet = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForQueueThresholds = newCapture();
        final Capture<FlowRule> capturedReportRules = newCapture(CaptureType.ALL);
        final List<FlowRule> expectRules = Lists.newArrayList();
        expectRules.addAll(queueReportFlows(LEAF_DEVICE_ID, DEFAULT_QUEUE_REPORT_TRIGGER_LATENCY_THRESHOLD,
            DEFAULT_QUEUE_REPORT_RESET_LATENCY_THRESHOLD));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_INT_INGRESS_DROP,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                INT_REPORT_TYPE_FLOW, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_DEFLECTED,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR, INT_REPORT_TYPE_QUEUE, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR,
                                (short) (INT_REPORT_TYPE_QUEUE | INT_REPORT_TYPE_FLOW),
                                MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildFilterConfigFlow(LEAF_DEVICE_ID));

        // Expected steps of method calls, captures, and results.
        reset(flowRuleService);
        expect(flowRuleService.getFlowEntriesById(APP_ID)).andReturn(existsEntries).times(3);
        flowRuleService.apply(capture(capturedOpsForCollector));
        flowRuleService.apply(capture(capturedOpsForSubnet));
        flowRuleService.apply(capture(capturedOpsForQueueThresholds));
        flowRuleService.applyFlowRules(capture(capturedReportRules));
        expectLastCall().times(expectRules.size());
        replay(flowRuleService);

        // Verify values.
        assertTrue(intProgrammable.setUpIntConfig(intConfig));
        assertFlowRuleOperationsEquals(expectedOpsForCollector, capturedOpsForCollector.getValue());
        assertFlowRuleOperationsEquals(expectedOpsForSubnet, capturedOpsForSubnet.getValue());
        for (int i = 0; i < expectRules.size(); i++) {
            FlowRule expectRule = expectRules.get(i);
            FlowRule actualRule = capturedReportRules.getValues().get(i);
            assertTrue(expectRule.exactMatch(actualRule));
        }
        verify(flowRuleService);
    }

    /**
     * Test "setUpIntConfig" function of IntProgrammable, but there is an old
     * flow rule in the store for collector.
     */
    @Test
    public void testSetupIntConfigWithOldEntry() {
        final List<FlowEntry> existsEntries = ImmutableList.of(
            buildFlowEntry(buildCollectorWatchlistRule(LEAF_DEVICE_ID))
        );
        final FlowRuleOperations expectedOpsForCollector = FlowRuleOperations.builder()
            .remove(buildCollectorWatchlistRule(LEAF_DEVICE_ID))
            .newStage()
            .add(buildCollectorWatchlistRule(LEAF_DEVICE_ID))
            .build();
        final IntReportConfig intConfig = getIntReportConfig(APP_ID, "/int-report.json");
        final Capture<FlowRuleOperations> capturedOpsForCollector = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForSubnet = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForQueueThresholds = newCapture();
        final Capture<FlowRule> capturedReportRules = newCapture(CaptureType.ALL);
        final List<FlowRule> expectRules = Lists.newArrayList();
        expectRules.addAll(queueReportFlows(LEAF_DEVICE_ID, DEFAULT_QUEUE_REPORT_TRIGGER_LATENCY_THRESHOLD,
            DEFAULT_QUEUE_REPORT_RESET_LATENCY_THRESHOLD));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_INT_INGRESS_DROP,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                INT_REPORT_TYPE_FLOW, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_DEFLECTED,
                                INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR, INT_REPORT_TYPE_QUEUE, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR,
                                (short) (INT_REPORT_TYPE_QUEUE | INT_REPORT_TYPE_FLOW),
                                MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildFilterConfigFlow(LEAF_DEVICE_ID));

        // Expected steps of method calls, captures, and results.
        reset(flowRuleService);
        expect(flowRuleService.getFlowEntriesById(APP_ID)).andReturn(existsEntries).times(3);
        flowRuleService.apply(capture(capturedOpsForCollector));
        flowRuleService.apply(capture(capturedOpsForSubnet));
        flowRuleService.apply(capture(capturedOpsForQueueThresholds));
        flowRuleService.applyFlowRules(capture(capturedReportRules));
        expectLastCall().times(expectRules.size());
        replay(flowRuleService);

        // Verify values.
        assertTrue(intProgrammable.setUpIntConfig(intConfig));
        assertFlowRuleOperationsEquals(expectedOpsForCollector, capturedOpsForCollector.getValue());
        assertFlowRuleOperationsEquals(EMPTY_FLOW_RULE_OPS, capturedOpsForSubnet.getValue());
        for (int i = 0; i < expectRules.size(); i++) {
            FlowRule expectRule = expectRules.get(i);
            FlowRule actualRule = capturedReportRules.getValues().get(i);
            assertTrue(expectRule.exactMatch(actualRule));
        }
        verify(flowRuleService);
    }

    /**
     * Test "setUpIntConfig" function of IntProgrammable with a config that contains
     * queue report threshold config.
     */
    @Test
    public void testSetupIntConfigWithQueueReportThreshold() {
        final IntReportConfig intConfig = getIntReportConfig(APP_ID, "/int-report-queue-report-threshold.json");
        final List<FlowRule> expectRules = Lists.newArrayList();
        final Capture<FlowRuleOperations> capturedOpsForCollector = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForSubnet = newCapture();
        final Capture<FlowRuleOperations> capturedOpsForQueueThresholds = newCapture();
        final Capture<FlowRule> capturedReportRules = newCapture(CaptureType.ALL);
        final FlowRuleOperations expectedOpsForCollector = FlowRuleOperations.builder()
            .add(buildCollectorWatchlistRule(LEAF_DEVICE_ID))
            .build();

        // Queue threshold rules.
        // Queue 0 has some old rules which must be removed before adding the new ones.
        final List<FlowRule> queueThresholdRulesToRemove = queueReportFlows(
                LEAF_DEVICE_ID, 0, 0, (byte) 0);
        final FlowRuleOperations.Builder opsBuilder = FlowRuleOperations.builder();
        queueThresholdRulesToRemove.forEach(opsBuilder::remove);
        opsBuilder.newStage();
        // Add new entries for all queues.
        for (byte queueId = 0; queueId < MAX_QUEUES; queueId++) {
            // In the json config, the queue 0 and queue 7 uses a different queue latency
            // threshold config.
            if (queueId == 0) {
                queueReportFlows(LEAF_DEVICE_ID, 1888, 388, queueId)
                        .forEach(opsBuilder::add);
            } else if (queueId == 7) {
                // Queue 7 contains the "triggerNs" config only, the value of "resetNs"
                // will be half of "triggerNs".
                queueReportFlows(LEAF_DEVICE_ID, 500, 250, queueId)
                        .forEach(opsBuilder::add);
            } else {
                // The rest of the queues use the default queue latency threshold.
                queueReportFlows(LEAF_DEVICE_ID,
                    DEFAULT_QUEUE_REPORT_TRIGGER_LATENCY_THRESHOLD,
                    DEFAULT_QUEUE_REPORT_RESET_LATENCY_THRESHOLD, queueId).forEach(opsBuilder::add);
            }
        }
        final FlowRuleOperations expectedOpsForQueueThresholds = opsBuilder.build();

        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
            BMD_TYPE_INT_INGRESS_DROP, INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
            BMD_TYPE_EGRESS_MIRROR, INT_REPORT_TYPE_DROP, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
            BMD_TYPE_EGRESS_MIRROR, INT_REPORT_TYPE_FLOW, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
            BMD_TYPE_DEFLECTED, INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR, INT_REPORT_TYPE_QUEUE, MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildReportTableRule(LEAF_DEVICE_ID, false,
                                BMD_TYPE_EGRESS_MIRROR,
                                (short) (INT_REPORT_TYPE_QUEUE | INT_REPORT_TYPE_FLOW),
                                MIRROR_TYPE_INT_REPORT));
        expectRules.add(buildFilterConfigFlow(LEAF_DEVICE_ID));

        List<Capture<FlowRule>> captures = Lists.newArrayList();
        for (int i = 0; i < expectRules.size(); i++) {
            Capture<FlowRule> flowRuleCapture = newCapture();
            flowRuleService.applyFlowRules(capture(flowRuleCapture));
            captures.add(flowRuleCapture);
        }

        // Expected steps of method calls, captures, and results.
        reset(flowRuleService);
        final List<FlowEntry> existingFlowEntries = queueThresholdRulesToRemove.stream()
                .map(f -> buildFlowEntry(f))
                .collect(Collectors.toList());
        expect(flowRuleService.getFlowEntriesById(APP_ID)).andReturn(existingFlowEntries).times(3);
        flowRuleService.apply(capture(capturedOpsForCollector));
        flowRuleService.apply(capture(capturedOpsForSubnet));
        flowRuleService.apply(capture(capturedOpsForQueueThresholds));
        flowRuleService.applyFlowRules(capture(capturedReportRules));
        expectLastCall().times(expectRules.size());
        replay(flowRuleService);

        // Verify values.
        assertTrue(intProgrammable.setUpIntConfig(intConfig));
        assertFlowRuleOperationsEquals(expectedOpsForCollector, capturedOpsForCollector.getValue());
        assertFlowRuleOperationsEquals(EMPTY_FLOW_RULE_OPS, capturedOpsForSubnet.getValue());
        assertFlowRuleOperationsEquals(expectedOpsForQueueThresholds, capturedOpsForQueueThresholds.getValue());
        for (int i = 0; i < expectRules.size(); i++) {
            FlowRule expectRule = expectRules.get(i);
            FlowRule actualRule = capturedReportRules.getValues().get(i);
            assertTrue(expectRule.exactMatch(actualRule));
        }
        verify(flowRuleService);
    }

    @Test
    public void testUtilityMethods() {
        assertEquals(0xffffffffL, intProgrammable.getSuitableQmaskForLatencyChange(0));
        assertEquals(0xffffffffL, intProgrammable.getSuitableQmaskForLatencyChange(1));
        assertEquals(0xfffffffeL, intProgrammable.getSuitableQmaskForLatencyChange(2));
        assertEquals(0xffffff00L, intProgrammable.getSuitableQmaskForLatencyChange(256));
        assertEquals(0xffffff00L, intProgrammable.getSuitableQmaskForLatencyChange(300));
        assertEquals(0xffff0000L, intProgrammable.getSuitableQmaskForLatencyChange(65536));
        assertEquals(0xffff0000L, intProgrammable.getSuitableQmaskForLatencyChange(100000));
        assertEquals(0xf0000000L, intProgrammable.getSuitableQmaskForLatencyChange(1 << 28));
        assertEquals(0xf0000000L, intProgrammable.getSuitableQmaskForLatencyChange((1 << 28) + 10));
        assertEquals(0xc0000000L, intProgrammable.getSuitableQmaskForLatencyChange(1 << 30));
        assertEquals(0xc0000000L, intProgrammable.getSuitableQmaskForLatencyChange(0x40000000));
        assertEquals(0xc0000000L, intProgrammable.getSuitableQmaskForLatencyChange(0x7fffffff));

        // Illegal argument.
        try {
            intProgrammable.getSuitableQmaskForLatencyChange(-1);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    "Flow latency change value must equal or greater than zero.");
        }
    }

    @Test
    public void testCleanup() {
        Set<FlowEntry> intEntries = ImmutableSet.of(
                // Watchlist table entry
                buildFlowEntry(buildCollectorWatchlistRule(LEAF_DEVICE_ID)),
                // Report table entry
                buildFlowEntry(buildFilterConfigFlow(LEAF_DEVICE_ID)),
                buildFlowEntry(
                    buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_INT_INGRESS_DROP,
                                         INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID)),
                buildFlowEntry(
                    buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                         INT_REPORT_TYPE_DROP, MIRROR_TYPE_INT_REPORT)),
                buildFlowEntry(
                    buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                         INT_REPORT_TYPE_FLOW, MIRROR_TYPE_INT_REPORT)),
                buildFlowEntry(
                    buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_DEFLECTED,
                                         INT_REPORT_TYPE_DROP, MIRROR_TYPE_INVALID)),
                buildFlowEntry(
                    buildReportTableRule(LEAF_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                         INT_REPORT_TYPE_QUEUE, MIRROR_TYPE_INT_REPORT)),
                buildFlowEntry(
                    buildReportTableRule(SPINE_DEVICE_ID, false, BMD_TYPE_EGRESS_MIRROR,
                                         (short) (INT_REPORT_TYPE_QUEUE | INT_REPORT_TYPE_FLOW),
                                         MIRROR_TYPE_INT_REPORT))
        );
        Set<FlowEntry> randomEntries = buildRandomFlowEntries();
        Set<FlowEntry> entries = Sets.newHashSet(intEntries);
        entries.addAll(randomEntries);
        reset(flowRuleService);
        expect(flowRuleService.getFlowEntries(LEAF_DEVICE_ID))
                .andReturn(entries)
                .anyTimes();
        intEntries.forEach(e -> {
            flowRuleService.removeFlowRules(e);
            expectLastCall().once();
        });
        replay(flowRuleService);
        intProgrammable.cleanup();
        verify(flowRuleService);
    }

    /**
     * Test both getMatchRangesForTrigger and getMatchRangesForReset to ensure we get
     * the correct range values for the queue_latency_thresholds table.
     */
    @Test
    public void testGetMatchRanges() {
        // Test when threshold is less than 0xffff
        List<List<Range<Integer>>> ranges = intProgrammable.getMatchRangesForTrigger(100);
        // Range for trigger is [100, 0xffff]
        assertFalse(numberInRange(0, ranges));
        assertFalse(numberInRange(99, ranges));
        assertTrue(numberInRange(100, ranges));
        assertTrue(numberInRange(200, ranges));
        assertTrue(numberInRange(0x0000ffff, ranges));
        assertTrue(numberInRange(0x0001ffff, ranges));
        assertTrue(numberInRange(0xffffffffL, ranges));

        // Range for reset is [0, 100)
        ranges = intProgrammable.getMatchRangesForReset(100);
        assertTrue(numberInRange(0, ranges));
        assertTrue(numberInRange(99, ranges));
        assertFalse(numberInRange(100, ranges));
        assertFalse(numberInRange(200, ranges));
        assertFalse(numberInRange(0x0000ffff, ranges));
        assertFalse(numberInRange(0x0001ffff, ranges));
        assertFalse(numberInRange(0xffffffffL, ranges));

        // Test when threshold is bigger than 0xffff
        // Range for trigger is [0x0100ff00, 0xffffffff]
        ranges = intProgrammable.getMatchRangesForTrigger(0x0100ff00);
        assertFalse(numberInRange(0, ranges));
        assertFalse(numberInRange(0x0001ffff, ranges));
        assertFalse(numberInRange(0x0100feff, ranges));
        assertTrue(numberInRange(0x0100ff00, ranges));
        assertTrue(numberInRange(0x0ff00000, ranges));
        assertTrue(numberInRange(0xffffffffL, ranges));
        // Range for reset is [0, 0x0100ff00)
        ranges = intProgrammable.getMatchRangesForReset(0x0100ff00);
        assertTrue(numberInRange(0, ranges));
        assertTrue(numberInRange(0x0001ffff, ranges));
        assertTrue(numberInRange(0x0100feff, ranges));
        assertFalse(numberInRange(0x0100ff00, ranges));
        assertFalse(numberInRange(0x0ff00000, ranges));
        assertFalse(numberInRange(0xffffffffL, ranges));


        // Some edge cases:
        // Match from 0 to 0xffffffff - 1
        ranges = intProgrammable.getMatchRangesForReset(0xffffffffL);
        assertTrue(numberInRange(0x0001ffff, ranges));
        assertTrue(numberInRange(0x0100feff, ranges));
        assertTrue(numberInRange(0x0100ff00, ranges));
        assertTrue(numberInRange(0x0ff00000, ranges));
        assertTrue(numberInRange(0xfffffffeL, ranges));
        assertFalse(numberInRange(0xffffffffL, ranges));
        // Never reset
        ranges = intProgrammable.getMatchRangesForReset(0);
        assertEquals(ranges.size(), 0);
        // Always trigger
        ranges = intProgrammable.getMatchRangesForTrigger(0);
        assertTrue(numberInRange(0x0001ffff, ranges));
        assertTrue(numberInRange(0x0100feff, ranges));
        assertTrue(numberInRange(0x0100ff00, ranges));
        assertTrue(numberInRange(0x0ff00000, ranges));
        assertTrue(numberInRange(0xffffffffL, ranges));
        // Never trigger
        ranges = intProgrammable.getMatchRangesForTrigger(0xffffffffL);
        assertEquals(ranges.size(), 0);
    }

    private boolean numberInRange(long number, List<List<Range<Integer>>> ranges) {
        int upper = (int) (number >> 16);
        int lower = (int) (number & 0xffff);
        for (List<Range<Integer>> range: ranges) {
            Range<Integer> upperRange = range.get(0);
            Range<Integer> lowerRange = range.get(1);
            if (upperRange.contains(upper) && lowerRange.contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private PiAction buildReportAction(boolean setMpls, short reportType, short bmdType) {
        final PiActionParam srcIpParam = new PiActionParam(
                P4InfoConstants.SRC_IP, ROUTER_IP.toOctets());
        final PiActionParam monIpParam = new PiActionParam(
                P4InfoConstants.MON_IP,
                COLLECTOR_IP.toOctets());
        final PiActionParam monPortParam = new PiActionParam(
                P4InfoConstants.MON_PORT,
                COLLECTOR_PORT.toInt());
        final PiActionParam switchIdParam = new PiActionParam(
                P4InfoConstants.SWITCH_ID,
                NODE_SID_IPV4);
        final PiAction.Builder reportAction = PiAction.builder()
                .withParameter(srcIpParam)
                .withParameter(monIpParam)
                .withParameter(monPortParam)
                .withParameter(switchIdParam);
        if (setMpls) {
            reportAction.withParameter(new PiActionParam(
                    P4InfoConstants.MON_LABEL,
                    NODE_SID_IPV4
            ));
            if ((reportType & (INT_REPORT_TYPE_FLOW | INT_REPORT_TYPE_QUEUE)) != 0) {
                reportAction.withId(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_DO_LOCAL_REPORT_ENCAP_MPLS);
            } else {
                reportAction.withId(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_DO_DROP_REPORT_ENCAP_MPLS);
            }
        } else {
            if ((reportType & (INT_REPORT_TYPE_FLOW | INT_REPORT_TYPE_QUEUE)) != 0) {
                reportAction.withId(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_DO_LOCAL_REPORT_ENCAP);
            } else {
                reportAction.withId(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_DO_DROP_REPORT_ENCAP);
            }
        }
        return reportAction.build();
    }

    private FlowRule buildReportTableRule(
            DeviceId deviceId, boolean setMpls, short bmdType, short reportType, short mirrorType) {
        PiAction reportAction = buildReportAction(setMpls, reportType, bmdType);
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(reportAction)
                .build();
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(PiCriterion.builder()
                        .matchExact(P4InfoConstants.HDR_BMD_TYPE, bmdType)
                        .matchExact(P4InfoConstants.HDR_MIRROR_TYPE, mirrorType)
                        .matchExact(P4InfoConstants.HDR_INT_REPORT_TYPE, reportType)
                        .build())
                .build();
        return DefaultFlowRule.builder()
                .withSelector(selector)
                .withTreatment(treatment)
                .fromApp(APP_ID)
                .withPriority(DEFAULT_PRIORITY)
                .makePermanent()
                .forDevice(deviceId)
                .forTable(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_REPORT)
                .build();
    }

    private FlowRule buildFilterConfigFlow(DeviceId deviceId) {
        final PiActionParam hopLatencyMask = new PiActionParam(P4InfoConstants.HOP_LATENCY_MASK, DEFAULT_QMASK);
        final PiActionParam timestampMask = new PiActionParam(P4InfoConstants.TIMESTAMP_MASK, DEFAULT_TIMESTAMP_MASK);
        final PiAction quantizeAction =
                PiAction.builder()
                        .withId(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_SET_CONFIG)
                        .withParameter(hopLatencyMask)
                        .withParameter(timestampMask)
                        .build();
        final TrafficTreatment quantizeTreatment = DefaultTrafficTreatment.builder()
                .piTableAction(quantizeAction)
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId)
                .makePermanent()
                .withPriority(DEFAULT_PRIORITY)
                .withTreatment(quantizeTreatment)
                .fromApp(APP_ID)
                .forTable(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_CONFIG)
                .build();
    }

    private FlowRule buildWatchlistRule(IpPrefix ipPrefix, Criterion.Type criterionType) {
        // Flow rule that we expected.
        TrafficSelector.Builder expectedSelector = DefaultTrafficSelector.builder();
        PiCriterion.Builder expectedPiCriterion = PiCriterion.builder()
                .matchExact(P4InfoConstants.HDR_IPV4_VALID, 1);
        if (ipPrefix != null && criterionType == Criterion.Type.IPV4_DST) {
            expectedSelector.matchIPDst(ipPrefix);
        } else if (ipPrefix != null && criterionType == Criterion.Type.IPV4_SRC) {
            expectedSelector.matchIPSrc(ipPrefix);
        }
        expectedSelector.matchPi(expectedPiCriterion.build());
        PiAction expectedPiAction = PiAction.builder()
                .withId(P4InfoConstants.FABRIC_INGRESS_INT_WATCHLIST_MARK_TO_REPORT)
                .build();
        TrafficTreatment expectedTreatment = DefaultTrafficTreatment.builder()
                .piTableAction(expectedPiAction)
                .build();
        return DefaultFlowRule.builder()
                .forDevice(LEAF_DEVICE_ID)
                .withSelector(expectedSelector.build())
                .withTreatment(expectedTreatment)
                .fromApp(APP_ID)
                .withPriority(DEFAULT_PRIORITY)
                .forTable(P4InfoConstants.FABRIC_INGRESS_INT_WATCHLIST_WATCHLIST)
                .makePermanent()
                .build();
    }

    private FlowEntry buildFlowEntry(FlowRule flowRule) {
        return new DefaultFlowEntry(flowRule, FlowEntry.FlowEntryState.ADDED, 1, TimeUnit.SECONDS, 0, 0);
    }

    private Set<FlowEntry> buildRandomFlowEntries() {
        FlowRule rule1 = DefaultFlowRule.builder()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchTcpDst(TpPort.tpPort(8080))
                        .build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.P0)
                        .build())
                .makePermanent()
                .forTable(0)
                .withPriority(1)
                .forDevice(LEAF_DEVICE_ID)
                .withCookie(123)
                .build();
        FlowRule rule2 = DefaultFlowRule.builder()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchIPDst(IpPrefix.valueOf("0.0.0.0/0"))
                        .build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .setEthDst(MacAddress.valueOf("10:00:01:12:23:34"))
                        .setOutput(PortNumber.portNumber(10))
                        .build())
                .makePermanent()
                .forTable(0)
                .withPriority(1)
                .forDevice(LEAF_DEVICE_ID)
                .withCookie(456)
                .build();
        return ImmutableSet.of(
                buildFlowEntry(rule1),
                buildFlowEntry(rule2)
        );
    }

    private void testInit() {}

    private GroupBucket getCloneBucket(Long port) {
        if (intProgrammable.capabilities.isArchV1model()) {
            return createCloneGroupBucket(DefaultTrafficTreatment.builder()
                                                  .setOutput(PortNumber.portNumber(port))
                                                  .build());
        }
        return createCloneGroupBucket(DefaultTrafficTreatment.builder()
                                              .setOutput(PortNumber.portNumber(port))
                                              .truncate(INT_MIRROR_TRUNCATE_MAX_LEN)
                                              .build());
    }

    private FlowRule buildCollectorWatchlistRule(DeviceId deviceId) {

        final PiAction watchlistAction = PiAction.builder()
                .withId(P4InfoConstants.FABRIC_INGRESS_INT_WATCHLIST_NO_REPORT_COLLECTOR)
                .build();

        final TrafficTreatment watchlistTreatment = DefaultTrafficTreatment.builder()
                .piTableAction(watchlistAction)
                .build();

        final PiCriterion piCriterion = PiCriterion.builder()
                .matchExact(P4InfoConstants.HDR_IPV4_VALID, 1)
                .matchRange(P4InfoConstants.HDR_L4_DPORT, COLLECTOR_PORT.toInt(), COLLECTOR_PORT.toInt())
                .build();

        final TrafficSelector watchlistSelector =
                DefaultTrafficSelector.builder()
                        .matchIPDst(COLLECTOR_IP.toIpPrefix())
                        .matchIPProtocol(IPv4.PROTOCOL_UDP)
                        .matchPi(piCriterion)
                        .build();

        return DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(watchlistSelector)
                .withTreatment(watchlistTreatment)
                .withPriority(DEFAULT_PRIORITY + 10)
                .forTable(P4InfoConstants.FABRIC_INGRESS_INT_WATCHLIST_WATCHLIST)
                .fromApp(APP_ID)
                .makePermanent()
                .build();
    }

    private FlowRule buildQueueReportFlow(DeviceId deviceId, byte queueId, long[] upperRange,
            long[] lowerRange, PiActionId actionId) {
        final PiCriterion.Builder matchCriterionBuilder = PiCriterion.builder()
                .matchExact(P4InfoConstants.HDR_EGRESS_QID, queueId);
        if (doCareRangeMatch(upperRange[0], upperRange[1], 16)) {
            matchCriterionBuilder.matchRange(P4InfoConstants.HDR_HOP_LATENCY_UPPER, (int) upperRange[0],
                    (int) upperRange[1]);
        }
        if (doCareRangeMatch(lowerRange[0], lowerRange[1], 16)) {
            matchCriterionBuilder.matchRange(P4InfoConstants.HDR_HOP_LATENCY_LOWER, (int) lowerRange[0],
                    (int) lowerRange[1]);
        }
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(matchCriterionBuilder.build())
                .build();
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(PiAction.builder().withId(actionId).build())
                .build();
        return DefaultFlowRule.builder()
            .forDevice(deviceId)
            .forTable(P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_QUEUE_LATENCY_THRESHOLDS)
            .withSelector(selector)
            .withTreatment(treatment)
            .makePermanent()
            .fromApp(APP_ID)
            .withPriority(DEFAULT_PRIORITY)
            .build();
    }

    private List<FlowRule> queueReportFlows(DeviceId deviceId, long triggerThreshold, long resetThreshold) {
        final List<FlowRule> rules = Lists.newArrayList();
        for (byte queueId = 0; queueId < MAX_QUEUES; queueId++) {
            rules.addAll(queueReportFlows(deviceId, triggerThreshold, resetThreshold, queueId));
        }
        return rules;
    }

    private List<FlowRule> queueReportFlows(DeviceId deviceId, long triggerThreshold,
            long resetThreshold, byte queueId) {
        final List<FlowRule> rules = Lists.newArrayList();
        FlowRule queueReportFlow;
        if (triggerThreshold < 0xffff) {
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{0, 0},
                new long[]{triggerThreshold, 0xffff},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_CHECK_QUOTA);
            rules.add(queueReportFlow);
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{1, 0xffff},
                new long[]{0, 0xffff},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_CHECK_QUOTA);
            rules.add(queueReportFlow);
        } else if (triggerThreshold < 0xffffffffL) {
            int thresholdUpper = (int) (triggerThreshold >> 16);
            int thresholdLower = (int) (triggerThreshold & 0xffff);
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{thresholdUpper, thresholdUpper},
                new long[]{thresholdLower, 0xffff},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_CHECK_QUOTA);
            rules.add(queueReportFlow);
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{thresholdUpper + 1, 0xffff},
                new long[]{0, 0xffff},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_CHECK_QUOTA);
            rules.add(queueReportFlow);
        }

        if (resetThreshold > 0xffff) {
            int thresholdUpper = (int) (resetThreshold >> 16);
            int thresholdLower = (int) (resetThreshold & 0xffff);
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{0, thresholdUpper - 1},
                new long[]{0, 0xffff},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_RESET_QUOTA);
            rules.add(queueReportFlow);
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{thresholdUpper, thresholdUpper},
                new long[]{0, thresholdLower - 1},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_RESET_QUOTA);
            rules.add(queueReportFlow);

        } else if (resetThreshold > 0) {
            queueReportFlow = buildQueueReportFlow(deviceId, queueId,
                new long[]{0, 0},
                new long[]{0, resetThreshold - 1},
                P4InfoConstants.FABRIC_EGRESS_INT_EGRESS_RESET_QUOTA);
            rules.add(queueReportFlow);
        }

        return rules;
    }

    private void assertFlowRuleOperationsEquals(FlowRuleOperations ops1, FlowRuleOperations ops2) {
        // Compare stage by stage, op by op.
        assertEquals(ops1.stages().size(), ops2.stages().size());
        for (int i = 0; i < ops1.stages().size(); i++) {
            Set<FlowRuleOperation> ops1Stage = ops1.stages().get(i);
            Set<FlowRuleOperation> ops2Stage = ops2.stages().get(i);
            assertEquals(ops1Stage.size(), ops2Stage.size());

            // TODO: Find a better way to compare two sets of FlowRuleOperation.
            for (FlowRuleOperation op1 : ops1Stage) {
                boolean found = false;
                for (FlowRuleOperation op2 : ops2Stage) {
                    if (op1.type() == op2.type() && op1.rule().exactMatch(op2.rule())) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found);
            }
        }
    }
}
