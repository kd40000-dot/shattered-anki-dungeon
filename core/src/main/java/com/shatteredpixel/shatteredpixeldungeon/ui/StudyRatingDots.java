/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.ui.Component;

/**
 * Tiny pixel-dot version of AnkiDroid's previous-answer indicator.
 *
 * Dot count is the recorded ease (1..4), while all dots use that ease's
 * SPD-native rating color.
 */
public class StudyRatingDots extends Component {

    private static final float DOT = 2f;
    private static final float GAP = 1f;

    private static final int[] COLORS = {
            0,
            ItemSlot.DEGRADED,
            ItemSlot.WARNING,
            ItemSlot.UPGRADED,
            ItemSlot.ENHANCED
    };

    private final ColorBlock[] dots;
    private final int ease;

    public StudyRatingDots(int ease) {
        this.ease = ease >= 1 && ease <= 4 ? ease : 0;
        this.dots = new ColorBlock[this.ease];

        for (int i = 0; i < this.ease; i++) {
            dots[i] = new ColorBlock(DOT, DOT, COLORS[this.ease]);
            add(dots[i]);
        }

        setSize(preferredWidth(), preferredHeight());
    }

    public float preferredWidth() {
        return ease == 0 ? 0f : ease * DOT + (ease - 1) * GAP;
    }

    public float preferredHeight() {
        return ease == 0 ? 0f : DOT;
    }

    @Override
    protected void layout() {
        for (int i = 0; i < dots.length; i++) {
            dots[i].x = x + i * (DOT + GAP);
            dots[i].y = y;
            dots[i].size(DOT, DOT);
        }
    }
}
