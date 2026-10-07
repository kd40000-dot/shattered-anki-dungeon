/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

/**
 * Shared Anki rating window. It deliberately uses only SPD's existing palette:
 * degraded red, warning orange, upgraded green, and enhanced blue.
 */
public abstract class WndStudyRating extends Window {

    private boolean resolved;

    private static final int WIDTH_P = 132;
    private static final int WIDTH_L = 160;
    private static final int MARGIN = 2;
    private static final int BUTTON_HEIGHT = 23;

    private static final int[] COLORS = {
            ItemSlot.DEGRADED,
            ItemSlot.WARNING,
            ItemSlot.UPGRADED,
            ItemSlot.ENHANCED
    };

    public WndStudyRating(String title, int titleColor, String message, String[] options) {
        super();

        int width = PixelScene.landscape() ? WIDTH_L : WIDTH_P;
        float pos = MARGIN;

        if (title != null) {
            RenderedTextBlock txtTitle = PixelScene.renderTextBlock(title, 9);
            txtTitle.hardlight(titleColor);
            txtTitle.maxWidth(width - MARGIN * 2);
            txtTitle.setPos(MARGIN, pos);
            add(txtTitle);
            pos = txtTitle.bottom() + 2 * MARGIN;
        }

        RenderedTextBlock txtMessage = PixelScene.renderTextBlock(6);
        txtMessage.text(message == null ? "" : message, width);
        txtMessage.setPos(0, pos);
        add(txtMessage);
        pos = txtMessage.bottom() + 2 * MARGIN;

        int count = Math.min(options == null ? 0 : options.length, 4);
        for (int i = 0; i < count; i++) {
            final int index = i;
            StyledButton button = new StyledButton(Chrome.Type.GREY_BUTTON, options[i], 8) {
                @Override
                protected void onClick() {
                    if (resolved) return;
                    resolved = true;
                    hide();
                    onSelect(index);
                }
            };
            button.multiline = true;
            button.textColor(COLORS[i]);
            add(button);
            button.setRect(0, pos, width, BUTTON_HEIGHT);
            pos += BUTTON_HEIGHT + MARGIN;
        }

        resize(width, (int)(pos - MARGIN));
    }

    protected abstract void onSelect(int index);

    protected void onCancelled() {
    }

    @Override
    public void onBackPressed() {
        if (resolved) return;
        resolved = true;
        onCancelled();
        hide();
    }

    @Override
    public void destroy() {
        if (!resolved) {
            resolved = true;
            onCancelled();
        }
        super.destroy();
    }
}
