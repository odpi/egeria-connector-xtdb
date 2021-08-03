/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.auditlog;

import org.odpi.egeria.connectors.juxt.crux.mapping.Constants;
import org.odpi.openmetadata.frameworks.auditlog.messagesets.AuditLogMessageDefinition;
import org.odpi.openmetadata.frameworks.auditlog.messagesets.AuditLogMessageSet;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLogRecordSeverity;

/**
 * The CruxOMRSAuditCode is used to define the message content for the OMRS Audit Log.
 *
 * The 5 fields in the enum are:
 * <ul>
 *     <li>Log Message Id - to uniquely identify the message</li>
 *     <li>Severity - is this an event, decision, action, error or exception</li>
 *     <li>Log Message Text - includes placeholder to allow additional values to be captured</li>
 *     <li>Additional Information - further parameters and data relating to the audit message (optional)</li>
 *     <li>SystemAction - describes the result of the situation</li>
 *     <li>UserAction - describes how a user should correct the situation</li>
 * </ul>
 */
public enum CruxOMRSAuditCode implements AuditLogMessageSet {

    REPOSITORY_NODE_STARTING("OMRS-CRUX-REPOSITORY-0001",
            OMRSAuditLogRecordSeverity.STARTUP,
            "A request to start a new Crux repository node has been received.",
            "The local server is creating a new local node for the Crux repository.",
            "This repository will be used as the local repository for this server. Depending on the configuration " +
                    "of your Crux repository, this may be the only Crux node (standalone) or one of many (for example, in " +
                    "high-availability configurations). All nodes will display this initial message at startup, whether using " +
                    "an existing persistent store or instantiating a new one. " +
                    "You should see one of two subsequent messages: OMRS-CRUX-REPOSITORY-0002 indicates that the " +
                    "node is starting with a configuration provided as part of the administration of the instance, or OMRS-CRUX-REPOSITORY-0003 " +
                    "indicating that the node is starting up with a default, in-memory-only configuration."),
    REPOSITORY_NODE_STARTING_WITH_CONFIG("OMRS-CRUX-REPOSITORY-0002",
            OMRSAuditLogRecordSeverity.STARTUP,
            "A new Crux repository node is starting with a persistent store.",
            "The local server is starting a new Crux node based on the configuration provided through the configurationProperties of the local repository connection.",
            "All nodes that are started with a provided configuration that can be interpreted will display this " +
                    "message. You should see a subsequent OMRS-CRUX-REPOSITORY-0004 indicating that the " +
                    "node has started based on this provided configuration."),
    REPOSITORY_NODE_STARTING_NO_CONFIG("OMRS-CRUX-REPOSITORY-0003",
            OMRSAuditLogRecordSeverity.STARTUP,
            "A new Crux repository node is starting in-memory (only).",
            "The local server is starting a new Crux node in-memory (only), as no configuration was provided through configurationProperties of the local repository connection.",
            "All nodes that are started without any configuration provided will display this " +
                    "message. You should see a subsequent OMRS-CRUX-REPOSITORY-0004 indicating that the " +
                    "node has started purely in-memory."),
    REPOSITORY_SERVICE_STARTED("OMRS-CRUX-REPOSITORY-0004",
            OMRSAuditLogRecordSeverity.STARTUP,
            "A new Crux repository node has started, running Crux version {0} with: {1}",
            "The local server has completed startup of a new Crux repository node.",
            "A Crux repository node is only in a known running state once this message is displayed. When " +
                    "the node is shutdown, an OMRS-CRUX-REPOSITORY-0005 will be displayed."),
    REPOSITORY_SERVICE_SHUTDOWN("OMRS-CRUX-REPOSITORY-0005",
            OMRSAuditLogRecordSeverity.SHUTDOWN,
            "The Crux repository node has shutdown in server {0}",
            "The local server has requested shut down of a Crux repository node.",
            "No action is required. The existing Crux repository node has been closed and shutdown."),
    FAILED_RELATIONSHIP_DELETE_CASCADE("OMRS-CRUX-REPOSITORY-0006",
            OMRSAuditLogRecordSeverity.EXCEPTION,
            "The Crux repository failed to cascade deletions of all relationships for entity {0} due to {1}.",
            "The local server failed to delete all relationships of the entity during the entity's deletion.",
            "This exception and stacktrace indicate the reason why the relationships for the specified entity " +
                    "could not be determined, and therefore could not be deleted. You will need to manually investigate " +
                    "the cause of the error and then search for and remove any relationships that have the specified " +
                    "entity as one end from the Crux repository. Please raise an issue on GitHub with the details of the" +
                    "error for guidance and assistance."),
    FAILED_RELATIONSHIP_DELETE("OMRS-CRUX-REPOSITORY-0007",
            OMRSAuditLogRecordSeverity.EXCEPTION,
            "The Crux repository failed to cascade deletions of relationship {0} for entity {1} due to {2}.",
            "The local server failed to delete the specified relationship of the entity during the entity's deletion.",
            "This exception and stacktrace indicate the reason why a single relationships for the specified entity " +
                    "could not be deleted. You will need to manually investigate " +
                    "the cause of the error and then search for and manually remove the specified relationship " +
                    "from the Crux repository. Please raise an issue on GitHub with the details of the" +
                    "error for guidance and assistance."),
    FAILED_REPOSITORY_STARTUP("OMRS-CRUX-REPOSITORY-0008",
            OMRSAuditLogRecordSeverity.EXCEPTION,
            "The Crux repository node failed to start due to {0}.",
            "The local Crux repository node failed to startup within the server, likely due to some configuration issue.",
            "Review the exception and stacktrace for additional information, correct any configuration issues, and retry."),
    FAILED_REPOSITORY_SHUTDOWN("OMRS-CRUX-REPOSITORY-0009",
            OMRSAuditLogRecordSeverity.EXCEPTION,
            "The Crux repository node failed to shutdown due to {0}.",
            "The local Crux repository node failed to shutdown within the server.",
            "Review the exception and stacktrace for additional information, correct any configuration issues, and retry."),
    CANNOT_READ_CONFIGURATION("OMRS-CRUX-REPOSITORY-0010",
            OMRSAuditLogRecordSeverity.EXCEPTION,
            "Unable to read the configuration for the Crux repository node due to {0}.",
            "The local server was unable to parse the provided configuration for the Crux server.",
            "Investigate the logs for additional information, and correct the configurationProperties sent to the connector."),
    ;

