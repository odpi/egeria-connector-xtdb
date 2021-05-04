/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EnumPropertyValue;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the mappings of various property values.
 */
public class EnumPropertyValueMappingTest {

    @Test
    void testValid() {

        Integer value = 5;

        EnumPropertyValue epv = new EnumPropertyValue();
        epv.setSymbolicName("TEST");
        epv.setOrdinal(value);
        epv.setDescription("test");

        Integer comparison = EnumPropertyValueMapping.getEnumPropertyValueForComparison(epv);
        assertNotNull(comparison);
        assertEquals(comparison, value, "Enum's ordinal values are expected to be identical after conversion.");

    }

    @Test
    void testEmpty() {
        EnumPropertyValue epv = new EnumPropertyValue();
        Integer comparison = EnumPropertyValueMapping.getEnumPropertyValueForComparison(epv);
        assertEquals(comparison, Integer.valueOf(99), "Empty enum ordinal value is expected to default to 99.");
    }

}
