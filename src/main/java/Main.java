
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.imaging.ImageProcessingException;

public class Main {
    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton selectButton;
    private JButton organizeButton;
    private File selectedDirectory;
    private final Set<String> SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp", ".heic",
        ".cr2", ".nef", ".arw", ".raw", ".rw2", ".orf", ".raf", ".srw", ".dng"
    ));
    private volatile boolean isOrganizing = false;

    public Main() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame = new JFrame("Photo Organizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 250);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        selectButton = new JButton("Select Folder");
        organizeButton = new JButton("Start Organizing");
        organizeButton.setEnabled(false);
        buttonPanel.add(selectButton);
        buttonPanel.add(organizeButton);

        JPanel progressPanel = new JPanel(new BorderLayout(5, 10));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, 25));

        statusLabel = new JLabel("Select a folder to begin", SwingConstants.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(12.0f));

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
        organizeButton.addActionListener(e -> {
            if (!isOrganizing) {
                startOrganizing();
            } else {
                int choice = JOptionPane.showConfirmDialog(frame,
                    "Organization in progress. Do you want to cancel?",
                    "Cancel Organization",
                    JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    isOrganizing = false;
                }
            }
        });
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Photos Folder");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = chooser.getSelectedFile();
            statusLabel.setText("Selected: " + selectedDirectory.getPath());
            organizeButton.setEnabled(true);
        }
    }

    private void startOrganizing() {
        if (selectedDirectory == null) return;

        isOrganizing = true;
        selectButton.setEnabled(false);
        organizeButton.setText("Cancel");
        progressBar.setValue(0);
        statusLabel.setText("Scanning for images...");

        SwingWorker<Map<String, Integer>, PhotoProgress> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Integer> doInBackground() throws Exception {
                Map<String, Integer> stats = new HashMap<>();
                stats.put("processed", 0);
                stats.put("errors", 0);

                List<Path> imageFiles = new ArrayList<>();
                try {
                    Files.walk(selectedDirectory.toPath())
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            boolean isImage = isImageFile(path.toString());
                            if (isImage) {
                                System.out.println("Found image: " + path);
                            }
                            return isImage;
                        })
                        .forEach(imageFiles::add);

                    int total = imageFiles.size();
                    if (total == 0) {
                        publish(new PhotoProgress(0, 0, "No image files found"));
                        return stats;
                    }

                    for (int i = 0; i < total && isOrganizing; i++) {
                        Path imagePath = imageFiles.get(i);
                        try {
                            processImage(imagePath, i, total);
                            stats.put("processed", stats.get("processed") + 1);
                        } catch (Exception e) {
                            stats.put("errors", stats.get("errors") + 1);
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    publish(new PhotoProgress(-1, -1, "Error scanning directory: " + e.getMessage()));
                }
                return stats;
            }

            private void processImage(Path imagePath, int current, int total) throws IOException, InterruptedException {
                File imageFile = imagePath.toFile();
                Date photoDate = getPhotoDate(imageFile);
                String yearMonth = new SimpleDateFormat("yyyy/MM").format(photoDate);
                
                Path targetDir = selectedDirectory.toPath().resolve(yearMonth);
                Files.createDirectories(targetDir);

                Path targetPath = targetDir.resolve(imagePath.getFileName());
                String fileName = imagePath.getFileName().toString();
                
                // Handle duplicate filenames
                int counter = 1;
                while (Files.exists(targetPath)) {
                    String newName = fileName.replaceFirst("(\\.[^.]+)$", "_" + counter + "$1");
                    targetPath = targetDir.resolve(newName);
                    counter++;
                }

                Files.move(imagePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
                publish(new PhotoProgress(current + 1, total, 
                    String.format("Processed: %s → %s", fileName, yearMonth)));
                
                Thread.sleep(50); // Small delay to prevent UI freezing
            }

            @Override
            protected void process(List<PhotoProgress> chunks) {
                if (!chunks.isEmpty()) {
                    PhotoProgress latest = chunks.get(chunks.size() - 1);
                    if (latest.total > 0) {
                        int progress = (latest.current * 100) / latest.total;
                        progressBar.setValue(progress);
                    }
                    statusLabel.setText(String.format("%s (%d/%d)", 
                        latest.message, latest.current, latest.total));
                }
            }

            @Override
            protected void done() {
                try {
                    Map<String, Integer> stats = get();
                    String message = String.format("Organization complete!\nProcessed: %d files\nErrors: %d",
                        stats.get("processed"), stats.get("errors"));
                    JOptionPane.showMessageDialog(frame, message, "Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "An error occurred: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
                
                isOrganizing = false;
                selectButton.setEnabled(true);
                organizeButton.setText("Start Organizing");
                organizeButton.setEnabled(true);
                statusLabel.setText("Select a folder to begin");
            }
        };

        worker.execute();
    }

    private boolean isImageFile(String name) {
        return SUPPORTED_FORMATS.stream()
            .anyMatch(format -> name.toLowerCase().endsWith(format));
    }

    private Date getPhotoDate(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            
            // Try all available directories for date information
            for (com.drew.metadata.Directory directory : metadata.getDirectories()) {
                // Try various date tags in order of preference
                for (int tagType : new int[] {
                    ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                    ExifSubIFDDirectory.TAG_DATETIME,
                    ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED
                }) {
                    try {
                        Date date = directory.getDate(tagType);
                        if (date != null) return date;
                    } catch (Exception ignored) {
                        // Continue to next tag if this one fails
                    }
                }
            }
        } catch (ImageProcessingException | IOException e) {
            System.out.println("Warning: Could not read metadata from " + file.getName() + ": " + e.getMessage());
            // Fallback to file dates
        }
        
        // Try creation time first, then fall back to modified time
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            FileTime creationTime = attrs.creationTime();
            if (creationTime != null) {
                return new Date(creationTime.toMillis());
            }
        } catch (IOException e) {
            // Fall back to last modified date
        }
        
        return new Date(file.lastModified());
    }

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