    private final String logMessageId;
    private final OMRSAuditLogRecordSeverity severity;
    private final String logMessage;
    private final String systemAction;
    private final String userAction;


    /**
     * The constructor for OMRSAuditCode expects to be passed one of the enumeration rows defined in
     * OMRSAuditCode above.   For example:
     * <p>
     * OMRSAuditCode   auditCode = OMRSAuditCode.SERVER_NOT_AVAILABLE;
     * <p>
     * This will expand out to the 4 parameters shown below.
     *
     * @param messageId    - unique Id for the message
     * @param severity     - the severity of the message
     * @param message      - text for the message
     * @param systemAction - description of the action taken by the system when the condition happened
     * @param userAction   - instructions for resolving the situation, if any
     */
    CruxOMRSAuditCode(String messageId, OMRSAuditLogRecordSeverity severity, String message,
                      String systemAction, String userAction) {
        this.logMessageId = messageId;
        this.severity = severity;
        this.logMessage = message;
        this.systemAction = systemAction;
        this.userAction = userAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuditLogMessageDefinition getMessageDefinition() {
        return new AuditLogMessageDefinition(logMessageId,
                severity,
                logMessage,
                systemAction,
                userAction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuditLogMessageDefinition getMessageDefinition(String ...params) {
        AuditLogMessageDefinition messageDefinition = new AuditLogMessageDefinition(logMessageId,
                severity,
                logMessage,
                systemAction,
                userAction);
        messageDefinition.setMessageParameters(params);
        return messageDefinition;
    }

}
