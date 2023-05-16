/*
 * Copyright (c) 2021 Fraunhofer IOSB, eine rechtlich nicht selbstaendige
 * Einrichtung der Fraunhofer-Gesellschaft zur Foerderung der angewandten
 * Forschung e.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fraunhofer.iosb.ilt.faaast.converter.packageexplorer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.adminshell.aas.v3.dataformat.core.util.AasUtils;
import io.adminshell.aas.v3.model.KeyType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PackageExplorerConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PackageExplorerConverter.class);
    private static final TypeRef<List<ObjectNode>> TYPE_OBJECT_NODE_LIST = new TypeRef<List<ObjectNode>>() {};
    private static final TypeRef<List<JsonNode>> TYPE_JSON_NODE_LIST = new TypeRef<List<JsonNode>>() {};
    private static final KeyType DEFAULT_KEY_TYPE = KeyType.IRI;
    private final DocumentContext document;

    private PackageExplorerConverter(InputStream input) {
        document = JsonPath
                .using(new Configuration.ConfigurationBuilder()
                        .jsonProvider(new JacksonJsonNodeJsonProvider())
                        .mappingProvider(new JacksonMappingProvider())
                        .options(Option.SUPPRESS_EXCEPTIONS)
                        .build())
                .parse(input, StandardCharsets.UTF_8.name());
    }


    public static InputStream toFaaast(InputStream input) {
        return new PackageExplorerConverter(input).convert();
    }


    private ByteArrayInputStream convert() {
        removeEmptyKeys();
        removeKeyIndex();
        removeKeyLocal();
        removeViews();
        capitalizeEnumValues();
        transformAssets();
        flattenValueType();
        flattenOperationVariables();
        flattenMultiLanguagePropertyValue();
        fixEmbeddedDataSpecificationDataType();
        addMissingEmbeddedDataSpecificationType();
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document.json()));
            }
            catch (JsonProcessingException e) {
                LOGGER.warn("Could not log converter result as serialization failed", e);
            }
        }
        return new ByteArrayInputStream(document.jsonString().getBytes());
    }


    private void delete(String path, Function<List<JsonNode>, String> logMessageProvider, boolean logElements) {
        List<JsonNode> elements = document.read(path, TYPE_JSON_NODE_LIST);
        if (!elements.isEmpty()) {
            LOGGER.debug(logMessageProvider.apply(elements));
            if (logElements) {
                elements.forEach(x -> LOGGER.debug("     {}", x));
            }
        }
        try {
            document.delete(path);
        }
        catch (ClassCastException e) {
            // ignore
        }
    }


    private void removeEmptyKeys() {
        delete("$..keys[?(@.type == '' || @.value == '' || @.idType == '')]",
                x -> String.format("Found %d keys with empty type, value, and/or idType. These keys will be removed which may render enclosing element (e.g. a reference) invalid.",
                        x.size()),
                true);
    }


    private void removeKeyIndex() {
        delete("$..keys[*].index", x -> String.format("Removed key.index (because package explorer-specific)"), false);
    }


    private void removeKeyLocal() {
        delete("$..keys[*].local", x -> String.format("Removed key.local (because removed in AAS v3.0)"), false);
    }


    private void removeViews() {
        delete("$.assetAdministrationShells[*].views", x -> String.format("Removed views (because removed in AAS v3.0)"), false);
    }


    private void capitalizeEnumValues() {
        LOGGER.debug("Adjusting values for 'idType' and 'category' (FA³ST-specific)");
        document.map("$..idType", (x, config) -> transformIdTye(x.toString()));
        document.map("$..category", (x, config) -> AasUtils.serializeEnumName(x.toString()));
    }


    private String transformIdTye(String idType) {
        Optional<KeyType> keyType = Stream.of(KeyType.values())
                .filter(x -> normalize(x.name()).equals(normalize(idType)))
                .findFirst();
        if (keyType.isPresent()) {
            return AasUtils.serializeEnumName(keyType.get().name());
        }
        LOGGER.warn("found invalid idType '{}', replacing it with default ({})", idType, DEFAULT_KEY_TYPE.name());
        return AasUtils.serializeEnumName(DEFAULT_KEY_TYPE.name());
    }


    private static String normalize(String input) {
        return input.toLowerCase().replace("_", "");
    }


    private void fixEmbeddedDataSpecificationDataType() {
        LOGGER.debug("Adjusting values for 'dataType' inside embeddedDataSpecifications (FA³ST-specific)");
        document.map("$..embeddedDataSpecifications[*]..dataType", (x, config) -> {
            if (x.toString().isBlank()) {
                LOGGER.warn("Found embeddedDataSpecification with missing datatype property - setting to 'String' (default)");
                return "String";
            }
            return AasUtils.serializeEnumName(x.toString());
        });
    }


    private void addMissingEmbeddedDataSpecificationType() {
        document.map("$.conceptDescriptions[*].embeddedDataSpecifications", (node, config) -> {
            StreamSupport.stream(((ArrayNode) node).spliterator(), false).forEach(x -> {
                try {
                    JsonNode dataSpecificationContent = x.get("dataSpecificationContent");
                    if (dataSpecificationContent == null || dataSpecificationContent.isEmpty()) {
                        return;
                    }
                    ObjectNode dataSpecification = (ObjectNode) dataSpecificationContent.get("dataSpecification");
                    if (dataSpecification == null) {
                        dataSpecification = ((ObjectNode) x).putObject("dataSpecification");
                    }
                    ArrayNode keys = (ArrayNode) dataSpecification.get("keys");
                    if (keys == null) {
                        keys = dataSpecification.putArray("keys");
                    }
                    if (keys.isEmpty()) {
                        LOGGER.debug("Adding missing type information for embeddedDataSpecification in conceptDescription");
                        keys.add(JsonNodeFactory.instance.objectNode()
                                .put("idType", "Iri")
                                .put("type", "GlobalReference")
                                .put("value", "http://admin-shell.io/DataSpecificationTemplates/DataSpecificationIEC61360/2/0"));
                    }
                }
                catch (Exception e) {
                    LOGGER.error("error while adding missing embeddedDataSpecification type", e);
                }
            });
            return node;
        });
    }


    private void transformAssets() {
        LOGGER.debug(
                "Updating assets: removing top-level 'assets' array and converter 'AssetAdministrationShell.asset' to 'AssetAdministrationShell.assetInformation' (introduced in v3.0)");
        List<ObjectNode> assets = document.read("$.assets", TYPE_OBJECT_NODE_LIST);
        Map<String, String> assetKinds;
        if (assets == null) {
            assetKinds = Map.of();
        }
        else {
            assetKinds = assets.stream()
                    .collect(Collectors.toMap(
                            x -> x.at("/identification/id").asText(),
                            x -> x.get("kind").asText()));
            document.delete("$.assets");
        }
        document.map("$.assetAdministrationShells[?(@.asset)]", (x, config) -> {
            ObjectNode node = (ObjectNode) x;
            ObjectNode nodeAssetInformation = node.putObject("assetInformation");
            String id = node.at("/asset/keys/0/value").asText();
            nodeAssetInformation.put("assetKind", assetKinds.containsKey(id) ? assetKinds.get(id) : "Instance");
            nodeAssetInformation
                    .putObject("globalAssetId")
                    .putArray("keys")
                    .addObject()
                    .put("value", id)
                    .put("type",  "Asset")
                    .set("idType", node.at("/asset/keys/0/idType"))                    ;
            node.remove("asset");
            return node;
        });
    }


    private void flattenValueType() {
        LOGGER.debug("Flattening valueType structure (because package explorer-specific)");
        document.map("$..valueType[?(@.dataObjectType)]", (x, config) -> {
            ObjectNode node = (ObjectNode) x;
            return JsonNodeFactory.instance.textNode(node.at("/dataObjectType/name").asText().toLowerCase());
        });
    }


    private void flattenMultiLanguagePropertyValue() {
        LOGGER.debug("Flattening MultiLanguageProperty.value structure (because package explorer-specific)");
        document.map("$..value[?(@.langString)]", (x, config) -> {
            ObjectNode node = (ObjectNode) x;
            return node.get("langString");
        });
    }


    private void flattenOperationVariables() {
        LOGGER.debug("Flattening operation variable structure (because package explorer-specific)");
        document.map("$..value[?(@.submodelElement)]", (x, config) -> {
            ObjectNode node = (ObjectNode) x;
            return node.elements().next();
        });
    }
}
