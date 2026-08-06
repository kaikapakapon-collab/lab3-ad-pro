package se233.chapter3.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import se233.chapter3.Launcher;
import se233.chapter3.model.FileFreq;
import se233.chapter3.model.PdfDocument;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MainViewController {
    private LinkedHashMap<String, List<FileFreq>> uniqueSets;

    // Mapping format string กลับไปเป็น Key ตั้งต้น เพื่อให้คลิกแล้วหาใน Map เจอ
    private Map<String, String> displayToOriginalKey = new HashMap<>();

    // Exercise 3.5 (3): เก็บ Path เต็มไว้ประมวลผลเบื้องหลัง
    private List<String> actualFilePaths = new ArrayList<>();

    @FXML
    private ListView<String> inputListView;
    @FXML
    private Button startButton;
    @FXML
    private ListView<String> listView;
    @FXML
    private MenuItem closeMenuItem; // Exercise 3.5 (4)

    @FXML
    public void initialize() {
        inputListView.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            final boolean isAccepted = db.getFiles().get(0).getName().toLowerCase().endsWith(".pdf");
            if (db.hasFiles() && isAccepted) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });

        inputListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                int total_files = db.getFiles().size();
                for (int i = 0; i < total_files; i++) {
                    File file = db.getFiles().get(i);
                    // Exercise 3.5 (3): โชว์แค่ชื่อไฟล์บน GUI
                    inputListView.getItems().add(file.getName());//ตัวโชวืชื่อ
                    actualFilePaths.add(file.getAbsolutePath());//ตัวเเก็บ path
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        startButton.setOnAction(event -> {
            Parent bgRoot = Launcher.primaryStage.getScene().getRoot();
            Task<Void> processTask = new Task<Void>() {
                @Override
                public Void call() throws IOException {
                    Platform.runLater(() -> {
                        ProgressIndicator pi = new ProgressIndicator();
                        VBox box = new VBox(pi);
                        box.setAlignment(Pos.CENTER);
                        Launcher.primaryStage.getScene().setRoot(box);
                    });

                    ExecutorService executor = Executors.newFixedThreadPool(4);
                    final ExecutorCompletionService<Map<String, FileFreq>> completionService = new ExecutorCompletionService<>(executor);

                    int total_files = actualFilePaths.size();
                    Map<String, FileFreq>[] wordMap = new Map[total_files];

                    for (int i = 0; i < total_files; i++) {
                        try {
                            String filePath = actualFilePaths.get(i);
                            PdfDocument p = new PdfDocument(filePath);
                            completionService.submit(new WordCountMapTask(p));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    for (int i = 0; i < total_files; i++) {
                        try {
                            Future<Map<String, FileFreq>> future = completionService.take();
                            wordMap[i] = future.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        WordCountReduceTask merger = new WordCountReduceTask(wordMap);
                        Future<LinkedHashMap<String, List<FileFreq>>> future = executor.submit(merger);
                        uniqueSets = future.get();

                        Platform.runLater(() -> {
                            listView.getItems().clear();
                            displayToOriginalKey.clear();

                            // Exercise 3.5 (2): จัดฟอร์แมตให้เป็น "about (4, 2, 1)"
                            // วนลูปอ่านค่าข้อมูลทีละคำ
                            for (Map.Entry<String, List<FileFreq>> entry : uniqueSets.entrySet()) {
                                String key = entry.getKey();
                                List<FileFreq> freqs = entry.getValue();
                                // ดึงข้อมูลเฉพาะตัวเลขมาแปลงเป็น String แล้วเอาลูกน้ำมาคั้น
                                String freqsStr = freqs.stream()
                                        .map(f -> String.valueOf(f.getFreq()))
                                        .collect(Collectors.joining(", "));
                                // เอาศัพมาต่อด้วยเว้นวรรคแล้วก็เลข
                                String displayText = key + " (" + freqsStr + ")";

                                // เก็บ Mapping ไว้ตอนคลิกเปิดไฟล์ แล้วเพิ่มขึ้นหน้าจอ
                                displayToOriginalKey.put(displayText, key);
                                listView.getItems().add(displayText);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        executor.shutdown();
                    }
                    return null;
                }
            };

            processTask.setOnSucceeded(e -> {
                Launcher.primaryStage.getScene().setRoot(bgRoot);
            });

            Thread thread = new Thread(processTask);
            thread.setDaemon(true);
            thread.start();
        });

        listView.setOnMouseClicked(event -> {
            String selectedDisplay = listView.getSelectionModel().getSelectedItem();
            if (selectedDisplay == null) return;

            String originalKey = displayToOriginalKey.get(selectedDisplay);
            List<FileFreq> listOfLinks = uniqueSets.get(originalKey);

            ListView<FileFreq> popupListView = new ListView<>();
            LinkedHashMap<FileFreq, String> lookupTable = new LinkedHashMap<>();
            for (int i = 0; i < listOfLinks.size(); i++) {
                lookupTable.put(listOfLinks.get(i), listOfLinks.get(i).getPath());
                popupListView.getItems().add(listOfLinks.get(i));
            }
            popupListView.setPrefWidth(250);
            popupListView.setPrefHeight(popupListView.getItems().size() * 40);

            popupListView.setOnMouseClicked(innerEvent -> {
                Launcher.hs.showDocument("file:///" + lookupTable.get(popupListView.getSelectionModel().getSelectedItem()));
                popupListView.getScene().getWindow().hide();
            });

            Popup popup = new Popup();
            popup.getContent().add(popupListView);

            // Exercise 3.5 (5): ปิด popup เมื่อกดปุ่ม ESC
            // สั่ง pop ให้ดูว่ามีใครกดคีย์บอร์ดไหม
            popupListView.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == KeyCode.ESCAPE) { // ถ้ามีคนกด esc
                    popup.hide(); // ให้ซ่อนมันไป
                }
            });

            popup.show(Launcher.primaryStage);
        });
    }

    // Exercise 3.5 (4): ฟังก์ชันสำหรับปิดโปรแกรมจาก MenuBar
    @FXML
    public void handleClose() {
        Platform.exit(); // สั่งปิดหน้าต่าง javaFX
        System.exit(0); // ปิดการทำงานของโปรแกรมและคืน mem ให้เครื่อง
    }
}