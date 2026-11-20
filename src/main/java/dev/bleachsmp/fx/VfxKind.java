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
    CRACK(Display.Billboard.FIXED, false);

    final Display.Billboard billboard;
    final boolean glows;

    VfxKind(Display.Billboard billboard, boolean glows) {
        this.billboard = billboard;
        this.glows = glows;
    }
}
