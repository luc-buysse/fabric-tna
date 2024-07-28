/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.stratumproject.fabric.tna;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration object for Inband Network Telemetry
 */
public class INTDeviceConfig extends Config<DeviceId> {
    private static final String SID = "sid";
    private static final String IP = "ip";

    @Override
    public boolean isValid() {
        return hasOnlyFields(SID, IP)
            && sid() != -1
            && ip() != null;
    }

    /**
     * Gets the address of the collector
     *
     * @return The address of the collector
     */
    public int sid() {
        return get(SID, -1);
    }

    /**
     * Gets the IPv4 address
     *
     * @return IPv4 address. Or null if not configured.
     */
    public Ip4Address ip() {
        String ip = get(IP, null);
        return ip != null ? Ip4Address.valueOf(ip) : null;
    }

    /**
     * Sets the address of the collector.
     *
     * @param sid switch identifier.
     * @return the config of the device INT.
     */
    public INTDeviceConfig setSid(int sid) {
        return (INTDeviceConfig) setOrClear(SID, sid);
    }

    /**
     * Sets the IPv4 used for INT.
     *
     * @param ip IPv4 address used for INT.
     * @return the config of the device INT.
     */
    public INTDeviceConfig setIp(String ip) {
        return (INTDeviceConfig) setOrClear(IP, ip);
    }
}
