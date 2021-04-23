/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2021 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.items.artifacts;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.WandEmpower;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.effects.Beam;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEnergy;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;
import com.shatteredpixel.shatteredpixeldungeon.utils.BArray;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class HolySymbol extends Artifact{
    {
        image = ItemSpriteSheet.ARTIFACT_SPELLBOOK;

        exp = 0;
        levelCap = 10;

        charge = Math.min(3 + level(), 10);
        partialCharge = 0;
        chargeCap = Math.min(3 + level(), 10);

        defaultAction = AC_SMITE;

        usesTargeting = true;
        unique = true;
        bones = false;

    }

    public int min() {
        return 4 + level();
    }

    public int max() {
        return 10 + 4*level();
    }

    public int damageRoll() {
        return Random.NormalIntRange(min(), max());
    }


    public static final String AC_SMITE = "SMITE";

    @Override
    public ArrayList<String> actions(Hero hero ) {
        ArrayList<String> actions = super.actions( hero );
        if (isEquipped( hero ) && charge > 0)
            actions.add(AC_SMITE);
        return actions;
    }

    @Override
    public void execute( Hero hero, String action) {
        super.execute(hero, action);
        if (action.equals(AC_SMITE)){
            curUser = hero;

            if (!isEquipped( hero )) {
                GLog.i( Messages.get(Artifact.class, "need_to_equip") );
                QuickSlotButton.cancel();

            } else if (charge < 1) {
                GLog.i( Messages.get(this, "no_charge") );
                QuickSlotButton.cancel();

            } else {
                GameScene.selectCell(caster);


            }
        }
    }

    private CellSelector.Listener caster = new CellSelector.Listener(){

        @Override
        public void onSelect(Integer target) {
            if (target != null ){

                curUser.busy();
                charge--;
                updateQuickslot();


                final Ballistica smite = new Ballistica(curUser.pos, target, Ballistica.MAGIC_BOLT);
                int cell = smite.collisionPos;

                curUser.sprite.zap(cell);
                curUser.sprite.parent.add(
                        new Beam.LightRay(curUser.sprite.center(), DungeonTilemap.raisedTileCenterToWorld(smite.collisionPos)));

                Char ch = Actor.findChar(cell);

                if (ch != null) {
                    ch.damage(damageRoll(), this);
                }

                //target hero level is 1 + 2*symbol level
                int lvlDiffFromTarget = (curUser).lvl - (1+level()*2);
                //plus an extra one for each level after 6
                if (level() >= 7){
                    lvlDiffFromTarget -= level()-6;
                }
                if (lvlDiffFromTarget >= 0){
                    exp += Math.round(10f * Math.pow(1.1f, lvlDiffFromTarget));
                } else {
                    exp += Math.round(10f * Math.pow(0.75f, -lvlDiffFromTarget));
                }

                if (exp >= (level() + 1) * 50 && level() < levelCap) {
                    upgrade();
                    exp -= level() * 50;
                    GLog.p(Messages.get(HolySymbol.class, "levelup"));

                }

                curUser.spend(1f);
                curUser.next();
            }

        }

        @Override
        public String prompt() {
            return Messages.get(HolySymbol.class, "prompt");
        }
    };

    @Override
    public Item upgrade() {
        chargeCap = Math.min(chargeCap+1, 10);
        return super.upgrade();
    }

    @Override
    public int value() { return 0; }

    @Override
    protected ArtifactBuff passiveBuff() { return new symbolRecharge(); }

    @Override
    public void charge(Hero target, float amount) {
        if (charge < chargeCap) {
            partialCharge += 0.25f*amount;
            if (partialCharge >= 1){
                partialCharge--;
                charge++;
                updateQuickslot();
            }
        }
    }

    public class symbolRecharge extends ArtifactBuff {
        @Override
        public boolean act() {
            if (charge < chargeCap) {
                float turnsToCharge = 30;
                turnsToCharge /= RingOfEnergy.artifactChargeMultiplier(target);
                float chargeToGain = 1f/turnsToCharge;
                partialCharge += chargeToGain;

                if (partialCharge >= 1){
                    charge++;
                    partialCharge -= 1;
                    if (charge == chargeCap){
                        partialCharge = 0;
                    }
                }
            } else
                partialCharge = 0;

            updateQuickslot();

            spend(TICK);

            return true;
        }
    }
}
