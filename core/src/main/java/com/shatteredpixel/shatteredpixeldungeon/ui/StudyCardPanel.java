/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

/**
 * Native SPD framed surface for prominent study-card text.
 */
public class StudyCardPanel extends Component {

    private static final int PADDING = 5;
    private static final int MIN_HEIGHT = 28;

    private final NinePatch bg;
    private final RenderedTextBlock text;

    public StudyCardPanel(String value, int size) {
        super();

        bg = Chrome.get(Chrome.Type.TOAST);
        add(bg);

        text = PixelScene.renderTextBlock(size);
        text.setHightlighting(false);
        text.align(RenderedTextBlock.CENTER_ALIGN);
        text.text(value == null ? "" : value);
        add(text);
    }

    public float preferredHeight(float panelWidth) {
        int textWidth = Math.max(1, (int)panelWidth - PADDING * 2);
        text.maxWidth(textWidth);
        return Math.max(MIN_HEIGHT, text.height() + PADDING * 2);
    }

    @Override
    protected void layout() {
        super.layout();

        bg.x = x;
        bg.y = y;
        bg.size(width, height);

        int textWidth = Math.max(1, (int)width - PADDING * 2);
        text.maxWidth(textWidth);
        text.setPos(
                x + (width - text.width()) / 2f,
                y + (height - text.height()) / 2f
        );
        PixelScene.align(text);
    }
}
