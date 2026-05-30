package com.proje.nosql2sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:project3.db";

    public Connection baglantiGetir() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void tablolariOlusturVeDoldur(Map<String, TableSchema> schemas) throws SQLException {
        try (Connection conn = baglantiGetir()) {
            conn.setAutoCommit(false);
            try {
                // Drop existing tables
                for (String tableName : schemas.keySet()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("DROP TABLE IF EXISTS \"" + tableName + "\"");
                    }
                }

                // Create tables
                for (TableSchema schema : schemas.values()) {
                    StringBuilder sql = new StringBuilder("CREATE TABLE \"");
                    sql.append(schema.getTableName()).append("\" (");
                    
                    List<String> colDefs = new ArrayList<>();
                    for (Map.Entry<String, String> entry : schema.getColumns().entrySet()) {
                        colDefs.add("\"" + entry.getKey() + "\" " + entry.getValue());
                    }
                    sql.append(String.join(", ", colDefs));
                    
                    // Add foreign key constraint if exists
                    if (schema.getForeignKeyColumn() != null && schema.getParentTable() != null) {
                        sql.append(", FOREIGN KEY(\"").append(schema.getForeignKeyColumn())
                           .append("\") REFERENCES \"").append(schema.getParentTable()).append("\"(\"id\")");
                    }
                    
                    sql.append(");");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql.toString());
                    }
                }

                // Insert data
                for (TableSchema schema : schemas.values()) {
                    if (schema.getRows().isEmpty()) continue;
                    
                    StringBuilder sql = new StringBuilder("INSERT INTO \"");
                    sql.append(schema.getTableName()).append("\" (");
                    
                    List<String> cols = new ArrayList<>(schema.getColumns().keySet());
                    String quotedCols = String.join(", ", cols.stream().map(c -> "\"" + c + "\"").toArray(String[]::new));
                    sql.append(quotedCols).append(") VALUES (");
                    
                    String placeholders = String.join(", ", cols.stream().map(c -> "?").toArray(String[]::new));
                    sql.append(placeholders).append(")");

                    try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                        for (Map<String, Object> row : schema.getRows()) {
                            for (int i = 0; i < cols.size(); i++) {
                                pstmt.setObject(i + 1, row.get(cols.get(i)));
                            }
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void tumTablolariTemizle() throws SQLException {
        try (Connection conn = baglantiGetir()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"});
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!tableName.equals("sqlite_sequence")) {
                    tables.add(tableName);
                }
            }
            try (Statement stmt = conn.createStatement()) {
                for (String table : tables) {
                    stmt.execute("DROP TABLE IF EXISTS \"" + table + "\"");
                }
            }
        }
    }

    public List<String> tumTabloIsimleriniGetir() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = baglantiGetir()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"});
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!tableName.equals("sqlite_sequence")) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }

    public TableData tabloVerisiniGetir(String tableName) throws SQLException {
        TableData data = new TableData();
        String query = "SELECT * FROM \"" + tableName + "\"";
        
        try (Connection conn = baglantiGetir();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
             
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            for (int i = 1; i <= columnCount; i++) {
                data.columns.add(meta.getColumnName(i));
            }
            
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                data.rows.add(row);
            }
        }
        return data;
    }

    public static class TableData {
        public Vector<String> columns = new Vector<>();
        public Vector<Vector<Object>> rows = new Vector<>();
    }
}
