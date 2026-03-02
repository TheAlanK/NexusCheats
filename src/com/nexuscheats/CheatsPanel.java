package com.nexuscheats;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.nexusui.bridge.GameDataBridge;
import com.nexusui.overlay.NexusFrame;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CheatsPanel - Main cheats UI with sidebar navigation and category panels.
 *
 * Weapons, Ships, Resources (quick-add), and Items tabs feature searchable
 * lists populated from the Starsector API via GameDataBridge.
 */
public class CheatsPanel extends JPanel {

    private final int port;
    private final CardLayout contentLayout;
    private final JPanel contentArea;
    private final JLabel feedbackLabel;
    private Timer feedbackClearTimer;
    private Timer pollTimer;

    // Pending command results: commandId -> description
    private final Map<String, String> pendingCommands = new LinkedHashMap<String, String>();

    // Category IDs
    private static final String CAT_CREDITS = "credits";
    private static final String CAT_RESOURCES = "resources";
    private static final String CAT_ITEMS = "items";
    private static final String CAT_WEAPONS = "weapons";
    private static final String CAT_SHIPS = "ships";
    private static final String CAT_XP = "xp_skills";
    private static final String CAT_COMBAT = "combat";

    // Persistent cheat keys (stored in SectorAPI.getPersistentData())
    public static final String PERSIST_KEY_GOD_MODE = "nexus_cheats_god_mode";
    public static final String PERSIST_KEY_BONUS_OP = "nexus_cheats_bonus_op";
    public static final String GOD_MODE_STAT_ID = "nexus_god_mode";
    public static final String OP_BONUS_STAT_ID = "nexus_op_bonus";

    // Combat panel UI references
    private JLabel godModeStatusLabel;
    private JLabel opBonusStatusLabel;

    private String selectedCategory = CAT_CREDITS;
    private final Map<String, JPanel> sidebarButtons = new LinkedHashMap<String, JPanel>();

    // Game data lists
    private final List<ItemEntry> allWeapons = new ArrayList<ItemEntry>();
    private final List<ItemEntry> allShips = new ArrayList<ItemEntry>();
    private final List<ItemEntry> allItems = new ArrayList<ItemEntry>();
    private boolean dataLoaded = false;
    private boolean dataRequested = false;
    private String dataCommandId = null;

    // List models and search fields (set during panel creation)
    private DefaultListModel<ItemEntry> weaponListModel;
    private DefaultListModel<ItemEntry> shipListModel;
    private DefaultListModel<ItemEntry> itemListModel;
    private JTextField weaponSearchField;
    private JTextField shipSearchField;
    private JTextField itemSearchField;
    private JLabel weaponCountLabel;
    private JLabel shipCountLabel;
    private JLabel itemCountLabel;
    private JList<ItemEntry> weaponList;
    private JList<ItemEntry> shipList;
    private JList<ItemEntry> itemList;

    // ========================================================================
    // ItemEntry — simple data holder for list items
    // ========================================================================

    static class ItemEntry {
        final String id;
        final String name;
        final String category;
        final String searchText;
        final boolean isSpecialItem;

        ItemEntry(String id, String name, String category, boolean isSpecialItem) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.isSpecialItem = isSpecialItem;
            this.searchText = (id + " " + name + " " + category).toLowerCase();
        }

