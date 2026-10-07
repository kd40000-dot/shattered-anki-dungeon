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
import com.shatteredpixel.shatteredpixeldungeon.ui.StudyRatingDots;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyPreviousRating;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.NinePatch;

/**
 * Shared Anki rating window using SPD-native chrome and palette.
 *
 * The question remains visible after reveal, followed by an Anki-style
 * per-character typed/answer comparison.
 */
public abstract class WndStudyRating extends Window {

    private boolean resolved;

    private static final int WIDTH_P = 135;
    private static final int WIDTH_L = 190;
    private static final int MARGIN = 2;
    private static final int BUTTON_HEIGHT = 23;

    private static final int[] COLORS = {
            ItemSlot.DEGRADED,
            ItemSlot.WARNING,
            ItemSlot.UPGRADED,
            ItemSlot.ENHANCED
    };

    public WndStudyRating(
            String title,
            int titleColor,
            String question,
            String typed,
            String fullAnswer,
            boolean neutralBlank,
            String youLabel,
            String answerLabel,
            String[] options
    ) {
        super();

        int width = PixelScene.landscape() ? WIDTH_L : WIDTH_P;
        float pos = MARGIN;

        if (title != null) {
            RenderedTextBlock txtTitle = PixelScene.renderTextBlock(title, 8);
            txtTitle.hardlight(titleColor);
            txtTitle.maxWidth(width - MARGIN * 2);
            txtTitle.setPos((width - txtTitle.width()) / 2f, pos);
            add(txtTitle);

            StudyRatingDots ratingDots = new StudyRatingDots(StudyPreviousRating.lastEase());
            if (ratingDots.preferredWidth() > 0) {
                ratingDots.setPos(
                        width - MARGIN - ratingDots.preferredWidth(),
                        pos + Math.max(0f, (txtTitle.height() - ratingDots.preferredHeight()) / 2f)
                );
                add(ratingDots);
            }

            pos = txtTitle.bottom() + 2 * MARGIN;
        }

        if (question != null && !question.trim().isEmpty()) {
            float panelX = MARGIN;
            float panelWidth = width - 2f * MARGIN;

            RenderedTextBlock txtQuestion = PixelScene.renderTextBlock(question, 10);
            txtQuestion.maxWidth((int)panelWidth - 10);
            txtQuestion.align(RenderedTextBlock.CENTER_ALIGN);

            float panelHeight = Math.max(32, txtQuestion.height() + 10);
            NinePatch questionFrame = Chrome.get(Chrome.Type.TOAST);
            questionFrame.x = panelX;
            questionFrame.y = pos;
            questionFrame.size(panelWidth, panelHeight);
            add(questionFrame);

            txtQuestion.setPos(
                    (width - txtQuestion.width()) / 2f,
                    pos + (panelHeight - txtQuestion.height()) / 2f
            );
            add(txtQuestion);

            pos += panelHeight + 3 * MARGIN;
        }

        StudyAnswerComparison comparison = new StudyAnswerComparison(
                width - 2f * MARGIN,
                typed,
                fullAnswer,
                neutralBlank,
                youLabel,
                answerLabel
        );
        comparison.setPos(MARGIN, pos);
        add(comparison);
        pos = comparison.bottom() + 3 * MARGIN;

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
