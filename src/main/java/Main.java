package com.photoorganizer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {
    private File selectedDirectory;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button selectButton;
    private Button organizeButton;
    private final AtomicBoolean isOrganizing = new AtomicBoolean(false);
    private final Set<String> SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp", ".heic",
        ".cr2", ".nef", ".arw", ".raw", ".rw2", ".orf", ".raf", ".srw", ".dng"
    ));

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Photo Organizer");

        // Create MenuBar
        MenuBar menuBar = createMenuBar();

        // Main container
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Buttons container
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        selectButton = new Button("Select Folder");
        selectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        organizeButton = new Button("Start Organizing");
        organizeButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        organizeButton.setDisable(true);

        buttonBox.getChildren().addAll(selectButton, organizeButton);

        // Progress section
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #4CAF50;");

        statusLabel = new Label("Select a folder to begin");
        statusLabel.setStyle("-fx-font-size: 14px;");

        // Add all components to root
        root.getChildren().addAll(menuBar, buttonBox, progressBar, statusLabel);

        // Setup event handlers
        setupEventHandlers(primaryStage);

        // Create scene
        Scene scene = new Scene(root, 600, 250);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitItem);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, helpMenu);
        return menuBar;
    }

    private void setupEventHandlers(Stage primaryStage) {
        selectButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Photos Folder");
            selectedDirectory = chooser.showDialog(primaryStage);
            if (selectedDirectory != null) {
                statusLabel.setText("Selected: " + selectedDirectory.getPath());
                organizeButton.setDisable(false);
            }
        });

        organizeButton.setOnAction(e -> {
            if (!isOrganizing.get()) {
                startOrganizing();
            } else {
                Optional<ButtonType> result = new Alert(Alert.AlertType.CONFIRMATION,
                    "Organization in progress. Do you want to cancel?",
                    ButtonType.YES, ButtonType.NO).showAndWait();
                
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    isOrganizing.set(false);
                }
            }
        });
    }

    private void startOrganizing() {
        if (selectedDirectory == null) return;

        isOrganizing.set(true);
        selectButton.setDisable(true);
        organizeButton.setText("Cancel");
        progressBar.setProgress(0);
        statusLabel.setText("Scanning for images...");

        Thread worker = new Thread(() -> {
            List<Path> imageFiles = new ArrayList<>();
            Map<String, Integer> stats = new HashMap<>();
            stats.put("processed", 0);
            stats.put("errors", 0);

            try {
                Files.walk(selectedDirectory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> isImageFile(path.toString()))
                    .forEach(imageFiles::add);

                int total = imageFiles.size();
                if (total == 0) {
                    Platform.runLater(() -> {
                        statusLabel.setText("No image files found");
                        finishOrganizing();
                    });
                    return;
                }

                for (int i = 0; i < total && isOrganizing.get(); i++) {
                    Path imagePath = imageFiles.get(i);
                    try {
                        processImage(imagePath, i, total);
                        stats.put("processed", stats.get("processed") + 1);
                    } catch (Exception e) {
                        stats.put("errors", stats.get("errors") + 1);
                        e.printStackTrace();
                    }
                }

                Platform.runLater(() -> {
                    showCompletionDialog(stats);
                    finishOrganizing();
                });

            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Error scanning directory: " + e.getMessage());
                    finishOrganizing();
                });
            }
        });

        worker.start();
    }

    private void processImage(Path imagePath, int current, int total) throws IOException {
        File imageFile = imagePath.toFile();
        Date photoDate = getPhotoDate(imageFile);
        String yearMonth = new SimpleDateFormat("yyyy/MM").format(photoDate);
        
        Path targetDir = selectedDirectory.toPath().resolve(yearMonth);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(imagePath.getFileName());
        String fileName = imagePath.getFileName().toString();
        
        int counter = 1;
        while (Files.exists(targetPath)) {
            String newName = fileName.replaceFirst("(\\.[^.]+)$", "_" + counter + "$1");
            targetPath = targetDir.resolve(newName);
            counter++;
        }

        Files.move(imagePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        
        final double progress = (current + 1.0) / total;
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            statusLabel.setText(String.format("Processed: %s → %s", fileName, yearMonth));
        });
    }

    private void finishOrganizing() {
        isOrganizing.set(false);
        selectButton.setDisable(false);
        organizeButton.setText("Start Organizing");
        organizeButton.setDisable(false);
        statusLabel.setText("Select a folder to begin");
    }

    private void showCompletionDialog(Map<String, Integer> stats) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Complete");
        alert.setHeaderText("Organization complete!");
        alert.setContentText(String.format("Processed: %d files\nErrors: %d",
            stats.get("processed"), stats.get("errors")));
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Photo Organizer");
        alert.setHeaderText("Photo Organizer");
        alert.setContentText("A simple tool to organize your photos by date.\n\n" +
            "Version: 1.0\n" +
            "Created with JavaFX");
        alert.showAndWait();
    }

    private boolean isImageFile(String name) {
        return SUPPORTED_FORMATS.stream()
            .anyMatch(format -> name.toLowerCase().endsWith(format));
    }

    private Date getPhotoDate(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            
            for (com.drew.metadata.Directory directory : metadata.getDirectories()) {
                for (int tagType : new int[] {
                    ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                    ExifSubIFDDirectory.TAG_DATETIME,
                    ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED
                }) {
                    try {
                        Date date = directory.getDate(tagType);
                        if (date != null) return date;
                    } catch (Exception ignored) {}
                }
            }
        } catch (ImageProcessingException | IOException e) {
            System.out.println("Warning: Could not read metadata from " + file.getName() + ": " + e.getMessage());
        }
        
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            FileTime creationTime = attrs.creationTime();
            if (creationTime != null) {
                return new Date(creationTime.toMillis());
            }
        } catch (IOException e) {}
        
        return new Date(file.lastModified());
    }

    public static void main(String[] args) {
        launch(args);
    }
}