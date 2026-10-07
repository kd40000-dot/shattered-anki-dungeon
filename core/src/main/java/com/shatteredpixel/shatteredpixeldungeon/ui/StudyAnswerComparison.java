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
import com.watabou.noosa.RenderedText;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Anki-style character comparison rendered with SPD-native colors.
 *
 * Matching typed/expected characters use SPD's upgraded green, incorrect typed
 * characters use degraded red, and expected-but-missed characters use warning
 * amber. Labels and neutral blanks use SPD's faded neutral.
 */
public class StudyAnswerComparison extends Component {

    private static final int PADDING = 5;
    private static final int LABEL_SIZE = 6;
    private static final int VALUE_SIZE = 9;
    private static final int ROW_GAP = 4;
    private static final float CHAR_GAP = 0.35f;
    private static final float LINE_GAP = 2f;

    private static final int MATCH_COLOR = ItemSlot.UPGRADED;
    private static final int WRONG_COLOR = ItemSlot.DEGRADED;
    private static final int MISSED_COLOR = ItemSlot.WARNING;
    private static final int NEUTRAL_COLOR = ItemSlot.FADED;

    private final NinePatch bg;
    private final Row typedRow;
    private final Row answerRow;
    private final float labelColumnWidth;

    private static final class Segment {
        final RenderedText text;
        final boolean whitespace;

        Segment(RenderedText text, boolean whitespace) {
            this.text = text;
            this.whitespace = whitespace;
        }
    }

    private static final class Row {
        final RenderedText label;
        final ArrayList<Segment> value = new ArrayList<>();

        Row(RenderedText label) {
            this.label = label;
        }
    }

    public StudyAnswerComparison(String typed, String fullAnswer, boolean neutralBlank) {
        super();

        bg = Chrome.get(Chrome.Type.TOAST);
        add(bg);

        typedRow = new Row(makeText("You:", LABEL_SIZE, NEUTRAL_COLOR));
        answerRow = new Row(makeText("Answer:", LABEL_SIZE, NEUTRAL_COLOR));

        List<StudyAnswerDiff.Piece> pieces =
                StudyAnswerDiff.alignToClosestAlternative(typed, fullAnswer);

        String trimmedTyped = typed == null ? "" : typed.trim();
        if (trimmedTyped.isEmpty()) {
            addValueText(
                    typedRow,
                    "(blank)",
                    neutralBlank ? NEUTRAL_COLOR : WRONG_COLOR
            );
        } else {
            for (StudyAnswerDiff.Piece piece : pieces) {
                if (piece.typed == null) continue;
                addValueChar(
                        typedRow,
                        piece.typed,
                        piece.match ? MATCH_COLOR : WRONG_COLOR
                );
            }
        }

        for (StudyAnswerDiff.Piece piece : pieces) {
            if (piece.expected == null) continue;
            addValueChar(
                    answerRow,
                    piece.expected,
                    piece.match ? MATCH_COLOR : MISSED_COLOR
            );
        }

        add(typedRow.label);
        add(answerRow.label);

        for (Segment segment : typedRow.value) add(segment.text);
        for (Segment segment : answerRow.value) add(segment.text);

        labelColumnWidth = Math.max(
                typedRow.label.width(),
                answerRow.label.width()
        ) + 4f;
    }

    public float preferredHeight(float panelWidth) {
        float innerWidth = Math.max(1f, panelWidth - PADDING * 2f);
        return PADDING * 2f
                + rowHeight(typedRow, innerWidth)
                + ROW_GAP
                + rowHeight(answerRow, innerWidth);
    }

    @Override
    protected void layout() {
        super.layout();

        bg.x = x;
        bg.y = y;
        bg.size(width, height);

        float innerLeft = x + PADDING;
        float innerWidth = Math.max(1f, width - PADDING * 2f);
        float rowY = y + PADDING;

        rowY = layoutRow(typedRow, innerLeft, rowY, innerWidth);
        rowY += ROW_GAP;
        layoutRow(answerRow, innerLeft, rowY, innerWidth);
    }

    private float layoutRow(Row row, float left, float top, float availableWidth) {
        float lineHeight = valueLineHeight(row);
        float valueLeft = left + labelColumnWidth;
        float valueRight = left + availableWidth;

        row.label.x = left;
        row.label.y = top + Math.max(0f, (lineHeight - row.label.height()) / 2f);
        PixelScene.align(row.label);

        float cursorX = valueLeft;
        float cursorY = top;

        for (int i = 0; i < row.value.size(); i++) {
            Segment segment = row.value.get(i);

            if (!segment.whitespace && isWordStart(row.value, i)) {
                float wordWidth = measureWord(row.value, i);
                if (cursorX > valueLeft && cursorX + wordWidth > valueRight) {
                    cursorX = valueLeft;
                    cursorY += lineHeight + LINE_GAP;
                }
            }

            float segmentWidth = segment.text.width();
            if (!segment.whitespace
                    && cursorX > valueLeft
                    && cursorX + segmentWidth > valueRight) {
                cursorX = valueLeft;
                cursorY += lineHeight + LINE_GAP;
            }

            if (segment.whitespace && cursorX == valueLeft) {
                segment.text.visible = false;
                continue;
            }

            segment.text.visible = true;
            segment.text.x = cursorX;
            segment.text.y = cursorY + Math.max(0f, (lineHeight - segment.text.height()) / 2f);
            PixelScene.align(segment.text);

            cursorX += segmentWidth + CHAR_GAP;
        }

        return cursorY + lineHeight;
    }

    private float rowHeight(Row row, float availableWidth) {
        float lineHeight = valueLineHeight(row);
        float valueWidth = Math.max(1f, availableWidth - labelColumnWidth);
        float cursor = 0f;
        int lines = 1;

        for (int i = 0; i < row.value.size(); i++) {
            Segment segment = row.value.get(i);

            if (!segment.whitespace && isWordStart(row.value, i)) {
                float wordWidth = measureWord(row.value, i);
                if (cursor > 0f && cursor + wordWidth > valueWidth) {
                    cursor = 0f;
                    lines++;
                }
            }

            float segmentWidth = segment.text.width();
            if (!segment.whitespace && cursor > 0f && cursor + segmentWidth > valueWidth) {
                cursor = 0f;
                lines++;
            }

            if (segment.whitespace && cursor == 0f) continue;
            cursor += segmentWidth + CHAR_GAP;
        }

        return lines * lineHeight + (lines - 1) * LINE_GAP;
    }

    private float valueLineHeight(Row row) {
        float height = VALUE_SIZE * 0.75f;
        for (Segment segment : row.value) {
            height = Math.max(height, segment.text.height());
        }
        return height;
    }

    private static boolean isWordStart(List<Segment> segments, int index) {
        return index == 0 || segments.get(index - 1).whitespace;
    }

    private static float measureWord(List<Segment> segments, int start) {
        float width = 0f;
        for (int i = start; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.whitespace) break;
            width += segment.text.width() + CHAR_GAP;
        }
        return width;
    }

    private void addValueText(Row row, String value, int color) {
        for (int i = 0; i < value.length(); i++) {
            addValueChar(row, value.charAt(i), color);
        }
    }

    private void addValueChar(Row row, char value, int color) {
        String rendered = String.valueOf(value);
        row.value.add(new Segment(
                makeText(rendered, VALUE_SIZE, color),
                Character.isWhitespace(value)
        ));
    }

    private static RenderedText makeText(String value, int size, int color) {
        RenderedText text = new RenderedText(value, size);
        text.hardlight(color);
        return text;
    }
}
