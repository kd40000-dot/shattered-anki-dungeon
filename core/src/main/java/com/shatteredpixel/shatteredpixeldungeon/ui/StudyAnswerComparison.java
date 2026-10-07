/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyAnswerDiff;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Anki-style typed answer comparison rendered entirely with SPD UI elements.
 *
 * Matching characters are green, incorrect typed characters red, and
 * expected/missing characters orange. The frame uses SPD's normal toast chrome.
 */
public class StudyAnswerComparison extends Component {

    private static final int PADDING = 5;
    private static final int ROW_GAP = 3;
    private static final int CHAR_SIZE = 9;
    private static final int LABEL_SIZE = 7;
    private static final int MIN_HEIGHT = 42;

    private static final int MATCH_COLOR = ItemSlot.UPGRADED;
    private static final int WRONG_COLOR = ItemSlot.DEGRADED;
    private static final int MISSING_COLOR = ItemSlot.WARNING;
    private static final int NEUTRAL_COLOR = ItemSlot.FADED;

    private final NinePatch frame;
    private final List<RenderedTextBlock> typedRow = new ArrayList<>();
    private final List<RenderedTextBlock> answerRow = new ArrayList<>();

    public StudyAnswerComparison(
            float width,
            String typed,
            String fullAnswer,
            boolean neutralBlank,
            String youLabel,
            String answerLabel
    ) {
        super();

        this.width = width;

        frame = Chrome.get(Chrome.Type.TOAST);
        add(frame);

        String expected = StudyAnswerDiff.closestAlternative(typed, fullAnswer);
        List<StudyAnswerDiff.Piece> pieces = StudyAnswerDiff.align(typed, expected);

        addLabel(typedRow, youLabel == null ? "You:" : youLabel);

        String trimmedTyped = typed == null ? "" : typed.trim();
        if (trimmedTyped.isEmpty()) {
            addText(
                    typedRow,
                    "(blank)",
                    neutralBlank ? NEUTRAL_COLOR : WRONG_COLOR,
                    CHAR_SIZE
            );
        } else {
            for (StudyAnswerDiff.Piece piece : pieces) {
                if (piece.typed == null) continue;
                addText(
                        typedRow,
                        String.valueOf(piece.typed),
                        piece.match ? MATCH_COLOR : WRONG_COLOR,
                        CHAR_SIZE
                );
            }
        }

        addLabel(answerRow, answerLabel == null ? "Answer:" : answerLabel);
        for (StudyAnswerDiff.Piece piece : pieces) {
            if (piece.expected == null) continue;
            addText(
                    answerRow,
                    String.valueOf(piece.expected),
                    piece.match ? MATCH_COLOR : MISSING_COLOR,
                    CHAR_SIZE
            );
        }

        relayout();
    }

    private void addLabel(List<RenderedTextBlock> row, String text) {
        addText(row, text + "  ", NEUTRAL_COLOR, LABEL_SIZE);
    }

    private void addText(List<RenderedTextBlock> row, String text, int color, int size) {
        RenderedTextBlock block = PixelScene.renderTextBlock(text, size);
        block.hardlight(color);
        row.add(block);
        add(block);
    }

    private void relayout() {
        float contentWidth = width - PADDING * 2f;

        float y = PADDING;
        y = layoutRow(typedRow, y, contentWidth);
        y += ROW_GAP;
        y = layoutRow(answerRow, y, contentWidth);
        y += PADDING;

        height = Math.max(MIN_HEIGHT, y);
        frame.size(width, height);
    }

    private float layoutRow(List<RenderedTextBlock> row, float startY, float contentWidth) {
        float x = PADDING;
        float y = startY;
        float lineHeight = 0;

        for (RenderedTextBlock block : row) {
            float blockWidth = block.width();
            float blockHeight = block.height();

            if (x > PADDING && x + blockWidth > PADDING + contentWidth) {
                y += Math.max(lineHeight, CHAR_SIZE) + 1;
                x = PADDING;
                lineHeight = 0;
            }

            block.setPos(x, y + Math.max(0, (CHAR_SIZE - blockHeight) / 2f));
            x += blockWidth;
            lineHeight = Math.max(lineHeight, blockHeight);
        }

        return y + Math.max(lineHeight, CHAR_SIZE);
    }

    public String closestAcceptedAnswer(String typed, String fullAnswer) {
        return StudyAnswerDiff.closestAlternative(typed, fullAnswer);
    }
}
