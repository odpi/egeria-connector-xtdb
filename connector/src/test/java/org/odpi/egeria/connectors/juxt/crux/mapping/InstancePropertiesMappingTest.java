/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import java.util.*;

import static org.testng.Assert.*;

/**
 * Test the mappings of multiple property values.
 */
public class InstancePropertiesMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    private static final String docId = InstancePropertiesMappingTest.class.getName();

    @Test
    void testValid() {
        try {

            String propertyName1 = "qualifiedName";
            String propertyValue1 = "a-qualified-name";
            String propertyName2 = "additionalProperties";
            Map<String, Object> propertyValue2 = new HashMap<>();
            propertyValue2.put("one", "a");
            propertyValue2.put("two", "b");
            String namespace = EntityDetailMapping.ENTITY_PROPERTIES_NS;

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            InstanceProperties properties = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    propertyName1,
                    propertyValue1,
                    this.getClass().getName());

            properties = helper.addMapPropertyToInstance(MockConnection.SOURCE_NAME,
                    properties,
                    propertyName2,
                    propertyValue2,
                    this.getClass().getName());

            CruxDocument.Builder builder = CruxDocument.builder(docId);
            TypeDef typeDef = helper.getTypeDefByName(MockConnection.SOURCE_NAME, "Referenceable");
            InstanceType type = helper.getNewInstanceType(MockConnection.SOURCE_NAME, typeDef);
            InstancePropertiesMapping.addToDoc(connector,
                    builder,
                    type,
                    properties,
                    namespace);

            InstanceProperties retrieved = InstancePropertiesMapping.getFromDoc(type,
                    builder.build(),
                    namespace);

            assertEquals(retrieved, properties, "Stored instance properties are expected to be identical when retrieved back again.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testEmpty() {
        try {

            String namespace = EntityDetailMapping.ENTITY_PROPERTIES_NS;

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            InstanceProperties properties = new InstanceProperties();

            CruxDocument.Builder builder = CruxDocument.builder(docId);
            TypeDef typeDef = helper.getTypeDefByName(MockConnection.SOURCE_NAME, "Referenceable");
            InstanceType type = helper.getNewInstanceType(MockConnection.SOURCE_NAME, typeDef);
            InstancePropertiesMapping.addToDoc(connector,
                    builder,
                    type,
                    properties,
                    namespace);

            InstanceProperties retrieved = InstancePropertiesMapping.getFromDoc(type,
                    builder.build(),
                    namespace);

            assertEquals(retrieved, properties, "Empty instance properties are expected to be empty when retrieved back again.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testNull() {
        try {

            String namespace = EntityDetailMapping.ENTITY_PROPERTIES_NS;

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();

            CruxDocument.Builder builder = CruxDocument.builder(docId);
            TypeDef typeDef = helper.getTypeDefByName(MockConnection.SOURCE_NAME, "Referenceable");
            InstanceType type = helper.getNewInstanceType(MockConnection.SOURCE_NAME, typeDef);
            InstancePropertiesMapping.addToDoc(connector,
                    builder,
                    type,
                    null,
                    namespace);

            InstanceProperties retrieved = InstancePropertiesMapping.getFromDoc(type,
                    builder.build(),
                    namespace);

            assertEquals(retrieved, new InstanceProperties(), "Null instance properties are expected to be empty when retrieved back again.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
