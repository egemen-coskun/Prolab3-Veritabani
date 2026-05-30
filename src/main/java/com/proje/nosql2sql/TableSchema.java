package com.proje.nosql2sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableSchema {
    private String tableName;
    private Map<String, String> columns; // ColumnName -> SQLType
    private List<Map<String, Object>> rows;
    private String parentTable;
    private String foreignKeyColumn;

    public TableSchema(String tableName) {
        this.tableName = tableName;
        this.columns = new LinkedHashMap<>();
        this.rows = new ArrayList<>();
        // Default Primary Key
        this.columns.put("id", "INTEGER PRIMARY KEY AUTOINCREMENT");
    }

    public String getTableName() { return tableName; }
    public Map<String, String> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }

    public void addColumn(String name, String type) {
        if (!columns.containsKey(name)) {
            columns.put(name, type);
        } else {
            // Upgrade type if necessary, e.g., INTEGER -> VARCHAR if mixed types
            String currentType = columns.get(name);
            if (!currentType.equals(type) && type.startsWith("VARCHAR")) {
                columns.put(name, type);
            }
        }
    }

    public void addRow(Map<String, Object> row) {
        rows.add(row);
    }

    public void setForeignKey(String parentTable, String foreignKeyColumn) {
        this.parentTable = parentTable;
        this.foreignKeyColumn = foreignKeyColumn;
        addColumn(foreignKeyColumn, "INTEGER");
    }

    public String getParentTable() { return parentTable; }
    public String getForeignKeyColumn() { return foreignKeyColumn; }
}
