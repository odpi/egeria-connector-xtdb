/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import org.odpi.egeria.connectors.juxt.xtdb.cache.TypeDefCache;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.ClassificationMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.Constants;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.XtdbDocument;
import xtdb.api.tx.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Base class that all transaction functions should implement.
 */
public abstract class AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(AbstractTransactionFunction.class);

    /**
     * Create the let statements necessary to capture the valid time that should be used when
     * submitting the transaction.
     * @param docVar the name of the variable containing the document map to check for valid time details
     * @return String snippet of Clojure that can be embedded within a 'let'
     */
    protected static String getTxnTimeCalculation(String docVar) {
        StringBuilder sb = new StringBuilder();
        sb.append(" txt-classification (").append(Keywords.LAST_CLASSIFICATION_CHANGE).append(" ").append(docVar).append(")");
        sb.append(" txt-update (").append(Keywords.UPDATE_TIME).append(" ").append(docVar).append(")");
        sb.append(" txt-tmp (if (< 0 (compare txt-classification txt-update)) txt-classification txt-update)");
        sb.append(" txt (if (some? txt-tmp) txt-tmp ").append("(").append(Keywords.CREATE_TIME).append(" ").append(docVar).append("))");
        return sb.toString();
    }

    /**
     * Create the transaction function within XTDB.
     * @param tx transaction through which to create the function
     * @param name of the transaction function
     * @param fn the logic of the transaction function
     */
    protected static void createTransactionFunction(Transaction.Builder tx,
                                                    Keyword name,
                                                    String fn) {
        log.debug("Creating transaction function: {}", fn);
        XtdbDocument function = XtdbDocument.createFunction(name, fn);
        tx.put(function);
    }

    /**
     * Changes the control information to reflect an update in an instance.
     *
     * @param userId   user making the change
     * @param instance instance to update
     * @return IPersistentMap with the updates applied
     */
    protected static IPersistentMap incrementVersion(String userId,
                                                     IPersistentMap instance) {
        return incrementVersion(userId, instance, null);
    }

    /**
     * Changes the control information to reflect an update in an instance.
     *
     * @param userId   user making the change
     * @param instance instance to update
     * @param classificationName of the classification being incremented (or null if incrementing the base instance)
     * @return IPersistentMap with the updates applied
     */
    @SuppressWarnings("unchecked")
    protected static IPersistentMap incrementVersion(String userId,
                                                     IPersistentMap instance,
                                                     String classificationName) {

        Keyword VERSION;
        Keyword MAINTAINED_BY;
        Keyword UPDATED_BY;
        Keyword UPDATE_TIME;
        if (classificationName != null) {
            String namespace = ClassificationMapping.getNamespaceForClassification(classificationName);
            VERSION = Keyword.intern(namespace, Keywords.VERSION.getName());
            MAINTAINED_BY = Keyword.intern(namespace, Keywords.MAINTAINED_BY.getName());
            UPDATED_BY = Keyword.intern(namespace, Keywords.UPDATED_BY.getName());
            UPDATE_TIME = Keyword.intern(namespace, Keywords.UPDATE_TIME.getName());
        } else {
            VERSION = Keywords.VERSION;
            MAINTAINED_BY = Keywords.MAINTAINED_BY;
            UPDATED_BY = Keywords.UPDATED_BY;
            UPDATE_TIME = Keywords.UPDATE_TIME;
        }

        Long currentVersion = (Long) instance.valAt(VERSION);
        List<String> maintainers = (List<String>) instance.valAt(MAINTAINED_BY);

        IPersistentMap modified = instance
                .assoc(UPDATED_BY, userId)
                .assoc(UPDATE_TIME, new Date())
                .assoc(VERSION, currentVersion + 1);

        if (maintainers == null) {
            maintainers = new ArrayList<>();
        }
        if (!maintainers.contains(userId)) {
            maintainers.add(userId);
            modified = modified.assoc(MAINTAINED_BY, PersistentVector.create(maintainers));
        }

        return modified;

    }

    /**
     * Retrieve the type definition GUID from the provided metadata instance.
     * @param instance of metadata
     * @return String unique identifier of the type definition for the metadata instance
     */
    public static String getTypeDefGUID(IPersistentMap instance) {
        return (String) ((IPersistentVector) instance.valAt(Keywords.TYPE_DEF_GUIDS)).nth(0);
    }

    /**
     * Retrieve the type definition for the provided metadata instance.
     * @param instance of metadata
     * @return TypeDef that defines the metadata instance
     */
    public static TypeDef getTypeDefForInstance(IPersistentMap instance) {
        return TypeDefCache.getTypeDef(getTypeDefGUID(instance));
    }

    /**
     * Retrieve the instance GUID from the provided metadata instance.
     * @param instance of metadata
     * @return String unique identifier of the metadata instance
     */
    public static String getGUID(IPersistentMap instance) {
        return (String) instance.valAt(Constants.XTDB_PK);
    }

    /**
     * Retrieve the metadataCollectionId from the provided metadata instance.
     * @param instance of metadata
     * @return String unique identifier of the metadata instance's metadataCollectionId
     */
    public static String getMetadataCollectionId(IPersistentMap instance) {
        return (String) instance.valAt(Keywords.METADATA_COLLECTION_ID);
    }

    /**
     * Retrieve the instanceProvenanceType from the provided metadata instance.
     * @param instance of metadata
     * @return Integer unique ordinal of the metadata instance's instanceProvenanceType
     */
    public static Integer getInstanceProvenanceType(IPersistentMap instance) {
        return (Integer) instance.valAt(Keywords.INSTANCE_PROVENANCE_TYPE);
    }

}
