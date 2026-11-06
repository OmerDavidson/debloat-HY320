package com.vibehome.launcher;

/** Pure geometry, independent of device APIs. Values are inward offsets per thousand. */
public final class ProjectionGeometry {
    private ProjectionGeometry() { }

    // Display transaction order: bottom-left, top-left, top-right, bottom-right; x,y per corner.
    public static int[] parse(String raw) {
        String[] fields = raw.split(",", -1);
        if (fields.length != 8) throw new IllegalArgumentException("Eight corner values required");
        int[] result = new int[8];
        for (int i = 0; i < result.length; i++) result[i] = Integer.parseInt(fields[i].trim());
        validate(result);
        return result;
    }

    public static void validate(int[] insets) {
        if (insets == null || insets.length != 8) throw new IllegalArgumentException("Eight corner values required");
        for (int value : insets) if (value < 0 || value > 450)
            throw new IllegalArgumentException("Keep each corner inset between 0 and 450");
        int[][] p = points(insets);
        for (int i = 0; i < 4; i++) {
            int[] a = p[i], b = p[(i + 1) % 4], c = p[(i + 2) % 4];
            long cross = (long) (b[0] - a[0]) * (c[1] - b[1]) - (long) (b[1] - a[1]) * (c[0] - b[0]);
            if (cross <= 0) throw new IllegalArgumentException("Corners must form a non-crossing picture");
        }
    }

    public static int[][] points(int[] v) {
        return new int[][] {{v[2], v[3]}, {1000-v[4], v[5]}, {1000-v[6], 1000-v[7]}, {v[0], 1000-v[1]}};
    }

    /** Scale around the center without squaring away the current corner correction. */
    public static int[] scale(int[] baseline, int percent) {
        validate(baseline);
        if (percent < 80 || percent > 120) throw new IllegalArgumentException("Size range is 80–120% of current");
        int[] result = new int[8];
        for (int i = 0; i < result.length; i++) result[i] = Math.round(500 + (baseline[i] - 500) * percent / 100f);
        validate(result);
        return result;
    }

    public static String encode(int[] values) {
        validate(values);
        StringBuilder result = new StringBuilder();
        for (int value : values) { if (result.length() > 0) result.append(','); result.append(value); }
        return result.toString();
    }
}
