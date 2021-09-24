/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.auditlog;

import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageDefinition;
import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageSet;

/**
 * The XtdbOMRSErrorCode is used to define first failure data capture (FFDC) for errors that occur when working with
 * XTDB as an OMRS Metadata Repository.  It is used in conjunction with both Checked and Runtime (unchecked) exceptions.
 * <br><br>
 * The 5 fields in the enum are:
 * <ul>
 *   <li>HTTP Error Code - for translating between REST and JAVA - Typically the numbers used are:</li>
 *   <li><ul>
 *     <li>500 - internal error</li>
 *     <li>400 - invalid parameters</li>
 *     <li>404 - not found</li>
 *   </ul></li>
 *   <li>Error Message Id - to uniquely identify the message</li>
 *   <li>Error Message Text - includes placeholder to allow additional values to be captured</li>
 *   <li>SystemAction - describes the result of the error</li>
 *   <li>UserAction - describes how a AssetConsumerInterface should correct the error</li>
 * </ul>
 */
public enum XtdbOMRSErrorCode implements ExceptionMessageSet {

    ENTITY_HOME_COLLECTION_REFERENCE(400, "OMRS-XTDB-REPOSITORY-400-001",
            "Attempt to save entity with GUID {0} as a reference copy in its home collection in repository {1}",
            "The system is unable to perform the request because it is not possible to save reference copies of instances homed in this same collection.",
            "Correct the caller's code and retry the request."),
    METADATA_COLLECTION_CONFLICT(400, "OMRS-XTDB-REPOSITORY-400-002",
            "There is an already an instance with GUID {0} from a different metadata collection, cannot create instance with same GUID in repository {1}",
            "The system is unable to perform the request because there is already an instance with the same GUID.",
            "Correct the caller's code and retry the request."),
    ENTITY_PROXY_ONLY(400, "OMRS-XTDB-REPOSITORY-400-003",
            "The attempt to retrieve an entity with GUID {0} found only an entity proxy in repository {1}",
            "The system was unable to perform the entity retrieval.",
            "Correct the caller's code to request an entity and retry the request."),
    INVALID_CLASSIFICATION_FOR_ENTITY(400, "OMRS-XTDB-REPOSITORY-400-004",
            "Open metadata repository {0} is unable to assign a classification of type {1} to an entity of type {2} as the classification type is not valid for this type of entity",
            "The system is not able to classify an entity since the ClassificationDef for the classification does not list the entity type, or one of its super-types.",
            "Update the ClassificationDef to include the entity's type and rerun the request. Alternatively use a different classification."),
    CANNOT_READ_CONFIGURATION(400, "OMRS-XTDB-REPOSITORY-400-005",
            "Unable to read the configuration for the XTDB repository at {0}",
            "The local server was unable to parse the configuration for the XTDB server.",
            "Investigate the logs for additional information, and correct the configurationProperties sent to the connector."),
    QUERY_TIMEOUT(400, "OMRS-XTDB-REPOSITORY-400-006",
            "The requested query against the XTDB repository at {0} timed out",
            "The local server was unable to respond to the query in the time permitted (default 30s).",
            "Investigate the logs for additional information, and either attempt a more restrictive query or increase the timeout through the XTDB configuration."),
    NULL_REQUIRED_PROPERTY(400, "OMRS-XTDB-REPOSITORY-400-007",
            "A null value has been passed for the required {0} property for instance {1}",
            "The system is unable to process the metadata instance request.",
            "The property value is supplied by the caller to the API. This call needs to be corrected before the server can operate correctly."),
    INSTANCE_ALREADY_DELETED(400, "OMRS-XTDB-REPOSITORY-400-008",
            "A request has been made to delete an instance {0} that is already deleted",
            "The system is unable to perform the request because the instance is in the wrong state.",
            "Try a different request or a different instance."),
    INSTANCE_NOT_DELETED(400, "OMRS-XTDB-REPOSITORY-400-009",
            "A request has been made to purge an instance {0} that is not deleted",
            "The system is unable to perform the request since the instance is in the wrong state.",
            "Try again with a different request or specify a different instance."),
    ENTITY_NOT_KNOWN(404, "OMRS-XTDB-REPOSITORY-404-001",
            "The repository does not contain any entity with the GUID {0}",
            "The XTDB repository is unable to find any entity with the provided GUID.",
            "Correct the caller's code to ensure the entity being requested is one contained in the local server."),
    RELATIONSHIP_NOT_KNOWN(404, "OMRS-XTDB-REPOSITORY-404-002",
            "The repository does not contain any relationship with the GUID {0}",
            "The XTDB repository is unable to find any relationship with the provided GUID.",
            "Correct the caller's code to ensure the relationship being requested is one contained in the local server."),
    UNKNOWN_RUNTIME_ERROR(500, "OMRS-XTDB-REPOSITORY-500-001 ",
            "An unknown runtime error occurred, and the system is unable to proceed with its processing",
            "The system was unable to complete processing of a given action due to a connectivity-related problem.",
            "Check the system logs and diagnose or report the problem."),
    CANNOT_CLOSE_RESOURCE(500, "OMRS-XTDB-REPOSITORY-500-002 ",
            "Unable to close an open XTDB resource",
            "The system was unable to close an open resource, typically used for multiple queries against the same database snapshot.",
            "This could result in memory leaks: check the system logs and diagnose or report the problem."),
    FAILED_DISCONNECT(500, "OMRS-XTDB-REPOSITORY-500-002",
            "The XTDB connector failed to disconnect",
            "The local server failed to disconnect from XTDB.",
            "Investigate the logs for additional information and cleanup XTDB if needed."),
    PERSISTENCE_LAYER_MISMATCH(500, "OMRS-XTDB-REPOSITORY-500-003",
            "The XTDB connector detected a different version of the persistence layer ({0}) than is supported by this release ({1})",
            "The local server cannot be started against a different version of the persistence layer.",
            "Run the migration utilities to upgrade your pre-existing metadata before running this version of the connector."),
    REGEX_NOT_IMPLEMENTED(501, "OMRS-XTDB-REPOSITORY-501-001 ",
            "Repository {0} is not able to support the regular expression \"{1}\"",
            "This repository has a fixed subset of regular expressions it can support.",
            "No action required, this is a limitation of the technology. To search using such regular expressions, the metadata of interest" +
                    " must be synchronized to a cohort repository that can support such regular expressions."),
    ;

