package com.proje.nosql2sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

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

        // Center Panel (SplitPane)
        SplitPane splitPane = new SplitPane();

        // Left side: JSON View
        VBox leftPane = new VBox(10);
        leftPane.getStyleClass().add("panel-white");
        Label jsonLabel = new Label("JSON Görünümü");
        jsonLabel.getStyleClass().add("label-title");
        jsonTextArea = new TextArea();
        jsonTextArea.setEditable(false);
        VBox.setVgrow(jsonTextArea, Priority.ALWAYS);
        leftPane.getChildren().addAll(jsonLabel, jsonTextArea);

        // Right side: SQL Table View
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

        // Bottom Panel
        lblStatus = new Label("Sistem Hazır. Lütfen bir JSON dosyası seçin.");
        lblStatus.getStyleClass().add("label-info");
        root.setBottom(lblStatus);
        BorderPane.setMargin(lblStatus, new Insets(10, 0, 0, 0));

        // Event Handlers
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
            lblStatus.setText("Durum: JSON yükleniyor, lütfen bekleyin...");

            // Arka planda dosyayı okuyup TextArea'ya yazma
            javafx.concurrent.Task<String> loadTask = new javafx.concurrent.Task<String>() {
                @Override
                protected String call() throws Exception {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(selectedFile);
                    String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

                    // Çok büyük dosyalarda UI'ın çökmemesi için ilk 10.000 karakteri alıyoruz
                    // (yaklaşık 300-500 satır)
                    if (prettyJson.length() > 10000) {
                        return prettyJson.substring(0, 10000)
                                + "\n\n... (Dosya çok büyük olduğu için sadece önizleme gösteriliyor. Toplam "
                                + prettyJson.length() + " karakter)";
                    }
                    return prettyJson;
                }
            };

            loadTask.setOnSucceeded(e -> {
                jsonTextArea.setText(loadTask.getValue());
                lblStatus.setText("Durum: JSON yüklendi. Aktarmak için 'Dönüştür ve Aktar'a tıklayın.");
            });

            loadTask.setOnFailed(e -> {
                Throwable ex = loadTask.getException();
                showAlert(Alert.AlertType.ERROR, "Hata", "JSON okuma hatası: " + ex.getMessage());
                lblStatus.setText("Durum: Dosya okunamadı.");
            });

            new Thread(loadTask).start();
        }
    }

    private void donusturVeAktar() {
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen önce bir JSON dosyası seçin.");
            return;
        }

        lblStatus.setText("Durum: Veriler dönüştürülüyor ve veritabanına aktarılıyor, lütfen bekleyin...");

        javafx.concurrent.Task<Map<String, TableSchema>> convertTask = new javafx.concurrent.Task<Map<String, TableSchema>>() {
            @Override
            protected Map<String, TableSchema> call() throws Exception {
                String fileName = selectedFile.getName();
                String rootTableName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.'))
                        : fileName;
                rootTableName = rootTableName.replaceAll("[^a-zA-Z0-9_]", "");
                if (rootTableName.isEmpty())
                    rootTableName = "root_table";

                Map<String, TableSchema> schemas = parserEngine.dosyaCozumle(selectedFile, rootTableName);
                dbManager.tablolariOlusturVeDoldur(schemas);
                return schemas;
            }
        };

        convertTask.setOnSucceeded(e -> {
            Map<String, TableSchema> schemas = convertTask.getValue();
            lblStatus.setText("Durum: Veriler başarıyla aktarıldı! " + schemas.size() + " tablo oluşturuldu.");
            showAlert(Alert.AlertType.INFORMATION, "Başarılı",
                    "Veriler başarıyla SQL veritabanına aktarıldı!\n\nOluşturulan Tablolar:\n"
                            + String.join("\n", schemas.keySet()));
            tablolariComboboxaYukle();
        });

        convertTask.setOnFailed(e -> {
            Throwable ex = convertTask.getException();
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Dönüşüm hatası: " + ex.getMessage());
            lblStatus.setText("Durum: Hata oluştu.");
        });

        new Thread(convertTask).start();
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
                String colName = data.columns.get(i);
                TableColumn<Vector<Object>, Object> column = new TableColumn<>(colName);

                double prefWidth = Math.max(150, colName.length() * 9.5);
                column.setPrefWidth(prefWidth);
                column.setMinWidth(100);

                Label headerLabel = new Label(colName);
                headerLabel.setTooltip(new Tooltip(colName));
                column.setGraphic(headerLabel);
                column.setText("");

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