        ItemEntry(String id, String name, String category) {
            this(id, name, category, false);
        }
    }

    // ========================================================================
    // Constructor
    // ========================================================================

    public CheatsPanel(int port) {
        this.port = port;
        setLayout(new BorderLayout(0, 0));
        setBackground(NexusFrame.BG_PRIMARY);

        // --- Sidebar ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(NexusFrame.BG_SECONDARY);
        sidebar.setPreferredSize(new Dimension(160, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, NexusFrame.BORDER));

        sidebar.add(Box.createVerticalStrut(8));
        addSidebarButton(sidebar, CAT_CREDITS, "Credits", NexusFrame.ORANGE);
        addSidebarButton(sidebar, CAT_RESOURCES, "Resources", NexusFrame.CYAN);
        addSidebarButton(sidebar, CAT_ITEMS, "Items", NexusFrame.YELLOW);
        addSidebarButton(sidebar, CAT_WEAPONS, "Weapons", NexusFrame.RED);
        addSidebarButton(sidebar, CAT_SHIPS, "Ships", NexusFrame.PURPLE);
        addSidebarButton(sidebar, CAT_XP, "XP & Skills", NexusFrame.GREEN);
        addSidebarButton(sidebar, CAT_COMBAT, "Combat", new Color(255, 100, 180));
        sidebar.add(Box.createVerticalGlue());
        add(sidebar, BorderLayout.WEST);

        // --- Content area ---
        JPanel centerWrapper = new JPanel(new BorderLayout(0, 0));
        centerWrapper.setBackground(NexusFrame.BG_PRIMARY);

        contentLayout = new CardLayout();
        contentArea = new JPanel(contentLayout);
        contentArea.setBackground(NexusFrame.BG_PRIMARY);

        contentArea.add(createCreditsPanel(), CAT_CREDITS);
        contentArea.add(createResourcesPanel(), CAT_RESOURCES);
        contentArea.add(createItemsPanel(), CAT_ITEMS);
        contentArea.add(createWeaponsPanel(), CAT_WEAPONS);
        contentArea.add(createShipsPanel(), CAT_SHIPS);
        contentArea.add(createXPPanel(), CAT_XP);
        contentArea.add(createCombatPanel(), CAT_COMBAT);

        centerWrapper.add(contentArea, BorderLayout.CENTER);

        // --- Feedback bar ---
        feedbackLabel = new JLabel(" ");
        feedbackLabel.setFont(NexusFrame.FONT_MONO);
        feedbackLabel.setForeground(NexusFrame.GREEN);
        feedbackLabel.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, NexusFrame.BORDER),
            new EmptyBorder(6, 12, 6, 12)
        ));
        feedbackLabel.setOpaque(true);
        feedbackLabel.setBackground(NexusFrame.BG_SECONDARY);
        centerWrapper.add(feedbackLabel, BorderLayout.SOUTH);

        add(centerWrapper, BorderLayout.CENTER);

        // Highlight default
        updateSidebarSelection();

        // Fast poll timer for command results (200ms)
        pollTimer = new Timer(200, new ActionListener() {
            public void actionPerformed(ActionEvent e) { pollResults(); }
        });
        pollTimer.start();
    }

    // ========================================================================
    // Refresh — called by CheatsPage from NexusUI refresh thread
    // ========================================================================

    public void refresh() {
        if (!dataLoaded && !dataRequested) {
            requestGameData();
        }
    }

    // ========================================================================
    // Sidebar
    // ========================================================================

    private void addSidebarButton(JPanel sidebar, final String catId, String label, final Color accent) {
        final JPanel btn = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                boolean sel = catId.equals(selectedCategory);
                if (sel) {
                    g.setColor(accent);
                    g.fillRect(0, 0, 3, getHeight());
                }
            }
        };
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setPreferredSize(new Dimension(160, 40));
        btn.setBackground(NexusFrame.BG_SECONDARY);
        btn.setBorder(new EmptyBorder(0, 14, 0, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lbl = new JLabel(label);
        lbl.setFont(NexusFrame.FONT_BODY);
        lbl.setForeground(NexusFrame.TEXT_SECONDARY);
        btn.add(lbl, BorderLayout.CENTER);

        btn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                selectedCategory = catId;
                contentLayout.show(contentArea, catId);
                updateSidebarSelection();
            }
            public void mouseEntered(MouseEvent e) {
                if (!catId.equals(selectedCategory)) {
                    btn.setBackground(new Color(22, 30, 48));
                }
            }
            public void mouseExited(MouseEvent e) {
                boolean sel = catId.equals(selectedCategory);
                btn.setBackground(sel ? NexusFrame.BG_CARD : NexusFrame.BG_SECONDARY);
            }
        });

        sidebarButtons.put(catId, btn);
        sidebar.add(btn);
    }

    private void updateSidebarSelection() {
        for (Map.Entry<String, JPanel> entry : sidebarButtons.entrySet()) {
            boolean sel = entry.getKey().equals(selectedCategory);
            JPanel btn = entry.getValue();
            btn.setBackground(sel ? NexusFrame.BG_CARD : NexusFrame.BG_SECONDARY);
            Component lbl = ((BorderLayout) btn.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (lbl instanceof JLabel) {
                ((JLabel) lbl).setForeground(sel ? NexusFrame.CYAN : NexusFrame.TEXT_SECONDARY);
            }
            btn.repaint();
        }
    }

    // ========================================================================
    // Category Panels
    // ========================================================================

    private JPanel createCreditsPanel() {
        JPanel p = makeCategoryPanel("ADD CREDITS");
        JPanel content = (JPanel) p.getComponent(1);

        final JTextField amountField = makeTextField(15);
        addRow(content, "Amount:", amountField);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presets.setOpaque(false);
        addPresetButton(presets, amountField, "1K", "1000");
        addPresetButton(presets, amountField, "10K", "10000");
        addPresetButton(presets, amountField, "100K", "100000");
        addPresetButton(presets, amountField, "1M", "1000000");
        content.add(presets);
        content.add(Box.createVerticalStrut(12));

        JButton addBtn = makeActionButton("Add Credits", NexusFrame.ORANGE);
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final long amount = parseLong(amountField.getText());
                if (amount <= 0) { showFeedback("Enter a valid amount", false); return; }
                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                        return null;
                    }
                }, "Added " + formatNum(amount) + " credits");
            }
        });
        content.add(addBtn);
        return p;
    }

    // ── Resources (quick-add for Supplies, Fuel, Crew, Marines) ──

    private JPanel createResourcesPanel() {
        JPanel p = makeCategoryPanel("ADD RESOURCES");
        JPanel content = (JPanel) p.getComponent(1);

        addResourceRow(content, "Supplies", "supplies", NexusFrame.CYAN);
        content.add(Box.createVerticalStrut(6));
        addResourceRow(content, "Fuel", "fuel", NexusFrame.ORANGE);
        content.add(Box.createVerticalStrut(6));
        addResourceRow(content, "Crew", "crew", NexusFrame.GREEN);
        content.add(Box.createVerticalStrut(6));
        addResourceRow(content, "Marines", "marines", NexusFrame.RED);

        return p;
    }

    private void addResourceRow(JPanel container, String label, final String resourceId, Color accent) {
        // Compact single-row layout: Label | Amount field | Presets | Add button
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel header = new JLabel(label + ":");
        header.setFont(NexusFrame.FONT_BODY);
        header.setForeground(accent);
        header.setPreferredSize(new Dimension(70, 24));
        row.add(header);

        final JTextField amountField = makeTextField(7);
        row.add(amountField);

        addPresetButton(row, amountField, "100", "100");
        addPresetButton(row, amountField, "500", "500");
        addPresetButton(row, amountField, "1K", "1000");
        addPresetButton(row, amountField, "5K", "5000");

        final String displayName = label;
        JButton addBtn = new JButton("Add");
        addBtn.setFont(NexusFrame.FONT_SMALL);
        addBtn.setForeground(NexusFrame.TEXT_PRIMARY);
        addBtn.setBackground(NexusFrame.BG_CARD);
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.setBorder(new CompoundBorder(
            new LineBorder(accent, 1),
            new EmptyBorder(4, 12, 4, 12)
        ));
        final Color accentRef = accent;
        addBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                ((JButton) e.getSource()).setBackground(
                    new Color(accentRef.getRed() / 4, accentRef.getGreen() / 4, accentRef.getBlue() / 4));
            }
            public void mouseExited(MouseEvent e) {
                ((JButton) e.getSource()).setBackground(NexusFrame.BG_CARD);
            }
        });
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final int amount = parseInt(amountField.getText());
                if (amount <= 0) { showFeedback("Enter a valid amount", false); return; }

                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                        if ("crew".equals(resourceId)) cargo.addCrew(amount);
                        else if ("marines".equals(resourceId)) cargo.addMarines(amount);
                        else if ("fuel".equals(resourceId)) cargo.addFuel(amount);
                        else if ("supplies".equals(resourceId)) cargo.addSupplies(amount);
                        return null;
                    }
                }, "Added " + formatNum(amount) + " " + displayName);
            }
        });
        row.add(addBtn);

        container.add(row);
    }

    // ── Items (commodities + special items with search) ──

    private JPanel createItemsPanel() {
        JPanel p = makeCategoryPanel("ADD ITEMS");
        JPanel content = (JPanel) p.getComponent(1);

        // Search + list
        itemSearchField = makeTextField(20);
        itemCountLabel = new JLabel("0");
        itemCountLabel.setFont(NexusFrame.FONT_SMALL);
        itemCountLabel.setForeground(NexusFrame.TEXT_MUTED);
        itemListModel = new DefaultListModel<ItemEntry>();
        itemList = new JList<ItemEntry>(itemListModel);
        content.add(createSearchableList(itemList, itemListModel, itemSearchField,
            itemCountLabel, allItems));
        content.add(Box.createVerticalStrut(8));

        // Count + add button
        final JTextField countField = makeTextField(5);
        countField.setText("1");
        addRow(content, "Count:", countField);
        content.add(Box.createVerticalStrut(8));

        JButton addBtn = makeActionButton("Add Item", NexusFrame.YELLOW);
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final ItemEntry sel = itemList.getSelectedValue();
                if (sel == null) { showFeedback("Select an item", false); return; }
                final int count = parseInt(countField.getText());
                if (count <= 0) { showFeedback("Enter a valid count", false); return; }

                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                        if (sel.isSpecialItem) {
                            for (int i = 0; i < count; i++) {
                                cargo.addSpecial(new SpecialItemData(sel.id, null), 1);
                            }
                        } else {
                            cargo.addCommodity(sel.id, count);
                        }
                        return null;
                    }
                }, "Added " + count + "x " + sel.name);
            }
        });
        content.add(addBtn);
        return p;
    }

    // ── Weapons ──

    private JPanel createWeaponsPanel() {
        JPanel p = makeCategoryPanel("ADD WEAPONS");
        JPanel content = (JPanel) p.getComponent(1);

        // Search + list
        weaponSearchField = makeTextField(20);
        weaponCountLabel = new JLabel("0");
        weaponCountLabel.setFont(NexusFrame.FONT_SMALL);
        weaponCountLabel.setForeground(NexusFrame.TEXT_MUTED);
        weaponListModel = new DefaultListModel<ItemEntry>();
        weaponList = new JList<ItemEntry>(weaponListModel);
        content.add(createSearchableList(weaponList, weaponListModel, weaponSearchField,
            weaponCountLabel, allWeapons));
        content.add(Box.createVerticalStrut(8));

        // Count + add button
        final JTextField countField = makeTextField(5);
        countField.setText("1");
        addRow(content, "Count:", countField);
        content.add(Box.createVerticalStrut(8));

        JButton addBtn = makeActionButton("Add Weapon", NexusFrame.RED);
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final ItemEntry sel = weaponList.getSelectedValue();
                if (sel == null) { showFeedback("Select a weapon", false); return; }
                final int count = parseInt(countField.getText());
                if (count <= 0) { showFeedback("Enter a valid count", false); return; }

                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        Global.getSector().getPlayerFleet().getCargo().addWeapons(sel.id, count);
                        return null;
                    }
                }, "Added " + count + "x " + sel.name);
            }
        });
        content.add(addBtn);
        return p;
    }

    // ── Ships ──

    private JPanel createShipsPanel() {
        JPanel p = makeCategoryPanel("ADD SHIPS");
        JPanel content = (JPanel) p.getComponent(1);

        // Search + list
        shipSearchField = makeTextField(20);
        shipCountLabel = new JLabel("0");
        shipCountLabel.setFont(NexusFrame.FONT_SMALL);
        shipCountLabel.setForeground(NexusFrame.TEXT_MUTED);
        shipListModel = new DefaultListModel<ItemEntry>();
        shipList = new JList<ItemEntry>(shipListModel);
        content.add(createSearchableList(shipList, shipListModel, shipSearchField,
            shipCountLabel, allShips));
        content.add(Box.createVerticalStrut(8));

        JButton addBtn = makeActionButton("Add Ship", NexusFrame.PURPLE);
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final ItemEntry sel = shipList.getSelectedValue();
                if (sel == null) { showFeedback("Select a ship", false); return; }

                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        // Try hullId_Hull first, then search for any matching variant
                        String variantId = sel.id + "_Hull";
                        try {
                            FleetMemberAPI member = Global.getFactory().createFleetMember(
                                FleetMemberType.SHIP, variantId);
                            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
                            return null;
                        } catch (Exception ex) {
                            // Fallback: find first variant matching this hull
                            List<String> allIds = Global.getSettings().getAllVariantIds();
                            for (int i = 0; i < allIds.size(); i++) {
                                String vid = allIds.get(i);
                                if (vid.startsWith(sel.id + "_") || vid.equals(sel.id)) {
                                    try {
                                        FleetMemberAPI member = Global.getFactory().createFleetMember(
                                            FleetMemberType.SHIP, vid);
                                        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                                        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
                                        return null;
                                    } catch (Exception ex2) {
                                        // Try next variant
                                    }
                                }
                            }
                            return "{\"success\":false,\"error\":\"No variant found for " + sel.id + "\"}";
                        }
                    }
                }, "Added ship: " + sel.name);
            }
        });
        content.add(addBtn);
        return p;
    }

    // ── XP & Skills ──

    private JPanel createXPPanel() {
        JPanel p = makeCategoryPanel("XP & STORY POINTS");
        JPanel content = (JPanel) p.getComponent(1);

        // --- XP section ---
        JLabel xpHeader = new JLabel("Experience Points");
        xpHeader.setFont(NexusFrame.FONT_HEADER);
        xpHeader.setForeground(NexusFrame.CYAN);
        xpHeader.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(xpHeader);

        final JTextField xpField = makeTextField(15);
        addRow(content, "XP Amount:", xpField);

        JPanel xpPresets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        xpPresets.setOpaque(false);
        addPresetButton(xpPresets, xpField, "10K", "10000");
        addPresetButton(xpPresets, xpField, "100K", "100000");
        addPresetButton(xpPresets, xpField, "1M", "1000000");
        content.add(xpPresets);
        content.add(Box.createVerticalStrut(8));

        JButton addXP = makeActionButton("Add XP", NexusFrame.GREEN);
        addXP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final long amount = parseLong(xpField.getText());
                if (amount <= 0) { showFeedback("Enter a valid XP amount", false); return; }
                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        Global.getSector().getPlayerPerson().getStats().addXP(amount);
                        Global.getSector().getPlayerPerson().getStats().levelUpIfNeeded();
                        return null;
                    }
                }, "Added " + formatNum(amount) + " XP");
            }
        });
        content.add(addXP);

        content.add(Box.createVerticalStrut(20));

        // --- Story Points section ---
        JLabel spHeader = new JLabel("Story Points");
        spHeader.setFont(NexusFrame.FONT_HEADER);
        spHeader.setForeground(NexusFrame.ORANGE);
        spHeader.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(spHeader);

        final JTextField spField = makeTextField(5);
        addRow(content, "Points:", spField);

        JPanel spPresets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        spPresets.setOpaque(false);
        addPresetButton(spPresets, spField, "1", "1");
        addPresetButton(spPresets, spField, "5", "5");
        addPresetButton(spPresets, spField, "10", "10");
        addPresetButton(spPresets, spField, "25", "25");
        content.add(spPresets);
        content.add(Box.createVerticalStrut(8));

        JButton addSP = makeActionButton("Add Story Points", NexusFrame.ORANGE);
        addSP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final int amount = parseInt(spField.getText());
                if (amount <= 0) { showFeedback("Enter a valid amount", false); return; }
                enqueue(new GameDataBridge.GameCommand() {
                    public String execute() {
                        Global.getSector().getPlayerPerson().getStats().addStoryPoints(amount);
                        return null;
                    }
                }, "Added " + amount + " story points");
            }
        });
        content.add(addSP);
        return p;
    }

    // ========================================================================
    // Searchable List Component
    // ========================================================================

    private JPanel createSearchableList(final JList<ItemEntry> list,
            final DefaultListModel<ItemEntry> model, final JTextField searchField,
            final JLabel countLabel, final List<ItemEntry> masterList) {

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(LEFT_ALIGNMENT);

        // Search row
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(NexusFrame.FONT_BODY);
        searchLabel.setForeground(NexusFrame.TEXT_SECONDARY);
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(countLabel, BorderLayout.EAST);

        wrapper.add(searchRow);
        wrapper.add(Box.createVerticalStrut(6));

        // List setup
        list.setModel(model);
        list.setCellRenderer(new ItemCellRenderer());
        list.setBackground(NexusFrame.BG_SECONDARY);
        list.setForeground(NexusFrame.TEXT_PRIMARY);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionBackground(NexusFrame.BG_CARD);
        list.setSelectionForeground(NexusFrame.CYAN);
        list.setFixedCellHeight(44);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(500, 300));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        scroll.setBorder(new LineBorder(NexusFrame.BORDER, 1));
        scroll.getViewport().setBackground(NexusFrame.BG_SECONDARY);

        // Style scrollbar
        scroll.getVerticalScrollBar().setBackground(NexusFrame.BG_SECONDARY);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() {
                this.thumbColor = NexusFrame.BORDER;
                this.trackColor = NexusFrame.BG_SECONDARY;
            }
        });

        wrapper.add(scroll);

        // Search filter
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(model, masterList, searchField, countLabel); }
            public void removeUpdate(DocumentEvent e) { filterList(model, masterList, searchField, countLabel); }
            public void changedUpdate(DocumentEvent e) { filterList(model, masterList, searchField, countLabel); }
        });

        return wrapper;
    }

    private void filterList(DefaultListModel<ItemEntry> model, List<ItemEntry> masterList,
            JTextField searchField, JLabel countLabel) {
        String query = searchField.getText().toLowerCase().trim();
        model.clear();
        for (int i = 0; i < masterList.size(); i++) {
            ItemEntry e = masterList.get(i);
            if (query.isEmpty() || e.searchText.contains(query)) {
                model.addElement(e);
            }
        }
        countLabel.setText(String.valueOf(model.size()));
    }

    // ========================================================================
    // Cell Renderer
    // ========================================================================

    private class ItemCellRenderer extends JPanel implements ListCellRenderer<ItemEntry> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel detailLabel = new JLabel();

        ItemCellRenderer() {
            setLayout(new BorderLayout(0, 0));
            setBorder(new EmptyBorder(4, 10, 4, 10));

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            nameLabel.setFont(NexusFrame.FONT_BODY);
            nameLabel.setAlignmentX(LEFT_ALIGNMENT);
            textPanel.add(nameLabel);

            detailLabel.setFont(NexusFrame.FONT_SMALL);
            detailLabel.setAlignmentX(LEFT_ALIGNMENT);
            textPanel.add(detailLabel);

            add(textPanel, BorderLayout.CENTER);
        }

        public Component getListCellRendererComponent(JList<? extends ItemEntry> list,
                ItemEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value == null) return this;

            nameLabel.setText(value.name);
            detailLabel.setText(value.id + " \u00b7 " + value.category);

            if (isSelected) {
                setBackground(NexusFrame.BG_CARD);
                nameLabel.setForeground(NexusFrame.CYAN);
                detailLabel.setForeground(NexusFrame.TEXT_SECONDARY);
            } else {
                setBackground(index % 2 == 0 ? NexusFrame.BG_SECONDARY : new Color(20, 27, 38));
                nameLabel.setForeground(NexusFrame.TEXT_PRIMARY);
                detailLabel.setForeground(NexusFrame.TEXT_MUTED);
            }

            return this;
        }
    }

    // ========================================================================
    // Game Data Fetching
    // ========================================================================

    private void requestGameData() {
        GameDataBridge bridge = GameDataBridge.getInstance();
        if (bridge == null) return;
        dataRequested = true;

        dataCommandId = bridge.enqueueCommand(new GameDataBridge.GameCommand() {
            public String execute() {
                try {
                    JSONObject result = new JSONObject();

                    // Weapons
                    JSONArray weapons = new JSONArray();
                    java.util.List weaponSpecs = Global.getSettings().getAllWeaponSpecs();
                    for (int i = 0; i < weaponSpecs.size(); i++) {
                        com.fs.starfarer.api.loading.WeaponSpecAPI spec =
                            (com.fs.starfarer.api.loading.WeaponSpecAPI) weaponSpecs.get(i);
                        String typeName = spec.getType().name();
                        // Filter out system/decorative/built-in weapons
                        if ("BUILT_IN".equals(typeName) || "DECORATIVE".equals(typeName)
                                || "SYSTEM".equals(typeName) || "STATION_MODULE".equals(typeName)) {
                            continue;
                        }
                        JSONObject w = new JSONObject();
                        w.put("id", spec.getWeaponId());
                        w.put("name", spec.getWeaponName());
                        w.put("type", spec.getType().getDisplayName());
                        w.put("size", spec.getSize().getDisplayName());
                        weapons.put(w);
                    }
                    result.put("weapons", weapons);

                    // Ships
                    JSONArray ships = new JSONArray();
                    java.util.List hullSpecs = Global.getSettings().getAllShipHullSpecs();
                    for (int i = 0; i < hullSpecs.size(); i++) {
                        com.fs.starfarer.api.combat.ShipHullSpecAPI spec =
                            (com.fs.starfarer.api.combat.ShipHullSpecAPI) hullSpecs.get(i);
                        // Filter out fighters, stations, modules, d-hulls
                        String sizeName = spec.getHullSize().name();
                        if ("FIGHTER".equals(sizeName) || "DEFAULT".equals(sizeName)) continue;
                        if (spec.isDHull()) continue;
                        java.util.EnumSet hints = spec.getHints();
                        if (hints != null) {
                            if (hints.contains(com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints.STATION))
                                continue;
                            if (hints.contains(com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints.MODULE))
                                continue;
                            if (hints.contains(com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX))
                                continue;
                        }
                        String hullName = spec.getHullName();
                        if (hullName == null || hullName.isEmpty()) continue;

                        JSONObject s = new JSONObject();
                        s.put("id", spec.getHullId());
                        s.put("name", hullName);
                        s.put("size", prettifyHullSize(sizeName));
                        String designation = spec.getDesignation();
                        s.put("designation", designation != null ? designation : "");
                        ships.put(s);
                    }
                    result.put("ships", ships);

                    // Commodities (excluding core resources handled by Resources tab)
                    JSONArray commodities = new JSONArray();
                    java.util.Set<String> coreResources = new java.util.HashSet<String>();
                    coreResources.add("supplies");
                    coreResources.add("fuel");
                    coreResources.add("crew");
                    coreResources.add("marines");
                    java.util.List commoditySpecs = Global.getSettings().getAllCommoditySpecs();
                    for (int i = 0; i < commoditySpecs.size(); i++) {
                        com.fs.starfarer.api.campaign.econ.CommoditySpecAPI spec =
                            (com.fs.starfarer.api.campaign.econ.CommoditySpecAPI) commoditySpecs.get(i);
                        if (spec.isMeta()) continue;
                        String name = spec.getName();
                        if (name == null || name.isEmpty()) continue;
                        if (coreResources.contains(spec.getId())) continue;
                        JSONObject c = new JSONObject();
                        c.put("id", spec.getId());
                        c.put("name", name);
                        commodities.put(c);
                    }
                    result.put("commodities", commodities);

                    // Special items (colony items like nanoforges, synchrotrons, etc.)
                    JSONArray specialItems = new JSONArray();
                    java.util.List<com.fs.starfarer.api.campaign.SpecialItemSpecAPI> specialSpecs =
                        Global.getSettings().getAllSpecialItemSpecs();
                    for (int i = 0; i < specialSpecs.size(); i++) {
                        com.fs.starfarer.api.campaign.SpecialItemSpecAPI spec = specialSpecs.get(i);
                        String name = spec.getName();
                        if (name == null || name.isEmpty()) continue;
                        JSONObject si = new JSONObject();
                        si.put("id", spec.getId());
                        si.put("name", name);
                        String desc = spec.getDesc();
                        if (desc != null && desc.length() > 80) desc = desc.substring(0, 77) + "...";
                        si.put("desc", desc != null ? desc : "");
                        // Tag info for categorization
                        java.util.Set<String> tags = spec.getTags();
                        String tagStr = "";
                        if (tags != null && !tags.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (String t : tags) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(t);
                            }
                            tagStr = sb.toString();
                        }
                        si.put("tags", tagStr);
                        specialItems.put(si);
                    }
                    result.put("specialItems", specialItems);

                    return result.toString();
                } catch (Exception ex) {
                    return "{\"success\":false,\"error\":\"" + ex.getMessage() + "\"}";
                }
            }
        });
    }

    private void processGameDataResult(String json) {
        try {
            JSONObject data = new JSONObject(json);

            // Weapons
            JSONArray weapons = data.optJSONArray("weapons");
            if (weapons != null) {
                for (int i = 0; i < weapons.length(); i++) {
                    JSONObject w = weapons.getJSONObject(i);
                    allWeapons.add(new ItemEntry(
                        w.getString("id"),
                        w.getString("name"),
                        w.getString("type") + " \u00b7 " + w.getString("size")
                    ));
                }
                sortByName(allWeapons);
                filterList(weaponListModel, allWeapons, weaponSearchField, weaponCountLabel);
            }

            // Ships
            JSONArray ships = data.optJSONArray("ships");
            if (ships != null) {
                for (int i = 0; i < ships.length(); i++) {
                    JSONObject s = ships.getJSONObject(i);
                    String designation = s.optString("designation", "");
                    String displayName = s.getString("name");
                    if (!designation.isEmpty()) {
                        displayName = displayName + " \u2014 " + designation;
                    }
                    allShips.add(new ItemEntry(
                        s.getString("id"),
                        displayName,
                        s.getString("size")
                    ));
                }
                sortByName(allShips);
                filterList(shipListModel, allShips, shipSearchField, shipCountLabel);
            }

            // Items: commodities + special items combined
            JSONArray commodities = data.optJSONArray("commodities");
            if (commodities != null) {
                for (int i = 0; i < commodities.length(); i++) {
                    JSONObject c = commodities.getJSONObject(i);
                    allItems.add(new ItemEntry(
                        c.getString("id"),
                        c.getString("name"),
                        "Commodity",
                        false
                    ));
                }
            }

            JSONArray specialItems = data.optJSONArray("specialItems");
            if (specialItems != null) {
                for (int i = 0; i < specialItems.length(); i++) {
                    JSONObject si = specialItems.getJSONObject(i);
                    String tags = si.optString("tags", "");
                    String category = "Special Item";
                    if (tags.length() > 0) {
                        // Prettify first tag
                        String firstTag = tags;
                        int comma = tags.indexOf(',');
                        if (comma > 0) firstTag = tags.substring(0, comma);
                        firstTag = firstTag.replace('_', ' ').trim();
                        if (firstTag.length() > 0) {
                            category = firstTag.substring(0, 1).toUpperCase() + firstTag.substring(1);
                        }
                    }
                    allItems.add(new ItemEntry(
                        si.getString("id"),
                        si.getString("name"),
                        category,
                        true
                    ));
                }
            }

            sortByName(allItems);
            filterList(itemListModel, allItems, itemSearchField, itemCountLabel);

            dataLoaded = true;
        } catch (Exception ex) {
            showFeedback("Failed to load game data: " + ex.getMessage(), false);
        }
    }

    private static void sortByName(List<ItemEntry> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).name.compareToIgnoreCase(list.get(j).name) > 0) {
                    ItemEntry tmp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, tmp);
                }
            }
        }
    }

    private static String prettifyHullSize(String sizeName) {
        if ("CAPITAL_SHIP".equals(sizeName)) return "Capital Ship";
        if ("CRUISER".equals(sizeName)) return "Cruiser";
        if ("DESTROYER".equals(sizeName)) return "Destroyer";
        if ("FRIGATE".equals(sizeName)) return "Frigate";
        return sizeName;
    }

    // ========================================================================
    // Command execution
    // ========================================================================

    private void enqueue(GameDataBridge.GameCommand cmd, String description) {
        GameDataBridge bridge = GameDataBridge.getInstance();
        if (bridge == null) {
            showFeedback("Game not connected", false);
            return;
        }
        String id = bridge.enqueueCommand(cmd);
        pendingCommands.put(id, description);
        showFeedback("Processing...", true);
    }

    private void pollResults() {
        GameDataBridge bridge = GameDataBridge.getInstance();
        if (bridge == null) return;

        // Poll for data fetch result
        if (dataCommandId != null) {
            String result = bridge.pollCommandResult(dataCommandId);
            if (result != null) {
                final String json = result;
                dataCommandId = null;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() { processGameDataResult(json); }
                });
            }
        }

        // Poll for action command results
        Iterator<Map.Entry<String, String>> it = pendingCommands.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            String result = bridge.pollCommandResult(entry.getKey());
            if (result != null) {
                String desc = entry.getValue();
                it.remove();
                // Check if the result indicates error
                if (result.contains("\"success\":false")) {
                    showFeedback("Error: " + desc, false);
                } else {
                    showFeedback(desc, true);
                }
            }
        }
    }

    private void showFeedback(final String msg, final boolean success) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                feedbackLabel.setForeground(success ? NexusFrame.GREEN : NexusFrame.RED);
                feedbackLabel.setText(success ? "\u2713 " + msg : "\u2717 " + msg);
                if (feedbackClearTimer != null) feedbackClearTimer.stop();
                feedbackClearTimer = new Timer(4000, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        feedbackLabel.setText(" ");
                        feedbackClearTimer.stop();
                    }
                });
                feedbackClearTimer.setRepeats(false);
                feedbackClearTimer.start();
            }
        });
    }

    // ========================================================================
    // UI Factory helpers
    // ========================================================================

    private JPanel makeCategoryPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(NexusFrame.BG_PRIMARY);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel header = new JLabel(title);
        header.setFont(NexusFrame.FONT_TITLE);
        header.setForeground(NexusFrame.TEXT_PRIMARY);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 16, 0));
        panel.add(header);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(content);

        return panel;
    }

    private void addRow(JPanel container, String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel lbl = new JLabel(label);
        lbl.setFont(NexusFrame.FONT_BODY);
        lbl.setForeground(NexusFrame.TEXT_SECONDARY);
        lbl.setPreferredSize(new Dimension(90, 24));
        row.add(lbl);
        row.add(field);

        container.add(row);
        container.add(Box.createVerticalStrut(6));
    }

    private JTextField makeTextField(int columns) {
        JTextField tf = new JTextField(columns);
        tf.setFont(NexusFrame.FONT_MONO);
        tf.setForeground(NexusFrame.TEXT_PRIMARY);
        tf.setBackground(NexusFrame.BG_SECONDARY);
        tf.setCaretColor(NexusFrame.CYAN);
        tf.setBorder(new CompoundBorder(
            new LineBorder(NexusFrame.BORDER, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
        return tf;
    }

    private JButton makeActionButton(String text, final Color accent) {
        final JButton btn = new JButton(text);
        btn.setFont(NexusFrame.FONT_HEADER);
        btn.setForeground(NexusFrame.TEXT_PRIMARY);
        btn.setBackground(NexusFrame.BG_CARD);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
            new LineBorder(accent, 1),
            new EmptyBorder(8, 20, 8, 20)
        ));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(accent.getRed() / 4, accent.getGreen() / 4, accent.getBlue() / 4));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(NexusFrame.BG_CARD);
            }
        });
        return btn;
    }

    private void addPresetButton(JPanel row, final JTextField target, String label, final String value) {
        final JButton btn = new JButton(label);
        btn.setFont(NexusFrame.FONT_SMALL);
        btn.setForeground(NexusFrame.TEXT_SECONDARY);
        btn.setBackground(NexusFrame.BG_CARD);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
            new LineBorder(NexusFrame.BORDER, 1),
            new EmptyBorder(4, 10, 4, 10)
        ));
        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { target.setText(value); }
        });
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(NexusFrame.CYAN);
                btn.setBorder(new CompoundBorder(
                    new LineBorder(NexusFrame.CYAN, 1),
                    new EmptyBorder(4, 10, 4, 10)
                ));
            }
            public void mouseExited(MouseEvent e) {
                btn.setForeground(NexusFrame.TEXT_SECONDARY);
                btn.setBorder(new CompoundBorder(
                    new LineBorder(NexusFrame.BORDER, 1),
                    new EmptyBorder(4, 10, 4, 10)
                ));
            }
        });
        row.add(btn);
    }

    // ========================================================================
    // Combat Panel (God Mode + Ordnance Points) — PERSISTENT cheats
    // ========================================================================

    private JPanel createCombatPanel() {
        JPanel p = makeCategoryPanel("COMBAT CHEATS (PERSISTENT)");
        JPanel content = (JPanel) p.getComponent(1);

        // Info label
        JLabel infoLabel = new JLabel("<html>These cheats are saved with your game and persist across save/load.</html>");
        infoLabel.setFont(NexusFrame.FONT_SMALL);
        infoLabel.setForeground(NexusFrame.YELLOW);
        infoLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        content.add(infoLabel);

        // --- God Mode Section ---
        JLabel godHeader = new JLabel("GOD MODE");
        godHeader.setFont(NexusFrame.FONT_HEADER);
        godHeader.setForeground(NexusFrame.CYAN);
        godHeader.setBorder(new EmptyBorder(4, 0, 6, 0));
        content.add(godHeader);

        JLabel godDesc = new JLabel("<html>Makes all player fleet ships invulnerable in combat. Zeroes all damage types.</html>");
        godDesc.setFont(NexusFrame.FONT_SMALL);
        godDesc.setForeground(NexusFrame.TEXT_SECONDARY);
        godDesc.setBorder(new EmptyBorder(0, 0, 6, 0));
        content.add(godDesc);

        JPanel godRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        godRow.setOpaque(false);

        godModeStatusLabel = new JLabel("Status: checking...");
        godModeStatusLabel.setFont(NexusFrame.FONT_MONO);
        godModeStatusLabel.setForeground(NexusFrame.TEXT_MUTED);
        godRow.add(godModeStatusLabel);

        JButton godEnableBtn = makeActionButton("Enable", NexusFrame.GREEN);
        godEnableBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enqueueGodModeToggle(true);
            }
        });
        godRow.add(godEnableBtn);

        JButton godDisableBtn = makeActionButton("Disable", NexusFrame.RED);
        godDisableBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enqueueGodModeToggle(false);
            }
        });
        godRow.add(godDisableBtn);

        content.add(godRow);
        content.add(Box.createVerticalStrut(16));

        // --- Separator ---
        JSeparator sep = new JSeparator();
        sep.setForeground(NexusFrame.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(12));

        // --- Ordnance Points Section ---
        JLabel opHeader = new JLabel("BONUS ORDNANCE POINTS");
        opHeader.setFont(NexusFrame.FONT_HEADER);
        opHeader.setForeground(NexusFrame.CYAN);
        opHeader.setBorder(new EmptyBorder(4, 0, 6, 0));
        content.add(opHeader);

        JLabel opDesc = new JLabel("<html>Adds bonus OP to all ships in your fleet (fleet-wide, applied to player character stats).</html>");
        opDesc.setFont(NexusFrame.FONT_SMALL);
        opDesc.setForeground(NexusFrame.TEXT_SECONDARY);
        opDesc.setBorder(new EmptyBorder(0, 0, 6, 0));
        content.add(opDesc);

        JPanel opStatusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opStatusRow.setOpaque(false);

        opBonusStatusLabel = new JLabel("Current Bonus: checking...");
        opBonusStatusLabel.setFont(NexusFrame.FONT_MONO);
        opBonusStatusLabel.setForeground(NexusFrame.TEXT_MUTED);
        opStatusRow.add(opBonusStatusLabel);
        content.add(opStatusRow);

        content.add(Box.createVerticalStrut(6));

        JPanel opRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        opRow.setOpaque(false);

        JButton opAdd10 = makeActionButton("+10 OP", NexusFrame.GREEN);
        opAdd10.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { enqueueOPChange(10); }
        });
        opRow.add(opAdd10);

        JButton opAdd50 = makeActionButton("+50 OP", NexusFrame.GREEN);
        opAdd50.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { enqueueOPChange(50); }
        });
        opRow.add(opAdd50);

        JButton opAdd100 = makeActionButton("+100 OP", NexusFrame.GREEN);
        opAdd100.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { enqueueOPChange(100); }
        });
        opRow.add(opAdd100);

        JButton opSub10 = makeActionButton("-10 OP", NexusFrame.RED);
        opSub10.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { enqueueOPChange(-10); }
        });
        opRow.add(opSub10);

        JButton opReset = makeActionButton("Reset OP", NexusFrame.ORANGE);
        opReset.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { enqueueOPReset(); }
        });
        opRow.add(opReset);

        content.add(opRow);

        // Initial status refresh
        refreshCombatStatus();

        return p;
    }

    private void refreshCombatStatus() {
        GameDataBridge bridge = GameDataBridge.getInstance();
        if (bridge == null) return;
        bridge.enqueueCommand(new GameDataBridge.GameCommand() {
            public String execute() {
                boolean godMode = Boolean.TRUE.equals(
                        Global.getSector().getPersistentData().get(PERSIST_KEY_GOD_MODE));
                Object opObj = Global.getSector().getPersistentData().get(PERSIST_KEY_BONUS_OP);
                final int opBonus = opObj instanceof Number ? ((Number) opObj).intValue() : 0;
                final boolean gm = godMode;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (godModeStatusLabel != null) {
                            godModeStatusLabel.setText("Status: " + (gm ? "ENABLED" : "DISABLED"));
                            godModeStatusLabel.setForeground(gm ? NexusFrame.GREEN : NexusFrame.TEXT_MUTED);
                        }
                        if (opBonusStatusLabel != null) {
                            opBonusStatusLabel.setText("Current Bonus: +" + opBonus + " OP");
                            opBonusStatusLabel.setForeground(opBonus > 0 ? NexusFrame.GREEN : NexusFrame.TEXT_MUTED);
                        }
                    }
                });
                return "{\"godMode\":" + gm + ",\"opBonus\":" + opBonus + "}";
            }
        });
    }

    private void enqueueGodModeToggle(final boolean enable) {
        String desc = enable ? "God Mode enabled" : "God Mode disabled";
        enqueue(new GameDataBridge.GameCommand() {
            public String execute() {
                // Save to persistent data
                Global.getSector().getPersistentData().put(PERSIST_KEY_GOD_MODE, enable);

                // Apply/remove from all fleet members
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                if (fleet != null && fleet.getFleetData() != null) {
                    for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                        if (enable) {
                            applyGodMode(member.getStats());
                        } else {
                            removeGodMode(member.getStats());
                        }
                    }
                }
                return "{\"success\":true,\"godMode\":" + enable + "}";
            }
        }, desc);

        // Update UI
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (godModeStatusLabel != null) {
                    godModeStatusLabel.setText("Status: " + (enable ? "ENABLED" : "DISABLED"));
                    godModeStatusLabel.setForeground(enable ? NexusFrame.GREEN : NexusFrame.TEXT_MUTED);
                }
            }
        });
    }

    private void enqueueOPChange(final int delta) {
        enqueue(new GameDataBridge.GameCommand() {
            public String execute() {
                Object opObj = Global.getSector().getPersistentData().get(PERSIST_KEY_BONUS_OP);
                int current = opObj instanceof Number ? ((Number) opObj).intValue() : 0;
                final int newVal = Math.max(0, current + delta);

                Global.getSector().getPersistentData().put(PERSIST_KEY_BONUS_OP, newVal);
                applyOPBonus(newVal);

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (opBonusStatusLabel != null) {
                            opBonusStatusLabel.setText("Current Bonus: +" + newVal + " OP");
                            opBonusStatusLabel.setForeground(newVal > 0 ? NexusFrame.GREEN : NexusFrame.TEXT_MUTED);
                        }
                    }
                });
                return "{\"success\":true,\"opBonus\":" + newVal + "}";
            }
        }, "OP bonus " + (delta > 0 ? "+" : "") + delta);
    }

    private void enqueueOPReset() {
        enqueue(new GameDataBridge.GameCommand() {
            public String execute() {
                Global.getSector().getPersistentData().put(PERSIST_KEY_BONUS_OP, 0);
                applyOPBonus(0);

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (opBonusStatusLabel != null) {
                            opBonusStatusLabel.setText("Current Bonus: +0 OP");
                            opBonusStatusLabel.setForeground(NexusFrame.TEXT_MUTED);
                        }
                    }
                });
                return "{\"success\":true,\"opBonus\":0}";
            }
        }, "OP bonus reset");
    }

    // ========================================================================
    // God Mode / OP static helpers (called on game thread)
    // ========================================================================

    /** Apply god mode stat modifiers to a ship's stats. */
    public static void applyGodMode(MutableShipStatsAPI stats) {
        stats.getHullDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getArmorDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0.0001f);
        stats.getShieldDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getEnergyDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getKineticDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getHighExplosiveDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getFragmentationDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getEmpDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getBeamDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getMissileDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getProjectileDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getEngineDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
        stats.getWeaponDamageTakenMult().modifyMult(GOD_MODE_STAT_ID, 0f);
    }

    /** Remove god mode stat modifiers from a ship's stats. */
    public static void removeGodMode(MutableShipStatsAPI stats) {
        stats.getHullDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getArmorDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getShieldDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getEnergyDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getKineticDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getHighExplosiveDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getFragmentationDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getEmpDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getBeamDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getMissileDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getProjectileDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getEngineDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
        stats.getWeaponDamageTakenMult().unmodify(GOD_MODE_STAT_ID);
    }

    /** Apply ordnance point bonus to the player character (fleet-wide). */
    public static void applyOPBonus(int amount) {
        PersonAPI player = Global.getSector().getPlayerPerson();
        if (player == null) return;
        if (amount > 0) {
            player.getStats().getShipOrdnancePointBonus().modifyFlat(
                    OP_BONUS_STAT_ID, amount, "NexusCheats");
        } else {
            player.getStats().getShipOrdnancePointBonus().unmodify(OP_BONUS_STAT_ID);
        }
    }

    /** Reapply all persistent cheats from saved data. Call from onGameLoad(). */
    public static void reapplyPersistentCheats() {
        // God Mode
        boolean godMode = Boolean.TRUE.equals(
                Global.getSector().getPersistentData().get(PERSIST_KEY_GOD_MODE));
        if (godMode) {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null && fleet.getFleetData() != null) {
                for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                    applyGodMode(member.getStats());
                }
            }
        }

        // OP Bonus
        Object opObj = Global.getSector().getPersistentData().get(PERSIST_KEY_BONUS_OP);
        int opBonus = opObj instanceof Number ? ((Number) opObj).intValue() : 0;
        if (opBonus > 0) {
            applyOPBonus(opBonus);
        }
    }

    // ========================================================================
    // Parsing helpers
    // ========================================================================

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim().replace(",", "")); }
        catch (Exception e) { return -1; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim().replace(",", "")); }
        catch (Exception e) { return -1; }
    }

    private static String formatNum(long n) {
        if (n >= 1000000) return String.format("%.1fM", n / 1000000.0);
        if (n >= 1000) return String.format("%,d", n);
        return String.valueOf(n);
    }
}