    private final ExceptionMessageDefinition messageDefinition;

    /**
     * The constructor for iGCOMRSErrorCode expects to be passed one of the enumeration rows defined in
     * IGCOMRSErrorCode above.   For example:
     *
     *     IGCOMRSErrorCode   errorCode = IGCOMRSErrorCode.NULL_INSTANCE;
     *
     * This will expand out to the 5 parameters shown below.
     *
     * @param newHTTPErrorCode - error code to use over REST calls
     * @param newErrorMessageId - unique Id for the message
     * @param newErrorMessage - text for the message
     * @param newSystemAction - description of the action taken by the system when the error condition happened
     * @param newUserAction - instructions for resolving the error
     */
    XtdbOMRSErrorCode(int newHTTPErrorCode, String newErrorMessageId, String newErrorMessage, String newSystemAction, String newUserAction) {
        this.messageDefinition = new ExceptionMessageDefinition(newHTTPErrorCode,
                newErrorMessageId,
                newErrorMessage,
                newSystemAction,
                newUserAction);
    }

    /**
     * Retrieve a message definition object for an exception.  This method is used when there are no message inserts.
     *
     * @return message definition object.
     */
    @Override
    public ExceptionMessageDefinition getMessageDefinition() {
        return messageDefinition;
    }


    /**
     * Retrieve a message definition object for an exception.  This method is used when there are values to be inserted into the message.
     *
     * @param params array of parameters (all strings).  They are inserted into the message according to the numbering in the message text.
     * @return message definition object.
     */
    @Override
    public ExceptionMessageDefinition getMessageDefinition(String... params) {
        messageDefinition.setMessageParameters(params);
        return messageDefinition;
    }

    /**
     * toString() JSON-style
     *
     * @return string description
     */
    @Override
    public String toString() {
        return "IGCOMRSErrorCode{" +
                "messageDefinition=" + messageDefinition +
                '}';
    }

}
