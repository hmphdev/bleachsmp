package dev.bleachsmp.hueco;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geometry is authored in blocks about the mob's feet, with +Z facing forward. The same pieces feed both
 * the block-display fallback and the resource pack's item models. {@link Layer} marks pieces that only
 * one of the two shows: the pack can afford far more, finer pieces than block displays can.
 */
record HollowModel(String type, float height, List<Piece> pieces, List<Part> parts) {
    static final String GILLIAN_TYPE = "gillian";
    static final float GILLIAN_MAGNIFICATION = 2.5f;
    // The original 2.2x adult husk reached the original mask; grow both together.
    static final double GILLIAN_MOB_SCALE = 2.2 * GILLIAN_MAGNIFICATION;

    private static final Material SHADE = Material.BLACK_CONCRETE;
    private static final Material OBSIDIAN = Material.POLISHED_BLACKSTONE;
    private static final Material SLATE = Material.DEEPSLATE_TILES;
    private static final Material BONE = Material.BONE_BLOCK;
    private static final Material IVORY = Material.SMOOTH_QUARTZ;
    private static final Material MASK = Material.WHITE_CONCRETE;
    private static final Material SCAR = Material.NETHER_WART_BLOCK;
    private static final Material STRIPE = Material.RED_CONCRETE;
    private static final Material RED_EYE = Material.REDSTONE_BLOCK;
    private static final Material GOLD_EYE = Material.OCHRE_FROGLIGHT;
    private static final Material BLUE_EYE = Material.PEARLESCENT_FROGLIGHT;

    private static final HollowModel GILLIAN = gillian();
    private static final HollowModel ADJUCHAS = adjuchas();
    private static final HollowModel VASTO_LORDE = vastoLorde();

    static final List<String> TYPES = List.of(GILLIAN_TYPE, "adjuchas", "vasto_lorde");

    static HollowModel forType(String type) {
        return switch (type) {
            case GILLIAN_TYPE -> GILLIAN;
            case "adjuchas" -> ADJUCHAS;
            case "vasto_lorde" -> VASTO_LORDE;
            default -> null;
        };
    }

