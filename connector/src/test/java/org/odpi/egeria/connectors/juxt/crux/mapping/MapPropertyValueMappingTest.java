/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.MapPropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Test the mappings of various property values.
 */
public class MapPropertyValueMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testStrings() {

        Map<String, Object> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        OMRSRepositoryHelper helper = connector.getRepositoryHelper();
        MapPropertyValue mpv = new MapPropertyValue();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Integer value = (Integer) entry.getValue();
            InstanceProperties properties = helper.addIntPropertyToInstance(this.getClass().getName(),
                    null,
                    "property",
                    value,
                    this.getClass().getName());
            InstancePropertyValue ipv = properties.getPropertyValue("property");
            mpv.setMapValue(key, ipv);
        }
        Map<String, Object> comparison = MapPropertyValueMapping.getMapPropertyValueForComparison(mpv);
        assertEquals(comparison, map, "Map property values are expected to be identical after conversion.");

    }

    @Test
    void testEmpty() {
        MapPropertyValue mpv = new MapPropertyValue();
        Map<String, Object> comparison = MapPropertyValueMapping.getMapPropertyValueForComparison(mpv);
        assertNull(comparison, "Empty map property values are expected to be null after conversion.");
    }

}
