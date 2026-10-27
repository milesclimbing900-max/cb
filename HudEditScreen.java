package com.example.donutauction.ui;

import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Drag the glass panel with the left mouse button; buttons change opacity. Saved automatically. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging, wasDown;
    private int offX, offY, pw = 190, ph = 60;

    public HudEditScreen(Screen parent) {
        super(Text.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, by = height - 28;
        addBtn(cx - 205, by, 95, "Opacity -", () -> opacity(-0.05f));
        addBtn(cx - 105, by, 95, "Opacity +", () -> opacity(0.05f));
        addBtn(cx - 5, by, 100, "Reset position", () -> {
            ModConfig c = ConfigManager.get();
            c.hudX = 6;
            c.hudY = 6;
            ConfigManager.save();
        });
        addBtn(cx + 100, by, 105, "Done", this::close);
    }

    private void addBtn(int x, int y, int w, String label, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 20).build());
    }

    private void opacity(float d) {
        ModConfig c = ConfigManager.get();
        c.hudOpacity = Math.max(0.15f, Math.min(1f, c.hudOpacity + d));
        ConfigManager.save();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ModConfig c = ConfigManager.get();

        // Poll the left mouse button so we don't depend on screen mouse-callback signatures.
        boolean down = client != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && mouseX >= c.hudX && mouseX <= c.hudX + pw && mouseY >= c.hudY && mouseY <= c.hudY + ph) {
            dragging = true;
            offX = mouseX - c.hudX;
            offY = mouseY - c.hudY;
        }
        if (!down && dragging) {
            dragging = false;
            ConfigManager.save();
        }
        if (dragging) {
            c.hudX = Math.max(0, Math.min(mouseX - offX, width - pw));
            c.hudY = Math.max(0, Math.min(mouseY - offY, height - ph));
        }
        wasDown = down;

        GlassPanel.Size s = GlassPanel.draw(ctx, textRenderer, c.hudX, c.hudY, InfoLines.build(),
                "Auction Assistant", c.hudOpacity, 190);
        pw = s.w();
        ph = s.h();

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Drag the panel with the left mouse button. Opacity: "
                + Math.round(c.hudOpacity * 100) + "%"), width / 2, height - 44, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        ConfigManager.save();
        if (client != null) client.setScreen(parent);
    }
}
