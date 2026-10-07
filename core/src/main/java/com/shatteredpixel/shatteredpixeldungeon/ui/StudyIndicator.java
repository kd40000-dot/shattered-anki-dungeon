/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.study.ConjureFoodStudy;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyRunState;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Image;

/** Persistent gameplay tag for the optional Conjure Food study ability. */
public class StudyIndicator extends Tag {

    private Image icon;
    private BitmapText progress;

    private int lastCompleted = -1;
    private int lastRequired = -1;

    public StudyIndicator() {
        super(0x6A7591);
        setSize(SIZE, SIZE);
        visible = false;
    }

    @Override
    protected void createChildren() {
        super.createChildren();

        icon = Icons.get(Icons.SCROLL_COLOR);
        add(icon);

        progress = new BitmapText(PixelScene.pixelFont);
        add(progress);
        refreshProgress();
    }

    @Override
    protected void layout() {
        super.layout();

        if (icon == null || progress == null) {
            return;
        }

        if (!flipped) {
            icon.x = x + (SIZE - icon.width()) / 2f + 1;
        } else {
            icon.x = x + width - (SIZE + icon.width()) / 2f - 1;
        }
        icon.y = y + (height - icon.height()) / 2f;
        PixelScene.align(icon);

        progress.measure();
        if (!flipped) {
            progress.x = icon.center().x + 8 - progress.width();
        } else {
            progress.x = icon.center().x - 8;
        }
        progress.y = icon.center().y + 8 - progress.baseLine();
        PixelScene.align(progress);
    }

    @Override
    protected void onClick() {
        super.onClick();
        if (Dungeon.hero != null && Dungeon.hero.ready && Dungeon.hero.isAlive()) {
            ConjureFoodStudy.start();
        }
    }

    @Override
    protected String hoverText() {
        return Messages.get(StudyIndicator.class, "name",
                StudyRunState.reviewsTowardNext(),
                StudyRunState.reviewsRequired());
    }

    @Override
    public void update() {
        boolean shouldShow = Dungeon.hero != null
                && Dungeon.hero.isAlive()
                && StudyRunState.conjureFoodEnabled();

        if (visible != shouldShow) {
            visible = shouldShow;
            if (visible) {
                flash();
            }
        }

        if (StudyRunState.reviewsTowardNext() != lastCompleted
                || StudyRunState.reviewsRequired() != lastRequired) {
            refreshProgress();
            layout();
        }

        super.update();
    }

    private void refreshProgress() {
        lastCompleted = StudyRunState.reviewsTowardNext();
        lastRequired = StudyRunState.reviewsRequired();

        if (progress != null) {
            progress.text(lastCompleted + "/" + lastRequired);
            progress.measure();
        }
    }
}
