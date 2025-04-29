
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

public class Main {
    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton selectButton;
    private JButton organizeButton;
    private File selectedDirectory;

    public Main() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("Photo Organizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        selectButton = new JButton("Select Folder");
        organizeButton = new JButton("Start Organizing");
        organizeButton.setEnabled(false);
        buttonPanel.add(selectButton);
        buttonPanel.add(organizeButton);

        // Progress Panel
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("Select a folder to begin", SwingConstants.CENTER);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);

        mainPanel.add(buttonPanel, BorderLayout.NORTH);
        mainPanel.add(progressPanel, BorderLayout.CENTER);

        frame.add(mainPanel);

        setupListeners();
        frame.setVisible(true);
    }

    private void setupListeners() {
        selectButton.addActionListener(e -> selectFolder());
        organizeButton.addActionListener(e -> startOrganizing());
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = chooser.getSelectedFile();
            statusLabel.setText("Selected: " + selectedDirectory.getPath());
            organizeButton.setEnabled(true);
        }
    }

    private void startOrganizing() {
        if (selectedDirectory == null) return;

        selectButton.setEnabled(false);
        organizeButton.setEnabled(false);
        statusLabel.setText("Scanning for images...");

        SwingWorker<Void, PhotoProgress> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // First, scan for all image files recursively
                List<File> imageFiles = new ArrayList<>();
                scanDirectory(selectedDirectory, imageFiles);

                int total = imageFiles.size();
                int current = 0;

                for (File file : imageFiles) {
                    current++;
                    try {
                        // Get date and create target directory
                        Date photoDate = getPhotoDate(file);
                        String yearMonth = new SimpleDateFormat("yyyy/MM").format(photoDate);
                        File targetDir = new File(selectedDirectory, yearMonth);
                        targetDir.mkdirs();

                        // Move the file
                        Path source = file.toPath();
                        Path target = new File(targetDir, file.getName()).toPath();
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

                        // Update progress
                        publish(new PhotoProgress(current, total, 
                            String.format("Moving: %s to %s", file.getName(), yearMonth)));
                    } catch (IOException e) {
                        e.printStackTrace();
                        publish(new PhotoProgress(current, total, 
                            "Error moving: " + file.getName()));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<PhotoProgress> chunks) {
                PhotoProgress latest = chunks.get(chunks.size() - 1);
                int progress = (latest.current * 100) / latest.total;
                progressBar.setValue(progress);
                statusLabel.setText(String.format("%s (%d/%d)", 
                    latest.message, latest.current, latest.total));
            }

            @Override
            protected void done() {
                JOptionPane.showMessageDialog(frame, "Photo organization complete!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                selectButton.setEnabled(true);
                organizeButton.setEnabled(true);
                statusLabel.setText("Select a folder to begin");
                progressBar.setValue(0);
            }
        };

        worker.execute();
    }

    private void scanDirectory(File directory, List<File> imageFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, imageFiles);
            } else if (isImageFile(file.getName())) {
                imageFiles.add(file);
            }
        }
    }

    private boolean isImageFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
               name.endsWith(".png") || name.endsWith(".gif");
    }

    private Date getPhotoDate(File file) {
        try {
            // Try to get EXIF date
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            ExifSubIFDDirectory directory = 
                metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            
            if (directory != null && 
                directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                return directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            }
        } catch (Exception e) {
            // Fallback to file modification date if EXIF reading fails
        }
        
        return new Date(file.lastModified());
    }

    // Helper class to track progress
    private static class PhotoProgress {
        final int current;
        final int total;
        final String message;

        PhotoProgress(int current, int total, String message) {
            this.current = current;
            this.total = total;
            this.message = message;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
