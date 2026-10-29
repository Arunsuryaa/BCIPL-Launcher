package com.bci.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class Main extends JFrame {

    private static final String APP_VERSION = getInstalledVersion(); // Keep this aligned with the published release.
    private static final Color BG = new Color(10, 16, 31);
    private static final Color PANEL = new Color(18, 27, 47);
    private static final Color PANEL_ALT = new Color(23, 35, 59);
    private static final Color BLUE = new Color(48, 125, 246);
    private static final Color BLUE_HOVER = new Color(35, 103, 215);
    private static final Color TEXT = new Color(240, 245, 255);
    private static final Color MUTED = new Color(155, 169, 194);
    private static final Color BORDER = new Color(42, 57, 83);
    private static final Color TITLE_BG = new Color(7, 12, 24);   // Custom dark title bar colour.
    private static final Color CLOSE_HOVER = new Color(196, 43, 28);
    private static final int TITLE_BAR_HEIGHT = 36;
    private static final int BUTTON_RADIUS = 8;            // Corner roundness of all buttons (bigger = rounder).
    private static final int HEADER_LOGO_SIZE = 96; // The logo artwork is square and cropped tight, so it fills this box.

    private final InstallationConfigService configService = new InstallationConfigService();
    private final CreoDetector detector = new CreoDetector();
    private final CreoLauncher launcher = new CreoLauncher();
    private final GitHubUpdateChecker updateChecker = new GitHubUpdateChecker();
    private final JPanel installationList = new JPanel();
    private final JLabel statusLabel = new JLabel("Ready to launch");
    private final JButton scanButton = new RoundedButton("Rescan Drives", BUTTON_RADIUS);
    private SwingWorker<List<CreoInstallation>, String> activeScan;

    private static String getInstalledVersion() {
        // Get the version embedded by jpackage.
        String version = System.getProperty("jpackage.app-version");

        if (version != null && !version.isBlank()) {
            return version.trim();
        }

        // Fallback when running from a versioned JAR.
        Package appPackage = Main.class.getPackage();

        if (appPackage != null) {
            version = appPackage.getImplementationVersion();

            if (version != null && !version.isBlank()) {
                return version.trim();
            }
        }

        // NetBeans development run: packaged version is unavailable.
        return "DEV";
    }

    public Main() {
        super("BCIPL Launcher");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true); // We draw our own dark title bar (minimize + close only), so no white Windows bar and no maximize.
        setResizable(false);
        setSize(920, 650);
        setLocationRelativeTo(null);
        Image appIcon = loadLogo();
        if (appIcon != null) {
            // The logo is used for the taskbar and Alt+Tab only; the custom title bar below shows text only.
            setIconImages(List.of(scaleSmooth(appIcon, 16), scaleSmooth(appIcon, 32),
                    scaleSmooth(appIcon, 64), scaleSmooth(appIcon, 128), scaleSmooth(appIcon, 256)));
        }

        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(20, 30, 20, 30));
        JPanel frameContent = new JPanel(new BorderLayout());
        frameContent.setBackground(BG);
        frameContent.setBorder(new LineBorder(BORDER, 1));
        frameContent.add(createTitleBar(), BorderLayout.NORTH);
        frameContent.add(root, BorderLayout.CENTER);
        setContentPane(frameContent);
        root.add(createHeader(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        JPanel sectionHeader = new JPanel(new BorderLayout(10, 4));
        sectionHeader.setOpaque(false);
        JLabel sectionTitle = new JLabel("Available Creo Installations");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        sectionTitle.setForeground(TEXT);
        JLabel sectionHint = new JLabel("Select an installed version to open your design workspace.");
        sectionHint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sectionHint.setForeground(MUTED);
        JPanel sectionText = new JPanel();
        sectionText.setOpaque(false);
        sectionText.setLayout(new BoxLayout(sectionText, BoxLayout.Y_AXIS));
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionText.add(sectionTitle);
        sectionText.add(Box.createVerticalStrut(5));
        sectionText.add(sectionHint);
        sectionHeader.add(sectionText, BorderLayout.CENTER);
        content.add(sectionHeader, BorderLayout.NORTH);

        installationList.setLayout(new BoxLayout(installationList, BoxLayout.Y_AXIS));
        installationList.setBackground(BG);
        installationList.setBorder(new EmptyBorder(2, 1, 8, 4));
        JScrollPane scroll = new JScrollPane(installationList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(BG);
        scroll.getViewport().setOpaque(true);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(65, 86, 123);
                this.trackColor = BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
        scroll.getViewport().addChangeListener(e -> {
            int width = scroll.getViewport().getWidth();
            if (width > 0) {
                installationList.setPreferredSize(new Dimension(width,
                        installationList.getPreferredSize().height));
                installationList.revalidate();
            }
        });
        content.add(scroll, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);

        scanButton.addActionListener(e -> scanDrives());
        loadSavedInstallations();
        checkForUpdatesInBackground();
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(TITLE_BG);
        bar.setPreferredSize(new Dimension(0, TITLE_BAR_HEIGHT));

        JLabel title = new JLabel("BCIPL Launcher");
        title.setFont(new Font("SansSerif", Font.PLAIN, 12));
        title.setForeground(new Color(200, 210, 228));
        title.setBorder(new EmptyBorder(0, 14, 0, 0));

        TitleButton minimize = new TitleButton(false, new Color(35, 50, 77));
        minimize.setToolTipText("Minimize");
        minimize.addActionListener(e -> setState(Frame.ICONIFIED));
        TitleButton close = new TitleButton(true, CLOSE_HOVER);
        close.setToolTipText("Close");
        close.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        controls.add(minimize);
        controls.add(close);

        bar.add(title, BorderLayout.CENTER);
        bar.add(controls, BorderLayout.EAST);

        // Drag the window by its title bar.
        MouseAdapter drag = new MouseAdapter() {
            private Point offset;

            @Override
            public void mousePressed(MouseEvent e) {
                offset = new Point(e.getXOnScreen() - Main.this.getX(), e.getYOnScreen() - Main.this.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (offset != null) {
                    Main.this.setLocation(e.getXOnScreen() - offset.x, e.getYOnScreen() - offset.y);
                }
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        return bar;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setOpaque(false);
        JPanel brand = new JPanel(new BorderLayout(15, 0));
        brand.setOpaque(false);
        JLabel logo = new JLabel();
        logo.setPreferredSize(new Dimension(HEADER_LOGO_SIZE, HEADER_LOGO_SIZE));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setVerticalAlignment(SwingConstants.CENTER);
        Image image = loadLogo();
        if (image != null) {
            logo.setIcon(new ImageIcon(crispImage(image, HEADER_LOGO_SIZE)));
        } else {
            logo.setText("BCI");
            logo.setFont(new Font("SansSerif", Font.BOLD, 22));
            logo.setForeground(TEXT);
        }
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("BCIPL Launcher");
        title.setFont(new Font("SansSerif", Font.BOLD, 29));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Your workspace for launching PTC Creo");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        titles.add(Box.createVerticalGlue());
        titles.add(title);
        titles.add(Box.createVerticalStrut(6));
        titles.add(subtitle);
        titles.add(Box.createVerticalGlue());
        brand.add(logo, BorderLayout.WEST);
        brand.add(titles, BorderLayout.CENTER);

        JLabel version = new JLabel("VERSION " + APP_VERSION);
        version.setFont(new Font("SansSerif", Font.BOLD, 10));
        version.setForeground(new Color(157, 194, 255));
        version.setOpaque(true);
        version.setBackground(new Color(25, 49, 85));
        version.setBorder(new EmptyBorder(7, 10, 7, 10));
        JPanel versionWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        versionWrap.setOpaque(false);
        versionWrap.add(version);
        header.add(brand, BorderLayout.CENTER);
        header.add(versionWrap, BorderLayout.EAST);
        return header;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
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
        String[] resources = {"/resources/bci-icon_1.png", "/bci-icon_1.png"};
        for (String resourcePath : resources) {
            URL resource = getClass().getResource(resourcePath);
            if (resource != null) {
                return new ImageIcon(resource).getImage();
            }
        }
        String[] files = {"resources/bci-icon_1.png", "src/resources/bci-icon_1.png"};
        for (String file : files) {
            try {
                Path path = Paths.get(file);
                if (Files.isRegularFile(path)) {
                    return new ImageIcon(path.toAbsolutePath().toString()).getImage();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Downscales in halving steps with bicubic filtering so small logos stay
     * sharp instead of blurry or jagged.
     */
    private static Image scaleSmooth(Image source, int size) {
        int w = source.getWidth(null);
        int h = source.getHeight(null);
        if (w <= 0 || h <= 0) {
            return source;
        }
        BufferedImage current = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = current.createGraphics();
        g0.drawImage(source, 0, 0, null);
        g0.dispose();
        int target = size;
        int cw = w;
        int ch = h;
        while (cw > target * 2 || ch > target * 2) {
            cw = Math.max(target, cw / 2);
            ch = Math.max(target, ch / 2);
            current = drawScaled(current, cw, ch);
        }
        return drawScaled(current, target, target);
    }

    /**
     * Builds a multi-resolution image so Windows display scaling (125%, 150%,
     * 200%...) picks a pre-scaled bitmap instead of stretching a small one,
     * which is what makes logos look blurry.
     */
    private static Image crispImage(Image source, int size) {
        int[] sizes = {size, Math.round(size * 1.25f), Math.round(size * 1.5f), size * 2, size * 3};
        Image[] variants = new Image[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            variants[i] = scaleSmooth(source, sizes[i]);
        }
        return new BaseMultiResolutionImage(variants);
    }

    private static BufferedImage drawScaled(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void loadSavedInstallations() {
        try {
            List<CreoInstallation> saved = configService.load();
            if (!saved.isEmpty()) {
                showInstallations(saved);
                statusLabel.setText("Ready · " + saved.size() + " installation(s) found");
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
        activeScan = new SwingWorker<>() {
            @Override
            protected List<CreoInstallation> doInBackground() {
                return detector.scanAllAccessibleDrives(new CreoDetector.ScanListener() {
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
                    statusLabel.setText("Scan complete · " + installations.size() + " installation(s) found");
                } catch (Exception ex) {
                    statusLabel.setText("Could not complete the scan.");
                    JOptionPane.showMessageDialog(Main.this, rootMessage(ex), "BCIPL Launcher", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        activeScan.execute();
    }

    private void showInstallations(List<CreoInstallation> installations) {
        installationList.removeAll();
        if (installations.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout());
            empty.setBackground(PANEL);
            empty.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(25, 18, 25, 18)));
            JLabel label = new JLabel("<html><div style='text-align:center'><b>No Creo installations found</b><br><br>Check your installation or select Rescan Drives to search again.</div></html>");
            label.setForeground(MUTED);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            empty.add(label, BorderLayout.CENTER);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
            installationList.add(empty);
        } else {
            for (int i = 0; i < installations.size(); i++) {
                JPanel row = createInstallationRow(installations.get(i));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                installationList.add(row);
                if (i < installations.size() - 1) {
                    installationList.add(Box.createVerticalStrut(12));
                }
            }
        }
        installationList.revalidate();
        installationList.repaint();
    }

    private JPanel createInstallationRow(CreoInstallation installation) {
        JPanel row = new JPanel(new BorderLayout(16, 8));
        row.setBackground(PANEL);
        row.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(16, 18, 16, 18)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));
        row.setMinimumSize(new Dimension(0, 88));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("Creo Parametric " + installation.getVersion());
        name.setFont(new Font("SansSerif", Font.BOLD, 16));
        name.setForeground(TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel available = new JLabel(installation.isAvailable() ? "●  Installation available" : "●  Installation unavailable");
        available.setFont(new Font("SansSerif", Font.PLAIN, 11));
        available.setForeground(installation.isAvailable() ? new Color(102, 220, 166) : new Color(255, 135, 135));
        available.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextArea path = new JTextArea(installation.getExecutablePath());
        path.setFont(new Font("SansSerif", Font.PLAIN, 11));
        path.setForeground(MUTED);
        path.setBackground(PANEL);
        path.setEditable(false);
        path.setFocusable(false);
        path.setLineWrap(true);
        path.setWrapStyleWord(true);
        path.setOpaque(false);
        path.setBorder(BorderFactory.createEmptyBorder());
        path.setRows(2);
        path.setAlignmentX(Component.LEFT_ALIGNMENT);
        path.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        details.add(name);
        details.add(Box.createVerticalStrut(5));
        details.add(available);
        details.add(Box.createVerticalStrut(5));
        details.add(path);

        JButton launchButton = new RoundedButton("Launch Creo  →", BUTTON_RADIUS);
        launchButton.setEnabled(installation.isAvailable());
        styleButton(launchButton, true);
        launchButton.setToolTipText(installation.isAvailable() ? "Launch Creo " + installation.getVersion() : "This installation is currently unavailable");
        launchButton.addActionListener(e -> {
            try {
                launcher.launch(installation);
                statusLabel.setText("Creo " + installation.getVersion() + " launched");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to launch Creo", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Could not launch Creo.");
            }
        });
        JPanel buttonContainer = new JPanel(new GridBagLayout());
        buttonContainer.setOpaque(false);
        buttonContainer.add(launchButton);
        row.add(details, BorderLayout.CENTER);
        row.add(buttonContainer, BorderLayout.EAST);
        return row;
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);            // RoundedButton paints its own rounded background.
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        if (primary) {
            button.setBackground(BLUE);
            button.setForeground(Color.WHITE);
            button.setBorder(new EmptyBorder(12, 17, 12, 17));
            button.addChangeListener(e -> {
                if (!button.isEnabled()) {
                    button.setBackground(new Color(71, 83, 105));
                } else if (button.getModel().isPressed() || button.getModel().isRollover()) {
                    button.setBackground(BLUE_HOVER);
                } else {
                    button.setBackground(BLUE);
                }
            });
        } else {
            button.setBackground(PANEL_ALT);
            button.setForeground(TEXT);
            button.setBorder(new EmptyBorder(10, 15, 10, 15));
            if (button instanceof RoundedButton) {
                ((RoundedButton) button).setOutlineColor(BORDER);
            }
            button.addChangeListener(e -> button.setBackground(button.getModel().isRollover() ? new Color(35, 50, 77) : PANEL_ALT));
        }
    }

    /**
     * Flat minimize / close button for the custom title bar, drawn in code so
     * it needs no font or icon files.
     */
    private static final class TitleButton extends JButton {

        private final boolean closeButton;
        private final Color hoverColor;

        TitleButton(boolean closeButton, Color hoverColor) {
            this.closeButton = closeButton;
            this.hoverColor = hoverColor;
            setPreferredSize(new Dimension(46, TITLE_BAR_HEIGHT));
            setFocusable(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean active = getModel().isRollover() || getModel().isPressed();
            if (active) {
                g2.setColor(hoverColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(TEXT);
            g2.setStroke(new BasicStroke(1.3f));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            if (closeButton) {
                g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
            } else {
                g2.drawLine(cx - 5, cy, cx + 5, cy);
            }
            g2.dispose();
        }
    }

    /**
     * A button that paints a smooth rounded background (and optional outline)
     * instead of a sharp rectangle.
     */
    private static final class RoundedButton extends JButton {

        private final int radius;
        private Color outlineColor;

        RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
        }

        void setOutlineColor(Color color) {
            this.outlineColor = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            if (outlineColor != null) {
                g2.setColor(outlineColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
            }
            g2.dispose();
            super.paintComponent(g); // Draws the text only, because the content area is not filled.
        }
    }

    private void checkForUpdatesInBackground() {
        SwingWorker<GitHubUpdateChecker.UpdateInfo, Void> worker = new SwingWorker<>() {
            @Override
            protected GitHubUpdateChecker.UpdateInfo doInBackground()
                    throws Exception {

                System.out.println("Installed version: " + APP_VERSION);

                if ("DEV".equalsIgnoreCase(APP_VERSION)) {
                    throw new IllegalStateException(
                            "Packaged version unavailable. "
                            + "Run the installed BCIPL Launcher EXE to test updates.");
                }

                return updateChecker.check(APP_VERSION);
            }

            @Override
            protected void done() {
                try {
                    GitHubUpdateChecker.UpdateInfo info = get();
                    System.out.println("Installed version: " + APP_VERSION);
                    System.out.println("Latest version: " + info.getLatestVersion());
                    System.out.println("Update available: " + info.isUpdateAvailable());
                    System.out.println("Download URL: " + info.getDownloadUrl());
                    if (info.isUpdateAvailable()) {
                        showUpdateDialog(info);
                    }
                } catch (Exception ex) {
                    // Updates are optional: network/API errors must not prevent launching Creo.
                    System.err.println("BCIPL update check skipped: " + rootMessage(ex));
                }
            }
        };
        worker.execute();
    }

    private void showUpdateDialog(GitHubUpdateChecker.UpdateInfo info) {
        Object[] options = {"Download Update", "Remind Me Later"};
        int choice = JOptionPane.showOptionDialog(this,
                "A newer version of BCIPL Launcher is available.\n\n"
                + "Installed version: " + APP_VERSION + "\n"
                + "Latest version: " + info.getLatestVersion() + "\n\n"
                + "Download the latest release to get the newest improvements and fixes.",
                "BCIPL Launcher Update Available",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            try {
                GitHubUpdateChecker.openDownload(info.getDownloadUrl());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not open the download page. Please visit:\nhttps://github.com/Arunsuryaa/BCIPL-Launcher/releases/latest",
                        "Download Update", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new Main().setVisible(true);
        });
    }
}