    /** A towering black shroud, a spiked bone collar and a long-nosed mask: the Menos Grande. */
    private static HollowModel gillian() {
        Builder b = new Builder(GILLIAN_TYPE, 4.35f);
        b.pivot(Joint.LEFT_ARM, -.5, 3.0, 0).pivot(Joint.RIGHT_ARM, .5, 3.0, 0)
                .pivot(Joint.HEAD, 0, 3.05, 0).pivot(Joint.TAIL, 0, 1.2, 0);
        // Keep the torso narrow and let long, angled folds carry the silhouette out to the hem.
        b.box(SHADE, 0, .96, 0, .9, 1.92, .78, 0, 0, 0);
        b.box(SLATE, 0, .94, -.38, .77, 1.84, .28, 9, 0, 0, Joint.TAIL);
        b.hole(SHADE, 0, 2.42, 0, 1.02, 1.16, .62, .46, .48);
        b.hole(SLATE, 0, 2.42, .325, .62, .64, .09, .46, .48);
        b.hole(SLATE, 0, 2.42, -.325, .62, .64, .09, .46, .48);
        for (int s : new int[] {-1, 1}) {
            b.box(SHADE, s * .59, 1.08, -.02, .39, 2.08, .77, 0, 0, s * 17);
            b.box(OBSIDIAN, s * .53, 1.11, .365, .29, 2.12, .17, -4, s * 7, s * 14);
            b.box(SLATE, s * .66, 1.03, .39, .11, 1.98, .12, -4, s * 7, s * 17);
            b.box(SHADE, s * .84, .51, -.19, .29, 1.04, .52, 7, s * 10, s * 19, Joint.TAIL);
            b.box(BONE, s * .35, 2.42, .33, .08, .39, .09, 0, 0, s * -19);
            b.box(MASK, s * .22, .055, .37, .17, .11, .24, 0, 0, 0);
        }
        // A ruff of white bone spikes around the neck.
        for (int i = 0; i < 12; i++) {
            double yaw = i * 30;
            double r = Math.toRadians(yaw);
            b.horn(i % 2 == 0 ? IVORY : BONE, Math.sin(r) * .44, 3.0, Math.cos(r) * .36, i % 2 == 0 ? 62 : 74, yaw, 0,
                    1, i % 2 == 0 ? .8 : .55, .17, .17, Joint.BODY);
        }
        // Head and mask.
        b.box(SHADE, 0, 3.45, -.05, .78, .86, .72, 0, 0, 0, Joint.HEAD);
        b.box(MASK, 0, 3.47, .33, .74, .92, .16, -6, 0, 0, Joint.HEAD);
        b.box(IVORY, 0, 3.82, .4, .66, .1, .1, -6, 0, 0, Joint.HEAD);
        for (int s : new int[] {-1, 1}) {
            b.box(SHADE, s * .19, 3.66, .42, .2, .15, .04, -6, 0, s * -14, Joint.HEAD);
            b.box(RED_EYE, s * .19, 3.66, .445, .08, .06, .02, -6, 0, 0, Joint.HEAD);
            b.box(BONE, s * .37, 3.83, .26, .09, .26, .09, -12, 0, s * -28, Joint.HEAD);
            b.box(IVORY, s * .33, 3.25, .36, .14, .4, .12, -6, s * -18, s * 10, Joint.HEAD);
        }
        // The long, tapering beak of a nose.
        b.horn(MASK, 0, 3.46, .42, 98, 0, 4, 4, 1.05, .2, .045, Joint.HEAD);
        b.box(SHADE, 0, 3.17, .42, .44, .08, .04, -6, 0, 0, Joint.HEAD);
        for (int i = -1; i <= 1; i++) {
            b.box(IVORY, i * .12, 3.135, .435, .06, .1, .03, -6, 0, 0, Joint.HEAD);
        }
        // Long thin arms ending in small white claws.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.box(SHADE, s * .7, 2.35, .02, .24, 1.45, .3, -4, 0, s * 9, arm);
            b.box(OBSIDIAN, s * .79, 1.6, .06, .2, .55, .25, -10, 0, s * 4, arm);
            b.horn(MASK, s * .8, 1.34, .1, 170, s * 20, 0, 1, .28, .07, .03, arm);
            b.horn(MASK, s * .73, 1.34, .14, 165, s * -10, 0, 1, .26, .07, .03, arm);
        }
        b.detail();
        // A tattered hem: ragged points of cloth hanging below the robe.
        for (int i = 0; i < 18; i++) {
            double yaw = i * 20 + (i % 3) * 4;
            double r = Math.toRadians(yaw);
            double length = .22 + (i * 7 % 5) * .05;
            b.horn(SHADE, Math.sin(r) * .92, .17, Math.cos(r) * .5, 180 - 12, yaw, 0, 1, length, .2, .03, Joint.BODY);
        }
        // Smaller spikes between the collar's main ones, and a second, inner row.
        for (int i = 0; i < 12; i++) {
            double yaw = i * 30 + 15;
            double r = Math.toRadians(yaw);
            b.horn(BONE, Math.sin(r) * .4, 2.98, Math.cos(r) * .32, 50, yaw, 6, 2, .42, .1, .03, Joint.BODY);
            b.horn(IVORY, Math.sin(r) * .3, 3.02, Math.cos(r) * .25, 32, yaw, 0, 1, .3, .08, .02, Joint.BODY);
        }
        // Brow ridge, cheekbones, eye-socket rims and a full row of teeth on the mask.
        b.box(IVORY, 0, 3.76, .43, .7, .07, .06, -6, 0, 0, Joint.HEAD);
        for (int s : new int[] {-1, 1}) {
            b.box(MASK, s * .19, 3.75, .44, .26, .04, .03, -6, 0, s * -14, Joint.HEAD);
            b.box(MASK, s * .19, 3.575, .44, .24, .035, .03, -6, 0, s * 8, Joint.HEAD);
            b.box(IVORY, s * .3, 3.42, .43, .1, .3, .05, -6, 0, s * 12, Joint.HEAD);
        }
        for (int i = -3; i <= 3; i++) {
            b.horn(IVORY, i * .055, 3.2, .45, 180, 0, 0, 1, .09, .035, .012, Joint.HEAD);
            b.horn(IVORY, i * .055 + .027, 3.12, .45, 0, 0, 0, 1, .07, .03, .01, Joint.HEAD);
        }
        // Wide sleeve cuffs.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.box(SHADE, s * .8, 1.82, .06, .32, .2, .36, -10, 0, s * 6, arm);
            b.box(SLATE, s * .8, 1.73, .06, .34, .04, .38, -10, 0, s * 6, arm);
        }
        return b.build();
    }

    /** A long, low predator: skull-masked, spine-backed, six-limbed, with a hole through its flank. */
    private static HollowModel adjuchas() {
        Builder b = new Builder("adjuchas", 2.23f);
        b.pivot(Joint.LEFT_ARM, -.38, .95, .55).pivot(Joint.RIGHT_ARM, .38, .95, .55)
                .pivot(Joint.LEFT_LEG, -.36, .95, -.55).pivot(Joint.RIGHT_LEG, .36, .95, -.55)
                .pivot(Joint.HEAD, 0, 1.2, .85).pivot(Joint.TAIL, 0, 1.0, -.85);
        // The torso is open through the middle: a Hollow's hole, seen from the side.
        b.sideHole(OBSIDIAN, 0, 1.0, 0, .78, .62, 1.7, .3, .28);
        b.box(BONE, 0, 1.33, .1, .62, .1, 1.3, -3, 0, 0);
        for (int i = 0; i < 4; i++) {
            b.horn(i % 2 == 0 ? IVORY : BONE, 0, 1.36, .5 - i * .34, -42 - i * 6, 0, -10, 2,
                    .46 - i * .05, .22, .06, Joint.BODY);
        }
        // Neck, skull mask, jaw and fangs.
        b.box(SHADE, 0, 1.2, .82, .42, .4, .4, -30, 0, 0);
        b.box(MASK, 0, 1.34, 1.05, .5, .42, .46, 8, 0, 0, Joint.HEAD);
        b.box(MASK, 0, 1.25, 1.42, .34, .24, .5, 12, 0, 0, Joint.HEAD);
        b.box(IVORY, 0, 1.06, 1.33, .3, .09, .52, -6, 0, 0, Joint.HEAD);
        for (int s : new int[] {-1, 1}) {
            b.box(SHADE, s * .14, 1.4, 1.29, .15, .1, .06, 8, 0, s * 18, Joint.HEAD);
            b.box(GOLD_EYE, s * .14, 1.4, 1.315, .07, .045, .03, 8, 0, s * 18, Joint.HEAD);
            b.box(STRIPE, s * .14, 1.5, 1.2, .05, .2, .04, -30, 0, s * 10, Joint.HEAD);
            b.horn(MASK, s * .1, 1.11, 1.6, 168, 0, 0, 1, .16, .05, .02, Joint.HEAD);
            b.horn(IVORY, s * .08, 1.09, 1.42, 12, 0, 0, 1, .12, .04, .02, Joint.HEAD);
            b.horn(IVORY, s * .2, 1.52, .9, -50, s * -28, -12, 3, .8, .12, .035, Joint.HEAD);
        }
        // Raptorial scythe arms folded at the shoulders.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.horn(BONE, s * .44, 1.22, .6, 25, s * 35, 55, 3, 1.0, .14, .04, arm);
        }
        // Four digitigrade legs.
        for (int s : new int[] {-1, 1}) {
            for (int front = 0; front <= 1; front++) {
                Joint joint = front == 1 ? (s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM)
                        : (s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG);
                double z = front == 1 ? .55 : -.55;
                b.box(OBSIDIAN, s * .5, .82, z, .24, .55, .3, front == 1 ? -18 : 22, 0, s * 18, joint);
                b.box(SHADE, s * .6, .38, z + (front == 1 ? .1 : -.12), .16, .5, .18, front == 1 ? 14 : -18, 0, s * 6, joint);
                b.box(BONE, s * .62, .06, z + (front == 1 ? .2 : -.06), .22, .1, .34, 0, 0, 0, joint);
            }
        }
        // A segmented tail ending in a bone stinger.
        b.horn(OBSIDIAN, 0, 1.02, -.84, -70, 0, -10, 3, 1.0, .26, .12, Joint.TAIL);
        b.horn(BONE, 0, 1.19, -1.81, -20, 0, 35, 2, .5, .12, .03, Joint.TAIL);
        b.detail();
        // Ribs curling down both flanks from the spine.
        for (int s : new int[] {-1, 1}) {
            for (int i = 0; i < 5; i++) {
                double z = .62 - i * .28;
                b.horn(i % 2 == 0 ? IVORY : BONE, s * .3, 1.33, z, 112, s * 90, 22, 3, .5 - i * .03, .07, .03,
                        Joint.BODY);
            }
        }
        // Vertebrae along the back, between the big spines.
        for (int i = 0; i < 9; i++) {
            b.box(BONE, 0, 1.4, .74 - i * .19, .14, .07, .1, -3, 45, 0);
        }
        // Teeth along both jaws.
        for (int s : new int[] {-1, 1}) {
            for (int i = 0; i < 4; i++) {
                b.horn(IVORY, s * .13, 1.12, 1.25 + i * .09, 180, 0, 0, 1, .08, .03, .01, Joint.HEAD);
                b.horn(IVORY, s * .12, 1.04, 1.29 + i * .09, 0, 0, 0, 1, .06, .025, .01, Joint.HEAD);
            }
        }
        // Three claws on every foot.
        for (int s : new int[] {-1, 1}) {
            for (int front = 0; front <= 1; front++) {
                Joint joint = front == 1 ? (s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM)
                        : (s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG);
                double z = (front == 1 ? .55 : -.55) + (front == 1 ? .2 : -.06) + .17;
                for (int c = -1; c <= 1; c++) {
                    b.horn(IVORY, s * .62 + c * .07, .06, z, 100, c * 12, 25, 2, .16, .05, .015, joint);
                }
            }
        }
        // Wicked hooks on the scythe arms.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.horn(IVORY, s * .74, 1.6, 1.05, 150, s * 20, 20, 3, .35, .06, .015, arm);
        }
        return b.build();
    }

    /** A lean, white, humanoid apex Hollow: long forward-swept horns, a fur mane and a hole in its chest. */
    private static HollowModel vastoLorde() {
        Builder b = new Builder("vasto_lorde", 3.24f);
        b.pivot(Joint.LEFT_ARM, -.42, 2.33, 0).pivot(Joint.RIGHT_ARM, .42, 2.33, 0)
                .pivot(Joint.LEFT_LEG, -.17, 1.38, 0).pivot(Joint.RIGHT_LEG, .17, 1.38, 0)
                .pivot(Joint.HEAD, 0, 2.5, 0).pivot(Joint.TAIL, 0, 2.6, -.18);
        for (int s : new int[] {-1, 1}) {
            Joint leg = s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG;
            b.box(IVORY, s * .18, 1.08, 0, .26, .62, .3, -4, 0, s * 3, leg);
            b.box(IVORY, s * .19, .52, .03, .21, .6, .24, 4, 0, 0, leg);
            b.box(SHADE, s * .19, .2, .02, .29, .16, .32, 0, 0, 0, leg);
            b.box(MASK, s * .19, .06, .1, .23, .12, .4, 0, 0, 0, leg);
            b.horn(SHADE, s * .19, .05, .3, 95, 0, 0, 1, .14, .06, .02, leg);
        }
        // Dark flanks and angled hip plates break up the ivory without covering the chest opening.
        b.box(OBSIDIAN, 0, 1.42, 0, .5, .24, .3, 0, 0, 0);
        b.box(SHADE, 0, 1.66, 0, .4, .3, .26, 0, 0, 0);
        b.box(IVORY, 0, 1.66, .126, .19, .3, .065, 0, 0, 0);
        b.hole(IVORY, 0, 2.08, 0, .64, .58, .32, .24, .24);
        b.hole(OBSIDIAN, 0, 2.08, .167, .43, .43, .07, .24, .24);
        b.hole(SCAR, 0, 2.08, .205, .32, .32, .012, .24, .24);
        for (int s : new int[] {-1, 1}) {
            b.box(SLATE, s * .151, 1.69, .14, .095, .27, .07, 0, 0, s * -18);
            b.box(IVORY, s * .14, 1.44, .155, .19, .12, .06, 0, 0, s * 24);
            b.box(STRIPE, s * .263, 2.05, .174, .035, .42, .026, 0, 0, s * -12);
        }
        // A black mane around the neck and shoulders.
        b.box(SHADE, 0, 2.38, -.02, .74, .18, .44, 0, 0, 0);
        for (int s : new int[] {-1, 1}) {
            b.box(SHADE, s * .34, 2.33, 0, .26, .22, .4, 0, 0, s * 25);
            b.box(SCAR, s * .44, 2.26, 0, .12, .12, .3, 0, 0, s * 40);
        }
        // Arms with fur cuffs and claws.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.box(IVORY, s * .45, 2.05, 0, .18, .55, .2, 0, 0, s * 8, arm);
            b.box(IVORY, s * .5, 1.55, .03, .16, .5, .18, -6, 0, s * 4, arm);
            b.box(SHADE, s * .51, 1.3, .04, .23, .14, .23, -6, 0, 0, arm);
            b.box(MASK, s * .52, 1.16, .06, .15, .16, .14, -6, 0, 0, arm);
            for (int c = -1; c <= 1; c++) {
                b.horn(SHADE, s * .52 + c * .045, 1.08, .09, 172, s * c * 8, 0, 1, .18, .035, .015, arm);
            }
        }
        // The mask: a full helmet with red markings, black eyes and gold irises.
        b.box(MASK, 0, 2.72, .02, .42, .46, .44, 0, 0, 0, Joint.HEAD);
        b.box(MASK, 0, 2.55, .17, .3, .16, .2, 6, 0, 0, Joint.HEAD);
        b.box(SHADE, 0, 2.53, .265, .22, .045, .02, 6, 0, 0, Joint.HEAD);
        for (int i = -2; i <= 2; i++) {
            b.box(IVORY, i * .04, 2.535, .275, .02, .06, .015, 6, 0, 0, Joint.HEAD);
        }
        for (int s : new int[] {-1, 1}) {
            b.box(SHADE, s * .1, 2.73, .236, .12, .06, .02, 0, 0, s * -10, Joint.HEAD);
            b.box(GOLD_EYE, s * .1, 2.73, .248, .035, .035, .01, 0, 0, 0, Joint.HEAD);
            b.box(STRIPE, s * .1, 2.86, .24, .03, .16, .01, 0, 0, s * 8, Joint.HEAD);
            b.box(STRIPE, s * .1, 2.6, .236, .03, .1, .01, 0, 0, s * -8, Joint.HEAD);
            // Horns sweep out from the temples, then forward and up.
            b.horn(MASK, s * .2, 2.84, .04, 80, s * 70, -9, 5, 1.05, .14, .03, Joint.HEAD);
        }
        // Long black hair down the back.
        for (int s : new int[] {-1, 1}) {
            b.horn(SHADE, s * .12, 2.86, -.2, 165, 180 + s * 12, -6, 3, 1.0, .2, .08, Joint.TAIL);
        }
        b.detail();
        // A shaggy mane: tufts standing out around the neck and shoulders, and more hair down the back.
        for (int i = 0; i < 16; i++) {
            double yaw = i * 22.5;
            double r = Math.toRadians(yaw);
            boolean back = Math.cos(r) < 0;
            b.horn(i % 4 == 0 ? SCAR : SHADE, Math.sin(r) * .34, 2.36, Math.cos(r) * .2, back ? 70 : 55, yaw,
                    10, 2, .26 + (i % 3) * .05, .1, .02, Joint.BODY);
        }
        for (int i = -2; i <= 2; i++) {
            b.horn(SHADE, i * .07, 2.8, -.22, 168, 180 + i * 6, -4, 3, .85 + Math.abs(i) * .05 * -1, .1, .03,
                    Joint.TAIL);
        }
        // Fur at the wrists and ankles, knee guards, and a split at the mask's brow.
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            Joint leg = s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG;
            for (int i = 0; i < 6; i++) {
                double yaw = i * 60;
                double r = Math.toRadians(yaw);
                b.horn(SHADE, s * .51 + Math.sin(r) * .1, 1.36, .04 + Math.cos(r) * .1, 120, yaw, 0, 1, .14, .07, .02,
                        arm);
                b.horn(SHADE, s * .19 + Math.sin(r) * .13, .26, .02 + Math.cos(r) * .13, 110, yaw, 0, 1, .12, .07,
                        .02, leg);
            }
            b.box(MASK, s * .18, .82, .16, .2, .18, .06, 8, 0, 0, leg);
            b.box(SHADE, s * .05, 2.86, .24, .015, .14, .012, 0, 0, s * 20, Joint.HEAD);
        }
        return b.build();
    }

    enum Joint {
        BODY(0, 0, 0), LEFT_ARM(-.47f, 1.65f, .15f), RIGHT_ARM(.47f, 1.65f, .15f),
        LEFT_LEG(-.22f, 1.20f, 0), RIGHT_LEG(.22f, 1.20f, 0), TAIL(0, 1.13f, -.35f), HEAD(0, 2f, 0);

        private final Vector3f pivot;

        Joint(float x, float y, float z) {
            pivot = new Vector3f(x, y, z);
        }
    }

    enum Layer { BOTH, BLOCKS, MODEL }

    /** Pieces shown as block displays to players without the resource pack. */
    List<Piece> blockPieces() {
        return pieces.stream().filter(piece -> piece.layer() != Layer.MODEL).toList();
    }

    /** Pieces in the resource pack's item models. */
    List<Piece> modelPieces() {
        return pieces.stream().filter(piece -> piece.layer() != Layer.BLOCKS).toList();
    }

    /** Rotation of a joint for an animation swing, shared by both renderers. */
    static Quaternionf jointRotation(Joint joint, float swing) {
        Quaternionf rotation = new Quaternionf();
        return joint == Joint.TAIL ? rotation.rotateY(swing) : rotation.rotateX(swing);
    }

    /**
     * One item model per joint. Its elements are stored relative to {@code pivot}, shrunk by {@code scale}
     * so they fit the resource pack's -16..32 model bounds.
     */
    record Part(Joint joint, Vector3f pivot, float scale, List<Piece> pieces) {
        Transformation pose(float magnification, float lift, float swing) {
            return new Transformation(new Vector3f(pivot).mul(magnification).add(0, lift, 0),
                    jointRotation(joint, swing), new Vector3f(scale * magnification), new Quaternionf());
        }
    }

    record Piece(Material material, Vector3f center, Vector3f size, Quaternionf rotation, Joint joint, Vector3f pivot,
            Layer layer) {
        Transformation pose(float scale, float lift, float swing) {
            Quaternionf jointRotation = jointRotation(joint, swing);
            Vector3f corner = new Vector3f(size).mul(-.5f).rotate(rotation).add(center)
                    .sub(pivot).rotate(jointRotation).add(pivot).mul(scale).add(0, lift, 0);
            return new Transformation(corner, jointRotation.mul(rotation), new Vector3f(size).mul(scale),
                    new Quaternionf());
        }

        boolean luminous() {
            return material == RED_EYE || material == GOLD_EYE || material == BLUE_EYE;
        }
    }

    private static final class Builder {
        private final String type;
        private final float height;
        private Layer layer = Layer.BOTH;
        private final List<Piece> pieces = new ArrayList<>();
        private final java.util.Map<Joint, Vector3f> pivots = new java.util.EnumMap<>(Joint.class);

        private Builder(String type, float height) {
            this.type = type;
            this.height = height;
        }

        /** Pieces added after this are too fine for block displays and only go in the resource pack. */
        private Builder detail() {
            layer = Layer.MODEL;
            return this;
        }

        private Builder pivot(Joint joint, double x, double y, double z) {
            pivots.put(joint, new Vector3f((float) x, (float) y, (float) z));
            return this;
        }

        private void box(Material material, double x, double y, double z, double w, double h, double d,
                double pitch, double yaw, double roll) {
            box(material, x, y, z, w, h, d, pitch, yaw, roll, Joint.BODY);
        }

        private void box(Material material, double x, double y, double z, double w, double h, double d,
                double pitch, double yaw, double roll, Joint joint) {
            Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw),
                    (float) Math.toRadians(pitch), (float) Math.toRadians(roll));
            pieces.add(new Piece(material, new Vector3f((float) x, (float) y, (float) z),
                    new Vector3f((float) w, (float) h, (float) d), rotation, joint,
                    pivots.getOrDefault(joint, joint.pivot), layer));
        }

        /**
         * A tapering, curving spike built from overlapping segments. {@code tilt} is degrees from
         * straight up toward +Z, {@code yaw} turns that around the vertical, and each segment adds
         * {@code bend} degrees of tilt.
         */
        private void horn(Material material, double x, double y, double z, double tilt, double yaw, double bend,
                int segments, double length, double startWidth, double endWidth, Joint joint) {
            Layer outer = layer;
            if (outer == Layer.BOTH) {
                // Block displays keep the few chunky segments; the pack gets a smooth, sharper taper.
                layer = Layer.BLOCKS;
                hornSegments(material, x, y, z, tilt, yaw, bend, segments, length, startWidth, endWidth, joint);
            }
            layer = Layer.MODEL;
            int smooth = Math.max(4, segments * 4);
            double totalBend = bend * segments;
            hornSegments(material, x, y, z, tilt - bend / 2 + totalBend / smooth / 2, yaw, totalBend / smooth, smooth,
                    length, startWidth, endWidth * .55, joint);
            layer = outer;
        }

        private void hornSegments(Material material, double x, double y, double z, double tilt, double yaw,
                double bend, int segments, double length, double startWidth, double endWidth, Joint joint) {
            double step = length / segments;
            double t = tilt;
            for (int i = 0; i < segments; i++) {
                double tr = Math.toRadians(t);
                double yr = Math.toRadians(yaw);
                double dx = Math.sin(tr) * Math.sin(yr);
                double dy = Math.cos(tr);
                double dz = Math.sin(tr) * Math.cos(yr);
                double width = segments == 1 ? startWidth
                        : startWidth + (endWidth - startWidth) * i / (segments - 1.0);
                box(material, x + dx * step / 2, y + dy * step / 2, z + dz * step / 2, width, step * 1.12, width,
                        t, yaw, 0, joint);
                x += dx * step;
                y += dy * step;
                z += dz * step;
                t += bend;
            }
        }

        /** A block with a see-through square hole running front to back (along Z). */
        private void hole(Material material, double x, double y, double z, double w, double h, double d,
                double holeW, double holeH) {
            double side = (w - holeW) / 2;
            double cap = (h - holeH) / 2;
            box(material, x - (holeW + side) / 2, y, z, side, h, d, 0, 0, 0);
            box(material, x + (holeW + side) / 2, y, z, side, h, d, 0, 0, 0);
            box(material, x, y + (holeH + cap) / 2, z, holeW, cap, d, 0, 0, 0);
            box(material, x, y - (holeH + cap) / 2, z, holeW, cap, d, 0, 0, 0);
        }

        /** A block with a see-through square hole running side to side (along X). */
        private void sideHole(Material material, double x, double y, double z, double w, double h, double d,
                double holeD, double holeH) {
            double end = (d - holeD) / 2;
            double cap = (h - holeH) / 2;
            box(material, x, y, z - (holeD + end) / 2, w, h, end, 0, 0, 0);
            box(material, x, y, z + (holeD + end) / 2, w, h, end, 0, 0, 0);
            box(material, x, y + (holeH + cap) / 2, z, w, cap, holeD, 0, 0, 0);
            box(material, x, y - (holeH + cap) / 2, z, w, cap, holeD, 0, 0, 0);
        }

        private HollowModel build() {
            List<Part> parts = new ArrayList<>();
            for (Joint joint : Joint.values()) {
                List<Piece> members = pieces.stream()
                        .filter(piece -> piece.joint() == joint && piece.layer() != Layer.BLOCKS).toList();
                if (members.isEmpty()) {
                    continue;
                }
                Vector3f pivot = pivots.getOrDefault(joint, joint.pivot);
                // Item model elements must stay within 1.5 model-blocks of the model's centre.
                float reach = 0;
                for (Piece piece : members) {
                    Vector3f offset = new Vector3f(piece.center()).sub(pivot).absolute();
                    reach = Math.max(reach, Math.max(offset.x, Math.max(offset.y, offset.z))
                            + Math.max(piece.size().x, Math.max(piece.size().y, piece.size().z)) * .5f);
                }
                parts.add(new Part(joint, new Vector3f(pivot), Math.max(1, reach / 1.45f), members));
            }
            return new HollowModel(type, height, List.copyOf(pieces), List.copyOf(parts));
        }
    }
}
