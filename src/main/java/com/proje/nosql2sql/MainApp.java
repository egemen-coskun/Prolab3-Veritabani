package com.proje.nosql2sql;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainApp extends Application {

    private TextArea jsonTextArea;
    private ComboBox<String> tableSelector;
    private TableView<Vector<Object>> sqlTable;
    private File selectedFile;
    private DatabaseManager dbManager;
    private JsonParserEngine parserEngine;
    private Label lblStatus;
    private Label lblSelectedFile;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        dbManager = new DatabaseManager();
        parserEngine = new JsonParserEngine();

        primaryStage.setTitle("NoSQL'den SQL'e Dönüşüm Sistemi (JavaFX)");
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top Panel
        VBox topPanel = new VBox(10);
        HBox buttonBox = new HBox(15);
        Button btnSelectFile = new Button("JSON Seç");
        Button btnConvert = new Button("Dönüştür ve Aktar");
        Button btnReset = new Button("Sıfırla (Tabloları Sil)");
        
        btnSelectFile.getStyleClass().add("button");
        btnConvert.getStyleClass().add("button");
        btnReset.getStyleClass().addAll("button", "button-danger");
        
        buttonBox.getChildren().addAll(btnSelectFile, btnConvert, btnReset);
        
        lblSelectedFile = new Label("Seçili Dosya: Yok");
        lblSelectedFile.getStyleClass().add("label-info");
        
        topPanel.getChildren().addAll(buttonBox, lblSelectedFile);
        root.setTop(topPanel);
        BorderPane.setMargin(topPanel, new Insets(0, 0, 10, 0));

        
        SplitPane splitPane = new SplitPane();
        
     
        VBox leftPane = new VBox(10);
        leftPane.getStyleClass().add("panel-white");
        Label jsonLabel = new Label("JSON Görünümü");
        jsonLabel.getStyleClass().add("label-title");
        jsonTextArea = new TextArea();
        jsonTextArea.setEditable(false);
        VBox.setVgrow(jsonTextArea, Priority.ALWAYS);
        leftPane.getChildren().addAll(jsonLabel, jsonTextArea);
        
       
        VBox rightPane = new VBox(10);
        rightPane.getStyleClass().add("panel-white");
        Label sqlLabel = new Label("SQL Veritabanı Gösterimi");
        sqlLabel.getStyleClass().add("label-title");
        
        HBox tableSelectBox = new HBox(10);
        tableSelectBox.getChildren().addAll(new Label("Görüntülenecek Tablo:"), tableSelector = new ComboBox<>());
        
        sqlTable = new TableView<>();
        VBox.setVgrow(sqlTable, Priority.ALWAYS);
        
        rightPane.getChildren().addAll(sqlLabel, tableSelectBox, sqlTable);
        
        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.4);
        root.setCenter(splitPane);

     
        lblStatus = new Label("Sistem Hazır. Lütfen bir JSON dosyası seçin.");
        lblStatus.getStyleClass().add("label-info");
        root.setBottom(lblStatus);
        BorderPane.setMargin(lblStatus, new Insets(10, 0, 0, 0));

   
        btnSelectFile.setOnAction(e -> dosyaSec());
        btnConvert.setOnAction(e -> donusturVeAktar());
        btnReset.setOnAction(e -> veritabaniniSifirla());
        tableSelector.setOnAction(e -> secilenTabloVerisiniYukle());

        Scene scene = new Scene(root, 1000, 700);
        try {
            String cssUrl = getClass().getResource("/com/proje/nosql2sql/style.css").toExternalForm();
            scene.getStylesheets().add(cssUrl);
        } catch (NullPointerException e) {
            System.err.println("style.css bulunamadı, varsayılan tema kullanılacak.");
        }
        primaryStage.setScene(scene);
        primaryStage.show();

        tablolariComboboxaYukle();
    }

    private void dosyaSec() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(primaryStage);
        
        if (file != null) {
            selectedFile = file;
            lblSelectedFile.setText("Seçili Dosya: " + selectedFile.getAbsolutePath());
            lblStatus.setText("Durum: JSON yüklendi. Aktarmak için 'Dönüştür ve Aktar'a tıklayın.");
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(selectedFile);
                String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                jsonTextArea.setText(prettyJson);
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Hata", "JSON okuma hatası: " + ex.getMessage());
            }
        }
    }

    private void donusturVeAktar() {
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen önce bir JSON dosyası seçin.");
            return;
        }
        
        try {
            String fileName = selectedFile.getName();
            String rootTableName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            rootTableName = rootTableName.replaceAll("[^a-zA-Z0-9_]", "");
            if(rootTableName.isEmpty()) rootTableName = "root_table";

            Map<String, TableSchema> schemas = parserEngine.dosyaCozumle(selectedFile, rootTableName);
            dbManager.tablolariOlusturVeDoldur(schemas);
            
            lblStatus.setText("Durum: Veriler başarıyla aktarıldı! " + schemas.size() + " tablo oluşturuldu.");
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Veriler başarıyla SQL veritabanına aktarıldı!\n\nOluşturulan Tablolar:\n" + String.join("\n", schemas.keySet()));
            tablolariComboboxaYukle();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Dönüşüm hatası: " + ex.getMessage());
        }
    }

    private void veritabaniniSifirla() {
        try {
            dbManager.tumTablolariTemizle();
            tablolariComboboxaYukle();
            jsonTextArea.setText("");
            selectedFile = null;
            lblSelectedFile.setText("Seçili Dosya: Yok");
            lblStatus.setText("Durum: Veritabanı sıfırlandı.");
            sqlTable.getColumns().clear();
            sqlTable.setItems(FXCollections.observableArrayList());
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Veritabanı sıfırlandı. Tüm tablolar silindi.");
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Sıfırlama hatası: " + ex.getMessage());
        }
    }

    private void tablolariComboboxaYukle() {
        try {
            tableSelector.getItems().clear();
            List<String> tables = dbManager.tumTabloIsimleriniGetir();
            tableSelector.getItems().addAll(tables);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void secilenTabloVerisiniYukle() {
        String selected = tableSelector.getValue();
        if (selected == null) {
            sqlTable.getColumns().clear();
            sqlTable.setItems(FXCollections.observableArrayList());
            return;
        }
        
        try {
            DatabaseManager.TableData data = dbManager.tabloVerisiniGetir(selected);
            
            sqlTable.getColumns().clear();
            for (int i = 0; i < data.columns.size(); i++) {
                final int colIndex = i;
                TableColumn<Vector<Object>, Object> column = new TableColumn<>(data.columns.get(i));
                column.setCellValueFactory(param -> {
                    Vector<Object> row = param.getValue();
                    if (row != null && row.size() > colIndex) {
                        return new SimpleObjectProperty<>(row.get(colIndex));
                    } else {
                        return new SimpleObjectProperty<>(null);
                    }
                });
                sqlTable.getColumns().add(column);
            }
            
            ObservableList<Vector<Object>> rowList = FXCollections.observableArrayList(data.rows);
            sqlTable.setItems(rowList);
            
        } catch (SQLException ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Tablo yükleme hatası: " + ex.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
