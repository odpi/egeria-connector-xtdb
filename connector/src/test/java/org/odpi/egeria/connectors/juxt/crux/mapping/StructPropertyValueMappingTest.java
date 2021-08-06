/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.StructPropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Test the mappings of various property values.
 */
public class StructPropertyValueMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testStrings() {

        Map<String, Object> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        OMRSRepositoryHelper helper = connector.getRepositoryHelper();
        StructPropertyValue spv = new StructPropertyValue();
        InstanceProperties properties = new InstanceProperties();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Integer value = (Integer) entry.getValue();
            properties = helper.addIntPropertyToInstance(this.getClass().getName(),
                    properties,
                    key,
                    value,
                    this.getClass().getName());
        }
        spv.setAttributes(properties);
        Map<String, Object> comparison = StructPropertyValueMapping.getStructPropertyValueForComparison(connector, spv);
        assertEquals(comparison, map, "Struct property values are expected to be identical after conversion.");

    }

    @Test
    void testEmpty() {
        StructPropertyValue spv = new StructPropertyValue();
        Map<String, Object> comparison = StructPropertyValueMapping.getStructPropertyValueForComparison(connector, spv);
        assertNull(comparison, "Empty struct property value is expected to be null after conversion.");
    }

}
