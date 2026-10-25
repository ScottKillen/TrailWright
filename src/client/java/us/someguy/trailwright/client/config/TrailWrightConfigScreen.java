/*
 * Purpose:      Settings screen for config/trailwright/trailwright.json (DESIGN.md §7). Edits a
 *               copy of the live config; Done validates, applies, and saves it, Cancel or
 *               Escape discards. Applying takes effect at once in a world this client hosts
 *               (singleplayer or LAN), since the integrated server reads the same instance.
 * Dependencies: Vanilla GUI widgets only, so it opens with or without Mod Menu (ModMenuCompat
 *               is its only caller today). TrailWrightConfig. Client source set only.
 */
package us.someguy.trailwright.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import us.someguy.trailwright.config.TrailWrightConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TrailWrightConfigScreen extends Screen {
    private static final String KEY = "trailwright.config.";
    // Layout matches vanilla options screens: 310-wide rows, 150-wide bottom buttons.
    private static final int ROW_WIDTH = 310;
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 100;
    private static final int TEXT_COLOR = 0xE0E0E0;     // EditBox default
    private static final int INVALID_COLOR = 0xFF5555;  // vanilla red (ChatFormatting.RED)

    private final Screen parent;
    private final TrailWrightConfig working = TrailWrightConfig.get().copy();
    // Keys whose text field does not currently parse to an allowed value; Done is off while
    // any are listed. `working` keeps the last valid value for each.
    private final Set<String> invalid = new HashSet<>();
    private boolean remote;
    private Button doneButton;
    private SettingsList list;

    public TrailWrightConfigScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // init() runs again on resize; fields are rebuilt from `working`, so text that never
        // parsed is dropped along with its invalid mark.
        invalid.clear();
        // Connected to a server this client does not host: that server reads its own file.
        remote = minecraft.level != null && !minecraft.hasSingleplayerServer();

        list = new SettingsList(minecraft, width, height, remote ? 44 : 32, height - 32);
        TrailWrightConfig c = working;
        list.toggle("enabled", c.enabled, v -> c.enabled = v);
        list.multiplier("wearMultiplier", c.wearMultiplier, v -> c.wearMultiplier = v);
        list.multiplier("sprintMultiplier", c.sprintMultiplier, v -> c.sprintMultiplier = v);
        list.toggle("countMounts", c.countMounts, v -> c.countMounts = v);
        list.toggle("countMobs", c.countMobs, v -> c.countMobs = v);
        list.multiplier("mobMultiplier", c.mobMultiplier, v -> c.mobMultiplier = v);
        list.toggle("respectMobGriefing", c.respectMobGriefing, v -> c.respectMobGriefing = v);
        list.multiplier("decayPerDay", c.decayPerDay, v -> c.decayPerDay = v);
        list.ticks("pruneIntervalTicks", c.pruneIntervalTicks, v -> c.pruneIntervalTicks = v);
        addWidget(list);

        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(width / 2 - 155, height - 27, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 5, height - 27, 150, 20).build());
        updateDone();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (remote) {
            g.drawCenteredString(font, Component.translatable(KEY + "remote_note"), width / 2, 26, 0xFFFF55);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void saveAndClose() {
        if (!invalid.isEmpty()) return;
        TrailWrightConfig.save(working);
        onClose();
    }

    private void updateDone() {
        doneButton.active = invalid.isEmpty();
    }

    private void markValid(String key, boolean valid) {
        if (valid) invalid.remove(key); else invalid.add(key);
        updateDone();
    }

    private static Component label(String key) { return Component.translatable(KEY + key); }
    private static Tooltip tooltip(String key) { return Tooltip.create(Component.translatable(KEY + key + ".tooltip")); }

    private final class SettingsList extends ContainerObjectSelectionList<Row> {
        SettingsList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, ROW_HEIGHT);
        }

        void toggle(String key, boolean value, Consumer<Boolean> apply) {
            CycleButton<Boolean> button = CycleButton.onOffBuilder(value).displayOnlyValue()
                    .create(0, 0, WIDGET_WIDTH, 20, label(key), (b, v) -> apply.accept(v));
            button.setTooltip(tooltip(key));
            addEntry(new Row(label(key), button));
        }

        void multiplier(String key, float value, Consumer<Float> apply) {
            number(key, Float.toString(value), s -> {
                float v = Float.parseFloat(s);
                // The file loader accepts infinity; the screen asks for a usable number.
                if (!Float.isFinite(v) || !TrailWrightConfig.isValidMultiplier(v)) return false;
                apply.accept(v);
                return true;
            });
        }

        void ticks(String key, int value, Consumer<Integer> apply) {
            number(key, Integer.toString(value), s -> {
                int v = Integer.parseInt(s);
                if (!TrailWrightConfig.isValidPruneInterval(v)) return false;
                apply.accept(v);
                return true;
            });
        }

        /** {@code tryApply} returns whether the text was accepted; a parse exception means no. */
        private void number(String key, String text, Predicate<String> tryApply) {
            EditBox box = new EditBox(font, 0, 0, WIDGET_WIDTH, 20, label(key));
            box.setMaxLength(16);
            box.setValue(text);
            box.setTooltip(tooltip(key));
            box.setResponder(s -> {
                boolean ok;
                try {
                    ok = tryApply.test(s.trim());
                } catch (NumberFormatException e) {
                    ok = false;
                }
                box.setTextColor(ok ? TEXT_COLOR : INVALID_COLOR);
                markValid(key, ok);
            });
            addEntry(new Row(label(key), box));
        }

        @Override
        public int getRowWidth() { return ROW_WIDTH; }

        @Override
        protected int getScrollbarPosition() { return width / 2 + ROW_WIDTH / 2 + 10; }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Component label;
        private final AbstractWidget widget;

        Row(Component label, AbstractWidget widget) {
            this.label = label;
            this.widget = widget;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            g.drawString(font, label, left, top + (20 - font.lineHeight) / 2 + 1, 0xFFFFFF);
            widget.setX(left + rowWidth - WIDGET_WIDTH);
            widget.setY(top);
            widget.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() { return List.of(widget); }

        @Override
        public List<? extends NarratableEntry> narratables() { return List.of(widget); }
    }
}
