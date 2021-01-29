/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.auditlog;

import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageDefinition;
import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageSet;

/**
 * The CruxOMRSErrorCode is used to define first failure data capture (FFDC) for errors that occur when working with
 * Crux as an OMRS Metadata Repository.  It is used in conjunction with both Checked and Runtime (unchecked) exceptions.
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
public enum CruxOMRSErrorCode implements ExceptionMessageSet {

    ENTITY_HOME_COLLECTION_REFERENCE(400, "OMRS-CRUX-REPOSITORY-400-001",
            "Attempt to save entity with GUID {0} as a reference copy in its home collection in repository {1}",
            "The system is unable to perform the request because it is not possible to save reference copies of instances homed in this same collection.",
            "Correct the caller's code and retry the request."),
    METADATA_COLLECTION_CONFLICT(400, "OMRS-CRUX-REPOSITORY-400-002",
            "There is an already an instance with GUID {0} from a different metadata collection, cannot create instance with same GUID in repository {1}",
            "The system is unable to perform the request because there is already an instance with the same GUID.",
            "Correct the caller's code and retry the request."),
    ENTITY_PROXY_ONLY(400, "OMRS-CRUX-REPOSITORY-400-003",
            "The attempt to retrieve an entity with GUID {0} found only an entity proxy in repository {1}",
            "The system was unable to perform the entity retrieval.",
            "Correct the caller's code to request an entity and retry the request."),
    INVALID_CLASSIFICATION_FOR_ENTITY(400, "OMRS-CRUX-REPOSITORY-400-004",
            "Open metadata repository {0} is unable to assign a classification of type {1} to an entity of type {2} as the classification type is not valid for this type of entity",
            "The system is not able to classify an entity since the ClassificationDef for the classification does not list the entity type, or one of its super-types.",
            "Update the ClassificationDef to include the entity's type and rerun the request. Alternatively use a different classification."),
    UNKNOWN_RUNTIME_ERROR(500, "OMRS-CRUX-REPOSITORY-500-001 ",
            "An unknown runtime error occurred, and the system is unable to proceed with its processing",
            "The system was unable to complete processing of a given action due to a connectivity-related problem.",
            "Check the system logs and diagnose or report the problem."),
    REST_CLIENT_FAILURE(500, "OMRS-CRUX-REPOSITORY-500-002 ",
            "The Crux API client was not successfully initialized to \"{0}\"",
            "The system was unable to login to or access the Crux repository via REST API.",
            "Check your authorization details are accurate, the Crux environment started, and is network-accessible."),
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
    CruxOMRSErrorCode(int newHTTPErrorCode, String newErrorMessageId, String newErrorMessage, String newSystemAction, String newUserAction) {
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
