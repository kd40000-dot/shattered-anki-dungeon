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

    // SPD's bitmap font gives literal spaces almost no visible advance when
    // rendered as isolated per-character blocks. Use a deliberate word gap.
    private static final float WORD_SPACE = 4f;

    private static final int MATCH_COLOR = ItemSlot.UPGRADED;
    private static final int WRONG_COLOR = ItemSlot.DEGRADED;
    private static final int MISSING_COLOR = ItemSlot.WARNING;
    private static final int NEUTRAL_COLOR = ItemSlot.FADED;

    private static final class Token {
        final RenderedTextBlock block;
        final float spacerWidth;

        private Token(RenderedTextBlock block, float spacerWidth) {
            this.block = block;
            this.spacerWidth = spacerWidth;
        }

        static Token text(RenderedTextBlock block) {
            return new Token(block, 0f);
        }

        static Token spacer(float width) {
            return new Token(null, width);
        }

        boolean isSpacer() {
            return block == null;
        }
    }

    private final NinePatch frame;
    private final List<Token> typedRow = new ArrayList<>();
    private final List<Token> answerRow = new ArrayList<>();

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
                addCharacter(
                        typedRow,
                        piece.typed,
                        piece.match ? MATCH_COLOR : WRONG_COLOR
                );
            }
        }

        addLabel(answerRow, answerLabel == null ? "Answer:" : answerLabel);
        for (StudyAnswerDiff.Piece piece : pieces) {
            if (piece.expected == null) continue;
            addCharacter(
                    answerRow,
                    piece.expected,
                    piece.match ? MATCH_COLOR : MISSING_COLOR
            );
        }

        layout();
    }

    private void addLabel(List<Token> row, String text) {
        addText(row, text, NEUTRAL_COLOR, LABEL_SIZE);
        row.add(Token.spacer(WORD_SPACE));
    }

    private void addCharacter(List<Token> row, char value, int color) {
        if (Character.isWhitespace(value)) {
            row.add(Token.spacer(WORD_SPACE));
        } else {
            addText(row, String.valueOf(value), color, CHAR_SIZE);
        }
    }

    private void addText(List<Token> row, String text, int color, int size) {
        RenderedTextBlock block = PixelScene.renderTextBlock(text, size);
        block.hardlight(color);
        row.add(Token.text(block));
        add(block);
    }

    @Override
    protected void layout() {
        float contentWidth = width - PADDING * 2f;

        float contentY = y + PADDING;
        contentY = layoutRow(typedRow, contentY, contentWidth);
        contentY += ROW_GAP;
        contentY = layoutRow(answerRow, contentY, contentWidth);
        contentY += PADDING;

        height = Math.max(MIN_HEIGHT, contentY - y);

        frame.x = x;
        frame.y = y;
        frame.size(width, height);
    }

    private float layoutRow(List<Token> row, float startY, float contentWidth) {
        final float left = x + PADDING;
        final float right = left + contentWidth;

        float cursorX = left;
        float cursorY = startY;
        float lineHeight = 0;

        for (Token token : row) {
            if (token.isSpacer()) {
                // Never begin a wrapped line with a word-space.
                if (cursorX > left) {
                    if (cursorX + token.spacerWidth > right) {
                        cursorY += Math.max(lineHeight, CHAR_SIZE) + 1;
                        cursorX = left;
                        lineHeight = 0;
                    } else {
                        cursorX += token.spacerWidth;
                    }
                }
                continue;
            }

            RenderedTextBlock block = token.block;
            float blockWidth = block.width();
            float blockHeight = block.height();

            if (cursorX > left && cursorX + blockWidth > right) {
                cursorY += Math.max(lineHeight, CHAR_SIZE) + 1;
                cursorX = left;
                lineHeight = 0;
            }

            block.setPos(
                    cursorX,
                    cursorY + Math.max(0, (CHAR_SIZE - blockHeight) / 2f)
            );
            cursorX += blockWidth;
            lineHeight = Math.max(lineHeight, blockHeight);
        }

        return cursorY + Math.max(lineHeight, CHAR_SIZE);
    }

    public String closestAcceptedAnswer(String typed, String fullAnswer) {
        return StudyAnswerDiff.closestAlternative(typed, fullAnswer);
    }
}
