package ch.framedev.explorer.ui;

import ch.framedev.explorer.model.FileTableModel;
import ch.framedev.explorer.model.FileTreeNode;
import ch.framedev.explorer.util.ExplorerSettings;
import ch.framedev.explorer.util.FileOperations;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FileExplorerFrame extends JFrame {
    private enum OperatingSystem {
        WINDOWS,
        MAC,
        LINUX,
        OTHER
    }

    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

    private final JTextField pathField = new JTextField();
    private final JTextField searchField = new JTextField();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JTextArea detailsArea = new JTextArea("Select a file or folder to view details.");
    private final JComboBox<UiDensity> densityBox = new JComboBox<>(UiDensity.values());
    private final JComboBox<UiScale> scaleBox = new JComboBox<>(UiScale.values());
    private final JComboBox<UiTheme> themeBox = new JComboBox<>(UiTheme.values());
    private final JComboBox<Path> favoritesBox = new JComboBox<>();
    private final DefaultListModel<Path> favoritesListModel = new DefaultListModel<>();
    private final JList<Path> favoritesList = new JList<>(favoritesListModel);
    private final JButton addFavoriteButton = new JButton("Add Current");
    private final JButton removeFavoriteButton = new JButton("Remove");
    private JPanel topPanel;
    private JPanel detailsPanel;
    private JPanel statusPanel;
    private JPanel favoritesPanel;
    private JSplitPane horizontalSplit;
    private JSplitPane verticalSplit;
    private JSplitPane leftSplit;
    private JLabel brandLabel;
    private JLabel brandSubtitleLabel;
    private JLabel favoritesTitleLabel;
    private JLabel detailsTitleLabel;

    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final ExplorerTreeRenderer treeRenderer;

    private final FileTableModel tableModel = new FileTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(tableModel);

    private final Deque<Path> backStack = new ArrayDeque<>();
    private final Deque<Path> forwardStack = new ArrayDeque<>();
    private final Deque<Path> recentDirectories = new ArrayDeque<>();

    private final List<Path> clipboardEntries = new ArrayList<>();
    private boolean clipboardCut;
    private boolean showHiddenFiles;
    private boolean updatingFavoritesUI;
    private UiDensity uiDensity = UiDensity.COMFORTABLE;
    private UiScale uiScale = UiScale.S100;
    private UiTheme uiTheme = UiTheme.OCEAN;
    private String lookAndFeelClassName = "";

    private Path currentDirectory;

    private final JButton backButton = new JButton("Back");
    private final JButton forwardButton = new JButton("Forward");
    private final ExplorerSettings settings;
    private JMenu recentMenu;
    private JCheckBoxMenuItem detailsToggleMenuItem;
    private boolean detailsVisible = true;

    public FileExplorerFrame() {
        super("Frame Explorer");
        settings = ExplorerSettings.load();
        uiDensity = parseEnum(UiDensity.class, settings.density, UiDensity.COMFORTABLE);
        uiScale = parseEnum(UiScale.class, settings.scale, UiScale.S100);
        uiTheme = parseEnum(UiTheme.class, settings.theme, UiTheme.OCEAN);
        lookAndFeelClassName = settings.lookAndFeelClassName == null || settings.lookAndFeelClassName.isBlank()
                ? UIManager.getLookAndFeel().getClass().getName()
                : settings.lookAndFeelClassName;
        showHiddenFiles = settings.showHiddenFiles;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Math.max(800, settings.windowWidth), Math.max(600, settings.windowHeight));
        setMinimumSize(new Dimension(980, 620));
        if (settings.windowX >= 0 && settings.windowY >= 0) {
            setLocation(settings.windowX, settings.windowY);
        } else {
            setLocationRelativeTo(null);
        }
        getContentPane().setBackground(uiTheme.surface);

        DefaultMutableTreeNode virtualRoot = new DefaultMutableTreeNode("Computer");
        treeModel = new DefaultTreeModel(virtualRoot);
        tree = new JTree(treeModel);
        treeRenderer = new ExplorerTreeRenderer(fileSystemView);

        buildMenuBar();
        buildTopPanel();
        buildTree(virtualRoot);
        buildTable();
        installKeyboardShortcuts();

        JScrollPane treePane = new JScrollPane(tree);
        treePane.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setBorder(BorderFactory.createEmptyBorder());

        JPanel leftPanel = buildLeftPanel(treePane);

        horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tablePane);
        horizontalSplit.setResizeWeight(0.25);
        horizontalSplit.setOneTouchExpandable(true);
        horizontalSplit.setContinuousLayout(true);
        horizontalSplit.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        horizontalSplit.setDividerSize(8);

        JPanel detailsPanel = buildDetailsPanel();

        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplit, detailsPanel);
        verticalSplit.setResizeWeight(0.82);
        verticalSplit.setOneTouchExpandable(true);
        verticalSplit.setContinuousLayout(true);
        verticalSplit.setBorder(BorderFactory.createEmptyBorder());
        verticalSplit.setDividerSize(6);

        add(verticalSplit, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                persistSettings();
            }
        });

        Path home = Path.of(System.getProperty("user.home"));
        Path initialDirectory = resolveInitialDirectory(home);
        navigateTo(initialDirectory, false);
        updateHistoryButtons();
        applyTheme(uiTheme);
        applyDensity(uiDensity);
        if (settings.maximized) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        SwingUtilities.invokeLater(() -> {
            if (horizontalSplit != null) {
                horizontalSplit.setDividerLocation(clampRatio(settings.horizontalSplitRatio));
            }
            if (verticalSplit != null) {
                verticalSplit.setDividerLocation(clampRatio(settings.verticalSplitRatio));
            }
            if (leftSplit != null) {
                leftSplit.setDividerLocation(clampRatio(settings.leftSplitRatio));
            }
        });
    }

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem open = createMenuItem("Open", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), e -> {
            Path selected = getSingleSelectedPath();
            if (selected != null) {
                openPath(selected);
            }
        });
        JMenuItem openWith = createMenuItem("Open With...", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), e -> openWithSelection());
        JMenuItem newFile = createMenuItem("New File", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), e -> createNewFile(false));
        JMenuItem newFolder = createMenuItem("New Folder", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), e -> createNewFile(true));
        JMenuItem rename = createMenuItem("Rename", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), e -> renameSelection());
        JMenuItem delete = createMenuItem("Delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), e -> deleteSelection());
        JMenuItem properties = createMenuItem("Properties", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_DOWN_MASK), e -> showProperties());
        JMenuItem exit = createMenuItem("Exit", null, e -> dispose());

        fileMenu.add(open);
        fileMenu.add(openWith);
        fileMenu.addSeparator();
        fileMenu.add(newFile);
        fileMenu.add(newFolder);
        fileMenu.add(rename);
        fileMenu.add(delete);
        fileMenu.addSeparator();
        fileMenu.add(properties);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(createMenuItem("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), e -> copySelection(false)));
        editMenu.add(createMenuItem("Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), e -> copySelection(true)));
        editMenu.add(createMenuItem("Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), e -> pasteClipboard()));
        editMenu.addSeparator();
        editMenu.add(createMenuItem("Copy Path", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), e -> copyPathOfSelection()));

        JMenu viewMenu = new JMenu("View");
        viewMenu.add(createMenuItem("Refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), e -> refreshCurrentDirectory()));
        viewMenu.add(createMenuItem("Home", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.ALT_DOWN_MASK), e -> navigateTo(Path.of(System.getProperty("user.home")), true)));
        viewMenu.addSeparator();

        JCheckBoxMenuItem hiddenToggle = new JCheckBoxMenuItem("Show Hidden Files");
        hiddenToggle.setSelected(showHiddenFiles);
        hiddenToggle.addActionListener(e -> {
            showHiddenFiles = hiddenToggle.isSelected();
            refreshCurrentDirectory();
            persistSettings();
        });
        viewMenu.add(hiddenToggle);

        JMenu densityMenu = new JMenu("Density");
        ButtonGroup densityGroup = new ButtonGroup();
        addDensityMenuItem(densityMenu, densityGroup, UiDensity.COMPACT);
        addDensityMenuItem(densityMenu, densityGroup, UiDensity.COMFORTABLE);
        addDensityMenuItem(densityMenu, densityGroup, UiDensity.SPACIOUS);
        viewMenu.add(densityMenu);

        JMenu scaleMenu = new JMenu("Scale");
        ButtonGroup scaleGroup = new ButtonGroup();
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S75);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S100);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S125);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S150);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S200);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S300);
        addScaleMenuItem(scaleMenu, scaleGroup, UiScale.S400);
        viewMenu.add(scaleMenu);

        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        for (UiTheme theme : UiTheme.values()) {
            addThemeMenuItem(themeMenu, themeGroup, theme);
        }
        viewMenu.add(themeMenu);

        JMenu lookAndFeelMenu = new JMenu("Look & Feel");
        ButtonGroup lafGroup = new ButtonGroup();
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            addLookAndFeelMenuItem(lookAndFeelMenu, lafGroup, info.getName(), info.getClassName());
        }
        viewMenu.add(lookAndFeelMenu);

        JMenu windowSizeMenu = new JMenu("Window Size");
        windowSizeMenu.add(createMenuItem("Small (1000x650)", null, e -> setSize(1000, 650)));
        windowSizeMenu.add(createMenuItem("Medium (1280x820)", null, e -> setSize(1280, 820)));
        windowSizeMenu.add(createMenuItem("Large (1500x960)", null, e -> setSize(1500, 960)));
        windowSizeMenu.add(createMenuItem("Maximize", null, e -> setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH)));
        viewMenu.add(windowSizeMenu);

        detailsToggleMenuItem = new JCheckBoxMenuItem("Show Details Panel");
        detailsToggleMenuItem.setSelected(detailsVisible);
        detailsToggleMenuItem.addActionListener(e -> toggleDetailsPanel(detailsToggleMenuItem.isSelected()));
        viewMenu.add(detailsToggleMenuItem);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.add(createMenuItem("Copy File Name", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK), e -> copyNameOfSelection()));
        toolsMenu.add(createMenuItem("Open In Terminal", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK), e -> openInTerminal()));

        JMenu goMenu = new JMenu("Go");
        goMenu.add(createMenuItem("Back", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), e -> goBack()));
        goMenu.add(createMenuItem("Forward", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), e -> goForward()));
        goMenu.add(createMenuItem("Up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), e -> goUp()));
        goMenu.add(createMenuItem("Home", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.ALT_DOWN_MASK), e -> navigateTo(Path.of(System.getProperty("user.home")), true)));
        goMenu.addSeparator();
        recentMenu = new JMenu("Recent");
        refreshRecentMenu();
        goMenu.add(recentMenu);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(goMenu);
        menuBar.add(toolsMenu);
        setJMenuBar(menuBar);
    }

    private void addDensityMenuItem(JMenu menu, ButtonGroup group, UiDensity density) {
        JRadioButtonMenuItem item = createDensityItem(density);
        group.add(item);
        menu.add(item);
    }

    private JRadioButtonMenuItem createDensityItem(UiDensity density) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(density.label);
        item.setSelected(density == uiDensity);
        item.addActionListener(e -> {
            densityBox.setSelectedItem(density);
            applyDensity(density);
            persistSettings();
        });
        return item;
    }

    private void addScaleMenuItem(JMenu menu, ButtonGroup group, UiScale scale) {
        JRadioButtonMenuItem item = createScaleItem(scale);
        group.add(item);
        menu.add(item);
    }

    private JRadioButtonMenuItem createScaleItem(UiScale scale) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(scale.label);
        item.setSelected(scale == uiScale);
        item.addActionListener(e -> {
            scaleBox.setSelectedItem(scale);
            applyScale(scale);
            persistSettings();
        });
        return item;
    }

    private void addThemeMenuItem(JMenu menu, ButtonGroup group, UiTheme theme) {
        JRadioButtonMenuItem item = createThemeItem(theme);
        group.add(item);
        menu.add(item);
    }

    private JRadioButtonMenuItem createThemeItem(UiTheme theme) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(theme.label);
        item.setSelected(theme == uiTheme);
        item.addActionListener(e -> {
            themeBox.setSelectedItem(theme);
            applyTheme(theme);
            persistSettings();
        });
        return item;
    }

    private void addLookAndFeelMenuItem(JMenu menu, ButtonGroup group, String displayName, String className) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(displayName);
        item.setSelected(className.equals(lookAndFeelClassName));
        item.addActionListener(e -> applyLookAndFeel(className));
        group.add(item);
        menu.add(item);
    }

    private JMenuItem createMenuItem(String title, KeyStroke shortcut, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(title);
        if (shortcut != null) {
            item.setAccelerator(shortcut);
        }
        item.addActionListener(listener);
        return item;
    }

    private void refreshRecentMenu() {
        if (recentMenu == null) {
            return;
        }
        recentMenu.removeAll();
        if (recentDirectories.isEmpty()) {
            JMenuItem empty = new JMenuItem("No recent folders");
            empty.setEnabled(false);
            recentMenu.add(empty);
            return;
        }
        for (Path recent : recentDirectories) {
            JMenuItem item = new JMenuItem(recent.toString());
            item.addActionListener(e -> navigateTo(recent, true));
            recentMenu.add(item);
        }
    }

    private void addRecentDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        recentDirectories.remove(directory);
        recentDirectories.addFirst(directory);
        while (recentDirectories.size() > 12) {
            recentDirectories.removeLast();
        }
        refreshRecentMenu();
    }

    private void buildTopPanel() {
        JPanel controls = new JPanel(new BorderLayout(0, 8));
        controls.setBackground(uiTheme.panel);
        controls.setBorder(new EmptyBorder(8, 10, 8, 10));
        topPanel = controls;

        JButton upButton = new JButton("Up");
        JButton homeButton = new JButton("Home");
        JButton refreshButton = new JButton("Refresh");

        backButton.addActionListener(e -> goBack());
        forwardButton.addActionListener(e -> goForward());
        upButton.addActionListener(e -> goUp());
        homeButton.addActionListener(e -> navigateTo(Path.of(System.getProperty("user.home")), true));
        refreshButton.addActionListener(e -> refreshCurrentDirectory());

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        navButtons.setOpaque(false);
        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(upButton);
        navButtons.add(homeButton);
        navButtons.add(refreshButton);

        pathField.addActionListener(e -> {
            String input = pathField.getText().trim();
            if (!input.isBlank()) {
                navigateTo(Path.of(input), true);
            }
        });

        JPanel pathPanel = new JPanel(new BorderLayout(6, 0));
        pathPanel.setOpaque(false);
        pathPanel.add(new JLabel("Path:"), BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        densityBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : value.label));
        scaleBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : value.label));
        themeBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : value.label));
        favoritesBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String display = "";
            if (value != null) {
                String name = fileSystemView.getSystemDisplayName(value.toFile());
                display = (name == null || name.isBlank()) ? value.toString() : name;
            }
            JLabel label = new JLabel(display);
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        densityBox.setSelectedItem(uiDensity);
        densityBox.addActionListener(e -> {
            UiDensity selected = (UiDensity) densityBox.getSelectedItem();
            if (selected != null) {
                applyDensity(selected);
                persistSettings();
            }
        });
        scaleBox.setSelectedItem(uiScale);
        scaleBox.addActionListener(e -> {
            UiScale selected = (UiScale) scaleBox.getSelectedItem();
            if (selected != null) {
                applyScale(selected);
                persistSettings();
            }
        });
        themeBox.setSelectedItem(uiTheme);
        themeBox.addActionListener(e -> {
            UiTheme selected = (UiTheme) themeBox.getSelectedItem();
            if (selected != null) {
                applyTheme(selected);
                persistSettings();
            }
        });

        buildFavorites();
        favoritesBox.addActionListener(e -> {
            if (updatingFavoritesUI) {
                return;
            }
            Path favorite = (Path) favoritesBox.getSelectedItem();
            if (favorite != null && Files.exists(favorite)) {
                favoritesList.setSelectedValue(favorite, true);
                navigateTo(favorite, true);
            }
        });

        JPanel rowTop = new JPanel(new BorderLayout(10, 0));
        rowTop.setOpaque(false);
        JPanel leftHeader = new JPanel(new BorderLayout(8, 0));
        leftHeader.setOpaque(false);
        JPanel brandPanel = new JPanel(new GridLayout(2, 1, 0, 0));
        brandPanel.setOpaque(false);
        brandLabel = new JLabel("Frame Explorer");
        brandLabel.setFont(brandLabel.getFont().deriveFont(Font.BOLD, 15f));
        brandSubtitleLabel = new JLabel("Clean desktop browsing");
        brandSubtitleLabel.setFont(brandSubtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        brandPanel.add(brandLabel);
        brandPanel.add(brandSubtitleLabel);
        leftHeader.add(brandPanel, BorderLayout.WEST);
        leftHeader.add(navButtons, BorderLayout.EAST);

        rowTop.add(leftHeader, BorderLayout.WEST);
        rowTop.add(pathPanel, BorderLayout.CENTER);

        JPanel rowBottom = new JPanel(new GridBagLayout());
        rowBottom.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 8);
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addLabeledControl(rowBottom, gbc, 0, 2.0, "Search:", searchField);
        addLabeledControl(rowBottom, gbc, 1, 1.7, "Favorites:", favoritesBox);
        addLabeledControl(rowBottom, gbc, 2, 1.0, "Density:", densityBox);
        addLabeledControl(rowBottom, gbc, 3, 1.0, "Scale:", scaleBox);
        addLabeledControl(rowBottom, gbc, 4, 1.2, "Theme:", themeBox);

        styleButton(backButton);
        styleButton(forwardButton);
        styleButton(upButton);
        styleButton(homeButton);
        styleButton(refreshButton);
        styleTextField(pathField);
        styleTextField(searchField);
        styleComboBox(favoritesBox);
        styleComboBox(densityBox);
        styleComboBox(scaleBox);
        styleComboBox(themeBox);

        JPanel bottomCard = new JPanel(new BorderLayout());
        bottomCard.setOpaque(true);
        bottomCard.setBorder(new EmptyBorder(6, 8, 6, 8));
        bottomCard.add(rowBottom, BorderLayout.CENTER);

        controls.add(rowTop, BorderLayout.NORTH);
        controls.add(bottomCard, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
    }

    private void addLabeledControl(JPanel parent, GridBagConstraints gbc, int x, double weight, String label, JComponent control) {
        JPanel wrapper = new JPanel(new BorderLayout(6, 0));
        wrapper.setOpaque(false);
        wrapper.add(new JLabel(label), BorderLayout.WEST);
        wrapper.add(control, BorderLayout.CENTER);

        gbc.gridx = x;
        gbc.weightx = weight;
        gbc.insets = new Insets(0, 0, 0, x == 4 ? 0 : 8);
        parent.add(wrapper, gbc);
    }

    private JPanel buildLeftPanel(JScrollPane treePane) {
        favoritesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        favoritesList.setVisibleRowCount(6);
        favoritesList.setFixedCellHeight(22);
        favoritesList.setBorder(BorderFactory.createEmptyBorder());
        favoritesList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String display = "";
            if (value != null) {
                String name = fileSystemView.getSystemDisplayName(value.toFile());
                display = (name == null || name.isBlank()) ? value.toString() : name;
            }
            JLabel label = new JLabel(display);
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(2, 6, 2, 6));
            if (isSelected) {
                label.setBackground(uiTheme.selection);
                label.setForeground(uiTheme.selectionText);
            } else {
                label.setBackground(uiTheme.rowPrimary);
                label.setForeground(uiTheme.text);
            }
            return label;
        });

        favoritesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    Path selected = favoritesList.getSelectedValue();
                    if (selected != null && Files.exists(selected)) {
                        navigateTo(selected, true);
                    }
                }
            }
        });

        addFavoriteButton.addActionListener(e -> addCurrentLocationToFavorites());
        removeFavoriteButton.addActionListener(e -> removeSelectedFavorite());

        JPanel favoriteButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        favoriteButtons.setOpaque(false);
        favoriteButtons.add(addFavoriteButton);
        favoriteButtons.add(removeFavoriteButton);

        favoritesPanel = new JPanel(new BorderLayout(6, 6));
        favoritesPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
        favoritesTitleLabel = new JLabel("Favorites");
        favoritesTitleLabel.setFont(favoritesTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        favoritesPanel.add(favoritesTitleLabel, BorderLayout.NORTH);
        favoritesPanel.add(new JScrollPane(favoritesList), BorderLayout.CENTER);
        favoritesPanel.add(favoriteButtons, BorderLayout.SOUTH);

        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, favoritesPanel, treePane);
        leftSplit.setResizeWeight(0.30);
        leftSplit.setOneTouchExpandable(true);
        leftSplit.setContinuousLayout(true);
        leftSplit.setDividerSize(6);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(leftSplit, BorderLayout.CENTER);
        return wrapper;
    }

    private void buildFavorites() {
        favoritesListModel.clear();
        if (!settings.favorites.isEmpty()) {
            for (String favorite : settings.favorites) {
                addFavorite(Path.of(favorite));
            }
        }

        if (favoritesListModel.isEmpty()) {
            Path home = Path.of(System.getProperty("user.home"));
            addFavorite(home);
            addFavorite(home.resolve("Desktop"));
            addFavorite(home.resolve("Documents"));
            addFavorite(home.resolve("Downloads"));
            addFavorite(home.resolve("Pictures"));
        }
        refreshFavoritesCombo();
    }

    private void addFavorite(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isDirectory(normalized)) {
            return;
        }

        for (int i = 0; i < favoritesListModel.size(); i++) {
            if (favoritesListModel.get(i).equals(normalized)) {
                return;
            }
        }

        favoritesListModel.addElement(normalized);
    }

    private boolean isFavorite(Path path) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < favoritesListModel.size(); i++) {
            if (favoritesListModel.get(i).equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void refreshFavoritesCombo() {
        updatingFavoritesUI = true;
        Object currentSelection = favoritesBox.getSelectedItem();
        favoritesBox.removeAllItems();
        for (int i = 0; i < favoritesListModel.size(); i++) {
            favoritesBox.addItem(favoritesListModel.get(i));
        }
        if (currentSelection instanceof Path path && Files.exists(path)) {
            favoritesBox.setSelectedItem(path);
        } else if (favoritesBox.getItemCount() > 0 && favoritesBox.getSelectedItem() == null) {
            favoritesBox.setSelectedIndex(0);
        }
        updatingFavoritesUI = false;
    }

    private void addCurrentLocationToFavorites() {
        Path candidate = currentDirectory;
        Path selected = getSingleSelectedPath();
        if (selected != null && Files.isDirectory(selected)) {
            candidate = selected;
        }
        if (candidate != null) {
            addFavorite(candidate);
            refreshFavoritesCombo();
            favoritesList.setSelectedValue(candidate.toAbsolutePath().normalize(), true);
            persistSettings();
        }
    }

    private void removeSelectedFavorite() {
        Path selected = favoritesList.getSelectedValue();
        if (selected == null) {
            selected = (Path) favoritesBox.getSelectedItem();
        }
        if (selected == null) {
            return;
        }
        for (int i = 0; i < favoritesListModel.size(); i++) {
            if (favoritesListModel.get(i).equals(selected)) {
                favoritesListModel.remove(i);
                break;
            }
        }
        refreshFavoritesCombo();
        persistSettings();
    }

    private void buildTree(DefaultMutableTreeNode rootNode) {
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            rootNode.add(new FileTreeNode(root));
        }

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(uiTheme.surface);
        tree.setCellRenderer(treeRenderer);

        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                Object node = event.getPath().getLastPathComponent();
                if (node instanceof FileTreeNode fileTreeNode) {
                    fileTreeNode.loadChildren(treeModel);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        });

        tree.addTreeSelectionListener(e -> {
            Object selected = tree.getLastSelectedPathComponent();
            if (selected instanceof FileTreeNode fileTreeNode && fileTreeNode.getPathValue() != null) {
                navigateTo(fileTreeNode.getPathValue(), true);
            }
        });
    }

    private void buildTable() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowSorter(sorter);
        table.setShowHorizontalLines(false);
        table.setGridColor(uiTheme.grid);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(uiTheme.selection);
        table.setSelectionForeground(uiTheme.selectionText);
        table.setBackground(uiTheme.rowPrimary);
        table.getTableHeader().setBackground(uiTheme.headerBg);
        table.getTableHeader().setForeground(uiTheme.headerText);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setDefaultRenderer(Object.class, new ExplorerTableCellRenderer(tableModel, fileSystemView, uiTheme.rowPrimary, uiTheme.rowAlt, uiTheme.text));
        table.setDefaultRenderer(Long.class, new ExplorerTableCellRenderer(tableModel, fileSystemView, uiTheme.rowPrimary, uiTheme.rowAlt, uiTheme.text));

        sorter.setComparator(0, Comparator.comparing(Objects::toString, String.CASE_INSENSITIVE_ORDER));
        sorter.setComparator(1, Comparator.comparingLong(value -> (Long) value));
        sorter.setComparator(3, Comparator.comparing(Objects::toString));

        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Path selected = getSingleSelectedPath();
                    if (selected != null) {
                        openPath(selected);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> updateSelectionDetails());
    }

    private JPanel buildDetailsPanel() {
        detailsPanel = new JPanel(new BorderLayout(8, 6));
        detailsPanel.setBackground(uiTheme.detailsBg);
        detailsPanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        detailsTitleLabel = new JLabel("Details");
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, 13f));

        detailsArea.setEditable(false);
        detailsArea.setOpaque(false);
        detailsArea.setForeground(uiTheme.text);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        detailsArea.setFont(detailsArea.getFont().deriveFont(12f));

        JPanel quickActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickActions.setOpaque(false);
        JButton openButton = new JButton("Open");
        JButton openWithButton = new JButton("Open With");
        JButton renameButton = new JButton("Rename");
        JButton deleteButton = new JButton("Delete");
        JButton pathButton = new JButton("Copy Path");
        openButton.addActionListener(e -> {
            Path selected = getSingleSelectedPath();
            if (selected != null) {
                openPath(selected);
            }
        });
        openWithButton.addActionListener(e -> openWithSelection());
        renameButton.addActionListener(e -> renameSelection());
        deleteButton.addActionListener(e -> deleteSelection());
        pathButton.addActionListener(e -> copyPathOfSelection());
        styleButton(openButton);
        styleButton(openWithButton);
        styleButton(renameButton);
        styleButton(deleteButton);
        styleButton(pathButton);
        quickActions.add(openButton);
        quickActions.add(openWithButton);
        quickActions.add(renameButton);
        quickActions.add(deleteButton);
        quickActions.add(pathButton);

        detailsPanel.add(detailsTitleLabel, BorderLayout.NORTH);
        detailsPanel.add(detailsArea, BorderLayout.CENTER);
        detailsPanel.add(quickActions, BorderLayout.SOUTH);
        return detailsPanel;
    }

    private JPanel buildStatusBar() {
        statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(uiTheme.panel);
        statusPanel.setBorder(new EmptyBorder(6, 10, 6, 10));
        statusLabel.setBorder(new EmptyBorder(2, 0, 2, 0));
        statusLabel.setForeground(uiTheme.text);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        return statusPanel;
    }

    private void installKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        bindShortcut(inputMap, actionMap, "open", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), () -> {
            Path selected = getSingleSelectedPath();
            if (selected != null) {
                openPath(selected);
            }
        });
        bindShortcut(inputMap, actionMap, "rename", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), this::renameSelection);
        bindShortcut(inputMap, actionMap, "delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), this::deleteSelection);
        bindShortcut(inputMap, actionMap, "refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::refreshCurrentDirectory);
        bindShortcut(inputMap, actionMap, "copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), () -> copySelection(false));
        bindShortcut(inputMap, actionMap, "cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), () -> copySelection(true));
        bindShortcut(inputMap, actionMap, "paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), this::pasteClipboard);
    }

    private void bindShortcut(InputMap inputMap, ActionMap actionMap, String key, KeyStroke stroke, Runnable action) {
        inputMap.put(stroke, key);
        actionMap.put(key, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void styleButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(uiTheme.border, 10),
                new EmptyBorder(6, 12, 6, 12)
        ));
        button.setBackground(uiTheme.controlBg);
        button.setForeground(uiTheme.text);
    }

    private void styleTextField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(uiTheme.border, 10),
                new EmptyBorder(6, 10, 6, 10)
        ));
        field.setBackground(uiTheme.controlBg);
        field.setForeground(uiTheme.text);
        field.setCaretColor(uiTheme.text);
    }

    private void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBorder(new RoundedLineBorder(uiTheme.border, 10));
        comboBox.setBackground(uiTheme.controlBg);
        comboBox.setForeground(uiTheme.text);
        comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void applyLookAndFeel(String className) {
        if (className == null || className.isBlank() || className.equals(lookAndFeelClassName)) {
            return;
        }
        try {
            UIManager.setLookAndFeel(className);
            lookAndFeelClassName = className;
            SwingUtilities.updateComponentTreeUI(this);
            applyTheme(uiTheme);
            updateScaling();
            persistSettings();
        } catch (Exception ex) {
            showError("Unable to apply Look & Feel: " + ex.getMessage());
        }
    }

    private void applyTheme(UiTheme theme) {
        this.uiTheme = theme;
        getContentPane().setBackground(theme.surface);

        if (topPanel != null) {
            topPanel.setBackground(theme.panel);
            topPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(10, 12, 10, 12)
            ));
            applyForegroundRecursively(topPanel, theme.text);
            applyControlStylesRecursively(topPanel);
            if (topPanel.getComponentCount() > 1 && topPanel.getComponent(1) instanceof JPanel card) {
                card.setBackground(theme.rowPrimary);
                card.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedLineBorder(theme.border, 12),
                        new EmptyBorder(7, 9, 7, 9)
                ));
            }
        }
        if (brandLabel != null) {
            brandLabel.setForeground(theme.headerText);
        }
        if (brandSubtitleLabel != null) {
            brandSubtitleLabel.setForeground(new Color(
                    Math.min(255, theme.text.getRed() + 25),
                    Math.min(255, theme.text.getGreen() + 25),
                    Math.min(255, theme.text.getBlue() + 25)
            ));
        }
        if (detailsPanel != null) {
            detailsPanel.setBackground(theme.detailsBg);
            detailsPanel.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedLineBorder(theme.border, 12),
                    new EmptyBorder(10, 12, 10, 12)
            ));
            applyForegroundRecursively(detailsPanel, theme.text);
            applyControlStylesRecursively(detailsPanel);
        }
        if (favoritesPanel != null) {
            favoritesPanel.setBackground(theme.detailsBg);
            favoritesPanel.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedLineBorder(theme.border, 12),
                    new EmptyBorder(10, 10, 10, 10)
            ));
        }
        if (statusPanel != null) {
            statusPanel.setBackground(theme.panel);
            statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.border));
        }

        statusLabel.setForeground(theme.text);
        detailsArea.setForeground(theme.text);
        detailsArea.setCaretColor(theme.text);

        styleButton(backButton);
        styleButton(forwardButton);
        styleTextField(pathField);
        styleTextField(searchField);
        styleComboBox(favoritesBox);
        styleComboBox(densityBox);
        styleComboBox(scaleBox);
        styleComboBox(themeBox);
        styleButton(addFavoriteButton);
        styleButton(removeFavoriteButton);
        if (favoritesTitleLabel != null) {
            favoritesTitleLabel.setForeground(theme.headerText);
        }
        if (detailsTitleLabel != null) {
            detailsTitleLabel.setForeground(theme.headerText);
        }

        tree.setBackground(theme.surface);
        tree.setForeground(theme.text);
        treeRenderer.setThemeColors(theme.surface, theme.selection, theme.text, theme.selectionText);
        favoritesList.setBackground(theme.rowPrimary);
        favoritesList.setForeground(theme.text);
        favoritesList.setSelectionBackground(theme.selection);
        favoritesList.setSelectionForeground(theme.selectionText);

        table.setGridColor(theme.grid);
        table.setSelectionBackground(theme.selection);
        table.setSelectionForeground(theme.selectionText);
        table.setBackground(theme.rowPrimary);
        table.setForeground(theme.text);
        table.getTableHeader().setBackground(theme.headerBg);
        table.getTableHeader().setForeground(theme.headerText);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border));
        table.setDefaultRenderer(Object.class, new ExplorerTableCellRenderer(tableModel, fileSystemView, theme.rowPrimary, theme.rowAlt, theme.text));
        table.setDefaultRenderer(Long.class, new ExplorerTableCellRenderer(tableModel, fileSystemView, theme.rowPrimary, theme.rowAlt, theme.text));

        repaint();
    }

    private void applyForegroundRecursively(Component component, Color foreground) {
        if (component instanceof JLabel || component instanceof AbstractButton || component instanceof JTextArea) {
            component.setForeground(foreground);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyForegroundRecursively(child, foreground);
            }
        }
    }

    private void applyControlStylesRecursively(Component component) {
        if (component instanceof JButton button) {
            styleButton(button);
        } else if (component instanceof JTextField textField) {
            styleTextField(textField);
        } else if (component instanceof JComboBox<?> comboBox) {
            styleComboBox(comboBox);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyControlStylesRecursively(child);
            }
        }
    }

    private void applyDensity(UiDensity density) {
        this.uiDensity = density;
        updateScaling();
    }

    private void applyScale(UiScale scale) {
        this.uiScale = scale;
        updateScaling();
    }

    private void toggleDetailsPanel(boolean show) {
        detailsVisible = show;
        if (verticalSplit == null || detailsPanel == null) {
            return;
        }
        if (show) {
            detailsPanel.setVisible(true);
            verticalSplit.setDividerSize(Math.max(6, verticalSplit.getDividerSize()));
            verticalSplit.setDividerLocation(clampRatio(settings.verticalSplitRatio));
        } else {
            detailsPanel.setVisible(false);
            verticalSplit.setDividerLocation(1.0);
            verticalSplit.setDividerSize(0);
        }
        if (detailsToggleMenuItem != null && detailsToggleMenuItem.isSelected() != show) {
            detailsToggleMenuItem.setSelected(show);
        }
        revalidate();
    }

    private Path resolveInitialDirectory(Path fallback) {
        if (settings.lastDirectory != null && !settings.lastDirectory.isBlank()) {
            try {
                Path configured = Path.of(settings.lastDirectory);
                if (Files.exists(configured) && Files.isDirectory(configured)) {
                    return configured;
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5d;
        }
        return Math.max(0.10d, Math.min(0.90d, value));
    }

    private double getDividerRatio(JSplitPane splitPane) {
        if (splitPane == null) {
            return 0.5d;
        }
        int dividerLocation = splitPane.getDividerLocation();
        int size = splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? splitPane.getWidth() : splitPane.getHeight();
        if (size <= 0) {
            return 0.5d;
        }
        return clampRatio((double) dividerLocation / (double) size);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void persistSettings() {
        settings.density = uiDensity.name();
        settings.scale = uiScale.name();
        settings.theme = uiTheme.name();
        settings.lookAndFeelClassName = lookAndFeelClassName;
        settings.showHiddenFiles = showHiddenFiles;
        settings.lastDirectory = currentDirectory != null ? currentDirectory.toString() : "";

        settings.maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
        if (!settings.maximized) {
            settings.windowX = getX();
            settings.windowY = getY();
            settings.windowWidth = getWidth();
            settings.windowHeight = getHeight();
        }

        settings.horizontalSplitRatio = getDividerRatio(horizontalSplit);
        settings.verticalSplitRatio = getDividerRatio(verticalSplit);
        settings.leftSplitRatio = getDividerRatio(leftSplit);

        settings.favorites.clear();
        for (int i = 0; i < favoritesListModel.size(); i++) {
            settings.favorites.add(favoritesListModel.get(i).toString());
        }
        settings.save();
    }

    private void updateScaling() {
        float scaleFactor = uiScale.factor;
        int tableRowHeight = Math.max(18, Math.round(uiDensity.tableRowHeight * scaleFactor));
        int treeRowHeight = Math.max(18, Math.round(uiDensity.treeRowHeight * scaleFactor));
        float baseFont = uiDensity.fontSize * scaleFactor;
        int headerHeight = Math.max(22, Math.round(24 * scaleFactor));
        int verticalPad = Math.max(6, Math.round(8 * Math.min(scaleFactor, 2.0f)));
        int horizontalPad = Math.max(8, Math.round(10 * Math.min(scaleFactor, 2.0f)));

        table.setRowHeight(tableRowHeight);
        tree.setRowHeight(treeRowHeight);
        table.getTableHeader().setPreferredSize(new Dimension(table.getTableHeader().getWidth(), headerHeight));

        Font uiFont = table.getFont().deriveFont(baseFont);
        table.setFont(uiFont);
        tree.setFont(tree.getFont().deriveFont(baseFont));
        pathField.setFont(pathField.getFont().deriveFont(baseFont));
        searchField.setFont(searchField.getFont().deriveFont(baseFont));
        favoritesBox.setFont(favoritesBox.getFont().deriveFont(baseFont));
        densityBox.setFont(densityBox.getFont().deriveFont(baseFont));
        scaleBox.setFont(scaleBox.getFont().deriveFont(baseFont));
        themeBox.setFont(themeBox.getFont().deriveFont(baseFont));
        favoritesList.setFont(favoritesList.getFont().deriveFont(baseFont));
        favoritesList.setFixedCellHeight(Math.max(20, Math.round(22 * Math.min(scaleFactor, 2.5f))));
        detailsArea.setFont(detailsArea.getFont().deriveFont(Math.max(10f, baseFont - 0.5f)));
        addFavoriteButton.setFont(addFavoriteButton.getFont().deriveFont(baseFont));
        removeFavoriteButton.setFont(removeFavoriteButton.getFont().deriveFont(baseFont));
        if (brandLabel != null) {
            brandLabel.setFont(brandLabel.getFont().deriveFont(Font.BOLD, Math.max(13f, baseFont + 2f)));
        }
        if (brandSubtitleLabel != null) {
            brandSubtitleLabel.setFont(brandSubtitleLabel.getFont().deriveFont(Font.PLAIN, Math.max(10f, baseFont - 1.5f)));
        }
        if (favoritesTitleLabel != null) {
            favoritesTitleLabel.setFont(favoritesTitleLabel.getFont().deriveFont(Font.BOLD, Math.max(12f, baseFont)));
        }
        if (detailsTitleLabel != null) {
            detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, Math.max(12f, baseFont)));
        }

        if (topPanel != null) {
            topPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, uiTheme.border),
                    new EmptyBorder(verticalPad, horizontalPad, verticalPad, horizontalPad)
            ));
            if (topPanel.getComponentCount() > 1 && topPanel.getComponent(1) instanceof JPanel card) {
                int cardPad = Math.max(5, Math.round(6 * Math.min(scaleFactor, 1.7f)));
                card.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedLineBorder(uiTheme.border, 12),
                        new EmptyBorder(cardPad, cardPad + 1, cardPad, cardPad + 1)
                ));
            }
        }
        if (detailsPanel != null) {
            detailsPanel.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedLineBorder(uiTheme.border, 12),
                    new EmptyBorder(verticalPad, horizontalPad + 2, verticalPad, horizontalPad + 2)
            ));
        }
        if (statusPanel != null) {
            statusPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, uiTheme.border),
                    new EmptyBorder(Math.max(4, verticalPad - 2), horizontalPad, Math.max(4, verticalPad - 2), horizontalPad)
            ));
        }

        int searchWidth = Math.max(180, Math.round(230 * Math.min(scaleFactor, 2.0f)));
        int comboWidth = Math.max(120, Math.round(150 * Math.min(scaleFactor, 2.0f)));
        searchField.setPreferredSize(new Dimension(searchWidth, Math.max(26, tableRowHeight)));
        favoritesBox.setPreferredSize(new Dimension(comboWidth + 30, Math.max(26, tableRowHeight)));
        densityBox.setPreferredSize(new Dimension(comboWidth - 10, Math.max(26, tableRowHeight)));
        scaleBox.setPreferredSize(new Dimension(comboWidth - 10, Math.max(26, tableRowHeight)));
        themeBox.setPreferredSize(new Dimension(comboWidth + 10, Math.max(26, tableRowHeight)));
        addFavoriteButton.setPreferredSize(new Dimension(Math.max(90, Math.round(110 * Math.min(scaleFactor, 2.0f))), Math.max(26, tableRowHeight)));
        removeFavoriteButton.setPreferredSize(new Dimension(Math.max(80, Math.round(95 * Math.min(scaleFactor, 2.0f))), Math.max(26, tableRowHeight)));

        if (horizontalSplit != null) {
            horizontalSplit.setDividerSize(Math.max(8, Math.round(8 * Math.min(scaleFactor, 1.5f))));
        }
        if (verticalSplit != null) {
            verticalSplit.setDividerSize(Math.max(6, Math.round(6 * Math.min(scaleFactor, 1.5f))));
        }
        if (leftSplit != null) {
            leftSplit.setDividerSize(Math.max(6, Math.round(6 * Math.min(scaleFactor, 1.5f))));
        }

        revalidate();
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && !table.isRowSelected(row)) {
            table.setRowSelectionInterval(row, row);
        }

        JPopupMenu menu = new JPopupMenu();
        menu.add(createContextItem("Open", () -> {
            Path selected = getSingleSelectedPath();
            if (selected != null) {
                openPath(selected);
            }
        }));
        menu.add(createContextItem("Open With...", this::openWithSelection));
        menu.addSeparator();
        menu.add(createContextItem("Copy", () -> copySelection(false)));
        menu.add(createContextItem("Cut", () -> copySelection(true)));
        menu.add(createContextItem("Paste", this::pasteClipboard));
        menu.add(createContextItem("Copy Path", this::copyPathOfSelection));
        menu.add(createContextItem("Copy File Name", this::copyNameOfSelection));
        menu.addSeparator();
        menu.add(createContextItem("Open In Terminal", this::openInTerminal));
        menu.addSeparator();
        menu.add(createContextItem("Rename", this::renameSelection));
        menu.add(createContextItem("Delete", this::deleteSelection));
        menu.addSeparator();
        menu.add(createContextItem("New File", () -> createNewFile(false)));
        menu.add(createContextItem("New Folder", () -> createNewFile(true)));
        menu.addSeparator();
        menu.add(createContextItem("Properties", this::showProperties));
        menu.add(createContextItem("Refresh", this::refreshCurrentDirectory));

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem createContextItem(String title, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isBlank()) {
            sorter.setRowFilter(null);
            updateStatus();
            return;
        }

        try {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 0));
            updateStatus();
        } catch (PatternSyntaxException ignored) {
        }
    }

    private void navigateTo(Path target, boolean trackHistory) {
        try {
            if (target == null) {
                return;
            }
            Path normalized = target.toAbsolutePath().normalize();
            if (!Files.exists(normalized)) {
                showError("Path does not exist: " + normalized);
                return;
            }
            if (!Files.isDirectory(normalized)) {
                if (trackHistory && currentDirectory != null) {
                    backStack.push(currentDirectory);
                    forwardStack.clear();
                }
                openPath(normalized);
                updateHistoryButtons();
                return;
            }

            if (trackHistory && currentDirectory != null && !currentDirectory.equals(normalized)) {
                backStack.push(currentDirectory);
                forwardStack.clear();
            }

            currentDirectory = normalized;
            addRecentDirectory(normalized);
            pathField.setText(normalized.toString());
            tableModel.setDirectory(normalized, showHiddenFiles);
            favoritesList.setSelectedValue(normalized, true);
            if (!updatingFavoritesUI && isFavorite(normalized)) {
                favoritesBox.setSelectedItem(normalized);
            }
            updateSelectionDetails();
            updateStatus();
            updateHistoryButtons();
            persistSettings();
        } catch (Exception ex) {
            showError("Cannot open directory: " + ex.getMessage());
        }
    }

    private void goBack() {
        if (backStack.isEmpty()) {
            return;
        }
        if (currentDirectory != null) {
            forwardStack.push(currentDirectory);
        }
        Path previous = backStack.pop();
        navigateTo(previous, false);
        updateHistoryButtons();
    }

    private void goForward() {
        if (forwardStack.isEmpty()) {
            return;
        }
        if (currentDirectory != null) {
            backStack.push(currentDirectory);
        }
        Path next = forwardStack.pop();
        navigateTo(next, false);
        updateHistoryButtons();
    }

    private void goUp() {
        if (currentDirectory == null) {
            return;
        }
        Path parent = currentDirectory.getParent();
        if (parent != null) {
            navigateTo(parent, true);
        }
    }

    private void refreshCurrentDirectory() {
        if (currentDirectory != null) {
            navigateTo(currentDirectory, false);
        }
    }

    private void updateHistoryButtons() {
        backButton.setEnabled(!backStack.isEmpty());
        forwardButton.setEnabled(!forwardStack.isEmpty());
    }

    private void openPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                navigateTo(path, true);
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                return;
            }
            openPathWithSystemFallback(path);
        } catch (Exception ex) {
            showError("Unable to open: " + ex.getMessage());
        }
    }

    private void openWithSelection() {
        Path selected = getSingleSelectedPath();
        if (selected == null) {
            return;
        }
        if (Files.isDirectory(selected)) {
            navigateTo(selected, true);
            return;
        }

        LinkedHashMap<String, String> options = buildOpenWithOptions();
        JComboBox<String> appBox = new JComboBox<>(options.keySet().toArray(new String[0]));
        appBox.setEditable(true);
        styleComboBox(appBox);

        JPanel panel = new JPanel(new BorderLayout(8, 6));
        panel.add(new JLabel("Open '" + selected.getFileName() + "' with:"), BorderLayout.NORTH);
        panel.add(appBox, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Open With",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Object rawChoice = appBox.getEditor().getItem();
        String choice = rawChoice == null ? "" : rawChoice.toString().trim();
        if (choice.isBlank()) {
            openPath(selected);
            return;
        }

        String command = options.getOrDefault(choice, choice);
        if (command.isBlank()) {
            openPath(selected);
            return;
        }

        try {
            runCommandForPath(command, selected);
            statusLabel.setText("Opened with: " + choice);
        } catch (Exception ex) {
            showError("Open With failed: " + ex.getMessage());
        }
    }

    private LinkedHashMap<String, String> buildOpenWithOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        options.put("System Default", "");

        OperatingSystem os = detectOs();
        if (os == OperatingSystem.WINDOWS) {
            options.put("Notepad", "notepad.exe");
            if (commandExists("code")) {
                options.put("VS Code", "code");
            }
        } else if (os == OperatingSystem.MAC) {
            options.put("TextEdit", "open -a TextEdit");
            options.put("Preview", "open -a Preview");
            if (commandExists("code")) {
                options.put("VS Code", "code");
            }
        } else {
            if (commandExists("xdg-open")) {
                options.put("xdg-open", "xdg-open");
            }
            if (commandExists("code")) {
                options.put("VS Code", "code");
            }
            if (commandExists("gedit")) {
                options.put("Gedit", "gedit");
            }
            if (commandExists("kate")) {
                options.put("Kate", "kate");
            }
            if (commandExists("libreoffice")) {
                options.put("LibreOffice", "libreoffice");
            }
            if (commandExists("firefox")) {
                options.put("Firefox", "firefox");
            }
        }
        return options;
    }

    private void runCommandForPath(String command, Path target) throws IOException {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isBlank()) {
            openPath(target);
            return;
        }

        if ("xdg-open".equals(trimmed) && commandExists("xdg-open")) {
            new ProcessBuilder("xdg-open", target.toAbsolutePath().toString()).start();
            return;
        }
        if (trimmed.startsWith("open -a ")) {
            String appName = trimmed.substring("open -a ".length()).trim();
            if (!appName.isBlank()) {
                new ProcessBuilder("open", "-a", appName, target.toAbsolutePath().toString()).start();
                return;
            }
        }

        if (!trimmed.contains(" ") && !trimmed.contains("\"") && !trimmed.contains("'")) {
            new ProcessBuilder(trimmed, target.toAbsolutePath().toString()).start();
            return;
        }

        runShellCommandWithPath(trimmed, target);
    }

    private void copySelection(boolean cut) {
        List<Path> selected = getSelectedPaths();
        if (selected.isEmpty()) {
            return;
        }

        clipboardEntries.clear();
        clipboardEntries.addAll(selected);
        clipboardCut = cut;
        updateStatus();
    }

    private void copyPathOfSelection() {
        Path selected = getSingleSelectedPath();
        if (selected == null) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected.toString()), null);
        statusLabel.setText("Path copied: " + selected);
    }

    private void copyNameOfSelection() {
        Path selected = getSingleSelectedPath();
        if (selected == null || selected.getFileName() == null) {
            return;
        }
        String name = selected.getFileName().toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null);
        statusLabel.setText("File name copied: " + name);
    }

    private void openInTerminal() {
        Path directory = currentDirectory;
        if (directory == null) {
            return;
        }
        try {
            openDirectoryInTerminal(directory);
            statusLabel.setText("Opened terminal at: " + directory);
        } catch (Exception ex) {
            showError("Open terminal failed: " + ex.getMessage());
        }
    }

    private OperatingSystem detectOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return OperatingSystem.WINDOWS;
        }
        if (osName.contains("mac")) {
            return OperatingSystem.MAC;
        }
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OperatingSystem.LINUX;
        }
        return OperatingSystem.OTHER;
    }

    private boolean commandExists(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        try {
            OperatingSystem os = detectOs();
            Process process;
            if (os == OperatingSystem.WINDOWS) {
                process = new ProcessBuilder("cmd", "/c", "where", command).start();
            } else {
                process = new ProcessBuilder("sh", "-lc", "command -v " + quoteForPosix(command)).start();
            }
            int exit = process.waitFor();
            return exit == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openPathWithSystemFallback(Path path) throws IOException {
        OperatingSystem os = detectOs();
        if (os == OperatingSystem.WINDOWS) {
            new ProcessBuilder("cmd", "/c", "start", "\"\"", "\"" + path.toAbsolutePath() + "\"").start();
            return;
        }
        if (os == OperatingSystem.MAC) {
            new ProcessBuilder("open", path.toAbsolutePath().toString()).start();
            return;
        }
        if (os == OperatingSystem.LINUX && commandExists("xdg-open")) {
            new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
            return;
        }
        throw new IOException("No supported open handler for this platform.");
    }

    private void runShellCommandWithPath(String command, Path target) throws IOException {
        String absolutePath = target.toAbsolutePath().toString();
        if (detectOs() == OperatingSystem.WINDOWS) {
            String shellCommand = command + " " + quoteForWindowsCmd(absolutePath);
            new ProcessBuilder("cmd", "/c", shellCommand).start();
            return;
        }
        String shellCommand = command + " " + quoteForPosix(absolutePath);
        new ProcessBuilder("sh", "-lc", shellCommand).start();
    }

    private void openDirectoryInTerminal(Path directory) throws IOException {
        String dir = directory.toAbsolutePath().toString();
        OperatingSystem os = detectOs();
        if (os == OperatingSystem.WINDOWS) {
            new ProcessBuilder("cmd", "/c", "start", "\"\"", "cmd", "/k", "cd /d \"" + dir + "\"").start();
            return;
        }
        if (os == OperatingSystem.MAC) {
            new ProcessBuilder("open", "-a", "Terminal", dir).start();
            return;
        }
        if (os == OperatingSystem.LINUX) {
            if (commandExists("x-terminal-emulator")) {
                new ProcessBuilder("x-terminal-emulator", "--working-directory", dir).start();
                return;
            }
            if (commandExists("gnome-terminal")) {
                new ProcessBuilder("gnome-terminal", "--working-directory=" + dir).start();
                return;
            }
            if (commandExists("konsole")) {
                new ProcessBuilder("konsole", "--workdir", dir).start();
                return;
            }
            if (commandExists("xfce4-terminal")) {
                new ProcessBuilder("xfce4-terminal", "--working-directory", dir).start();
                return;
            }
            if (commandExists("alacritty")) {
                new ProcessBuilder("alacritty", "--working-directory", dir).start();
                return;
            }
            if (commandExists("xterm")) {
                String shellCommand = "cd " + quoteForPosix(dir) + " && exec \"$SHELL\"";
                new ProcessBuilder("xterm", "-e", "sh", "-lc", shellCommand).start();
                return;
            }
        }
        throw new IOException("No terminal application found.");
    }

    private String quoteForPosix(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String quoteForWindowsCmd(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private void pasteClipboard() {
        if (currentDirectory == null || clipboardEntries.isEmpty()) {
            return;
        }

        int copied = 0;
        List<Path> failed = new ArrayList<>();
        for (Path source : clipboardEntries) {
            try {
                Path target = FileOperations.resolveUniqueTarget(currentDirectory.resolve(source.getFileName()));
                if (clipboardCut) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    FileOperations.copyRecursively(source, target);
                }
                copied++;
            } catch (Exception ex) {
                failed.add(source);
            }
        }

        if (clipboardCut && failed.isEmpty()) {
            clipboardEntries.clear();
        }

        refreshCurrentDirectory();
        String message = "Pasted " + copied + " item(s)";
        if (!failed.isEmpty()) {
            message += " | Failed: " + failed.size();
        }
        statusLabel.setText(message);
    }

    private void renameSelection() {
        Path selected = getSingleSelectedPath();
        if (selected == null) {
            return;
        }

        String newName = JOptionPane.showInputDialog(this, "New name:", selected.getFileName().toString());
        if (newName == null || newName.isBlank()) {
            return;
        }

        try {
            Files.move(selected, selected.resolveSibling(newName));
            refreshCurrentDirectory();
        } catch (IOException ex) {
            showError("Rename failed: " + ex.getMessage());
        }
    }

    private void deleteSelection() {
        List<Path> selected = getSelectedPaths();
        if (selected.isEmpty()) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete " + selected.size() + " item(s)?",
                "Confirm delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        int deleted = 0;
        for (Path path : selected) {
            try {
                FileOperations.deleteRecursively(path);
                deleted++;
            } catch (Exception ex) {
                showError("Failed to delete " + path.getFileName() + ": " + ex.getMessage());
            }
        }

        statusLabel.setText("Deleted " + deleted + " item(s)");
        refreshCurrentDirectory();
    }

    private void createNewFile(boolean directory) {
        if (currentDirectory == null) {
            return;
        }

        String prompt = directory ? "Folder name:" : "File name:";
        String name = JOptionPane.showInputDialog(this, prompt);
        if (name == null || name.isBlank()) {
            return;
        }

        Path path = currentDirectory.resolve(name.trim());
        try {
            if (directory) {
                Files.createDirectories(path);
            } else {
                Files.createFile(path);
            }
            refreshCurrentDirectory();
        } catch (IOException ex) {
            showError("Creation failed: " + ex.getMessage());
        }
    }

    private void showProperties() {
        Path path = getSingleSelectedPath();
        if (path == null) {
            path = currentDirectory;
        }
        if (path == null) {
            return;
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long size = attrs.isDirectory() ? FileOperations.calculateDirectorySize(path) : attrs.size();
            String details = "Path: " + path + "\n"
                    + "Type: " + (attrs.isDirectory() ? "Directory" : "File") + "\n"
                    + "Size: " + FileTableModel.formatSize(size) + "\n"
                    + "Modified: " + FileTableModel.formatDate(attrs.lastModifiedTime()) + "\n"
                    + "Readable: " + Files.isReadable(path) + "\n"
                    + "Writable: " + Files.isWritable(path) + "\n"
                    + "Executable: " + Files.isExecutable(path);

            JTextArea area = new JTextArea(details);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);

            JScrollPane pane = new JScrollPane(area);
            pane.setPreferredSize(new Dimension(520, 220));
            JOptionPane.showMessageDialog(this, pane, "Properties", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("Cannot read properties: " + ex.getMessage());
        }
    }

    private List<Path> getSelectedPaths() {
        int[] rows = table.getSelectedRows();
        List<Path> paths = new ArrayList<>();
        for (int row : rows) {
            int modelRow = table.convertRowIndexToModel(row);
            Path path = tableModel.getPathAt(modelRow);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }

    private Path getSingleSelectedPath() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        return tableModel.getPathAt(modelRow);
    }

    private void updateSelectionDetails() {
        Path path = getSingleSelectedPath();
        if (path == null) {
            detailsArea.setText("Select a file or folder to view details.");
            return;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long size = attrs.isDirectory() ? FileOperations.calculateDirectorySize(path) : attrs.size();
            detailsArea.setText(
                    "Name: " + path.getFileName() + "\n"
                            + "Location: " + path + "\n"
                            + "Type: " + (attrs.isDirectory() ? "Directory" : "File") + "\n"
                            + "Size: " + FileTableModel.formatSize(size) + "\n"
                            + "Modified: " + FileTableModel.formatDate(attrs.lastModifiedTime())
            );
        } catch (Exception ex) {
            detailsArea.setText("Cannot load details for: " + path);
        }
    }

    private void updateStatus() {
        int selectedCount = table.getSelectedRowCount();
        String selectedText = selectedCount > 0 ? " | Selected: " + selectedCount : "";
        String hiddenText = showHiddenFiles ? " | Hidden: On" : " | Hidden: Off";
        String scaleText = " | " + uiDensity.label + " @ " + uiScale.label;
        statusLabel.setText(
                "Items: " + table.getRowCount() + " / " + tableModel.getEntryCount()
                        + " | Visible size: " + FileTableModel.formatSize(tableModel.getTotalFileSize())
                        + selectedText
                        + hiddenText
                        + scaleText
        );
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("Error: " + message);
    }
}
