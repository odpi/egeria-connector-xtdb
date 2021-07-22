<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Explore

Once a connector instance is up and running, you should now be able to start interacting with the repository.
For very basic verification, you can use direct REST API interactions with the metadata server's repository
services.

!!! warning "These are not really public interfaces"
    The metadata server's REST API is not intended to be a public interface, so these should be treated
    as purely a validation mechanism. For more in-depth exploration, refer to the [Egeria tutorials](https://egeria.odpi.org/open-metadata-resources/open-metadata-tutorials/)
    and simply replace the steps therefore configuring the metadata server with the steps under
    [Setup](./setup/).

## Create a new entity

Create a new `GlossaryTerm`:

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"class":"EntityCreateRequest","entityTypeGUID":"0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a","initialProperties":{"class":"InstanceProperties","instanceProperties":{"qualifiedName":{"class":"PrimitivePropertyValue","primitiveDefCategory":"OM_PRIMITIVE_TYPE_STRING","primitiveValue":"Test Term 1"}}}}' \
  "https://localhost:9443/servers/crux/open-metadata/repository-services/users/admin/instances/entity"
```

??? question "Explanation of the payload"
    ```json linenums="1" hl_lines="3"
    {
      "class": "EntityCreateRequest",
      "entityTypeGUID": "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a",
      "initialProperties": {
        "class": "InstanceProperties",
        "instanceProperties": {
          "qualifiedName": {
            "class": "PrimitivePropertyValue",
            "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
            "primitiveValue": "Test Term 1"
          }
        }
      }
    }
    ```

    This payload defines the details of the entity that should be created: its type (by GUID) and any
    initial properties that should be set on it when it is created.

??? success "Response from creating a new GlossaryTerm"
    ```json linenums="1" hl_lines="47 49"
    {
        "class": "EntityDetailResponse",
        "relatedHTTPCode": 200,
        "entity":
        {
            "class": "EntityDetail",
            "headerVersion": 1,
            "type":
            {
                "class": "InstanceType",
                "typeDefCategory": "ENTITY_DEF",
                "typeDefGUID": "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a",
                "typeDefName": "GlossaryTerm",
                "typeDefVersion": 1,
                "typeDefDescription": "A semantic description of something, such as a concept, object, asset, technology, role or group.",
                "typeDefSuperTypes":
                [
                    {
                        "headerVersion": 1,
                        "guid": "a32316b8-dc8c-48c5-b12b-71c1b2a080bf",
                        "name": "Referenceable",
                        "status": "ACTIVE_TYPEDEF"
                    },
                    {
                        "headerVersion": 1,
                        "guid": "4e7761e8-3969-4627-8f40-bfe3cde85a1d",
                        "name": "OpenMetadataRoot",
                        "status": "ACTIVE_TYPEDEF"
                    }
                ],
                "validInstanceProperties":
                [
                    "qualifiedName",
                    "additionalProperties",
                    "displayName",
                    "summary",
                    "description",
                    "examples",
                    "abbreviation",
                    "usage"
                ]
            },
            "instanceProvenanceType": "LOCAL_COHORT",
            "metadataCollectionId": "3c38c245-bc50-4796-b195-4d9f1d8c5d22",
            "metadataCollectionName": "crux",
            "createdBy": "admin",
            "createTime": "2021-03-03T10:19:35.138+00:00",
            "version": 1,
            "guid": "050d91e7-8b2f-465e-83ef-7355a5047cc6",
            "properties":
            {
                "class": "InstanceProperties",
                "instanceProperties":
                {
                    "qualifiedName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    }
                },
                "propertyNames":
                [
                    "qualifiedName"
                ],
                "propertyCount": 1
            },
            "status": "ACTIVE"
        }
    }
    ```

Note the GUID and creation time from the response (will be needed in subsequent steps).

## Update that entity

Now update that same entity. This will create a new version of the entity while setting a new
`displayName` property on the term set to the same value as the `qualifiedName` property.

!!! tip "Remember to replace the GUID of the instance in the URL if copy / pasting."

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"class":"InstancePropertiesRequest","instanceProperties":{"class":"InstanceProperties","instanceProperties":{"qualifiedName":{"class":"PrimitivePropertyValue","primitiveDefCategory":"OM_PRIMITIVE_TYPE_STRING","primitiveValue":"Test Term 1"},"displayName":{"class":"PrimitivePropertyValue","primitiveDefCategory":"OM_PRIMITIVE_TYPE_STRING","primitiveValue":"Test Term 1"}}}}' \
  "https://localhost:9443/servers/crux/open-metadata/repository-services/users/admin/instances/entity/050d91e7-8b2f-465e-83ef-7355a5047cc6/properties"
```

