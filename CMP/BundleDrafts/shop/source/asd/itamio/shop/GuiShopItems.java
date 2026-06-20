package asd.itamio.shop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiShopItems extends GuiScreen {

    private final ShopCategory category;
    private final int categoryIndex;
    private final List<ItemStack> items;
    private int scrollOffset = 0;

    private static final int SLOT_SIZE = 22;
    private static final int SPACING = 4;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private GuiTextField quantityField;
    private boolean stackMode = false;

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.items = category.getItems();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
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

        int cellSize = SLOT_SIZE + SPACING;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;

        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        // Draw items - NO hover highlighting on slots, same color always
        for (int i = 0; i < visibleCount && (startIndex + i) < items.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            int itemIndex = startIndex + i;
            ItemStack item = items.get(itemIndex);

            drawSlotBackground(x, y, SLOT_SIZE, SLOT_SIZE);
            renderItem(item, x + (SLOT_SIZE - 16) / 2, y + (SLOT_SIZE - 16) / 2);
        }

        // Reset GL state before tooltip (tooltip leaves dirty state)
        resetGlState();

        // Draw tooltip
        for (int i = 0; i < visibleCount && (startIndex + i) < items.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            if (isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                int itemIndex = startIndex + i;
                ItemStack item = items.get(itemIndex);
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

        if (items.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }

        String footer = "\u00a77Left-click: Buy menu | Right-click: Quick buy stack | ESC: Back";
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(int mouseX, int mouseY) {
        if (detailItemIndex < 0 || detailItemIndex >= items.size()) return;

        ItemStack item = items.get(detailItemIndex);
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

        int cellSize = SLOT_SIZE + SPACING;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && (startIndex + i) < items.size(); i++) {
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
        if (itemIndex < 0 || itemIndex >= items.size()) return;

        ItemStack item = items.get(itemIndex);
        PriceEngine priceEngine = ShopMod.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(item);
        double balance = getClientBalance();

        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);

        if (quantity <= 0) return;

        ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(categoryIndex, itemIndex, quantity));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (detailView) {
            if (button.id == 0) {
                int qty = getQuantity();
                if (qty > 0 && detailItemIndex >= 0) {
                    ItemStack item = items.get(detailItemIndex);
                    int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                    ShopMod.NETWORK.sendToServer(ShopPacket.buyItem(categoryIndex, detailItemIndex, actualItems));
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
                if (detailItemIndex >= 0 && detailItemIndex < items.size()) {
                    ItemStack item = items.get(detailItemIndex);
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
            if (Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
                quantityField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (detailView) return;

        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0) {
            int cellSize = SLOT_SIZE + SPACING;
            int guiTop = 25;
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
        return Math.max(1, (int) Math.ceil((double) items.size() / totalSlots));
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
