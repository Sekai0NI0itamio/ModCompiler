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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuiShopCategories extends GuiScreen {

    private List<ShopCategory> categories;
    private int scrollOffset = 0;
    private static final int ICON_SIZE = 28;
    private static final int CAT_SPACING = 6;
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_SPACING = 4;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;

    // Search
    private GuiTextField searchField;
    private boolean searchMode = false;
    private List<SearchResult> searchResults = new ArrayList<>();

    // Detail view (for search results)
    private boolean detailView = false;
    private int detailResultIndex = -1;
    private GuiTextField quantityField;
    private boolean stackMode = false;

    private static class SearchResult {
        final ItemStack stack;
        final int categoryIndex;
        final int itemIndex;

        SearchResult(ItemStack stack, int categoryIndex, int itemIndex) {
            this.stack = stack;
            this.categoryIndex = categoryIndex;
            this.itemIndex = itemIndex;
        }
    }

    public GuiShopCategories() {
        this.categories = ShopMod.getCategories();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int searchFieldWidth = 160;
        int searchFieldHeight = 16;
        int labelWidth = this.fontRenderer.getStringWidth("Search: ");
        int searchFieldX = (this.width - searchFieldWidth) / 2 + labelWidth / 2;
        int searchFieldY = 28;

        this.searchField = new GuiTextField(0, this.fontRenderer, searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight);
        this.searchField.setFocused(false);
        this.searchField.setMaxStringLength(50);

        rebuildButtons();
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
        } else if (searchMode) {
            this.buttonList.add(new GuiButton(4, this.width / 2 - 100, this.height - 22, 200, 20, "\u00a7cBack to Categories"));
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
        } else if (searchMode) {
            drawSearchResultsView(mouseX, mouseY);
        } else {
            drawCategoryView(mouseX, mouseY);
        }

        resetGlState();

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    private void drawCategoryView(int mouseX, int mouseY) {
        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lShop - Categories", this.width / 2, 12, 0xFFFFFF);

        drawSearchBar();

        int cellSize = ICON_SIZE + CAT_SPACING;
        int gridWidth = COLUMNS * cellSize - CAT_SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 50;

        int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && (startIndex + i) < categories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            int catIndex = startIndex + i;
            ShopCategory category = categories.get(catIndex);

            drawSlotBackground(x, y, ICON_SIZE, ICON_SIZE);

            ItemStack icon = category.getIcon();
            renderItem(icon, x + (ICON_SIZE - 16) / 2, y + (ICON_SIZE - 16) / 2);
        }

        resetGlState();

        for (int i = 0; i < visibleCount && (startIndex + i) < categories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) {
                int catIndex = startIndex + i;
                ShopCategory category = categories.get(catIndex);
                String name = formatCategoryName(category.getName());
                this.drawHoveringText(Collections.singletonList("\u00a7f" + name + " \u00a77(" + category.getItems().size() + " items)"), mouseX, mouseY);
                break;
            }
        }

        resetGlState();

        if (categories.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (scrollOffset + 1) + "/" + getMaxScrollPagesCat(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, this.height - 30, 0xFFFFFF);
        }

        String footer = "\u00a77Click a category to browse items | ESC to close";
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, this.height - 15, 0xAAAAAA);
    }

    private void drawSearchResultsView(int mouseX, int mouseY) {
        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lShop - Search Results", this.width / 2, 12, 0xFFFFFF);

        drawSearchBar();

        int cellSize = SLOT_SIZE + SLOT_SPACING;
        int gridWidth = COLUMNS * cellSize - SLOT_SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 50;

        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && (startIndex + i) < searchResults.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            int resultIndex = startIndex + i;
            SearchResult result = searchResults.get(resultIndex);

            drawSlotBackground(x, y, SLOT_SIZE, SLOT_SIZE);
            renderItem(result.stack, x + (SLOT_SIZE - 16) / 2, y + (SLOT_SIZE - 16) / 2);
        }

        resetGlState();

        for (int i = 0; i < visibleCount && (startIndex + i) < searchResults.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                int resultIndex = startIndex + i;
                SearchResult result = searchResults.get(resultIndex);
                PriceEngine priceEngine = ShopMod.getPriceEngine();
                double buyPrice = priceEngine.getBuyPrice(result.stack);
                double sellPrice = priceEngine.getSellPrice(result.stack);

                List<String> tooltip = new ArrayList<>();
                tooltip.add("\u00a7f" + result.stack.getDisplayName());
                tooltip.add("\u00a77Category: " + formatCategoryName(categories.get(result.categoryIndex).getName()));
                tooltip.add("\u00a7aBuy: $" + String.format("%.2f", buyPrice));
                tooltip.add("\u00a7cSell: $" + String.format("%.2f", sellPrice));
                tooltip.add("");
                tooltip.add("\u00a7eLeft-click: Buy menu");
                tooltip.add("\u00a7bRight-click: Quick buy stack");

                this.drawHoveringText(tooltip, mouseX, mouseY);
                break;
            }
        }

        resetGlState();

        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        drawRect(0, barTop, this.width, this.height, 0xFF2A2A2A);

        if (searchResults.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (scrollOffset + 1) + "/" + getMaxScrollPagesSearch(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }

        String resultCount = "\u00a77Found " + searchResults.size() + " items | Left-click: Buy menu | Right-click: Quick buy stack";
        this.drawCenteredString(this.fontRenderer, resultCount, this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(int mouseX, int mouseY) {
        if (detailResultIndex < 0 || detailResultIndex >= searchResults.size()) return;

        SearchResult result = searchResults.get(detailResultIndex);
        ItemStack item = result.stack;
        PriceEngine priceEngine = ShopMod.getPriceEngine();
        double buyPricePerItem = priceEngine.getBuyPrice(item);
        double sellPricePerItem = priceEngine.getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;

        String title = "\u00a76\u00a7l" + item.getDisplayName();
        this.drawCenteredString(this.fontRenderer, title, centerX, y, 0xFFFFFF);
        y += 16;

        this.drawCenteredString(this.fontRenderer, "\u00a77Category: " + formatCategoryName(categories.get(result.categoryIndex).getName()), centerX, y, 0xAAAAAA);
        y += 14;

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

    private void drawSearchBar() {
        if (searchField != null) {
            int searchFieldWidth = searchField.getWidth();
            int labelWidth = this.fontRenderer.getStringWidth("Search: ");
            int totalWidth = labelWidth + searchFieldWidth;
            int startX = (this.width - totalWidth) / 2;
            int searchFieldY = searchField.y;

            this.drawString(this.fontRenderer, "Search:", startX, searchFieldY + 4, 0xCCCCCC);
            searchField.drawTextBox();
        }
    }

    private void performSearch(String query) {
        searchResults.clear();
        if (query.isEmpty()) {
            searchMode = false;
            scrollOffset = 0;
            rebuildButtons();
            return;
        }

        String lowerQuery = query.toLowerCase();
        Set<String> seenItems = new HashSet<>();

        for (int catIdx = 0; catIdx < categories.size(); catIdx++) {
            ShopCategory category = categories.get(catIdx);
            List<ItemStack> items = category.getItems();
            for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
                ItemStack item = items.get(itemIdx);

                // Deduplicate by item identity
                String itemKey = item.getItem().getRegistryName() + ":" + item.getMetadata();
                if (item.hasTagCompound()) {
                    itemKey += item.getTagCompound().toString();
                }
                if (seenItems.contains(itemKey)) continue;

                // Search against comprehensive name
                String searchableName = getSearchableName(item).toLowerCase();
                if (searchableName.contains(lowerQuery)) {
                    searchResults.add(new SearchResult(item, catIdx, itemIdx));
                    seenItems.add(itemKey);
                }
            }
        }

        searchMode = true;
        scrollOffset = 0;
        rebuildButtons();
    }

    private String getSearchableName(ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        // Display name (includes enchantment names for enchanted books, potion names, etc.)
        sb.append(stack.getDisplayName());

        // Registry name (e.g. "minecraft:diamond_sword")
        if (stack.getItem().getRegistryName() != null) {
            sb.append(" ").append(stack.getItem().getRegistryName().toString());
        }

        // Unlocalized name (e.g. "item.diamond_sword" or "tile.dirt")
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

        if (searchMode) {
            mouseClickedSearchResults(mouseX, mouseY, mouseButton);
        } else {
            mouseClickedCategories(mouseX, mouseY, mouseButton);
        }
    }

    private void mouseClickedCategories(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int cellSize = ICON_SIZE + CAT_SPACING;
            int gridWidth = COLUMNS * cellSize - CAT_SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 50;
            int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && (startIndex + i) < categories.size(); i++) {
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                int x = guiLeft + col * cellSize;
                int y = guiTop + row * cellSize;

                if (isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) {
                    int catIndex = startIndex + i;
                    Minecraft.getMinecraft().displayGuiScreen(new GuiShopItems(categories.get(catIndex), catIndex));
                    return;
                }
            }
        }
    }

    private void mouseClickedSearchResults(int mouseX, int mouseY, int mouseButton) {
        int cellSize = SLOT_SIZE + SLOT_SPACING;
        int gridWidth = COLUMNS * cellSize - SLOT_SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 50;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && (startIndex + i) < searchResults.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                int resultIndex = startIndex + i;
                if (mouseButton == 0) {
                    detailView = true;
                    detailResultIndex = resultIndex;
                    stackMode = false;
                    rebuildButtons();
                } else if (mouseButton == 1) {
                    quickBuyStack(resultIndex);
                }
                return;
            }
        }
    }

    private void quickBuyStack(int resultIndex) {
        if (resultIndex < 0 || resultIndex >= searchResults.size()) return;

        SearchResult result = searchResults.get(resultIndex);
        PriceEngine priceEngine = ShopMod.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(result.stack);
        double balance = getClientBalance();

        int maxStackSize = result.stack.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);

        if (quantity <= 0) return;

        ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(result.categoryIndex, result.itemIndex, quantity));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (detailView) {
            if (button.id == 0) {
                // Buy
                int qty = getQuantity();
                if (qty > 0 && detailResultIndex >= 0 && detailResultIndex < searchResults.size()) {
                    SearchResult result = searchResults.get(detailResultIndex);
                    int actualItems = stackMode ? qty * result.stack.getMaxStackSize() : qty;
                    ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(result.categoryIndex, result.itemIndex, actualItems));
                }
            } else if (button.id == 1) {
                // Back from detail
                detailView = false;
                detailResultIndex = -1;
                rebuildButtons();
            } else if (button.id == 2) {
                // Toggle stack/item mode
                stackMode = !stackMode;
                if (quantityField != null) quantityField.setText("1");
                rebuildButtons();
            } else if (button.id == 3) {
                // Max Afford
                if (detailResultIndex >= 0 && detailResultIndex < searchResults.size()) {
                    SearchResult result = searchResults.get(detailResultIndex);
                    PriceEngine priceEngine = ShopMod.getPriceEngine();
                    double buyPrice = priceEngine.getBuyPrice(result.stack);
                    double balance = getClientBalance();
                    int maxAfford = (int) (balance / buyPrice);
                    if (stackMode) {
                        int maxStacks = maxAfford / result.stack.getMaxStackSize();
                        if (quantityField != null) quantityField.setText(String.valueOf(maxStacks));
                    } else {
                        if (quantityField != null) quantityField.setText(String.valueOf(maxAfford));
                    }
                }
            }
        } else if (searchMode) {
            if (button.id == 4) {
                // Back to Categories
                searchMode = false;
                searchResults.clear();
                if (searchField != null) searchField.setText("");
                scrollOffset = 0;
                rebuildButtons();
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
                performSearch(searchField.getText());
                return;
            }

            String oldText = searchField.getText();
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (!searchField.getText().equals(oldText)) {
                performSearch(searchField.getText());
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
            int rowsPerPage;
            if (searchMode) {
                int cellSize = SLOT_SIZE + SLOT_SPACING;
                int guiTop = 50;
                int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
                rowsPerPage = Math.max(1, availableHeight / cellSize);
                if (scroll > 0) {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                } else {
                    scrollOffset = Math.min(getMaxScrollPagesSearch(rowsPerPage) - 1, scrollOffset + 1);
                }
            } else {
                rowsPerPage = Math.max(1, (this.height - 90) / (ICON_SIZE + CAT_SPACING));
                if (scroll > 0) {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                } else {
                    scrollOffset = Math.min(getMaxScrollPagesCat(rowsPerPage) - 1, scrollOffset + 1);
                }
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

    private int getMaxScrollPagesCat(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) categories.size() / totalSlots));
    }

    private int getMaxScrollPagesSearch(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) searchResults.size() / totalSlots));
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