??? question "Explanation of the payload"
    ```json
    {
      "class": "InstancePropertiesRequest",
      "instanceProperties": {
        "class": "InstanceProperties",
        "instanceProperties": {
          "qualifiedName": {
            "class": "PrimitivePropertyValue",
            "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
            "primitiveValue": "Test Term 1"
          },
          "displayName": {
            "class": "PrimitivePropertyValue",
            "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
            "primitiveValue": "Test Term 1"
          }
        }
      }
    }
    ```

    Note that for an update, we specify the instance that is being updated as part of the URL itself.
    So unlike the create request, this payload does not contain details about either the instance GUID
    or its type (those are inherent in the existing instance itself). Instead, we only need to specify
    the properties that we want to set as part of this update operation.

??? success "Response from updating the GlossaryTerm"
    ```json linenums="1" hl_lines="53-54"
    {
        "class": "EntityDetailResponse",
        "relatedHTTPCode": 200,
        "entity":
        {
            "class": "EntityDetail",
            "headerVersion": 1,
            "type":
            {
                "class": "InstanceType",
                "typeDefCategory": "ENTITY_DEF",
                "typeDefGUID": "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a",
                "typeDefName": "GlossaryTerm",
                "typeDefVersion": 1,
                "typeDefDescription": "A semantic description of something, such as a concept, object, asset, technology, role or group.",
                "typeDefSuperTypes":
                [
                    {
                        "headerVersion": 1,
                        "guid": "a32316b8-dc8c-48c5-b12b-71c1b2a080bf",
                        "name": "Referenceable",
                        "status": "ACTIVE_TYPEDEF"
                    },
                    {
                        "headerVersion": 1,
                        "guid": "4e7761e8-3969-4627-8f40-bfe3cde85a1d",
                        "name": "OpenMetadataRoot",
                        "status": "ACTIVE_TYPEDEF"
                    }
                ],
                "validInstanceProperties":
                [
                    "qualifiedName",
                    "additionalProperties",
                    "displayName",
                    "summary",
                    "description",
                    "examples",
                    "abbreviation",
                    "usage"
                ]
            },
            "instanceProvenanceType": "LOCAL_COHORT",
            "metadataCollectionId": "3c38c245-bc50-4796-b195-4d9f1d8c5d22",
            "metadataCollectionName": "crux",
            "createdBy": "admin",
            "updatedBy": "admin",
            "maintainedBy":
            [
                "admin"
            ],
            "createTime": "2021-03-03T10:19:35.138+00:00",
            "updateTime": "2021-03-03T10:37:36.044+00:00",
            "version": 2,
            "guid": "050d91e7-8b2f-465e-83ef-7355a5047cc6",
            "properties":
            {
                "class": "InstanceProperties",
                "instanceProperties":
                {
                    "qualifiedName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    },
                    "displayName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    }
                },
                "propertyNames":
                [
                    "qualifiedName",
                    "displayName"
                ],
                "propertyCount": 2
            },
            "status": "ACTIVE"
        }
    }
    ```

Note the update time from the response, and that we are now at version 2 of the entity.

## Retrieve that entity

### ... its current version

