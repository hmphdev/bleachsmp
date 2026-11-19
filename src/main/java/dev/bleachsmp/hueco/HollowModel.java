package dev.bleachsmp.hueco;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Geometry is authored in blocks about the mob's feet, with +Z facing forward. */
record HollowModel(float height, List<Piece> pieces) {
    static final String GILLIAN_TYPE = "gillian";
    static final float GILLIAN_MAGNIFICATION = 2.5f;
    // The original 2.2x adult husk reached the original mask; grow both together.
    static final double GILLIAN_MOB_SCALE = 2.2 * GILLIAN_MAGNIFICATION;

    private static final Material SHADE = Material.BLACK_CONCRETE;
    private static final Material OBSIDIAN = Material.POLISHED_BLACKSTONE;
    private static final Material SLATE = Material.DEEPSLATE_TILES;
    private static final Material BONE = Material.BONE_BLOCK;
    private static final Material IVORY = Material.SMOOTH_QUARTZ;
    private static final Material SCAR = Material.NETHER_WART_BLOCK;
    private static final Material RED_EYE = Material.REDSTONE_BLOCK;
    private static final Material GOLD_EYE = Material.OCHRE_FROGLIGHT;
    private static final Material BLUE_EYE = Material.PEARLESCENT_FROGLIGHT;

    private static final HollowModel GILLIAN = gillian();
    private static final HollowModel ADJUCHAS = adjuchas();
    private static final HollowModel VASTO_LORDE = vastoLorde();

    static HollowModel forType(String type) {
        return switch (type) {
            case GILLIAN_TYPE -> GILLIAN;
            case "adjuchas" -> ADJUCHAS;
            case "vasto_lorde" -> VASTO_LORDE;
            default -> null;
        };
    }

