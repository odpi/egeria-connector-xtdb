/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.migration.model;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import xtdb.api.XtdbDocument;
import xtdb.api.IXtdb;
import xtdb.api.tx.Transaction;
import org.odpi.egeria.connectors.juxt.xtdb.model.PersistenceLayer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Date;

import static org.odpi.egeria.connectors.juxt.xtdb.migration.model.UpgradeInitialTo2.SUPERTYPE_DEF_GUIDS;
import static org.odpi.egeria.connectors.juxt.xtdb.migration.model.UpgradeInitialTo2.TYPE_DEF_GUID;
import static org.testng.Assert.*;

/**
 * Test the application of the upgrade.
 */
public class UpgradeTest {

    private static final IXtdb embedded = IXtdb.startNode();

    @BeforeClass
    void setup() {
        // Start off by setting up a back-level repository, and injecting some back-level metadata
        createInitialMetadata();
    }

    @Test(groups = { "one" })
    void testMigration1() {
        try {

            UpgradeInitialTo2 upgrade1 = new UpgradeInitialTo2(embedded, 1);
            assertEquals(upgrade1.getFromVersion(), -1L, "Expected to upgrade from version -1.");
            assertEquals(upgrade1.getToVersion(), 2L, "Expected to upgrade to version 2.");
            assertEquals(upgrade1.getBatchSize(), 1, "Expected batch size to match what was set explicitly.");
            upgrade1.migrate();

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test(groups = { "two" }, dependsOnGroups = { "one" })
    void testMigration2() {
        try {

            Upgrade2To3 upgrade2 = new Upgrade2To3(embedded, 1);
            assertEquals(upgrade2.getFromVersion(), 2L, "Expected to upgrade from version 2.");
            assertEquals(upgrade2.getToVersion(), 3L, "Expected to upgrade to version 3.");
            assertEquals(upgrade2.getBatchSize(), 1, "Expected batch size to match what was set explicitly.");
            upgrade2.migrate();
            assertTrue(PersistenceLayer.isLatestVersion(embedded), "Expected persistence to be at the latest version after migration.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    private void createInitialMetadata() {
        Transaction.Builder builder = Transaction.builder();
        builder.put(getEntityCreate(e1));
        builder.put(getEntityCreate(e2));
        builder.put(getRelationshipCreate(r1));
        embedded.awaitTx(embedded.submitTx(builder.build()), null);
    }

    private static final String metadataCollectionId = "d4882da8-5ef7-4a93-92b9-429c387b6d56";
    private static final String e1 = "e_290229e4-a0aa-4e36-b1ec-dcd60a7f233a";
    private static final String e2 = "e_term@abcdefgh-1234-ijkl-5678-mnopqrstuvwx:6662c0f2.e1b1ec6c.svu583pvk.3sr7b7n.mq748u.ru37pccq07437ncqvhvjs";
    private static final String r1 = "r_c4b46ea3-e429-4496-95c7-ad216ca8e64f";

    private XtdbDocument getEntityCreate(String guid) {
        XtdbDocument.Builder builder = XtdbDocument.builder(guid);
        builder.put("createdBy", "admin");
        builder.put("createTime", new Date());
        builder.put("type.category", 6);
        builder.put("headerVersion", 1);
        builder.put("e/proxy", false);
        builder.put("version", 1);
        builder.put("currentStatus", 15);
        builder.put("metadataCollectionName", "xtdb");
        builder.put("instanceProvenanceType", 1);
        builder.put("metadataCollectionId", metadataCollectionId);
        builder.put(TYPE_DEF_GUID, "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a");
        builder.put(SUPERTYPE_DEF_GUIDS, PersistentVector.create("a32316b8-dc8c-48c5-b12b-71c1b2a080bf", "4e7761e8-3969-4627-8f40-bfe3cde85a1d"));
        IPersistentMap map = PersistentHashMap.EMPTY;
        map = map.assoc(Keyword.intern("json"), "unused");
        builder.put("type", map);
        return builder.build();
    }

    private XtdbDocument getRelationshipCreate(String guid) {
        XtdbDocument.Builder builder = XtdbDocument.builder(guid);
        builder.put("createdBy", "admin");
        builder.put("createTime", new Date());
        builder.put("type.category", 8);
        builder.put("headerVersion", 1);
        builder.put("version", 1);
        builder.put("currentStatus", 15);
        builder.put("metadataCollectionName", "xtdb");
        builder.put("instanceProvenanceType", 1);
        builder.put("metadataCollectionId", metadataCollectionId);
        builder.put("entityOneProxy", e1);
        builder.put("entityTwoProxy", e2);
        builder.put(TYPE_DEF_GUID, "74f4094d-dba2-4ad9-874e-d422b69947e2");
        IPersistentMap map = PersistentHashMap.EMPTY;
        map = map.assoc(Keyword.intern("json"), "unused");
        builder.put("type", map);
        return builder.build();
    }

}
