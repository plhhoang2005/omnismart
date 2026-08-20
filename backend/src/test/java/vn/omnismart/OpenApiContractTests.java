package vn.omnismart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTests {

    @Test
    void basicBackendContractIsValidYamlWithUniqueOperationIds() throws Exception {
        Path contract = Path.of("..", "docs", "openapi", "backend-basic.yaml");
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> document = new Yaml().load(input);
            assertThat(document.get("openapi")).isEqualTo("3.1.0");
            Map<String, Map<String, Object>> paths = castMap(document.get("paths"));
            assertThat(paths).hasSize(19);

            Set<String> operationIds = new HashSet<>();
            for (Map<String, Object> pathItem : paths.values()) {
                for (Map.Entry<String, Object> operation : pathItem.entrySet()) {
                    if (!Set.of("get", "post", "patch", "delete", "put").contains(operation.getKey())) {
                        continue;
                    }
                    Map<String, Object> operationDefinition = castMap(operation.getValue());
                    String operationId = (String) operationDefinition.get("operationId");
                    assertThat(operationId).isNotBlank();
                    assertThat(operationIds.add(operationId))
                            .as("operationId %s must be unique", operationId)
                            .isTrue();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> castMap(Object value) {
        return (Map<K, V>) value;
    }
}
