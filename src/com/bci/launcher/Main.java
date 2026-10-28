
package com.bci.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class Main extends JFrame {

    private static final Color PRIMARY = new Color(31, 91, 160);
    private static final Color PRIMARY_HOVER = new Color(23, 72, 130);
    private static final Color BACKGROUND = new Color(245, 248, 252);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color TEXT = new Color(38, 49, 64);
    private static final Color MUTED = new Color(105, 117, 132);
    private static final Color BORDER = new Color(224, 231, 240);

    private final InstallationConfigService configService =
            new InstallationConfigService();
    private final CreoDetector detector = new CreoDetector();
    private final CreoLauncher launcher = new CreoLauncher();

    private final JPanel installationList = new JPanel();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JButton scanButton = new JButton("Rescan Drives");

    private SwingWorker<List<CreoInstallation>, String> activeScan;

    public Main() {
        super("BCIPL Launcher");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(640, 440));
        setSize(760, 540);
        setLocationRelativeTo(null);

        setBackground(BACKGROUND);
        setIconImage(loadLogo());

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));
        setContentPane(root);

        // Header
        root.add(createHeader(), BorderLayout.NORTH);

        // Installation section
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(BACKGROUND);

        JLabel sectionTitle = new JLabel("Available Creo Installations");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        sectionTitle.setForeground(TEXT);
        content.add(sectionTitle, BorderLayout.NORTH);

        installationList.setLayout(new BoxLayout(
                installationList, BoxLayout.Y_AXIS));
        installationList.setBackground(BACKGROUND);
        installationList.setBorder(new EmptyBorder(2, 1, 8, 5));

        JScrollPane scroll = new JScrollPane(installationList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BACKGROUND);
        scroll.getViewport().setBackground(BACKGROUND);

        // Prevent horizontal scrolling.
        scroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Keep the list fitted to the available viewport width.
        scroll.getViewport().addChangeListener(e -> {
            int width = scroll.getViewport().getWidth();
            if (width > 0) {
                installationList.setPreferredSize(
                        new Dimension(width, installationList.getPreferredSize().height));
                installationList.revalidate();
            }
        });

        content.add(scroll, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);

        // Footer
        root.add(createFooter(), BorderLayout.SOUTH);

        scanButton.addActionListener(e -> scanDrives());

        loadSavedInstallations();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(BACKGROUND);

        JLabel logo = new JLabel();
        logo.setPreferredSize(new Dimension(64, 64));
        logo.setHorizontalAlignment(SwingConstants.CENTER);

        Image image = loadLogo();
        if (image != null) {
            Image scaled = image.getScaledInstance(
                    60, 60, Image.SCALE_SMOOTH);
            logo.setIcon(new ImageIcon(scaled));
        } else {
            logo.setText("BCI");
            logo.setFont(new Font("SansSerif", Font.BOLD, 21));
            logo.setForeground(PRIMARY);
        }

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setBackground(BACKGROUND);

        JLabel title = new JLabel("BCIPL Launcher");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(
                "Your workspace for launching PTC Creo");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titles.add(Box.createVerticalGlue());
        titles.add(title);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);
        titles.add(Box.createVerticalGlue());

        header.add(logo, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);

        return header;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(BACKGROUND);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);

        styleButton(scanButton, false);
        scanButton.setToolTipText("Search accessible drives for Creo installations");

        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(scanButton, BorderLayout.EAST);

        return footer;
    }

    private Image loadLogo() {
        // Recommended location: src/resources/bci-icon_1.png
        String[] resourcePaths = {
            "/resources/bci-icon_1.png",
            "/bci-icon_1.png"
        };

        for (String resourcePath : resourcePaths) {
            URL resource = getClass().getResource(resourcePath);
            if (resource != null) {
                return new ImageIcon(resource).getImage();
            }
        }

        // Also support a resources folder at the project root.
        String[] filePaths = {
            "resources/bci-icon_1.png",
            "src/resources/bci-icon_1.png"
        };

        for (String filePath : filePaths) {
            try {
                Path path = Paths.get(filePath);
                if (Files.isRegularFile(path)) {
                    return new ImageIcon(path.toAbsolutePath().toString())
                            .getImage();
                }
            } catch (Exception ignored) {
                // Try the next location.
            }
        }

        return null;
    }

    private void loadSavedInstallations() {
        try {
            List<CreoInstallation> saved = configService.load();

            if (!saved.isEmpty()) {
                showInstallations(saved);
                statusLabel.setText(
                        "Ready · " + saved.size() + " installation(s) found");
            } else {
                scanDrives();
            }
        } catch (IOException ex) {
            statusLabel.setText("Searching for Creo installations...");
            scanDrives();
        }
    }

    private void scanDrives() {
        if (activeScan != null && !activeScan.isDone()) {
            statusLabel.setText("A scan is already running.");
            return;
        }

        scanButton.setEnabled(false);
        scanButton.setText("Scanning...");
        statusLabel.setText("Scanning accessible drives...");

        activeScan = new SwingWorker<List<CreoInstallation>, String>() {

            @Override
            protected List<CreoInstallation> doInBackground() {
                return detector.scanAllAccessibleDrives(
                        new CreoDetector.ScanListener() {
                    @Override
                    public void onProgress(String message) {
                        publish(message);
                    }

                    @Override
                    public boolean isCancelled() {
                        return Thread.currentThread().isInterrupted();
                    }
                });
            }

            @Override
            protected void process(List<String> messages) {
                if (!messages.isEmpty()) {
                    statusLabel.setText(messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                scanButton.setEnabled(true);
                scanButton.setText("Rescan Drives");

                try {
                    List<CreoInstallation> installations = get();

                    configService.save(installations);
                    showInstallations(installations);

                    statusLabel.setText(
                            "Scan complete · " + installations.size()
                            + " installation(s) found");

                } catch (Exception ex) {
                    statusLabel.setText("Could not complete the scan.");

                    JOptionPane.showMessageDialog(
                            Main.this,
                            rootMessage(ex),
                            "BCIPL Launcher",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        };

        activeScan.execute();
    }

    private void showInstallations(
            List<CreoInstallation> installations) {

        installationList.removeAll();

        if (installations.isEmpty()) {
            JPanel emptyPanel = new JPanel(new BorderLayout());
            emptyPanel.setBackground(CARD_BACKGROUND);
            emptyPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER, 1, true),
                    new EmptyBorder(22, 18, 22, 18)));

            JLabel emptyLabel = new JLabel(
                    "<html><div style='text-align:center'>"
                    + "<b>No Creo installations found</b><br><br>"
                    + "Check your installation or select "
                    + "Rescan Drives to search again."
                    + "</div></html>");

            emptyLabel.setForeground(MUTED);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyPanel.add(emptyLabel, BorderLayout.CENTER);
            emptyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyPanel.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, 120));

            installationList.add(emptyPanel);
        } else {
            for (int i = 0; i < installations.size(); i++) {
                JPanel row = createInstallationRow(installations.get(i));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                installationList.add(row);

                if (i < installations.size() - 1) {
                    installationList.add(Box.createVerticalStrut(10));
                }
            }
        }

        installationList.revalidate();
        installationList.repaint();
    }

    private JPanel createInstallationRow(
            CreoInstallation installation) {

        JPanel row = new JPanel(new BorderLayout(12, 8));
        row.setBackground(CARD_BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(13, 15, 13, 15)));

        // Allow rows to grow horizontally, but not beyond the viewport.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
        row.setMinimumSize(new Dimension(0, 80));

        JPanel details = new JPanel();
        details.setBackground(CARD_BACKGROUND);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(
                "Creo Parametric " + installation.getVersion());
        name.setFont(new Font("SansSerif", Font.BOLD, 15));
        name.setForeground(TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea path = new JTextArea(
                installation.getExecutablePath());
        path.setFont(new Font("SansSerif", Font.PLAIN, 11));
        path.setForeground(MUTED);
        path.setBackground(CARD_BACKGROUND);
        path.setEditable(false);
        path.setFocusable(false);
        path.setLineWrap(true);
        path.setWrapStyleWord(true);
        path.setOpaque(false);
        path.setBorder(BorderFactory.createEmptyBorder());
        path.setRows(2);
        path.setAlignmentX(Component.LEFT_ALIGNMENT);
        path.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        details.add(name);
        details.add(Box.createVerticalStrut(6));
        details.add(path);

        JButton launchButton = new JButton("Launch Now");
        launchButton.setEnabled(installation.isAvailable());
        styleButton(launchButton, true);

        launchButton.setToolTipText(
                installation.isAvailable()
                ? "Launch Creo " + installation.getVersion()
                : "This installation is currently unavailable");

        launchButton.addActionListener(e -> {
            try {
                launcher.launch(installation);
                statusLabel.setText(
                        "Creo " + installation.getVersion() + " launched");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Unable to launch Creo",
                        JOptionPane.ERROR_MESSAGE);

                statusLabel.setText("Could not launch Creo.");
            }
        });

        JPanel buttonContainer = new JPanel(new GridBagLayout());
        buttonContainer.setBackground(CARD_BACKGROUND);
        buttonContainer.add(launchButton);

        row.add(details, BorderLayout.CENTER);
        row.add(buttonContainer, BorderLayout.EAST);

        return row;
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);

        if (primary) {
            button.setBackground(PRIMARY);
            button.setForeground(Color.WHITE);
            button.setBorder(new EmptyBorder(10, 16, 10, 16));

            button.addChangeListener(e -> {
                if (!button.isEnabled()) {
                    button.setBackground(new Color(180, 191, 205));
                } else if (button.getModel().isPressed()) {
                    button.setBackground(PRIMARY_HOVER);
                } else if (button.getModel().isRollover()) {
                    button.setBackground(PRIMARY_HOVER);
                } else {
                    button.setBackground(PRIMARY);
                }
            });
        } else {
            button.setBackground(new Color(231, 239, 250));
            button.setForeground(PRIMARY);
            button.setBorder(new EmptyBorder(9, 14, 9, 14));

            button.addChangeListener(e -> {
                if (button.getModel().isRollover()) {
                    button.setBackground(new Color(213, 228, 247));
                } else {
                    button.setBackground(new Color(231, 239, 250));
                }
            });
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null
                ? current.toString()
                : current.getMessage();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Use the default Swing look and feel.
            }

            new Main().setVisible(true);
        });
    }
}
