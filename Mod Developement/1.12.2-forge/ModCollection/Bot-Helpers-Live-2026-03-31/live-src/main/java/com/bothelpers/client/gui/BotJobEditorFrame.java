package com.bothelpers.client.gui;

import com.bothelpers.data.BotRegionsData;
import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.data.NamedLocationsData;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BotJobEditorFrame extends JFrame {
    private static final String[] GO_TO_TYPES = {"Name", "Coordinate", "Entity"};
    private static final String[] SLEEP_AT_TYPES = {"Name", "Coordinate"};
    private static final String[] DIG_TARGET_TYPES = {"Minecraft Block", "Name", "Coordinate"};
    private static final String[] DIG_TOOL_TYPES = {"Hand", "Inventory Tool", "Minecraft Tool"};
    private static final String[] PLACE_SOURCE_TYPES = {"Minecraft Block", "Inventory Block", "Name"};
    private static final String[] PLACE_TARGET_TYPES = {"Coordinate", "Name"};
    private static final String[] CUT_TREE_TOOL_TYPES = {"Pick Best / Fallback Hand", "Inventory Tool"};
    private static final String[] CUT_TREE_REPLANT_TYPES = {"No Replant", "Auto Detect", "Use Sapling List"};
    private static final String[] CROP_REPLANT_TYPES = {"No Replant", "Replant If Possible"};
    private static final String[] WAIT_TYPES = {"Seconds", "Minutes", "Minecraft Days"};
    private static final String[] REPEAT_TYPES = {"Amount", "Until Minecraft"};

    private final EntityBotHelper bot;
    private final List<ScratchBlock> blocks = new ArrayList<>();
    private final ScratchPanel panel;
    private final JLabel saveStatusLabel = new JLabel(" ");
    private Timer saveStatusTimer;

    private static final class OptionChoice {
        private final String value;
        private final String label;

        private OptionChoice(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
    
    public BotJobEditorFrame(EntityBotHelper bot) {
        this.bot = bot;
        setTitle("Job Editor - " + bot.getName());
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);
        
        this.panel = new ScratchPanel();
        ScratchPanel panel = this.panel;
        add(panel, BorderLayout.CENTER);
        
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomBar.setBackground(Color.WHITE);
        JButton btnSave = new JButton("Save");
        JButton btnClose = new JButton("Close");
        saveStatusLabel.setForeground(new Color(0x2E7D32));
        saveStatusLabel.setVisible(false);
        saveStatusLabel.setBorder(new EmptyBorder(0, 4, 0, 8));
        btnSave.addActionListener(e -> saveScriptToDisk());
        btnClose.addActionListener(e -> {
            dispose();
            Minecraft.getMinecraft().setIngameFocus();
        });
        bottomBar.add(btnSave);
        bottomBar.add(saveStatusLabel);
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);
        
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(220, 700));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(new LineBorder(Color.BLACK, 1));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        
        JLabel catEvents = new JLabel("Events");
        catEvents.setFont(new Font("SansSerif", Font.BOLD, 16));
        catEvents.setBorder(new EmptyBorder(10, 10, 5, 10));
        catEvents.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catEvents);
        
        sidebar.add(createPaletteDragger("When Day Starts", false, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("When Day Ends", false, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("When Bot Called", false, panel));
        
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JLabel catMovement = new JLabel("Movement");
        catMovement.setFont(new Font("SansSerif", Font.BOLD, 16));
        catMovement.setBorder(new EmptyBorder(10, 10, 5, 10));
        catMovement.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catMovement);
        
        sidebar.add(createPaletteDragger("Go To", true, panel));
        
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel catActions = new JLabel("Actions");
        catActions.setFont(new Font("SansSerif", Font.BOLD, 16));
        catActions.setBorder(new EmptyBorder(10, 10, 5, 10));
        catActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catActions);

        sidebar.add(createPaletteDragger("Dig Block", true, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("Place Block", true, panel));

        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel catJobActions = new JLabel("Job Actions");
        catJobActions.setFont(new Font("SansSerif", Font.BOLD, 16));
        catJobActions.setBorder(new EmptyBorder(10, 10, 5, 10));
        catJobActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catJobActions);

        sidebar.add(createPaletteDragger("Cut Tree", true, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("Fish", true, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("Pick Crops", true, panel));

        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel catUtility = new JLabel("Utility");
        catUtility.setFont(new Font("SansSerif", Font.BOLD, 16));
        catUtility.setBorder(new EmptyBorder(10, 10, 5, 10));
        catUtility.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catUtility);

        sidebar.add(createPaletteDragger("Wait", true, panel));
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        sidebar.add(createPaletteDragger("Repeat", true, panel));

        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JLabel catNecessities = new JLabel("Necessities");
        catNecessities.setFont(new Font("SansSerif", Font.BOLD, 16));
        catNecessities.setBorder(new EmptyBorder(10, 10, 5, 10));
        catNecessities.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(catNecessities);
        
        sidebar.add(createPaletteDragger("Sleep At", true, panel));
        
        sidebar.add(Box.createVerticalGlue());

        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(18);
        add(sidebarScroll, BorderLayout.WEST);
        
        setGlassPane(new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                panel.paintDraggedPreview(g2, this);
                g2.dispose();
            }
        });
        getGlassPane().setVisible(true);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Minecraft.getMinecraft().setIngameFocus();
            }
        });

        loadSavedScript();
    }

    private void loadSavedScript() {
        blocks.clear();
        blocks.addAll(BotJobScriptStorage.load(bot));
        for (ScratchBlock block : blocks) {
            block.refreshLayout();
        }
        syncBotJobState(!blocks.isEmpty());
        panel.repaint();
        getGlassPane().repaint();
    }

    private void saveScriptToDisk() {
        try {
            BotJobScriptStorage.save(bot, blocks);
            syncBotJobState(!blocks.isEmpty());
            showSaveStatus("Saved", new Color(0x2E7D32));
        } catch (IOException ex) {
            showSaveStatus("Save failed", new Color(0xC62828));
        }
    }

    private void showSaveStatus(String message, Color color) {
        saveStatusLabel.setText(message);
        saveStatusLabel.setForeground(color);
        saveStatusLabel.setVisible(true);

        if (saveStatusTimer != null) {
            saveStatusTimer.stop();
        }

        saveStatusTimer = new Timer(1800, e -> {
            saveStatusLabel.setVisible(false);
            saveStatusLabel.setText(" ");
        });
        saveStatusTimer.setRepeats(false);
        saveStatusTimer.start();
    }

    private void syncBotJobState(boolean hasSavedJob) {
        bot.hasJob = hasSavedJob;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.isSingleplayer() || minecraft.getIntegratedServer() == null) {
            return;
        }

        net.minecraft.world.World serverWorld = minecraft.getIntegratedServer().getWorld(bot.dimension);
        if (serverWorld == null) {
            serverWorld = minecraft.getIntegratedServer().getWorld(0);
        }
        if (serverWorld == null) {
            return;
        }

        for (net.minecraft.entity.Entity entity : serverWorld.loadedEntityList) {
            if (entity instanceof EntityBotHelper && entity.getUniqueID().equals(bot.getUniqueID())) {
                ((EntityBotHelper) entity).hasJob = hasSavedJob;
                break;
            }
        }
    }

    private void initializeBlockDefaults(ScratchBlock block) {
        if ("Dig Block".equals(block.text)) {
            block.inputType = "Minecraft Block";
            block.toolType = "Inventory Tool";
        } else if ("Place Block".equals(block.text)) {
            block.inputType = "Minecraft Block";
            block.secondaryType = "Coordinate";
        } else if ("Cut Tree".equals(block.text)) {
            block.inputValue = "8";
            block.secondaryType = "Whitelist";
            block.secondaryValue = "";
            block.toolType = "Pick Best / Fallback Hand";
            block.regionMode = "No Region Restriction";
            block.regionValue = "";
        } else if ("Fish".equals(block.text)) {
            block.inputValue = "12";
            block.regionMode = "No Region Restriction";
            block.regionValue = "";
        } else if ("Pick Crops".equals(block.text)) {
            block.inputValue = "12";
            block.secondaryType = "No Replant";
            block.regionMode = "No Region Restriction";
            block.regionValue = "";
        } else if ("Sleep At".equals(block.text)) {
            block.inputType = "Name";
        } else if ("Wait".equals(block.text)) {
            block.inputType = "Seconds";
            block.inputValue = "1";
        } else if ("Repeat".equals(block.text)) {
            block.inputType = "Amount";
            block.inputValue = "1";
        }

        block.refreshLayout();
    }

    private net.minecraft.world.World getSelectionWorld() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.isSingleplayer() && minecraft.getIntegratedServer() != null) {
                net.minecraft.world.World world = minecraft.getIntegratedServer().getWorld(bot.dimension);
                if (world != null) {
                    return world;
                }
                world = minecraft.getIntegratedServer().getWorld(0);
                if (world != null) {
                    return world;
                }
            }
        } catch (Exception ignored) {
        }

        return bot.world;
    }

    private List<OptionChoice> getNamedLocationOptions() {
        List<OptionChoice> options = new ArrayList<>();
        net.minecraft.world.World world = getSelectionWorld();
        NamedLocationsData data = NamedLocationsData.get(world);
        if (data != null) {
            for (NamedLocationsData.NamedLocation location : data.locations) {
                options.add(new OptionChoice(
                    location.name,
                    location.name + " - " + location.blockType + " @ " + location.pos.getX() + ", " + location.pos.getY() + ", " + location.pos.getZ()
                ));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getEntityOptions() {
        List<OptionChoice> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        net.minecraft.world.World world = getSelectionWorld();
        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            String name = entity.getName();
            if (name != null && !name.trim().isEmpty() && seen.add(name.toLowerCase())) {
                options.add(new OptionChoice(name, name + " - loaded entity"));
            }
        }
        for (ResourceLocation id : EntityList.getEntityNameList()) {
            if (id != null && seen.add(id.toString().toLowerCase())) {
                options.add(new OptionChoice(id.toString(), id + " - entity type"));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getRegionOptions() {
        List<OptionChoice> options = new ArrayList<>();
        BotRegionsData data = BotRegionsData.get(getSelectionWorld());
        if (data != null) {
            for (BotRegionsData.Region region : data.regions) {
                BlockPos min = region.getMinPos();
                BlockPos max = region.getMaxPos();
                options.add(new OptionChoice(
                    region.name,
                    region.name + " - " + min.getX() + "," + min.getY() + "," + min.getZ()
                        + " -> " + max.getX() + "," + max.getY() + "," + max.getZ()
                ));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getMinecraftBlockOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (ResourceLocation id : Block.REGISTRY.getKeys()) {
            Block block = Block.REGISTRY.getObject(id);
            if (block != null) {
                options.add(new OptionChoice(id.toString(), id + " - " + block.getLocalizedName()));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getMinecraftToolOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (ResourceLocation id : Item.REGISTRY.getKeys()) {
            Item item = Item.REGISTRY.getObject(id);
            if (item != null && isToolItem(item)) {
                options.add(new OptionChoice(id.toString(), id + " - " + item.getItemStackDisplayName(new net.minecraft.item.ItemStack(item))));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getInventoryBlockOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                String value = id == null ? stack.getDisplayName() : id.toString();
                options.add(new OptionChoice(value, value + " - slot " + slot + " - " + stack.getDisplayName()));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getInventoryToolOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && isToolItem(stack.getItem())) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                String value = id == null ? stack.getDisplayName() : id.toString();
                options.add(new OptionChoice(value, value + " - slot " + slot + " - " + stack.getDisplayName()));
            }
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getLogBlockOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (ResourceLocation id : Block.REGISTRY.getKeys()) {
            Block block = Block.REGISTRY.getObject(id);
            if (block == null) {
                continue;
            }

            String key = id.toString().toLowerCase();
            if (!key.contains("log") && !key.contains("wood")) {
                continue;
            }

            options.add(new OptionChoice(id.toString(), id + " - " + block.getLocalizedName()));
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getSaplingOptions() {
        List<OptionChoice> options = new ArrayList<>();
        for (ResourceLocation id : Item.REGISTRY.getKeys()) {
            Item item = Item.REGISTRY.getObject(id);
            if (!(item instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) item).getBlock();
            String key = id.toString().toLowerCase();
            if (!key.contains("sapling") && !block.getLocalizedName().toLowerCase().contains("sapling")) {
                continue;
            }

            options.add(new OptionChoice(id.toString(), id + " - " + block.getLocalizedName()));
        }
        sortOptions(options);
        return options;
    }

    private List<OptionChoice> getRepeatConditionOptions() {
        List<OptionChoice> options = new ArrayList<>();
        options.add(new OptionChoice("sunrise", "sunrise"));
        options.add(new OptionChoice("midday", "midday"));
        options.add(new OptionChoice("nightstart", "nightstart"));
        options.add(new OptionChoice("midnight", "midnight"));
        return options;
    }

    private boolean isToolItem(Item item) {
        return item instanceof ItemTool
            || item instanceof ItemSword
            || item instanceof ItemHoe
            || item instanceof ItemShears;
    }

    private void sortOptions(List<OptionChoice> options) {
        Collections.sort(options, Comparator.comparing(option -> option.label.toLowerCase()));
    }

    private String chooseOption(String title, List<OptionChoice> options, String currentValue) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setSize(450, 340);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        JTextField search = new JTextField(currentValue == null ? "" : currentValue);
        DefaultListModel<OptionChoice> model = new DefaultListModel<>();
        JList<OptionChoice> list = new JList<>(model);
        JScrollPane scrollPane = new JScrollPane(list);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(ok);
        buttons.add(cancel);

        Runnable refresh = () -> {
            model.clear();
            String query = search.getText().trim().toLowerCase();
            for (OptionChoice option : options) {
                if (query.isEmpty()
                    || option.label.toLowerCase().contains(query)
                    || option.value.toLowerCase().contains(query)) {
                    model.addElement(option);
                }
            }
        };

        refresh.run();

        final String[] result = {currentValue == null ? "" : currentValue};
        ok.addActionListener(e -> {
            OptionChoice selected = list.getSelectedValue();
            if (selected != null) {
                result[0] = selected.value;
            } else if (!search.getText().trim().isEmpty()) {
                result[0] = search.getText().trim();
            }
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2 && list.getSelectedValue() != null) {
                    result[0] = list.getSelectedValue().value;
                    dialog.dispose();
                }
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh.run(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh.run(); }
        });

        dialog.add(search, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
        return result[0];
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
    
    private JPanel createPaletteDragger(String text, boolean hasInput, ScratchPanel panel) {
        JPanel btn = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.BLACK);
                g.drawRect(0, 0, getWidth()-1, getHeight()-1);
                g.drawString(text, 10, 25);
            }
        };
        btn.setPreferredSize(new Dimension(200, 40));
        btn.setMaximumSize(new Dimension(200, 40));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ScratchBlock newBlock = new ScratchBlock(text, 50, 50, hasInput);
                initializeBlockDefaults(newBlock);
                Point p = SwingUtilities.convertPoint(btn, e.getPoint(), panel);
                Point canvasPoint = panel.toCanvas(p);
                newBlock.x = canvasPoint.x - newBlock.width / 2;
                newBlock.y = canvasPoint.y - 20;
                blocks.add(newBlock);
                panel.dragInteraction(newBlock, newBlock.width / 2, 20, canvasPoint);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                panel.dispatchEvent(SwingUtilities.convertMouseEvent(btn, e, panel));
            }
        });
        
        btn.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                panel.dispatchEvent(SwingUtilities.convertMouseEvent(btn, e, panel));
            }
        });
        
        return btn;
    }
    
    class ScratchPanel extends JPanel {
        private static final int SNAP_NONE = 0;
        private static final int SNAP_BOTTOM = 1;
        private static final int SNAP_TOP = 2;
        private static final int SNAP_BRANCH = 3;
        private static final int STACK_GAP = 15;
        private static final int BRANCH_GAP = 60;

        private ScratchBlock draggedBlock;
        private ScratchBlock selectedBlock;
        private ScratchBlock snapTarget;
        private ClipboardContent clipboard;
        private boolean clipboardSequence;
        private int offsetX;
        private int offsetY;
        private int snapMode = SNAP_NONE;
        private int canvasOffsetX;
        private int canvasOffsetY;
        private int panAnchorX;
        private int panAnchorY;
        private boolean panning;
        private Point lastContextCanvasPoint = new Point(120, 120);

        private final class ClipboardContent {
            private final ScratchBlock root;

            private ClipboardContent(ScratchBlock root) {
                this.root = root;
            }
        }

        public ScratchPanel() {
            setBackground(Color.WHITE);
            setFocusable(true);
            installKeyBindings();

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (handlePopupTrigger(e)) {
                        return;
                    }

                    Point canvasPoint = toCanvas(e.getPoint());
                    ScratchBlock block = findBlockAt(canvasPoint);
                    selectedBlock = block;

                    if (block != null) {
                        if (block.hasInput && block.getInputBounds().contains(canvasPoint) && SwingUtilities.isLeftMouseButton(e)) {
                            openEditDialog(block);
                            repaintAll();
                            return;
                        }

                        if (SwingUtilities.isLeftMouseButton(e)) {
                            detachFromParents(block);
                            dragInteraction(block, canvasPoint.x - block.x, canvasPoint.y - block.y, canvasPoint);
                            repaintAll();
                            return;
                        }
                    }

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        panning = true;
                        panAnchorX = e.getX();
                        panAnchorY = e.getY();
                    }
                    repaintAll();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (handlePopupTrigger(e)) {
                        return;
                    }

                    if (panning) {
                        panning = false;
                        return;
                    }

                    if (draggedBlock == null) {
                        return;
                    }

                    if (snapTarget != null) {
                        if (snapMode == SNAP_BOTTOM) {
                            snapTarget.next = draggedBlock;
                            draggedBlock.x = snapTarget.x;
                            draggedBlock.y = snapTarget.y + snapTarget.height + STACK_GAP;
                            updateConnectedPositions(snapTarget);
                        } else if (snapMode == SNAP_TOP) {
                            replaceChildReference(snapTarget, draggedBlock);
                            draggedBlock.next = snapTarget;
                            draggedBlock.x = snapTarget.x;
                            draggedBlock.y = snapTarget.y - draggedBlock.height - STACK_GAP;
                            updateConnectedPositions(draggedBlock);
                        } else if (snapMode == SNAP_BRANCH) {
                            snapTarget.branch = draggedBlock;
                            draggedBlock.x = snapTarget.x + snapTarget.width + BRANCH_GAP;
                            draggedBlock.y = snapTarget.y;
                            updateConnectedPositions(snapTarget);
                        }
                    }

                    clearDragState();
                    repaintAll();
                }
            };
            addMouseListener(mouseAdapter);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (panning) {
                        canvasOffsetX += e.getX() - panAnchorX;
                        canvasOffsetY += e.getY() - panAnchorY;
                        panAnchorX = e.getX();
                        panAnchorY = e.getY();
                        repaintAll();
                        return;
                    }

                    if (draggedBlock == null) {
                        return;
                    }

                    Point canvasPoint = toCanvas(e.getPoint());
                    draggedBlock.x = canvasPoint.x - offsetX;
                    draggedBlock.y = canvasPoint.y - offsetY;
                    updateConnectedPositions(draggedBlock);
                    resolveSnapTarget();
                    repaintAll();
                }
            });
        }

        private void installKeyBindings() {
            int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
            InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = getActionMap();

            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "copyBlock");
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | InputEvent.SHIFT_MASK), "copySequence");
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask), "pasteClipboard");

            actionMap.put("copyBlock", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    copySelection(false);
                }
            });
            actionMap.put("copySequence", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    copySelection(true);
                }
            });
            actionMap.put("pasteClipboard", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pasteClipboard(lastContextCanvasPoint);
                }
            });
        }

        Point toCanvas(Point point) {
            return new Point(point.x - canvasOffsetX, point.y - canvasOffsetY);
        }

        public void dragInteraction(ScratchBlock block, int offX, int offY, Point triggerPoint) {
            draggedBlock = block;
            selectedBlock = block;
            offsetX = offX;
            offsetY = offY;
            snapTarget = null;
            snapMode = SNAP_NONE;
            panning = false;
            blocks.remove(block);
            blocks.add(block);
            lastContextCanvasPoint = triggerPoint;
        }

        private boolean handlePopupTrigger(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return false;
            }

            Point canvasPoint = toCanvas(e.getPoint());
            lastContextCanvasPoint = canvasPoint;
            ScratchBlock block = findBlockAt(canvasPoint);
            selectedBlock = block;

            if (block != null) {
                showBlockPopupMenu(block, e.getX(), e.getY());
            } else {
                showCanvasPopupMenu(e.getX(), e.getY());
            }
            repaintAll();
            return true;
        }

        private void showBlockPopupMenu(ScratchBlock block, int x, int y) {
            JPopupMenu menu = new JPopupMenu();
            int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

            if (block.hasInput) {
                JMenuItem edit = new JMenuItem("Edit");
                edit.addActionListener(e -> openEditDialog(block));
                menu.add(edit);
                menu.addSeparator();
            }

            JMenuItem copyBlockItem = new JMenuItem("Copy Block");
            copyBlockItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
            copyBlockItem.addActionListener(e -> ScratchPanel.this.copyBlock(block, false));

            JMenuItem copySequenceItem = new JMenuItem("Copy Sequence");
            copySequenceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | InputEvent.SHIFT_MASK));
            copySequenceItem.addActionListener(e -> ScratchPanel.this.copyBlock(block, true));

            JMenuItem deleteBlockItem = new JMenuItem("Delete This Block");
            deleteBlockItem.setForeground(new Color(0xC62828));
            deleteBlockItem.addActionListener(e -> deleteSingleBlock(block));

            JMenuItem deleteSequenceItem = new JMenuItem("Delete Sequence");
            deleteSequenceItem.setForeground(new Color(0xC62828));
            deleteSequenceItem.addActionListener(e -> ScratchPanel.this.deleteSequence(block));

            menu.add(copyBlockItem);
            menu.add(copySequenceItem);
            menu.addSeparator();
            menu.add(deleteBlockItem);
            menu.add(deleteSequenceItem);
            menu.show(this, x, y);
        }

        private void showCanvasPopupMenu(int x, int y) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem paste = new JMenuItem(clipboardSequence ? "Paste Sequence" : "Paste Block");
            paste.setEnabled(clipboard != null && clipboard.root != null);
            paste.addActionListener(e -> pasteClipboard(lastContextCanvasPoint));

            JMenuItem cleanup = new JMenuItem("Cleanup");
            cleanup.addActionListener(e -> cleanupDetachedBlocks());

            JMenuItem deleteAll = new JMenuItem("Delete All Blocks");
            deleteAll.setForeground(new Color(0xC62828));
            deleteAll.addActionListener(e -> {
                blocks.clear();
                selectedBlock = null;
                repaintAll();
            });

            menu.add(paste);
            menu.add(cleanup);
            menu.addSeparator();
            menu.add(deleteAll);
            menu.show(this, x, y);
        }

        private void copySelection(boolean includeSequence) {
            if (selectedBlock != null) {
                copyBlock(selectedBlock, includeSequence);
            }
        }

        private void copyBlock(ScratchBlock block, boolean includeSequence) {
            if (block == null) {
                return;
            }

            ScratchBlock clonedRoot = cloneSubtree(block, includeSequence, new java.util.IdentityHashMap<>());
            clipboard = new ClipboardContent(clonedRoot);
            clipboardSequence = includeSequence;
        }

        private ScratchBlock cloneSubtree(ScratchBlock source, boolean includeChildren, java.util.IdentityHashMap<ScratchBlock, ScratchBlock> seen) {
            if (source == null) {
                return null;
            }
            ScratchBlock existing = seen.get(source);
            if (existing != null) {
                return existing;
            }

            ScratchBlock clone = source.copyShallow();
            seen.put(source, clone);
            if (includeChildren) {
                clone.next = cloneSubtree(source.next, true, seen);
                clone.branch = cloneSubtree(source.branch, true, seen);
            }
            return clone;
        }

        private void pasteClipboard(Point canvasPoint) {
            if (clipboard == null || clipboard.root == null) {
                return;
            }

            ScratchBlock root = cloneSubtree(clipboard.root, true, new java.util.IdentityHashMap<>());
            List<ScratchBlock> pasted = collectConnected(root);
            int deltaX = canvasPoint.x - root.x;
            int deltaY = canvasPoint.y - root.y;
            for (ScratchBlock block : pasted) {
                block.x += deltaX;
                block.y += deltaY;
            }

            blocks.addAll(pasted);
            selectedBlock = root;
            repaintAll();
        }

        private void deleteSingleBlock(ScratchBlock block) {
            if (block == null) {
                return;
            }

            ScratchBlock nextRoot = block.next;
            boolean reattached = replaceParentReference(block, nextRoot);
            if (!reattached && nextRoot != null) {
                nextRoot.x = block.x;
                nextRoot.y = block.y;
            }

            block.next = null;
            block.branch = null;
            blocks.remove(block);
            if (selectedBlock == block) {
                selectedBlock = nextRoot;
            }
            repaintAll();
        }

        private void deleteSequence(ScratchBlock block) {
            if (block == null) {
                return;
            }

            detachFromParents(block);
            blocks.removeAll(collectConnected(block));
            if (selectedBlock != null && !blocks.contains(selectedBlock)) {
                selectedBlock = null;
            }
            repaintAll();
        }

        private void cleanupDetachedBlocks() {
            Set<ScratchBlock> reachable = new HashSet<>();
            for (ScratchBlock block : blocks) {
                if (block.isEventStarter()) {
                    collectConnected(block, reachable);
                }
            }

            blocks.removeIf(block -> !reachable.contains(block));
            if (selectedBlock != null && !blocks.contains(selectedBlock)) {
                selectedBlock = null;
            }
            repaintAll();
        }

        private List<ScratchBlock> collectConnected(ScratchBlock root) {
            Set<ScratchBlock> visited = new HashSet<>();
            collectConnected(root, visited);
            return new ArrayList<>(visited);
        }

        private void collectConnected(ScratchBlock root, Set<ScratchBlock> visited) {
            if (root == null || !visited.add(root)) {
                return;
            }
            collectConnected(root.next, visited);
            collectConnected(root.branch, visited);
        }

        private void clearDragState() {
            draggedBlock = null;
            snapTarget = null;
            snapMode = SNAP_NONE;
        }

        private void repaintAll() {
            repaint();
            BotJobEditorFrame.this.getGlassPane().repaint();
        }

        private ScratchBlock findBlockAt(Point canvasPoint) {
            for (int i = blocks.size() - 1; i >= 0; i--) {
                ScratchBlock block = blocks.get(i);
                if (block.getBounds().contains(canvasPoint)) {
                    return block;
                }
            }
            return null;
        }

        private void detachFromParents(ScratchBlock child) {
            for (ScratchBlock block : blocks) {
                if (block.next == child) {
                    block.next = null;
                }
                if (block.branch == child) {
                    block.branch = null;
                }
            }
        }

        private boolean replaceParentReference(ScratchBlock existingChild, ScratchBlock replacement) {
            boolean replaced = false;
            for (ScratchBlock block : blocks) {
                if (block.next == existingChild) {
                    block.next = replacement;
                    replaced = true;
                }
                if (block.branch == existingChild) {
                    block.branch = replacement;
                    replaced = true;
                }
            }
            return replaced;
        }

        private void replaceChildReference(ScratchBlock existingChild, ScratchBlock replacement) {
            for (ScratchBlock block : blocks) {
                if (block == replacement) {
                    continue;
                }
                if (block.next == existingChild) {
                    block.next = replacement;
                    return;
                }
                if (block.branch == existingChild) {
                    block.branch = replacement;
                    return;
                }
            }
        }

        private boolean isConnected(ScratchBlock root, ScratchBlock target) {
            return isConnected(root, target, new HashSet<>());
        }

        private boolean isConnected(ScratchBlock root, ScratchBlock target, Set<ScratchBlock> visited) {
            if (root == null || !visited.add(root)) {
                return false;
            }
            if (root == target) {
                return true;
            }
            return isConnected(root.next, target, visited) || isConnected(root.branch, target, visited);
        }

        private void updateConnectedPositions(ScratchBlock root) {
            updateConnectedPositions(root, new HashSet<>());
        }

        private void updateConnectedPositions(ScratchBlock root, Set<ScratchBlock> visited) {
            if (root == null || !visited.add(root)) {
                return;
            }

            if (root.next != null) {
                root.next.x = root.x;
                root.next.y = root.y + root.height + STACK_GAP;
                updateConnectedPositions(root.next, visited);
            }

            if (root.isRepeatBlock() && root.branch != null) {
                root.branch.x = root.x + root.width + BRANCH_GAP;
                root.branch.y = root.y;
                updateConnectedPositions(root.branch, visited);
            }
        }

        private void resolveSnapTarget() {
            snapTarget = null;
            snapMode = SNAP_NONE;
            if (draggedBlock == null) {
                return;
            }

            double bestDistance = Double.MAX_VALUE;
            Point dragTop = new Point(draggedBlock.x + draggedBlock.width / 2, draggedBlock.y);
            Point dragBottom = new Point(draggedBlock.x + draggedBlock.width / 2, draggedBlock.y + draggedBlock.height);

            for (ScratchBlock block : blocks) {
                if (block == draggedBlock || isConnected(draggedBlock, block)) {
                    continue;
                }

                if (block.next == null) {
                    Rectangle port = block.getBottomPort();
                    double distance = Point.distance(dragTop.x, dragTop.y, port.getCenterX(), port.getCenterY());
                    if (distance < 40.0D && distance < bestDistance) {
                        bestDistance = distance;
                        snapTarget = block;
                        snapMode = SNAP_BOTTOM;
                    }
                }

                Rectangle topPort = block.getTopPort();
                double topDistance = Point.distance(dragBottom.x, dragBottom.y, topPort.getCenterX(), topPort.getCenterY());
                if (topDistance < 40.0D && topDistance < bestDistance) {
                    bestDistance = topDistance;
                    snapTarget = block;
                    snapMode = SNAP_TOP;
                }

                if (block.isRepeatBlock() && block.branch == null) {
                    Rectangle branchPort = block.getBranchPort();
                    double branchDistance = Point.distance(dragTop.x, dragTop.y, branchPort.getCenterX(), branchPort.getCenterY());
                    if (branchDistance < 45.0D && branchDistance < bestDistance) {
                        bestDistance = branchDistance;
                        snapTarget = block;
                        snapMode = SNAP_BRANCH;
                    }
                }
            }
        }

        void paintDraggedPreview(Graphics2D g2, Component target) {
            if (draggedBlock == null) {
                return;
            }

            Point origin = SwingUtilities.convertPoint(this, 0, 0, target);
            g2.translate(origin.x + canvasOffsetX, origin.y + canvasOffsetY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBlockSubtree(g2, draggedBlock, new HashSet<>());

            if (snapTarget != null) {
                g2.setColor(Color.LIGHT_GRAY);
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
                if (snapMode == SNAP_BOTTOM) {
                    g2.drawRect(snapTarget.x, snapTarget.y + snapTarget.height + STACK_GAP, draggedBlock.width, draggedBlock.height);
                } else if (snapMode == SNAP_TOP) {
                    g2.drawRect(snapTarget.x, snapTarget.y - draggedBlock.height - STACK_GAP, draggedBlock.width, draggedBlock.height);
                } else if (snapMode == SNAP_BRANCH) {
                    g2.drawRect(snapTarget.x + snapTarget.width + BRANCH_GAP, snapTarget.y, draggedBlock.width, draggedBlock.height);
                }
            }
        }

        private void drawBlockSubtree(Graphics2D g2, ScratchBlock root, Set<ScratchBlock> visited) {
            if (root == null || !visited.add(root)) {
                return;
            }

            if (root.next != null) {
                drawLine(g2, new Point(root.getBottomPort().x, root.getBottomPort().y), new Point(root.next.getTopPort().x, root.next.getTopPort().y),
                    root.getBottomPort().width, root.getBottomPort().height, root.next.getTopPort().width, root.next.getTopPort().height);
            }

            if (root.isRepeatBlock() && root.branch != null) {
                drawLine(g2, new Point(root.getBranchPort().x, root.getBranchPort().y), new Point(root.branch.getTopPort().x, root.branch.getTopPort().y),
                    root.getBranchPort().width, root.getBranchPort().height, root.branch.getTopPort().width, root.branch.getTopPort().height);
            }

            drawBlockShape(g2, root, root.x, root.y);
            drawBlockSubtree(g2, root.next, visited);
            drawBlockSubtree(g2, root.branch, visited);
        }

        private void openEditDialog(ScratchBlock block) {
            if ("Dig Block".equals(block.text)) {
                openDigBlockDialog(block);
                return;
            }
            if ("Cut Tree".equals(block.text)) {
                openCutTreeDialog(block);
                return;
            }
            if ("Fish".equals(block.text)) {
                openFishDialog(block);
                return;
            }
            if ("Pick Crops".equals(block.text)) {
                openPickCropsDialog(block);
                return;
            }
            if ("Place Block".equals(block.text)) {
                openPlaceBlockDialog(block);
                return;
            }
            if ("Wait".equals(block.text)) {
                openWaitDialog(block);
                return;
            }
            if ("Repeat".equals(block.text)) {
                openRepeatDialog(block);
                return;
            }

            openSimpleTargetDialog(block, "Go To".equals(block.text) ? GO_TO_TYPES : SLEEP_AT_TYPES);
        }

        private void openSimpleTargetDialog(ScratchBlock block, String[] allowedTypes) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit " + block.text, true);
            dialog.setSize(420, 260);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new BorderLayout(8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JComboBox<String> typeCombo = new JComboBox<>(allowedTypes);
            typeCombo.setSelectedItem(defaultString(block.inputType, allowedTypes[0]));

            CardLayout cardLayout = new CardLayout();
            JPanel cards = new JPanel(cardLayout);

            JPanel coordinatePanel = new JPanel(new GridLayout(3, 2, 5, 5));
            JTextField fieldX = new JTextField(String.valueOf(block.coordX));
            JTextField fieldY = new JTextField(String.valueOf(block.coordY));
            JTextField fieldZ = new JTextField(String.valueOf(block.coordZ));
            coordinatePanel.add(new JLabel("X:"));
            coordinatePanel.add(fieldX);
            coordinatePanel.add(new JLabel("Y:"));
            coordinatePanel.add(fieldY);
            coordinatePanel.add(new JLabel("Z:"));
            coordinatePanel.add(fieldZ);

            JPanel valuePanel = new JPanel(new BorderLayout(5, 5));
            JTextField valueField = new JTextField(defaultString(block.inputValue, ""));
            JButton pickButton = new JButton("Pick...");
            valuePanel.add(valueField, BorderLayout.CENTER);
            valuePanel.add(pickButton, BorderLayout.EAST);

            cards.add(valuePanel, "VALUE");
            cards.add(coordinatePanel, "COORD");

            Runnable updateCard = () -> cardLayout.show(cards, "Coordinate".equals(typeCombo.getSelectedItem()) ? "COORD" : "VALUE");
            typeCombo.addActionListener(e -> updateCard.run());
            updateCard.run();

            pickButton.addActionListener(e -> {
                String type = String.valueOf(typeCombo.getSelectedItem());
                List<OptionChoice> options = "Entity".equals(type) ? getEntityOptions() : getNamedLocationOptions();
                valueField.setText(chooseOption("Select " + type, options, valueField.getText()));
            });

            JPanel top = new JPanel(new GridLayout(1, 2, 5, 5));
            top.add(new JLabel("Type:"));
            top.add(typeCombo);

            center.add(top, BorderLayout.NORTH);
            center.add(cards, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputType = String.valueOf(typeCombo.getSelectedItem());
                if ("Coordinate".equals(block.inputType)) {
                    try {
                        block.coordX = Integer.parseInt(fieldX.getText().trim());
                        block.coordY = Integer.parseInt(fieldY.getText().trim());
                        block.coordZ = Integer.parseInt(fieldZ.getText().trim());
                        block.inputValue = block.coordX + ", " + block.coordY + ", " + block.coordZ;
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    block.inputValue = valueField.getText().trim();
                }
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openDigBlockDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Dig Block", true);
            dialog.setSize(520, 360);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new GridLayout(4, 1, 8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JComboBox<String> targetType = new JComboBox<>(DIG_TARGET_TYPES);
            targetType.setSelectedItem(defaultString(block.inputType, DIG_TARGET_TYPES[0]));
            JPanel targetRow = new JPanel(new BorderLayout(5, 5));
            targetRow.add(new JLabel("Target Type"), BorderLayout.WEST);
            targetRow.add(targetType, BorderLayout.CENTER);

            CardLayout targetCards = new CardLayout();
            JPanel targetPanel = new JPanel(targetCards);
            JTextField targetValue = new JTextField(defaultString(block.inputValue, ""));
            JButton pickTarget = new JButton("Pick...");
            JPanel targetValuePanel = new JPanel(new BorderLayout(5, 5));
            targetValuePanel.add(targetValue, BorderLayout.CENTER);
            targetValuePanel.add(pickTarget, BorderLayout.EAST);

            JTextField targetX = new JTextField(String.valueOf(block.coordX));
            JTextField targetY = new JTextField(String.valueOf(block.coordY));
            JTextField targetZ = new JTextField(String.valueOf(block.coordZ));
            JPanel targetCoordPanel = new JPanel(new GridLayout(3, 2, 5, 5));
            targetCoordPanel.add(new JLabel("X:"));
            targetCoordPanel.add(targetX);
            targetCoordPanel.add(new JLabel("Y:"));
            targetCoordPanel.add(targetY);
            targetCoordPanel.add(new JLabel("Z:"));
            targetCoordPanel.add(targetZ);

            targetPanel.add(targetValuePanel, "VALUE");
            targetPanel.add(targetCoordPanel, "COORD");

            Runnable updateTargetCard = () -> targetCards.show(targetPanel, "Coordinate".equals(targetType.getSelectedItem()) ? "COORD" : "VALUE");
            targetType.addActionListener(e -> updateTargetCard.run());
            updateTargetCard.run();

            pickTarget.addActionListener(e -> {
                String type = String.valueOf(targetType.getSelectedItem());
                List<OptionChoice> options = "Minecraft Block".equals(type) ? getMinecraftBlockOptions() : getNamedLocationOptions();
                targetValue.setText(chooseOption("Select " + type, options, targetValue.getText()));
            });

            JComboBox<String> toolType = new JComboBox<>(DIG_TOOL_TYPES);
            toolType.setSelectedItem(defaultString(block.toolType, DIG_TOOL_TYPES[0]));
            JPanel toolRow = new JPanel(new BorderLayout(5, 5));
            toolRow.add(new JLabel("Tool Type"), BorderLayout.WEST);
            toolRow.add(toolType, BorderLayout.CENTER);

            CardLayout toolCards = new CardLayout();
            JPanel toolPanel = new JPanel(toolCards);
            JPanel handInfoPanel = new JPanel(new BorderLayout());
            handInfoPanel.add(new JLabel("Digs with an empty hand."), BorderLayout.CENTER);
            JPanel toolInfoPanel = new JPanel(new BorderLayout());
            toolInfoPanel.add(new JLabel("Uses the best matching tool from the bot inventory."), BorderLayout.CENTER);
            JTextField toolValue = new JTextField(defaultString(block.toolValue, ""));
            JButton pickTool = new JButton("Pick...");
            JPanel toolValuePanel = new JPanel(new BorderLayout(5, 5));
            toolValuePanel.add(toolValue, BorderLayout.CENTER);
            toolValuePanel.add(pickTool, BorderLayout.EAST);
            toolPanel.add(handInfoPanel, "HAND");
            toolPanel.add(toolInfoPanel, "AUTO");
            toolPanel.add(toolValuePanel, "VALUE");

            Runnable updateToolCard = () -> {
                String selected = String.valueOf(toolType.getSelectedItem());
                if ("Minecraft Tool".equals(selected)) {
                    toolCards.show(toolPanel, "VALUE");
                } else if ("Hand".equals(selected)) {
                    toolCards.show(toolPanel, "HAND");
                } else {
                    toolCards.show(toolPanel, "AUTO");
                }
            };
            toolType.addActionListener(e -> updateToolCard.run());
            updateToolCard.run();

            pickTool.addActionListener(e -> toolValue.setText(chooseOption("Select Tool", getMinecraftToolOptions(), toolValue.getText())));

            center.add(targetRow);
            center.add(targetPanel);
            center.add(toolRow);
            center.add(toolPanel);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputType = String.valueOf(targetType.getSelectedItem());
                if ("Coordinate".equals(block.inputType)) {
                    try {
                        block.coordX = Integer.parseInt(targetX.getText().trim());
                        block.coordY = Integer.parseInt(targetY.getText().trim());
                        block.coordZ = Integer.parseInt(targetZ.getText().trim());
                        block.inputValue = block.coordX + ", " + block.coordY + ", " + block.coordZ;
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    block.inputValue = targetValue.getText().trim();
                }
                block.toolType = String.valueOf(toolType.getSelectedItem());
                block.toolValue = toolValue.getText().trim();
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openPlaceBlockDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Place Block", true);
            dialog.setSize(520, 420);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new GridLayout(4, 1, 8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JComboBox<String> sourceType = new JComboBox<>(PLACE_SOURCE_TYPES);
            sourceType.setSelectedItem(defaultString(block.inputType, PLACE_SOURCE_TYPES[0]));
            JPanel sourceRow = new JPanel(new BorderLayout(5, 5));
            sourceRow.add(new JLabel("Block Source"), BorderLayout.WEST);
            sourceRow.add(sourceType, BorderLayout.CENTER);

            JTextField sourceValue = new JTextField(defaultString(block.inputValue, ""));
            JButton pickSource = new JButton("Pick...");
            JPanel sourcePanel = new JPanel(new BorderLayout(5, 5));
            sourcePanel.add(sourceValue, BorderLayout.CENTER);
            sourcePanel.add(pickSource, BorderLayout.EAST);

            pickSource.addActionListener(e -> {
                String type = String.valueOf(sourceType.getSelectedItem());
                List<OptionChoice> options;
                if ("Inventory Block".equals(type)) {
                    options = getInventoryBlockOptions();
                } else if ("Name".equals(type)) {
                    options = getNamedLocationOptions();
                } else {
                    options = getMinecraftBlockOptions();
                }
                sourceValue.setText(chooseOption("Select Block Source", options, sourceValue.getText()));
            });

            JComboBox<String> targetType = new JComboBox<>(PLACE_TARGET_TYPES);
            targetType.setSelectedItem(defaultString(block.secondaryType, PLACE_TARGET_TYPES[0]));
            JPanel targetRow = new JPanel(new BorderLayout(5, 5));
            targetRow.add(new JLabel("Place At"), BorderLayout.WEST);
            targetRow.add(targetType, BorderLayout.CENTER);

            CardLayout targetCards = new CardLayout();
            JPanel targetPanel = new JPanel(targetCards);
            JTextField targetValue = new JTextField(defaultString(block.secondaryValue, ""));
            JButton pickTarget = new JButton("Pick...");
            JPanel targetValuePanel = new JPanel(new BorderLayout(5, 5));
            targetValuePanel.add(targetValue, BorderLayout.CENTER);
            targetValuePanel.add(pickTarget, BorderLayout.EAST);

            JTextField targetX = new JTextField(String.valueOf(block.secondaryCoordX));
            JTextField targetY = new JTextField(String.valueOf(block.secondaryCoordY));
            JTextField targetZ = new JTextField(String.valueOf(block.secondaryCoordZ));
            JPanel targetCoordPanel = new JPanel(new GridLayout(3, 2, 5, 5));
            targetCoordPanel.add(new JLabel("X:"));
            targetCoordPanel.add(targetX);
            targetCoordPanel.add(new JLabel("Y:"));
            targetCoordPanel.add(targetY);
            targetCoordPanel.add(new JLabel("Z:"));
            targetCoordPanel.add(targetZ);

            targetPanel.add(targetCoordPanel, "COORD");
            targetPanel.add(targetValuePanel, "VALUE");
            Runnable updateTargetCard = () -> targetCards.show(targetPanel, "Coordinate".equals(targetType.getSelectedItem()) ? "COORD" : "VALUE");
            targetType.addActionListener(e -> updateTargetCard.run());
            updateTargetCard.run();

            pickTarget.addActionListener(e -> targetValue.setText(chooseOption("Select Named Location", getNamedLocationOptions(), targetValue.getText())));

            center.add(sourceRow);
            center.add(sourcePanel);
            center.add(targetRow);
            center.add(targetPanel);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputType = String.valueOf(sourceType.getSelectedItem());
                block.inputValue = sourceValue.getText().trim();
                block.secondaryType = String.valueOf(targetType.getSelectedItem());
                if ("Coordinate".equals(block.secondaryType)) {
                    try {
                        block.secondaryCoordX = Integer.parseInt(targetX.getText().trim());
                        block.secondaryCoordY = Integer.parseInt(targetY.getText().trim());
                        block.secondaryCoordZ = Integer.parseInt(targetZ.getText().trim());
                        block.secondaryValue = block.secondaryCoordX + ", " + block.secondaryCoordY + ", " + block.secondaryCoordZ;
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    block.secondaryValue = targetValue.getText().trim();
                }
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openCutTreeDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Cut Tree", true);
            dialog.setSize(620, 620);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new BorderLayout(10, 10));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new GridLayout(7, 2, 8, 8));
            JTextField radiusField = new JTextField(defaultString(block.inputValue, "8"));
            JComboBox<String> filterMode = new JComboBox<>(new String[]{"Whitelist", "Blacklist"});
            filterMode.setSelectedItem(defaultString(block.secondaryType, "Whitelist"));
            JComboBox<String> toolMode = new JComboBox<>(CUT_TREE_TOOL_TYPES);
            toolMode.setSelectedItem(defaultString(block.toolType, CUT_TREE_TOOL_TYPES[0]));
            JComboBox<String> regionMode = new JComboBox<>(new String[]{"No Region Restriction", "Region Restriction"});
            regionMode.setSelectedItem(defaultString(block.regionMode, "No Region Restriction"));
            JTextField regionField = new JTextField(defaultString(block.regionValue, ""));
            JButton pickRegion = new JButton("Pick...");
            JPanel regionPanel = new JPanel(new BorderLayout(5, 5));
            regionPanel.add(regionField, BorderLayout.CENTER);
            regionPanel.add(pickRegion, BorderLayout.EAST);
            JComboBox<String> replantMode = new JComboBox<>(CUT_TREE_REPLANT_TYPES);
            replantMode.setSelectedItem(defaultString(block.saplingMode, CUT_TREE_REPLANT_TYPES[0]));
            JComboBox<String> saplingFilterMode = new JComboBox<>(new String[]{"Whitelist", "Blacklist"});
            saplingFilterMode.setSelectedItem(defaultString(block.saplingFilterMode, "Whitelist"));

            top.add(new JLabel("Scan Radius:"));
            top.add(radiusField);
            top.add(new JLabel("Tree List Mode:"));
            top.add(filterMode);
            top.add(new JLabel("Tool To Use:"));
            top.add(toolMode);
            top.add(new JLabel("Region Select:"));
            top.add(regionMode);
            top.add(new JLabel("Region:"));
            top.add(regionPanel);
            top.add(new JLabel("Replant Sapling:"));
            top.add(replantMode);
            top.add(new JLabel("Sapling List Mode:"));
            top.add(saplingFilterMode);

            DefaultListModel<String> logModel = new DefaultListModel<>();
            for (String value : defaultString(block.secondaryValue, "").split("[,;\\n]")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    logModel.addElement(trimmed);
                }
            }
            JList<String> logList = new JList<>(logModel);
            JScrollPane logScroll = new JScrollPane(logList);

            JPanel logButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addLog = new JButton("Add Log...");
            JButton removeLog = new JButton("Remove");
            JButton clearLogs = new JButton("Clear");
            logButtons.add(addLog);
            logButtons.add(removeLog);
            logButtons.add(clearLogs);

            JPanel logPanel = new JPanel(new BorderLayout(5, 5));
            logPanel.setBorder(BorderFactory.createTitledBorder("Allowed / Blocked Log Types"));
            logPanel.add(logScroll, BorderLayout.CENTER);
            logPanel.add(logButtons, BorderLayout.SOUTH);

            addLog.addActionListener(e -> {
                String choice = chooseOption("Select Log Type", getLogBlockOptions(), "");
                String trimmed = choice == null ? "" : choice.trim();
                if (trimmed.isEmpty()) {
                    return;
                }
                for (int i = 0; i < logModel.size(); i++) {
                    if (trimmed.equalsIgnoreCase(logModel.get(i))) {
                        return;
                    }
                }
                logModel.addElement(trimmed);
            });
            removeLog.addActionListener(e -> {
                int index = logList.getSelectedIndex();
                if (index >= 0) {
                    logModel.remove(index);
                }
            });
            clearLogs.addActionListener(e -> logModel.clear());

            DefaultListModel<String> saplingModel = new DefaultListModel<>();
            for (String value : defaultString(block.saplingValue, "").split("[,;\\n]")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    saplingModel.addElement(trimmed);
                }
            }
            JList<String> saplingList = new JList<>(saplingModel);
            JScrollPane saplingScroll = new JScrollPane(saplingList);

            JPanel saplingButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addSapling = new JButton("Add Sapling...");
            JButton removeSapling = new JButton("Remove");
            JButton clearSaplings = new JButton("Clear");
            saplingButtons.add(addSapling);
            saplingButtons.add(removeSapling);
            saplingButtons.add(clearSaplings);

            JPanel saplingPanel = new JPanel(new BorderLayout(5, 5));
            saplingPanel.setBorder(BorderFactory.createTitledBorder("Sapling Types"));
            saplingPanel.add(saplingScroll, BorderLayout.CENTER);
            saplingPanel.add(saplingButtons, BorderLayout.SOUTH);

            addSapling.addActionListener(e -> {
                String choice = chooseOption("Select Sapling Type", getSaplingOptions(), "");
                String trimmed = choice == null ? "" : choice.trim();
                if (trimmed.isEmpty()) {
                    return;
                }
                for (int i = 0; i < saplingModel.size(); i++) {
                    if (trimmed.equalsIgnoreCase(saplingModel.get(i))) {
                        return;
                    }
                }
                saplingModel.addElement(trimmed);
            });
            removeSapling.addActionListener(e -> {
                int index = saplingList.getSelectedIndex();
                if (index >= 0) {
                    saplingModel.remove(index);
                }
            });
            clearSaplings.addActionListener(e -> saplingModel.clear());

            JPanel toolPanel = new JPanel(new BorderLayout(5, 5));
            JTextField toolValue = new JTextField(defaultString(block.toolValue, ""));
            JButton pickTool = new JButton("Pick...");
            toolPanel.setBorder(BorderFactory.createTitledBorder("Inventory Tool"));
            toolPanel.add(toolValue, BorderLayout.CENTER);
            toolPanel.add(pickTool, BorderLayout.EAST);
            pickTool.addActionListener(e -> toolValue.setText(chooseOption("Select Inventory Tool", getInventoryToolOptions(), toolValue.getText())));

            CardLayout toolCardLayout = new CardLayout();
            JPanel toolCards = new JPanel(toolCardLayout);
            JPanel toolHint = new JPanel(new BorderLayout());
            toolHint.add(new JLabel("Automatically chooses the best inventory tool and falls back to hand."), BorderLayout.CENTER);
            toolCards.add(toolHint, "AUTO");
            toolCards.add(toolPanel, "CUSTOM");

            Runnable refreshToolCard = () -> toolCardLayout.show(toolCards,
                "Inventory Tool".equals(toolMode.getSelectedItem()) ? "CUSTOM" : "AUTO");
            toolMode.addActionListener(e -> refreshToolCard.run());
            refreshToolCard.run();

            Runnable refreshRegionPanel = () -> {
                boolean restricted = "Region Restriction".equals(regionMode.getSelectedItem());
                regionField.setEnabled(restricted);
                pickRegion.setEnabled(restricted);
            };
            regionMode.addActionListener(e -> refreshRegionPanel.run());
            refreshRegionPanel.run();

            pickRegion.addActionListener(e -> regionField.setText(chooseOption("Select Region", getRegionOptions(), regionField.getText())));

            Runnable refreshSaplingControls = () -> {
                boolean usingSaplingList = "Use Sapling List".equals(replantMode.getSelectedItem());
                saplingFilterMode.setEnabled(usingSaplingList);
                addSapling.setEnabled(usingSaplingList);
                removeSapling.setEnabled(usingSaplingList);
                clearSaplings.setEnabled(usingSaplingList);
                saplingList.setEnabled(usingSaplingList);
            };
            replantMode.addActionListener(e -> refreshSaplingControls.run());
            refreshSaplingControls.run();

            JTextArea hint = new JTextArea("Leave the log list empty to allow every detected log block from the registry.");
            hint.setEditable(false);
            hint.setOpaque(false);
            hint.setFocusable(false);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputValue = radiusField.getText().trim();
                block.secondaryType = String.valueOf(filterMode.getSelectedItem());

                List<String> configuredLogs = new ArrayList<>();
                for (int i = 0; i < logModel.size(); i++) {
                    configuredLogs.add(logModel.get(i));
                }
                block.secondaryValue = String.join(", ", configuredLogs);
                block.toolType = String.valueOf(toolMode.getSelectedItem());
                block.toolValue = toolValue.getText().trim();
                block.regionMode = String.valueOf(regionMode.getSelectedItem());
                block.regionValue = regionField.getText().trim();
                block.saplingMode = String.valueOf(replantMode.getSelectedItem());
                block.saplingFilterMode = String.valueOf(saplingFilterMode.getSelectedItem());
                List<String> configuredSaplings = new ArrayList<>();
                for (int i = 0; i < saplingModel.size(); i++) {
                    configuredSaplings.add(saplingModel.get(i));
                }
                block.saplingValue = String.join(", ", configuredSaplings);
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            center.add(top, BorderLayout.NORTH);
            JSplitPane listsPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, logPanel, saplingPanel);
            listsPane.setResizeWeight(0.55D);
            listsPane.setBorder(BorderFactory.createEmptyBorder());
            center.add(listsPane, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(8, 8));
            bottom.add(toolCards, BorderLayout.NORTH);
            bottom.add(hint, BorderLayout.SOUTH);
            center.add(bottom, BorderLayout.SOUTH);

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openFishDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Fish", true);
            dialog.setSize(500, 280);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new GridLayout(3, 2, 8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextField radiusField = new JTextField(defaultString(block.inputValue, "12"));
            JComboBox<String> regionMode = new JComboBox<>(new String[]{"No Region Restriction", "Region Restriction"});
            regionMode.setSelectedItem(defaultString(block.regionMode, "No Region Restriction"));
            JTextField regionField = new JTextField(defaultString(block.regionValue, ""));
            JButton pickRegion = new JButton("Pick...");
            JPanel regionPanel = new JPanel(new BorderLayout(5, 5));
            regionPanel.add(regionField, BorderLayout.CENTER);
            regionPanel.add(pickRegion, BorderLayout.EAST);

            center.add(new JLabel("Search Radius:"));
            center.add(radiusField);
            center.add(new JLabel("Region Select:"));
            center.add(regionMode);
            center.add(new JLabel("Region:"));
            center.add(regionPanel);

            Runnable refreshRegionPanel = () -> {
                boolean restricted = "Region Restriction".equals(regionMode.getSelectedItem());
                regionField.setEnabled(restricted);
                pickRegion.setEnabled(restricted);
            };
            regionMode.addActionListener(e -> refreshRegionPanel.run());
            refreshRegionPanel.run();

            pickRegion.addActionListener(e -> regionField.setText(chooseOption("Select Region", getRegionOptions(), regionField.getText())));

            JTextArea hint = new JTextArea("Fishes once by walking to the nearest water source, casting a fishing rod, and reeling in a single catch.");
            hint.setEditable(false);
            hint.setWrapStyleWord(true);
            hint.setLineWrap(true);
            hint.setOpaque(false);
            hint.setFocusable(false);
            hint.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputValue = radiusField.getText().trim();
                block.regionMode = String.valueOf(regionMode.getSelectedItem());
                block.regionValue = regionField.getText().trim();
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(hint, BorderLayout.NORTH);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openPickCropsDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Pick Crops", true);
            dialog.setSize(520, 320);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new GridLayout(4, 2, 8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextField radiusField = new JTextField(defaultString(block.inputValue, "12"));
            JComboBox<String> regionMode = new JComboBox<>(new String[]{"No Region Restriction", "Region Restriction"});
            regionMode.setSelectedItem(defaultString(block.regionMode, "No Region Restriction"));
            JTextField regionField = new JTextField(defaultString(block.regionValue, ""));
            JButton pickRegion = new JButton("Pick...");
            JPanel regionPanel = new JPanel(new BorderLayout(5, 5));
            regionPanel.add(regionField, BorderLayout.CENTER);
            regionPanel.add(pickRegion, BorderLayout.EAST);
            JComboBox<String> replantMode = new JComboBox<>(CROP_REPLANT_TYPES);
            replantMode.setSelectedItem(defaultString(block.secondaryType, CROP_REPLANT_TYPES[0]));

            center.add(new JLabel("Search Radius:"));
            center.add(radiusField);
            center.add(new JLabel("Region Select:"));
            center.add(regionMode);
            center.add(new JLabel("Region:"));
            center.add(regionPanel);
            center.add(new JLabel("Replant:"));
            center.add(replantMode);

            Runnable refreshRegionPanel = () -> {
                boolean restricted = "Region Restriction".equals(regionMode.getSelectedItem());
                regionField.setEnabled(restricted);
                pickRegion.setEnabled(restricted);
            };
            regionMode.addActionListener(e -> refreshRegionPanel.run());
            refreshRegionPanel.run();

            pickRegion.addActionListener(e -> regionField.setText(chooseOption("Select Region", getRegionOptions(), regionField.getText())));

            JTextArea hint = new JTextArea("Harvests mature crops with hand. Pumpkins and melons only break the fruit blocks, and replanting is attempted for crops that can be replanted from inventory.");
            hint.setEditable(false);
            hint.setWrapStyleWord(true);
            hint.setLineWrap(true);
            hint.setOpaque(false);
            hint.setFocusable(false);
            hint.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputValue = radiusField.getText().trim();
                block.regionMode = String.valueOf(regionMode.getSelectedItem());
                block.regionValue = regionField.getText().trim();
                block.secondaryType = String.valueOf(replantMode.getSelectedItem());
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(hint, BorderLayout.NORTH);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openWaitDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Wait", true);
            dialog.setSize(420, 220);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new GridLayout(2, 2, 8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JComboBox<String> typeCombo = new JComboBox<>(WAIT_TYPES);
            typeCombo.setSelectedItem(defaultString(block.inputType, WAIT_TYPES[0]));
            JTextField valueField = new JTextField(defaultString(block.inputValue, "1"));

            center.add(new JLabel("Wait Type:"));
            center.add(typeCombo);
            center.add(new JLabel("Value:"));
            center.add(valueField);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputType = String.valueOf(typeCombo.getSelectedItem());
                block.inputValue = valueField.getText().trim();
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        private void openRepeatDialog(ScratchBlock block) {
            JDialog dialog = new JDialog(BotJobEditorFrame.this, "Edit Repeat", true);
            dialog.setSize(470, 280);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel center = new JPanel(new BorderLayout(8, 8));
            center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new GridLayout(1, 2, 5, 5));
            JComboBox<String> modeCombo = new JComboBox<>(REPEAT_TYPES);
            modeCombo.setSelectedItem(defaultString(block.inputType, REPEAT_TYPES[0]));
            top.add(new JLabel("Repeat Mode:"));
            top.add(modeCombo);

            CardLayout cardLayout = new CardLayout();
            JPanel cards = new JPanel(cardLayout);

            JTextField amountField = new JTextField(defaultString(block.inputValue, "1"));
            JPanel amountPanel = new JPanel(new BorderLayout(5, 5));
            amountPanel.add(new JLabel("Times:"), BorderLayout.WEST);
            amountPanel.add(amountField, BorderLayout.CENTER);

            JTextField untilField = new JTextField(defaultString(block.inputValue, "sunrise"));
            JButton pickUntil = new JButton("Pick...");
            JPanel untilPanel = new JPanel(new BorderLayout(5, 5));
            untilPanel.add(untilField, BorderLayout.CENTER);
            untilPanel.add(pickUntil, BorderLayout.EAST);

            cards.add(amountPanel, "AMOUNT");
            cards.add(untilPanel, "UNTIL");

            Runnable updateCard = () -> cardLayout.show(cards, "Until Minecraft".equals(modeCombo.getSelectedItem()) ? "UNTIL" : "AMOUNT");
            modeCombo.addActionListener(e -> updateCard.run());
            updateCard.run();

            pickUntil.addActionListener(e -> untilField.setText(chooseOption("Repeat Until", getRepeatConditionOptions(), untilField.getText())));

            JTextArea hint = new JTextArea("Loop body connects below the Repeat block.\nExit path connects to the port on the right.");
            hint.setEditable(false);
            hint.setOpaque(false);
            hint.setFocusable(false);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            buttons.add(ok);
            buttons.add(cancel);

            ok.addActionListener(e -> {
                block.inputType = String.valueOf(modeCombo.getSelectedItem());
                block.inputValue = "Until Minecraft".equals(block.inputType)
                    ? untilField.getText().trim()
                    : amountField.getText().trim();
                block.refreshLayout();
                dialog.dispose();
                repaintAll();
            });
            cancel.addActionListener(e -> dialog.dispose());

            center.add(top, BorderLayout.NORTH);
            center.add(cards, BorderLayout.CENTER);
            center.add(hint, BorderLayout.SOUTH);

            dialog.add(center, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(canvasOffsetX, canvasOffsetY);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));

            for (ScratchBlock block : blocks) {
                if (draggedBlock != null && isConnected(draggedBlock, block)) {
                    continue;
                }
                if (block.next != null) {
                    drawLine(g2, new Point(block.getBottomPort().x, block.getBottomPort().y), new Point(block.next.getTopPort().x, block.next.getTopPort().y),
                        block.getBottomPort().width, block.getBottomPort().height, block.next.getTopPort().width, block.next.getTopPort().height);
                }
                if (block.isRepeatBlock() && block.branch != null) {
                    drawLine(g2, new Point(block.getBranchPort().x, block.getBranchPort().y), new Point(block.branch.getTopPort().x, block.branch.getTopPort().y),
                        block.getBranchPort().width, block.getBranchPort().height, block.branch.getTopPort().width, block.branch.getTopPort().height);
                }
            }

            for (ScratchBlock block : blocks) {
                if (draggedBlock != null && isConnected(draggedBlock, block)) {
                    continue;
                }
                drawBlockShape(g2, block, block.x, block.y);
            }

            g2.dispose();
        }

        private void drawLine(Graphics2D g2, Point p1, Point p2, int w1, int h1, int w2, int h2) {
            g2.drawLine(p1.x + w1 / 2, p1.y + h1 / 2, p2.x + w2 / 2, p2.y + h2 / 2);
        }

        private void drawBlockShape(Graphics2D g2, ScratchBlock block, int drawX, int drawY) {
            g2.setColor(Color.WHITE);
            g2.fillRect(drawX, drawY, block.width, block.height);
            g2.setStroke(new BasicStroke(1));
            g2.setColor(block == selectedBlock ? new Color(0x1565C0) : Color.BLACK);
            g2.drawRect(drawX, drawY, block.width, block.height);
            g2.setColor(Color.BLACK);
            g2.drawString(block.text, drawX + 5, drawY + 18);

            if (block.hasInput) {
                Rectangle input = block.getInputBounds();
                int inputX = drawX + (input.x - block.x);
                int inputY = drawY + (input.y - block.y);
                g2.setColor(Color.WHITE);
                g2.fillRect(inputX, inputY, input.width, input.height);
                g2.setColor(Color.BLACK);
                g2.drawRect(inputX, inputY, input.width, input.height);

                String[] lines = block.getDisplayLines();
                for (int i = 0; i < lines.length; i++) {
                    g2.drawString(lines[i], inputX + 5, inputY + 14 + (i * 14));
                }
            }

            Rectangle topPort = block.getTopPort();
            g2.drawOval(drawX + (topPort.x - block.x), drawY + (topPort.y - block.y), topPort.width, topPort.height);
            Rectangle bottomPort = block.getBottomPort();
            g2.fillOval(drawX + (bottomPort.x - block.x), drawY + (bottomPort.y - block.y), bottomPort.width, bottomPort.height);

            if (block.isRepeatBlock()) {
                Rectangle branchPort = block.getBranchPort();
                g2.fillOval(drawX + (branchPort.x - block.x), drawY + (branchPort.y - block.y), branchPort.width, branchPort.height);
                g2.drawString("exit", drawX + block.width - 28, drawY + block.height / 2 - 8);
                g2.drawString("loop", drawX + block.width - 28, drawY + block.height - 10);
            }
        }
    }

    static class ScratchBlock {
        String text;
        int x;
        int y;
        int width = 170;
        int height = 40;

        boolean hasInput;
        String inputType = "Name";
        String inputValue = "";
        int coordX = 0;
        int coordY = 0;
        int coordZ = 0;
        String secondaryType = "Coordinate";
        String secondaryValue = "";
        int secondaryCoordX = 0;
        int secondaryCoordY = 0;
        int secondaryCoordZ = 0;
        String toolType = "Inventory Tool";
        String toolValue = "";
        String regionMode = "No Region Restriction";
        String regionValue = "";
        String saplingMode = "No Replant";
        String saplingFilterMode = "Whitelist";
        String saplingValue = "";

        ScratchBlock next;
        ScratchBlock branch;

        public ScratchBlock(String text, int x, int y, boolean hasInput) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.hasInput = hasInput;
            refreshLayout();
        }

        ScratchBlock copyShallow() {
            ScratchBlock copy = new ScratchBlock(text, x, y, hasInput);
            copy.width = width;
            copy.height = height;
            copy.inputType = inputType;
            copy.inputValue = inputValue;
            copy.coordX = coordX;
            copy.coordY = coordY;
            copy.coordZ = coordZ;
            copy.secondaryType = secondaryType;
            copy.secondaryValue = secondaryValue;
            copy.secondaryCoordX = secondaryCoordX;
            copy.secondaryCoordY = secondaryCoordY;
            copy.secondaryCoordZ = secondaryCoordZ;
            copy.toolType = toolType;
            copy.toolValue = toolValue;
            copy.regionMode = regionMode;
            copy.regionValue = regionValue;
            copy.saplingMode = saplingMode;
            copy.saplingFilterMode = saplingFilterMode;
            copy.saplingValue = saplingValue;
            return copy;
        }

        void refreshLayout() {
            if (!hasInput) {
                height = 40;
            } else if (isRepeatBlock()) {
                height = 108;
            } else if ("Cut Tree".equals(text)) {
                height = 136;
            } else if ("Fish".equals(text) || "Pick Crops".equals(text)) {
                height = 108;
            } else if (isAdvancedActionBlock()) {
                height = 108;
            } else {
                height = 84;
            }
        }

        boolean isAdvancedActionBlock() {
            return "Dig Block".equals(text)
                || "Place Block".equals(text)
                || "Cut Tree".equals(text)
                || "Fish".equals(text)
                || "Pick Crops".equals(text);
        }

        boolean isRepeatBlock() {
            return "Repeat".equals(text);
        }

        boolean isEventStarter() {
            return "When Day Starts".equals(text)
                || "When Day Ends".equals(text)
                || "When Bot Called".equals(text);
        }

        String[] getDisplayLines() {
            if (!hasInput) {
                return new String[0];
            }

            if ("Dig Block".equals(text)) {
                return new String[]{
                    clip("Target: " + describePrimary()),
                    clip("Tool: " + describeTool())
                };
            }

            if ("Place Block".equals(text)) {
                return new String[]{
                    clip("Block: " + describePrimary()),
                    clip("At: " + describeSecondary())
                };
            }

            if ("Cut Tree".equals(text)) {
                return new String[]{
                    clip("Radius: " + defaultString(inputValue, "8")),
                    clip("Region: " + describeRegion()),
                    clip(describeTreeList()),
                    clip("Replant: " + describeReplant()),
                    clip("Tool: " + describeCutTreeTool())
                };
            }

            if ("Fish".equals(text)) {
                return new String[]{
                    clip("Radius: " + defaultString(inputValue, "12")),
                    clip("Region: " + describeRegion()),
                    clip("Casts once, then reels in")
                };
            }

            if ("Pick Crops".equals(text)) {
                return new String[]{
                    clip("Radius: " + defaultString(inputValue, "12")),
                    clip("Region: " + describeRegion()),
                    clip("Replant: " + describeCropReplant())
                };
            }

            if ("Wait".equals(text)) {
                return new String[]{clip("Wait: " + defaultString(inputValue, "1") + " " + defaultString(inputType, "Seconds"))};
            }

            if (isRepeatBlock()) {
                return new String[]{
                    clip("Repeat: " + describeRepeat()),
                    clip("Loop below, exit right")
                };
            }

            return new String[]{clip(describePrimary())};
        }

        private String describePrimary() {
            if ("Coordinate".equals(inputType)) {
                return coordX + ", " + coordY + ", " + coordZ;
            }
            String value = (inputValue == null || inputValue.isEmpty()) ? ("[" + inputType + "]") : inputValue;
            return inputType + ": " + value;
        }

        private String describeSecondary() {
            if ("Coordinate".equals(secondaryType)) {
                return secondaryCoordX + ", " + secondaryCoordY + ", " + secondaryCoordZ;
            }
            String value = (secondaryValue == null || secondaryValue.isEmpty()) ? ("[" + secondaryType + "]") : secondaryValue;
            return secondaryType + ": " + value;
        }

        private String describeTool() {
            if ("Hand".equals(toolType)) {
                return "Hand";
            }
            if ("Inventory Tool".equals(toolType)) {
                return "Best inventory tool";
            }
            return (toolValue == null || toolValue.isEmpty()) ? "[Minecraft Tool]" : toolValue;
        }

        private String describeTreeList() {
            if (secondaryValue == null || secondaryValue.trim().isEmpty()) {
                return defaultString(secondaryType, "Whitelist") + ": all logs";
            }
            return defaultString(secondaryType, "Whitelist") + ": " + secondaryValue;
        }

        private String describeCutTreeTool() {
            if ("Inventory Tool".equals(toolType)) {
                return (toolValue == null || toolValue.isEmpty()) ? "[Inventory Tool]" : toolValue;
            }
            return "Best tool, then hand";
        }

        private String describeRegion() {
            if (!"Region Restriction".equals(regionMode)) {
                return "none";
            }
            return (regionValue == null || regionValue.trim().isEmpty()) ? "[Select Region]" : regionValue;
        }

        private String describeReplant() {
            if ("Use Sapling List".equals(saplingMode)) {
                if (saplingValue == null || saplingValue.trim().isEmpty()) {
                    return saplingFilterMode + ": [Saplings]";
                }
                return saplingFilterMode + ": " + saplingValue;
            }
            return saplingMode;
        }

        private String describeCropReplant() {
            return defaultString(secondaryType, "No Replant");
        }

        private String describeRepeat() {
            if ("Until Minecraft".equals(inputType)) {
                return "until " + defaultString(inputValue, "sunrise");
            }
            return defaultString(inputValue, "1") + " times";
        }

        private String clip(String value) {
            return value.length() > 30 ? value.substring(0, 27) + "..." : value;
        }

        private String defaultString(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value;
        }

        Rectangle getBounds() { return new Rectangle(x, y, width, height); }
        Rectangle getInputBounds() { return hasInput ? new Rectangle(x + 5, y + 28, width - 10, height - 35) : new Rectangle(); }
        Rectangle getTopPort() { return new Rectangle(x + width / 2 - 5, y - 5, 10, 10); }
        Rectangle getBottomPort() { return new Rectangle(x + width / 2 - 5, y + height - 5, 10, 10); }
        Rectangle getBranchPort() { return new Rectangle(x + width - 5, y + height / 2 - 5, 10, 10); }
    }
}
