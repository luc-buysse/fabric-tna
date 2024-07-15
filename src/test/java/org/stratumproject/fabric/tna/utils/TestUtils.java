// Copyright 2021-present Open Networking Foundation
// SPDX-License-Identifier: Apache-2.0

package org.stratumproject.fabric.tna.utils;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import org.onosproject.segmentrouting.config.SegmentRoutingDeviceConfig;
import org.stratumproject.fabric.tna.inbandtelemetry.IntReportConfig;
import org.stratumproject.fabric.tna.slicing.api.SlicingConfig;
import org.stratumproject.fabric.tna.INTDeviceConfig;

import static org.junit.Assert.fail;

/**
 * Test utilities.
 */
public final class TestUtils {
    private static final String INT_REPORT_CONFIG_KEY = "report";
    private static final String INT_CONFIG_KEY = "inbandtelemetry";
    private static final String SLICING_CONFIG_KEY = "slicing";

    private TestUtils() { }

    public static INTDeviceConfig getINTConfig(DeviceId deviceId, String filename)  {
        INTDeviceConfig intCfg = new INTDeviceConfig();
        InputStream jsonStream = TestUtils.class.getResourceAsStream(filename);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(jsonStream);
            intCfg.init(deviceId, INT_CONFIG_KEY, jsonNode, mapper, config -> { });
        } catch (IOException e) {
            fail("Got error when reading file " + filename + " : " + e.getMessage());
        }

        return intCfg;
    }

    public static IntReportConfig getIntReportConfig(ApplicationId appId, String filename) {
        IntReportConfig config = new IntReportConfig();
        InputStream jsonStream = TestUtils.class.getResourceAsStream(filename);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(jsonStream);
            config.init(appId, INT_REPORT_CONFIG_KEY, jsonNode, mapper, c -> { });
        } catch (Exception e) {
            fail("Got error when reading file " + filename + " : " + e.getMessage());
        }
        return config;
    }

    public static SlicingConfig getSlicingConfig(ApplicationId appId, String filename) {
        SlicingConfig config = new SlicingConfig();
        InputStream jsonStream = TestUtils.class.getResourceAsStream(filename);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(jsonStream);
            config.init(appId, SLICING_CONFIG_KEY, jsonNode, mapper, c -> { });
        } catch (Exception e) {
            fail("Got error when reading file " + filename + " : " + e.getMessage());
        }
        return config;
    }
}