    private static HollowModel gillian() {
        Builder b = new Builder(4.35f);
        // Hanging shroud over a narrow torso, with layered, angled hems.
        b.box(SHADE, 0, 1.49, 0, .87, 2.73, .71, 0, 0, 0);
        b.box(OBSIDIAN, 0, 2.72, -.11, 1.13, 1.24, .86, -8, 0, 0);
        b.box(SLATE, 0, .69, -.39, .73, 1.65, .33, 13, 0, 0, Joint.TAIL);
        b.box(OBSIDIAN, 0, 3.62, -.09, .83, 1.18, .67, -12, 0, 0);
        b.box(IVORY, 0, 3.64, .32, .58, .9, .22, -12, 0, 0);
        b.box(SHADE, 0, 3.68, .46, .35, .27, .07, -12, 0, 0);
        b.box(RED_EYE, 0, 3.72, .51, .21, .062, .057, -12, 0, 0);
        b.box(BONE, 0, 3.44, .53, .17, .43, .19, -37, 0, 0);
        b.box(SHADE, 0, 2.27, .37, .34, .34, .11, 0, 0, 45);
        b.box(SCAR, 0, 2.26, .44, .17, .17, .05, 0, 0, 45);
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            b.box(SLATE, s * .45, 1.35, .05, .32, 2.53, .62, -5, s * 7, s * 12);
            b.box(SHADE, s * .74, .68, -.1, .22, 1.19, .42, 8, s * 12, s * 16, Joint.TAIL);
            b.box(SHADE, s * .76, 2.16, -.01, .26, 1.45, .35, -5, 0, s * 23, arm);
            b.box(BONE, s * .94, 1.50, .12, .13, .47, .16, -23, 0, s * 13, arm);
            b.box(BONE, s * .54, 2.99, -.17, .15, .83, .21, -20, 0, s * 39);
            b.box(IVORY, s * .28, 3.89, .26, .17, .61, .22, -12, 0, s * 21);
            b.box(BONE, s * .41, 4.17, .13, .09, .33, .13, -27, 0, s * 29);
            b.box(BONE, s * .405, 2.48, .27, .13, .56, .17, 0, 0, s * 40);
            b.box(BONE, s * .34, 2.19, .33, .10, .41, .15, 0, 0, s * 58);
        }
        return b.build();
    }

    private static HollowModel adjuchas() {
        Builder b = new Builder(2.23f);
        // A low, six-limbed dune hunter with a plated back and forked pincers.
        b.box(OBSIDIAN, 0, 1.0, -.16, .9, .62, 1.34, -10, 0, 0);
        b.box(SHADE, 0, .80, -.32, .68, .42, 1.02, -4, 0, 0);
        b.box(SCAR, 0, 1.30, -.31, .29, .17, .92, -13, 0, 0);
        b.box(BONE, 0, 1.32, .39, .77, .45, .57, 19, 0, 0);
        b.box(IVORY, 0, 1.21, .70, .51, .35, .28, 15, 0, 0);
        b.box(SHADE, 0, 1.21, .855, .45, .145, .08, 15, 0, 0);
        b.box(BONE, 0, 1.0, .65, .34, .12, .37, 6, 0, 0);
        b.box(OBSIDIAN, 0, .93, -1.03, .31, .32, .73, -23, 0, 0, Joint.TAIL);
        b.box(BONE, 0, 1.10, -1.42, .22, .22, .46, -36, 0, 0, Joint.TAIL);
        b.box(IVORY, 0, 1.24, -1.61, .11, .15, .29, -54, 0, 0, Joint.TAIL);
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            Joint leg = s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG;
            b.box(BONE, s * .34, 1.40, -.16, .44, .18, .85, -7, s * -9, s * -29);
            b.box(IVORY, s * .34, 1.17, -.66, .36, .15, .48, -21, s * -11, s * -24);
            b.box(BONE, s * .34, 1.50, .48, .23, .28, .40, -20, s * 15, s * 19);
            b.box(IVORY, s * .45, 1.66, .45, .12, .52, .15, 29, 0, s * 29);
            b.box(GOLD_EYE, s * .145, 1.252, .9, .10, .061, .056, 15, 0, s * 16);
            b.box(BONE, s * .25, 1.01, .82, .10, .26, .14, -16, 0, s * 17);
            b.box(SLATE, s * .52, .65, .41, .31, .72, .32, -22, 0, s * 49, arm);
            b.box(BONE, s * .75, .38, .67, .27, .29, .51, 13, s * 13, s * -12, arm);
            b.box(IVORY, s * .90, .36, .98, .12, .16, .36, 11, s * -14, 0, arm);
            b.box(IVORY, s * .63, .30, .96, .11, .14, .29, 11, s * 22, 0, arm);
            b.box(SLATE, s * .55, .56, -.23, .24, .73, .31, -21, 0, s * 57, leg);
            b.box(BONE, s * .83, .26, -.24, .14, .49, .18, -12, 0, s * -24, leg);
            b.box(OBSIDIAN, s * .48, .56, -.63, .26, .68, .29, -29, 0, s * 38, leg);
            b.box(BONE, s * .68, .25, -.81, .13, .45, .19, 22, 0, s * -18, leg);
        }
        return b.build();
    }

    private static HollowModel vastoLorde() {
        Builder b = new Builder(3.24f);
        // A narrow, split-jawed revenant with scythe arms and a swept-back crown.
        b.box(SHADE, 0, 1.55, -.07, .58, .92, .5, -14, 0, 0);
        b.box(OBSIDIAN, 0, 2.08, -.09, .78, .68, .56, -14, 0, 0);
        b.box(SLATE, 0, 1.20, -.1, .41, .43, .45, 0, 0, 0);
        b.box(SCAR, 0, 2.10, .207, .35, .37, .16, 0, 0, 45);
        b.box(SHADE, 0, 2.105, .30, .27, .27, .08, 0, 0, 45);
        b.box(SHADE, 0, 2.61, .11, .53, .51, .55, -13, 0, 0);
        b.box(BONE, 0, 2.74, .367, .47, .23, .16, -12, 0, 0);
        b.box(IVORY, 0, 2.54, .45, .11, .32, .18, -28, 0, 0);
        b.box(SCAR, 0, 2.54, .44, .29, .16, .07, -12, 0, 0);
        b.box(OBSIDIAN, 0, 1.42, -.52, .20, .32, .8, -14, 0, 0, Joint.TAIL);
        b.box(BONE, 0, 1.30, -.96, .14, .17, .45, 17, 0, 0, Joint.TAIL);
        for (int s : new int[] {-1, 1}) {
            Joint arm = s < 0 ? Joint.LEFT_ARM : Joint.RIGHT_ARM;
            Joint leg = s < 0 ? Joint.LEFT_LEG : Joint.RIGHT_LEG;
            b.box(IVORY, s * .28, 2.70, .367, .20, .37, .21, -22, s * 12, s * -26);
            b.box(BLUE_EYE, s * .153, 2.66, .474, .116, .052, .06, -12, s * 11, s * -22);
            b.box(BONE, s * .20, 2.48, .44, .13, .26, .16, 9, 0, s * 16);
            b.box(BONE, s * .34, 2.98, .15, .20, .49, .27, -34, 0, s * 32);
            b.box(IVORY, s * .49, 3.17, -.075, .12, .44, .16, -53, 0, s * 42);
            b.box(BONE, s * .64, 3.26, -.24, .072, .29, .11, -67, 0, s * 52);
            b.box(OBSIDIAN, s * .47, 1.88, -.04, .27, .78, .28, -13, 0, s * 27, arm);
            b.box(IVORY, s * .60, 2.22, -.16, .54, .24, .46, 21, 0, s * 38, arm);
            b.box(SLATE, s * .65, 1.47, .12, .24, .55, .26, -39, 0, s * 6, arm);
            b.box(BONE, s * .75, 1.31, .22, .16, .77, .34, -39, s * 14, s * 12, arm);
            b.box(IVORY, s * .82, 1.0, .47, .11, .53, .29, -49, s * 11, s * 18, arm);
            b.box(IVORY, s * .87, .81, .65, .063, .35, .18, -64, 0, s * 21, arm);
            b.box(BONE, s * .36, 1.83, .17, .13, .54, .19, -14, 0, s * 30);
            b.box(BONE, s * .24, 1.55, .18, .10, .40, .18, 8, 0, s * 50);
            b.box(SLATE, s * .25, .93, .02, .23, .66, .29, -32, 0, s * 16, leg);
            b.box(IVORY, s * .32, .67, .17, .23, .31, .26, -24, 0, s * 8, leg);
            b.box(OBSIDIAN, s * .35, .39, -.06, .18, .55, .23, 37, 0, s * -8, leg);
            b.box(BONE, s * .36, .17, .11, .21, .22, .42, -19, 0, s * 5, leg);
        }
        return b.build();
    }

    enum Joint {
        BODY(0, 0, 0), LEFT_ARM(-.47f, 1.65f, .15f), RIGHT_ARM(.47f, 1.65f, .15f),
        LEFT_LEG(-.22f, 1.20f, 0), RIGHT_LEG(.22f, 1.20f, 0), TAIL(0, 1.13f, -.35f);

        private final Vector3f pivot;

        Joint(float x, float y, float z) {
            pivot = new Vector3f(x, y, z);
        }
    }

    record Piece(Material material, Vector3f center, Vector3f size, Quaternionf rotation, Joint joint) {
        Transformation pose(float scale, float lift, float swing) {
            Quaternionf jointRotation = new Quaternionf();
            if (joint == Joint.TAIL) {
                jointRotation.rotateY(swing);
            } else {
                jointRotation.rotateX(swing);
            }
            Vector3f corner = new Vector3f(size).mul(-.5f).rotate(rotation).add(center)
                    .sub(joint.pivot).rotate(jointRotation).add(joint.pivot).mul(scale).add(0, lift, 0);
            return new Transformation(corner, jointRotation.mul(rotation), new Vector3f(size).mul(scale),
                    new Quaternionf());
        }

        boolean luminous() {
            return material == RED_EYE || material == GOLD_EYE || material == BLUE_EYE;
        }
    }

    private static final class Builder {
        private final float height;
        private final List<Piece> pieces = new ArrayList<>();

        private Builder(float height) {
            this.height = height;
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
                    new Vector3f((float) w, (float) h, (float) d), rotation, joint));
        }

        private HollowModel build() {
            return new HollowModel(height, List.copyOf(pieces));
        }
    }
}
