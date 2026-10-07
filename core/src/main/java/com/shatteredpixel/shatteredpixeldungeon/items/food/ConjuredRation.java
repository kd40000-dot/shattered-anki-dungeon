/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.items.food;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;

/**
 * Basic sustenance created by study. It intentionally does not trigger normal
 * meal talents, food-eaten statistics/badges, or economic value.
 */
public class ConjuredRation extends Food {

    {
        bones = false;
    }

    @Override
    public void execute(Hero hero, String action) {
        if (!AC_EAT.equals(action)) {
            super.execute(hero, action);
            return;
        }

        // Mirror Item.execute's normal action setup. We intentionally bypass
        // Food.execute below so normal meal talents/statistics are not triggered.
        GameScene.cancel();
        curUser = hero;
        curItem = this;

        detach(hero.belongings.backpack);

        // Keep normal hunger rules (including On Diet and cursed Horn effects),
        // but do not invoke Talent.onFoodEaten or food-eaten statistics.
        satisfy(hero);
        GLog.i(Messages.get(this, "eat_msg"));

        hero.sprite.operate(hero.pos);
        hero.busy();
        SpellSprite.show(hero, SpellSprite.FOOD);
        eatSFX();
        hero.spend(TIME_TO_EAT);
    }

    @Override
    public int value() {
        return 0;
    }
}
