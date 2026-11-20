package dev.bleachsmp.fx;

import org.bukkit.entity.Display;

/** The effect models in the resource pack. See {@link VfxAssets} for each one's shape and default size. */
public enum VfxKind {
    /** A flat crescent, middle at the origin, tips sweeping back toward -Z, radius one. */
    SLASH(Display.Billboard.FIXED, true),
    /** A flat ring of radius one, brightest at its outer edge. */
    RING(Display.Billboard.FIXED, true),
    /** A cylinder one block long along +Z, one block across. */
    BEAM(Display.Billboard.FIXED, true),
    /** A column one block tall and one across, fading out toward its top. */
    PILLAR(Display.Billboard.FIXED, true),
    /** A hollow sheath of reiatsu one block tall and across, fading toward its top: a released form's aura. */
    AURA(Display.Billboard.FIXED, true),
    /** A glowing ball one block across. */
    ORB(Display.Billboard.FIXED, true),
    /** A camera-facing star of light one block across. */
    FLARE(Display.Billboard.CENTER, true),
    /** A camera-facing tongue of flame one block tall, standing on the origin. */
    FLAME(Display.Billboard.VERTICAL, true),
    /** Two turns of ribbon climbing one block around a half-block radius. */
    SPIRAL(Display.Billboard.FIXED, true),
    /** A wing of light feathers about a block across, reaching toward +X. Mirror X for the other side. */
    WING(Display.Billboard.FIXED, true),
    /** A bone horn curving up and forward (+Z), about half a block long. */
    HORN(Display.Billboard.FIXED, false),
    /** A curved armour plate with glowing seams, facing +Z. */
    PLATE(Display.Billboard.FIXED, true),
    /** Scorched, cracked ground, one block square, lying flat. */
    CRACK(Display.Billboard.FIXED, false),

    // ---------------------------------------------------------------- element shapes
    /** An ice crystal one block tall, standing on the origin and tapering to a point. */
    SHARD(Display.Billboard.FIXED, true),
    /** A rough boulder about half a block across, centred on the origin. Takes the world's light. */
    ROCK(Display.Billboard.FIXED, false),
    /** A jagged spike of stone one block tall, standing on the origin. */
    SPIKE(Display.Billboard.FIXED, false),
    /** A crackling zigzag of lightning one block long along +Z. */
    ZAP(Display.Billboard.FIXED, true),
    /** Three thin swept streaks of wind lying flat, about a block across, curving like {@link #SLASH}. */
    GUST(Display.Billboard.FIXED, true),
    /** A soft billowing puff about a block across: shadow, ash, poison gas or mist, by colour. */
    SMOKE(Display.Billboard.FIXED, true),
    /** A curling crest of water one block wide (X), breaking toward +Z. */
    WAVE(Display.Billboard.FIXED, true),
    /** A falling drop about a quarter block tall: water, blood or venom, by colour. */
    DROP(Display.Billboard.FIXED, true),
    /** A blossom petal a quarter block across, flat in the XY plane. */
    PETAL(Display.Billboard.FIXED, true),
    /** A single feather one block long along +X, flat in the XY plane. */
    FEATHER(Display.Billboard.FIXED, true),

    // ---------------------------------------------------------------- weapons and props
    /** A blade of light one block long standing up from the origin, with a guard at its root. */
    BLADE(Display.Billboard.FIXED, true),
    /** A Heilig Pfeil: an arrow of reishi one block long, pointing +Z, its tip at the origin's +Z end. */
    ARROW(Display.Billboard.FIXED, true),
    /** A long spear of light one block long along +Z with a diamond head at its +Z end. */
    SPEAR(Display.Billboard.FIXED, true),
    /** A run of chain links one block long along +Z. */
    CHAIN(Display.Billboard.FIXED, true),
    /** A segment of bone blade with a hooked fang, about half a block long along +Z. */
    FANG(Display.Billboard.FIXED, false),
    /** Three parallel claw marks a block tall, facing +Z. */
    CLAW(Display.Billboard.FIXED, true),
    /** A beast's head of reiatsu with open jaws, about a block long, facing +Z: dragon, serpent, wolf or maw. */
    HEAD(Display.Billboard.FIXED, true),
    /** A giant armoured fist and forearm, the fist at the origin and the arm rising two blocks up +Y. */
    FIST(Display.Billboard.FIXED, false),
    /** An armoured giant's upper body, four blocks tall from the origin, facing +Z. */
    GIANT(Display.Billboard.FIXED, false),
    /** A Hollow's bone mask facing +Z, half a block across. */
    MASK(Display.Billboard.FIXED, false),
    /** A tapering tentacle one block long, rising from the origin and curling toward +Z. */
    TENTACLE(Display.Billboard.FIXED, true),

    // ---------------------------------------------------------------- glyphs, one block across
    HEART(Display.Billboard.CENTER, true),
    NOTE(Display.Billboard.CENTER, true),
    EYE(Display.Billboard.CENTER, true),
    STAR(Display.Billboard.CENTER, true),
    BUTTERFLY(Display.Billboard.CENTER, true),
    INVADER(Display.Billboard.CENTER, true),
    /** A playing card, facing +Z. */
    CARD(Display.Billboard.FIXED, true),
    /** A clock face, facing +Z. */
    CLOCK(Display.Billboard.FIXED, true),
    /** A Quincy pentacle in a circle, lying flat. */
    SIGIL(Display.Billboard.FIXED, true),
    /** A triangular shield pane, point down, facing +Z. */
    TRIANGLE(Display.Billboard.FIXED, true),
    /** A hexagonal barrier pane, facing +Z. */
    HEX(Display.Billboard.FIXED, true),
    /** A written page, facing +Z. */
    PAGE(Display.Billboard.FIXED, true);

    final Display.Billboard billboard;
    final boolean glows;

    VfxKind(Display.Billboard billboard, boolean glows) {
        this.billboard = billboard;
        this.glows = glows;
    }
}
