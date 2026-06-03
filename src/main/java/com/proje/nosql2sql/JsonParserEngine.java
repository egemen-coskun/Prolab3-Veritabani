package com.proje.nosql2sql;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonParserEngine {

    private Map<String, TableSchema> schemas = new LinkedHashMap<>();
    private Map<String, Integer> tableIdCounters = new LinkedHashMap<>();

    public Map<String, TableSchema> dosyaCozumle(File file, String rootTableName) throws IOException {
        schemas.clear();
        tableIdCounters.clear();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(file);

        if (rootNode.isArray()) {
            diziCozumle(rootNode, rootTableName, null, null, null);
        } else if (rootNode.isObject()) {
            objeCozumle(rootNode, rootTableName, null, null, null);
        }

        return schemas;
    }

    private void diziCozumle(JsonNode arrayNode, String tableName, String parentTable, String foreignKeyColumn, Integer parentId) {
        for (JsonNode element : arrayNode) {
            if (element.isObject()) {
                objeCozumle(element, tableName, parentTable, foreignKeyColumn, parentId);
            } else {
                // Array of primitives (e.g. [1, 2, 3]). We create a simple row.
                TableSchema schema = semaGetirVeyaOlustur(tableName, parentTable, foreignKeyColumn);
                Map<String, Object> row = new LinkedHashMap<>();
                int currentId = getNextId(tableName);
                row.put("id", currentId);
                
                String colName = "value";
                schema.addColumn(colName, sqlTipiniBelirle(element));
                row.put(colName, degerCikar(element));
                
                if (foreignKeyColumn != null && parentId != null) {
                    row.put(foreignKeyColumn, parentId);
                }
                schema.addRow(row);
            }
        }
    }

    private void objeCozumle(JsonNode objectNode, String tableName, String parentTable, String foreignKeyColumn, Integer parentId) {
        TableSchema schema = semaGetirVeyaOlustur(tableName, parentTable, foreignKeyColumn);
        Map<String, Object> row = new LinkedHashMap<>();
        int currentId = getNextId(tableName);
        row.put("id", currentId);

        if (foreignKeyColumn != null && parentId != null) {
            row.put(foreignKeyColumn, parentId);
        }

        objeDuzlestir(objectNode, "", schema, row, currentId, tableName);

        schema.addRow(row);
    }

    private void objeDuzlestir(JsonNode node, String prefix, TableSchema schema, Map<String, Object> row, int currentId, String currentTableName) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            String columnName = prefix.isEmpty() ? key : prefix + "_" + key;

            if (value.isObject()) {
                if (isSimpleObject(value)) {
                    objeDuzlestir(value, columnName, schema, row, currentId, currentTableName);
                } else {
                  
                    String childTableName = currentTableName + "_" + key;
                    String foreignKey = currentTableName + "_id";
                    objeCozumle(value, childTableName, currentTableName, foreignKey, currentId);
                }
            } else if (value.isArray()) {
            
                String childTableName = currentTableName + "_" + key;
                String foreignKey = currentTableName + "_id";
                diziCozumle(value, childTableName, currentTableName, foreignKey, currentId);
            } else if (!value.isNull()) {
               
                schema.addColumn(columnName, sqlTipiniBelirle(value));
                row.put(columnName, degerCikar(value));
            }
        }
    }

    private boolean isSimpleObject(JsonNode objectNode) {
        if (!objectNode.isObject()) return false;
        Iterator<JsonNode> elements = objectNode.elements();
        while (elements.hasNext()) {
            JsonNode val = elements.next();
            if (val.isObject() || val.isArray()) {
                return false;
            }
        }
        return true;
    }

    private int getNextId(String tableName) {
        int id = tableIdCounters.getOrDefault(tableName, 1);
        tableIdCounters.put(tableName, id + 1);
        return id;
    }

    private TableSchema semaGetirVeyaOlustur(String tableName, String parentTable, String foreignKeyColumn) {
        if (!schemas.containsKey(tableName)) {
            TableSchema schema = new TableSchema(tableName);
            if (parentTable != null && foreignKeyColumn != null) {
                schema.setForeignKey(parentTable, foreignKeyColumn);
            }
            schemas.put(tableName, schema);
        }
        return schemas.get(tableName);
    }

    private String sqlTipiniBelirle(JsonNode node) {
        if (node.isInt() || node.isLong()) return "INTEGER";
        if (node.isDouble() || node.isFloat()) return "REAL";
        if (node.isBoolean()) return "BOOLEAN";
        return "VARCHAR(255)";
    }

    private Object degerCikar(JsonNode node) {
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }
}
