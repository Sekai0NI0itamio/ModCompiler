package asd.itamio.shop;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuiShopItems extends GuiScreen {

    private final ShopCategory category;
    private final int categoryIndex;
    private final List<ItemStack> items;
    private List<ItemStack> filteredItems;
    private int scrollOffset = 0;

    private static final int SLOT_SIZE = 22;
    private static final int SPACING = 4;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;
    private static final int SEARCH_BAR_HEIGHT = 20;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private GuiTextField quantityField;
    private boolean stackMode = false;

    private GuiTextField searchField;

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.items = category.getItems();
        this.filteredItems = new ArrayList<>(items);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int searchFieldW = 200;
        int searchFieldH = 20;
        int searchLabelW = this.fontRenderer.getStringWidth("Search: ");
        int searchFieldX = (this.width - searchFieldW) / 2 + searchLabelW / 2;
        int searchFieldY = 24;
        this.searchField = new GuiTextField(20, this.fontRenderer, searchFieldX, searchFieldY, searchFieldW, searchFieldH);
        this.searchField.setFocused(false);
        this.searchField.setMaxStringLength(50);

        rebuildButtons();
    }

    private List<ItemStack> getFilteredItems() {
        return filteredItems;
    }

    private void rebuildFilteredList() {
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        Set<String> seenItems = new HashSet<>();
        filteredItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (filter.isEmpty()) {
                // Deduplicate even when not filtering
                String itemKey = item.getItem().getRegistryName() + ":" + item.getMetadata();
                if (item.hasTagCompound()) itemKey += item.getTagCompound().toString();
                if (seenItems.add(itemKey)) {
                    filteredItems.add(item);
                }
            } else {
                String itemKey = item.getItem().getRegistryName() + ":" + item.getMetadata();
                if (item.hasTagCompound()) itemKey += item.getTagCompound().toString();
                if (!seenItems.add(itemKey)) continue;

                String searchableName = getSearchableName(item).toLowerCase();
                if (searchableName.contains(filter)) {
                    filteredItems.add(item);
                }
            }
        }
        scrollOffset = 0;
    }

    private String getSearchableName(ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        // Display name
        sb.append(stack.getDisplayName());

        // Registry name (e.g. "minecraft:diamond_sword")
        if (stack.getItem().getRegistryName() != null) {
            sb.append(" ").append(stack.getItem().getRegistryName().toString());
        }

        // Unlocalized name
        sb.append(" ").append(stack.getItem().getUnlocalizedName());

        // For enchanted books, extract enchantment names from NBT
        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                sb.append(" ").append(entry.getKey().getTranslatedName(entry.getValue()));
            }
        }

        // For all items with enchantments, also add enchantment names
        if (stack.getItem() != Items.ENCHANTED_BOOK) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                sb.append(" ").append(entry.getKey().getTranslatedName(entry.getValue()));
            }
        }

        // For potion items, add potion effect names
        if (stack.getItem() == Items.POTIONITEM || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION || stack.getItem() == Items.TIPPED_ARROW) {
            sb.append(" ").append(PotionUtils.getPotionFromItem(stack).getNamePrefixed(""));
        }

        return sb.toString();
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;

            this.buttonList.add(new GuiButton(0, centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH, "\u00a7aBuy"));
            this.buttonList.add(new GuiButton(1, centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH, "\u00a7cBack"));
            this.buttonList.add(new GuiButton(2, centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH, stackMode ? "\u00a77Mode: Stacks" : "\u00a77Mode: Items"));
            this.buttonList.add(new GuiButton(3, centerX + 2, bottomY - btnH - 4, btnW, btnH, "\u00a7eMax Afford"));

            int fieldW = 100;
            int fieldH = 20;
            this.quantityField = new GuiTextField(10, this.fontRenderer, centerX - fieldW / 2, 0, fieldW, fieldH);
            this.quantityField.setText("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxStringLength(5);
        } else {
            this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 22, 200, 20, "\u00a7cBack to Categories"));
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    private void resetGlState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        drawRect(0, 0, this.width, this.height, 0xCC1E1E1E);
        GlStateManager.disableBlend();

        if (detailView) {
            drawDetailView(mouseX, mouseY);
        } else {
            drawGridView(mouseX, mouseY);
        }

        resetGlState();

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    private void drawGridView(int mouseX, int mouseY) {
        String title = "\u00a76\u00a7lShop - " + formatCategoryName(category.getName());
        this.drawCenteredString(this.fontRenderer, title, this.width / 2, 8, 0xFFFFFF);

        // Draw search bar
        if (searchField != null) {
            int searchFieldW = 200;
            int searchLabelW = this.fontRenderer.getStringWidth("Search: ");
            int searchFieldX = (this.width - searchFieldW) / 2 + searchLabelW / 2;
            int searchFieldY = 24;
            this.searchField.x = searchFieldX;
            this.searchField.y = searchFieldY;
            this.drawString(this.fontRenderer, "Search:", searchFieldX - searchLabelW - 2, searchFieldY + 6, 0xCCCCCC);
            this.searchField.drawTextBox();
        }

        int cellSize = SLOT_SIZE + SPACING;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25 + SEARCH_BAR_HEIGHT + 2;

        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        List<ItemStack> displayItems = getFilteredItems();

        // Draw items - NO hover highlighting on slots, same color always
        for (int i = 0; i < visibleCount && (startIndex + i) < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            int itemIndex = startIndex + i;
            ItemStack item = displayItems.get(itemIndex);

            drawSlotBackground(x, y, SLOT_SIZE, SLOT_SIZE);
            renderItem(item, x + (SLOT_SIZE - 16) / 2, y + (SLOT_SIZE - 16) / 2);
        }

        // Reset GL state before tooltip (tooltip leaves dirty state)
        resetGlState();

        // Draw tooltip
        for (int i = 0; i < visibleCount && (startIndex + i) < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                int itemIndex = startIndex + i;
                ItemStack item = displayItems.get(itemIndex);
                PriceEngine priceEngine = ShopMod.getPriceEngine();
                double buyPrice = priceEngine.getBuyPrice(item);
                double sellPrice = priceEngine.getSellPrice(item);

                List<String> tooltip = new ArrayList<>();
                tooltip.add("\u00a7f" + item.getDisplayName());
                tooltip.add("\u00a7aBuy: $" + String.format("%.2f", buyPrice));
                tooltip.add("\u00a7cSell: $" + String.format("%.2f", sellPrice));
                tooltip.add("");
                tooltip.add("\u00a7eLeft-click: Buy menu");
                tooltip.add("\u00a7bRight-click: Quick buy stack");

                this.drawHoveringText(tooltip, mouseX, mouseY);
                break;
            }
        }

        // CRITICAL: Reset GL state after tooltip before drawing bottom bar
        // drawHoveringText enables lighting, depth, and item lighting which causes the flicker
        resetGlState();

        // Draw bottom bar
        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        drawRect(0, barTop, this.width, this.height, 0xFF2A2A2A);

        if (displayItems.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }

        String footer = "\u00a77Left-click: Buy menu | Right-click: Quick buy stack | ESC: Back";
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(int mouseX, int mouseY) {
        List<ItemStack> displayItems = getFilteredItems();
        if (detailItemIndex < 0 || detailItemIndex >= displayItems.size()) return;

        ItemStack item = displayItems.get(detailItemIndex);
        PriceEngine priceEngine = ShopMod.getPriceEngine();
        double buyPricePerItem = priceEngine.getBuyPrice(item);
        double sellPricePerItem = priceEngine.getSellPrice(item);

        int centerX = this.width / 2;

        int y = 10;

        String title = "\u00a76\u00a7l" + item.getDisplayName();
        this.drawCenteredString(this.fontRenderer, title, centerX, y, 0xFFFFFF);
        y += 16;

        int itemCenterY = y + 24;
        drawSlotBackground(centerX - 24, itemCenterY - 24, 48, 48);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX - 8, itemCenterY - 8, 0);
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(item, 0, 0);
        GlStateManager.popMatrix();
        resetGlState();
        y = itemCenterY + 30;

        this.drawCenteredString(this.fontRenderer, "\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each", centerX, y, 0xFFFFFF);
        y += 12;
        this.drawCenteredString(this.fontRenderer, "\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each", centerX, y, 0xFFFFFF);
        y += 18;

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        this.drawCenteredString(this.fontRenderer, modeLabel, centerX, y, 0xCCCCCC);
        y += 14;

        if (quantityField != null) {
            quantityField.y = y;
            quantityField.drawTextBox();
        }
        y += 24;

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * actualItems;
        this.drawCenteredString(this.fontRenderer, "\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost), centerX, y, 0xFFFF55);
        y += 12;

        double balance = getClientBalance();
        int maxAfford = (int) (balance / buyPricePerItem);
        if (stackMode) {
            int maxStacks = maxAfford / item.getMaxStackSize();
            this.drawCenteredString(this.fontRenderer, "\u00a77You can afford: " + maxStacks + " stacks (" + maxAfford + " items)", centerX, y, 0xAAAAAA);
        } else {
            this.drawCenteredString(this.fontRenderer, "\u00a77You can afford: " + maxAfford + " items", centerX, y, 0xAAAAAA);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (GuiButton button : this.buttonList) {
            if (button.mousePressed(this.mc, mouseX, mouseY)) {
                button.playPressSound(this.mc.getSoundHandler());
                actionPerformed(button);
                return;
            }
        }

        if (detailView) {
            if (quantityField != null) {
                quantityField.mouseClicked(mouseX, mouseY, mouseButton);
            }
            return;
        }

        // Handle search field click
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        int cellSize = SLOT_SIZE + SPACING;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25 + SEARCH_BAR_HEIGHT + 2;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        List<ItemStack> displayItems = getFilteredItems();

        for (int i = 0; i < visibleCount && (startIndex + i) < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                int itemIndex = startIndex + i;
                if (mouseButton == 0) {
                    detailView = true;
                    detailItemIndex = itemIndex;
                    stackMode = false;
                    rebuildButtons();
                } else if (mouseButton == 1) {
                    quickBuyStack(itemIndex);
                }
                return;
            }
        }
    }

    private void quickBuyStack(int itemIndex) {
        List<ItemStack> displayItems = getFilteredItems();
        if (itemIndex < 0 || itemIndex >= displayItems.size()) return;

        ItemStack item = displayItems.get(itemIndex);
        PriceEngine priceEngine = ShopMod.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(item);
        double balance = getClientBalance();

        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);

        if (quantity <= 0) return;

        // Find the original item index in the category
        int originalIndex = items.indexOf(item);
        if (originalIndex >= 0) {
            ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(categoryIndex, originalIndex, quantity));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (detailView) {
            List<ItemStack> displayItems = getFilteredItems();
            if (button.id == 0) {
                int qty = getQuantity();
                if (qty > 0 && detailItemIndex >= 0 && detailItemIndex < displayItems.size()) {
                    ItemStack item = displayItems.get(detailItemIndex);
                    int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                    int originalIndex = items.indexOf(item);
                    if (originalIndex >= 0) {
                        ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(categoryIndex, originalIndex, actualItems));
                    }
                }
            } else if (button.id == 1) {
                detailView = false;
                detailItemIndex = -1;
                rebuildButtons();
            } else if (button.id == 2) {
                stackMode = !stackMode;
                if (quantityField != null) quantityField.setText("1");
                rebuildButtons();
            } else if (button.id == 3) {
                if (detailItemIndex >= 0 && detailItemIndex < displayItems.size()) {
                    ItemStack item = displayItems.get(detailItemIndex);
                    PriceEngine priceEngine = ShopMod.getPriceEngine();
                    double buyPrice = priceEngine.getBuyPrice(item);
                    double balance = getClientBalance();
                    int maxAfford = (int) (balance / buyPrice);
                    if (stackMode) {
                        int maxStacks = maxAfford / item.getMaxStackSize();
                        if (quantityField != null) quantityField.setText(String.valueOf(maxStacks));
                    } else {
                        if (quantityField != null) quantityField.setText(String.valueOf(maxAfford));
                    }
                }
            }
        } else {
            if (button.id == 0) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiShopCategories());
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (detailView && quantityField != null && quantityField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                actionPerformed(this.buttonList.get(0));
                return;
            }
            // Handle clipboard shortcuts in quantity field
            if (handleClipboardShortcut(quantityField, typedChar, keyCode)) {
                return;
            }
            if (Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
                quantityField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }

        if (!detailView && searchField != null && searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false);
                if (searchField.getText().isEmpty()) {
                    super.keyTyped(typedChar, keyCode);
                }
                return;
            }

            // Handle clipboard shortcuts in search field
            if (handleClipboardShortcut(searchField, typedChar, keyCode)) {
                rebuildFilteredList();
                return;
            }

            String oldText = searchField.getText();
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (!searchField.getText().equals(oldText)) {
                rebuildFilteredList();
            }
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private boolean handleClipboardShortcut(GuiTextField field, char typedChar, int keyCode) {
        boolean isCmd = Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA);
        boolean isCtrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (!isCmd && !isCtrl) return false;

        if (keyCode == Keyboard.KEY_V) {
            String clipboard = getClipboardString();
            if (clipboard != null && !clipboard.isEmpty()) {
                String text = field.getText();
                int pos = field.getCursorPosition();
                String newText = text.substring(0, pos) + clipboard + text.substring(pos);
                field.setText(newText);
                field.setCursorPosition(pos + clipboard.length());
            }
            return true;
        } else if (keyCode == Keyboard.KEY_C) {
            setClipboardString(field.getText());
            return true;
        } else if (keyCode == Keyboard.KEY_A) {
            field.setCursorPosition(0);
            field.setSelectionPos(field.getText().length());
            return true;
        } else if (keyCode == Keyboard.KEY_X) {
            setClipboardString(field.getText());
            field.setText("");
            field.setCursorPosition(0);
            return true;
        }
        return false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (detailView) return;

        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0) {
            int cellSize = SLOT_SIZE + SPACING;
            int guiTop = 25 + SEARCH_BAR_HEIGHT + 2;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            if (scroll > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else {
                scrollOffset = Math.min(getMaxScrollPages(rowsPerPage) - 1, scrollOffset + 1);
            }
        }
    }

    private int getQuantity() {
        if (quantityField == null) return 1;
        try {
            return Integer.parseInt(quantityField.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getClientBalance() {
        return 999999999.0;
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) getFilteredItems().size() / totalSlots));
    }

    private String formatCategoryName(String raw) {
        String[] parts = raw.split("_|\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void drawSlotBackground(int x, int y, int w, int h) {
        // No hover state - always the same color, no flicker possible
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        drawRect(x, y, x + w, y + h, 0xAA444444);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xAA333333);
        GlStateManager.disableBlend();
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        resetGlState();
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