Now retrieve the current version of the entity.

!!! tip "Remember to replace the GUID of the instance in the URL if copy / pasting."

```shell
curl -k "https://localhost:9443/servers/crux/open-metadata/repository-services/users/admin/instances/entity/050d91e7-8b2f-465e-83ef-7355a5047cc6"
```

??? success "Response containing the current GlossaryTerm"
    ```json linenums="1" hl_lines="53-54 61-67"
    {
        "class": "EntityDetailResponse",
        "relatedHTTPCode": 200,
        "entity":
        {
            "class": "EntityDetail",
            "headerVersion": 1,
            "type":
            {
                "class": "InstanceType",
                "typeDefCategory": "ENTITY_DEF",
                "typeDefGUID": "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a",
                "typeDefName": "GlossaryTerm",
                "typeDefVersion": 1,
                "typeDefDescription": "A semantic description of something, such as a concept, object, asset, technology, role or group.",
                "typeDefSuperTypes":
                [
                    {
                        "headerVersion": 1,
                        "guid": "a32316b8-dc8c-48c5-b12b-71c1b2a080bf",
                        "name": "Referenceable",
                        "status": "ACTIVE_TYPEDEF"
                    },
                    {
                        "headerVersion": 1,
                        "guid": "4e7761e8-3969-4627-8f40-bfe3cde85a1d",
                        "name": "OpenMetadataRoot",
                        "status": "ACTIVE_TYPEDEF"
                    }
                ],
                "validInstanceProperties":
                [
                    "qualifiedName",
                    "additionalProperties",
                    "displayName",
                    "summary",
                    "description",
                    "examples",
                    "abbreviation",
                    "usage"
                ]
            },
            "instanceProvenanceType": "LOCAL_COHORT",
            "metadataCollectionId": "3c38c245-bc50-4796-b195-4d9f1d8c5d22",
            "metadataCollectionName": "crux",
            "createdBy": "admin",
            "updatedBy": "admin",
            "maintainedBy":
            [
                "admin"
            ],
            "createTime": "2021-03-03T10:19:35.138+00:00",
            "updateTime": "2021-03-03T10:37:36.044+00:00",
            "version": 2,
            "guid": "050d91e7-8b2f-465e-83ef-7355a5047cc6",
            "properties":
            {
                "class": "InstanceProperties",
                "instanceProperties":
                {
                    "displayName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    },
                    "qualifiedName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    }
                },
                "propertyNames":
                [
                    "displayName",
                    "qualifiedName"
                ],
                "propertyCount": 2
            },
            "status": "ACTIVE"
        }
    }
    ```

Note that the response contains version 2 of the entity, where the `displayName` property is defined and populated.

### ... its initial version

Now let us retrieve a historical version, using a time between the creation and the update.

!!! tip "Remember to replace the GUID in the URL, and the timestamp in the payload"

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"class":"HistoryRequest","asOfTime":"2021-03-03T10:25:00.000+00:00"}' \
  "https://localhost:9443/servers/crux/open-metadata/repository-services/users/admin/instances/entity/050d91e7-8b2f-465e-83ef-7355a5047cc6/history"
