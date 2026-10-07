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
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.StudyAnswerComparison;
import com.shatteredpixel.shatteredpixeldungeon.ui.StudyCardPanel;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

/**
 * Shared Anki rating window.
 *
 * Rating colors and answer-diff colors deliberately use SPD's existing item
 * palette rather than importing Anki/Andor colors.
 */
public abstract class WndStudyRating extends Window {

    private boolean resolved;

    private static final int WIDTH_P = 135;
    private static final int WIDTH_L = 180;
    private static final int MARGIN = 2;
    private static final int BUTTON_HEIGHT = 23;

    private static final int[] COLORS = {
            ItemSlot.DEGRADED,
            ItemSlot.WARNING,
            ItemSlot.UPGRADED,
            ItemSlot.ENHANCED
    };

    /** Generic compatibility constructor. */
    public WndStudyRating(String title, int titleColor, String message, String[] options) {
        super();

        int width = windowWidth();
        float pos = MARGIN;

        pos = addCenteredTitle(title, titleColor, width, pos);

        RenderedTextBlock txtMessage = PixelScene.renderTextBlock(6);
        txtMessage.text(message == null ? "" : message, width);
        txtMessage.setPos(0, pos);
        add(txtMessage);
        pos = txtMessage.bottom() + 2 * MARGIN;

        pos = addRatingButtons(options, width, pos);
        resize(width, (int)(pos - MARGIN));
    }

    /**
     * Rich study-answer presentation used by combat and Conjure Food.
     *
     * The question remains visually prominent after reveal, and the comparison
     * mirrors the Andor/Anki-style You/Answer character highlighting.
     */
    public WndStudyRating(
            String title,
            int titleColor,
            String question,
            String typed,
            String fullAnswer,
            boolean neutralBlank,
            String[] options
    ) {
        super();

        int width = windowWidth();
        float pos = MARGIN;

        pos = addCenteredTitle(title, titleColor, width, pos);

        StudyCardPanel questionPanel = new StudyCardPanel(question, 10);
        float questionHeight = questionPanel.preferredHeight(width);
        add(questionPanel);
        questionPanel.setRect(0, pos, width, questionHeight);
        pos = questionPanel.bottom() + 2 * MARGIN;

        StudyAnswerComparison comparison =
                new StudyAnswerComparison(typed, fullAnswer, neutralBlank);
        float comparisonHeight = comparison.preferredHeight(width);
        add(comparison);
        comparison.setRect(0, pos, width, comparisonHeight);
        pos = comparison.bottom() + 3 * MARGIN;

        pos = addRatingButtons(options, width, pos);
        resize(width, (int)(pos - MARGIN));
    }

    private int windowWidth() {
        return PixelScene.landscape() ? WIDTH_L : WIDTH_P;
    }

    private float addCenteredTitle(String title, int titleColor, int width, float pos) {
        if (title == null || title.isEmpty()) return pos;

        RenderedTextBlock txtTitle = PixelScene.renderTextBlock(title, 9);
        txtTitle.hardlight(titleColor);
        txtTitle.setHightlighting(false);
        txtTitle.maxWidth(width - MARGIN * 2);
        txtTitle.align(RenderedTextBlock.CENTER_ALIGN);
        txtTitle.setPos((width - txtTitle.width()) / 2f, pos);
        add(txtTitle);

        return txtTitle.bottom() + 2 * MARGIN;
    }

    private float addRatingButtons(String[] options, int width, float pos) {
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

        return pos;
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
