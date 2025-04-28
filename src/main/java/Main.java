
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
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

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File[] files = selectedDirectory.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".jpg") || 
                    name.toLowerCase().endsWith(".jpeg") || 
                    name.toLowerCase().endsWith(".png"));

                if (files == null) return null;

                int total = files.length;
                int current = 0;

                for (File file : files) {
                    current++;
                    String dateStr = getImageDate(file);
                    File targetDir = new File(selectedDirectory, dateStr);
                    targetDir.mkdirs();

                    try {
                        Path source = file.toPath();
                        Path target = new File(targetDir, file.getName()).toPath();
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    int progress = (current * 100) / total;
                    publish(current, total);
                    setProgress(progress);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int current = chunks.get(0);
                int total = chunks.get(1);
                statusLabel.setText(String.format("Current: %d/%d files moved", current, total));
            }

            @Override
            protected void done() {
                JOptionPane.showMessageDialog(frame, "Organizing Complete!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                selectButton.setEnabled(true);
                organizeButton.setEnabled(true);
                statusLabel.setText("Select a folder to begin");
                progressBar.setValue(0);
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });

        worker.execute();
    }

    private String getImageDate(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            
            if (directory != null && directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                return new SimpleDateFormat("yyyy-MM-dd").format(date);
            }
        } catch (Exception e) {
            // Fallback to file modification date if EXIF reading fails
        }
        
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(file.lastModified()));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