```

??? success "Response containing the original GlossaryTerm"
    ```json linenums="1" hl_lines="48 53-66"
    {
        "class": "EntityDetailResponse",
        "relatedHTTPCode": 200,
        "entity":
        {
            "class": "EntityDetail",
            "headerVersion": 1,
            "type":
            {
                "class": "InstanceType",
                "typeDefCategory": "ENTITY_DEF",
                "typeDefGUID": "0db3e6ec-f5ef-4d75-ae38-b7ee6fd6ec0a",
                "typeDefName": "GlossaryTerm",
                "typeDefVersion": 1,
                "typeDefDescription": "A semantic description of something, such as a concept, object, asset, technology, role or group.",
                "typeDefSuperTypes":
                [
                    {
                        "headerVersion": 1,
                        "guid": "a32316b8-dc8c-48c5-b12b-71c1b2a080bf",
                        "name": "Referenceable",
                        "status": "ACTIVE_TYPEDEF"
                    },
                    {
                        "headerVersion": 1,
                        "guid": "4e7761e8-3969-4627-8f40-bfe3cde85a1d",
                        "name": "OpenMetadataRoot",
                        "status": "ACTIVE_TYPEDEF"
                    }
                ],
                "validInstanceProperties":
                [
                    "qualifiedName",
                    "additionalProperties",
                    "displayName",
                    "summary",
                    "description",
                    "examples",
                    "abbreviation",
                    "usage"
                ]
            },
            "instanceProvenanceType": "LOCAL_COHORT",
            "metadataCollectionId": "3c38c245-bc50-4796-b195-4d9f1d8c5d22",
            "metadataCollectionName": "crux",
            "createdBy": "admin",
            "createTime": "2021-03-03T10:19:35.138+00:00",
            "version": 1,
            "guid": "050d91e7-8b2f-465e-83ef-7355a5047cc6",
            "properties":
            {
                "class": "InstanceProperties",
                "instanceProperties":
                {
                    "qualifiedName":
                    {
                        "class": "PrimitivePropertyValue",
                        "instancePropertyCategory": "PRIMITIVE",
                        "primitiveDefCategory": "OM_PRIMITIVE_TYPE_STRING",
                        "primitiveValue": "Test Term 1"
                    }
                },
                "propertyNames":
                [
                    "qualifiedName"
                ],
                "propertyCount": 1
            },
            "status": "ACTIVE"
        }
    }
    ```

Now we receive version 1 in the repsonse, without the `displayName`, since that was the version
that existed at the specified time.

### ... prior to its creation

Now let us try to retrieve a version of the entity before it even existed.

!!! tip "Remember to replace the GUID in the URL, and the timestamp in the payload"

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"class":"HistoryRequest","asOfTime":"2021-03-03T10:18:00.000+00:00"}' \
  "https://localhost:9443/servers/crux/open-metadata/repository-services/users/admin/instances/entity/050d91e7-8b2f-465e-83ef-7355a5047cc6/history"
```

??? fail "Response indicating the entity does not exist"
    ```json linenums="1" hl_lines="6"
    {
        "class": "EntityDetailResponse",
        "relatedHTTPCode": 404,
        "actionDescription": "getEntityDetail",
        "exceptionClassName": "org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException",
        "exceptionErrorMessage": "OMRS-REPOSITORY-404-002 The entity identified with guid 050d91e7-8b2f-465e-83ef-7355a5047cc6 passed on the getEntityDetail call is not known to the open metadata repository crux",
        "exceptionErrorMessageId": "OMRS-REPOSITORY-404-002",
        "exceptionErrorMessageParameters":
        [
            "050d91e7-8b2f-465e-83ef-7355a5047cc6",
            "getEntityDetail",
            "crux"
        ],
        "exceptionSystemAction": "The system is unable to retrieve the properties for the requested entity because the supplied guid is not recognized.",
        "exceptionUserAction": "The guid is supplied by the caller to the server.  It may have a logic problem that has corrupted the guid, or the entity has been deleted since the guid was retrieved."
    }
    ```

Now we see that the entity cannot be found, because at this point in time the entity did not
yet exist.

## Summary

In conclusion, with the Crux plugin repository we now have the ability to both store and
retrieve historical metadata. We have illustrated the simple case of retrieval by ID, but
the same works for all metadata access: searching, retrieving relationships through the
graph queries, etc.

This is all easily accomplished through Crux's native support for temporal queries: the
underlying API to the Crux storage layer is as simple as passing a date (for a view of that
point-in-time) or not passing a date (for the current view). The "view" itself applies
against the entire database: so _everything_ we retrieve from that point-in-time is
referentially-intact per that point-in-time.

--8<-- "snippets/abbr.md"
