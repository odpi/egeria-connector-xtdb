/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.migration;

import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Test the migrator utility.
 */
public class MigratorTest {

    private static final String config = "{\"xtdb/index-store\":{\"kv-store\":{\"xtdb/module\":\"xtdb.mem-kv/->kv-store\"}},\"xtdb/document-store\":{\"kv-store\":{\"xtdb/module\":\"xtdb.mem-kv/->kv-store\"}},\"xtdb/tx-log\":{\"kv-store\":{\"xtdb/module\":\"xtdb.mem-kv/->kv-store\"}}}";

    @Test
    void testMain() {
        try {

            File tmp = File.createTempFile("extdb", ".json");
            FileWriter fw = new FileWriter(tmp);
            fw.write(config);
            fw.flush();
            fw.close();
            List<String> args = new ArrayList<>();
            args.add(tmp.getCanonicalPath());
            args.add("5");
            Migrator.main(args.toArray(new String[2]));

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
