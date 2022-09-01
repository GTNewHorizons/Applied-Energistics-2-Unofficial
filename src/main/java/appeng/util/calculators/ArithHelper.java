package appeng.util.calculators;

public class ArithHelper {

    private static final int DEF_DIV_SCALE = 16;

    private ArithHelper() {}

    /**
     * addition
     *
     * @param v1 p1
     * @param v2 p2
     * @return sum
     */
    public static String add(String v1, String v2) {
        java.math.BigDecimal b1 = new java.math.BigDecimal(v1);
        java.math.BigDecimal b2 = new java.math.BigDecimal(v2);
        return String.valueOf(b1.add(b2));
    }

    /**
     * subtraction
     *
     * @param v1 p1
     * @param v2 p2
     * @return sub
     */
    public static String sub(String v1, String v2) {
        java.math.BigDecimal b1 = new java.math.BigDecimal(v1);
        java.math.BigDecimal b2 = new java.math.BigDecimal(v2);
        return String.valueOf(b1.subtract(b2));
    }

    /**
     * multiplication
     *
     * @param v1
     *            p1
     * @param v2
     *            p2
     * @return mul
     */
    public static String mul(String v1, String v2) {
        java.math.BigDecimal b1 = new java.math.BigDecimal(v1);
        java.math.BigDecimal b2 = new java.math.BigDecimal(v2);
        return String.valueOf(b1.multiply(b2));
    }

    /**
     * division. e = 10^-10
     *
     * @param v1
     *            p1
     * @param v2
     *            p2
     * @return div
     */
    public static String div(String v1, String v2) {
        java.math.BigDecimal b1 = new java.math.BigDecimal(v1);
        java.math.BigDecimal b2 = new java.math.BigDecimal(v2);
        return String.valueOf(b1.divide(b2, DEF_DIV_SCALE, java.math.BigDecimal.ROUND_HALF_UP));
    }

    /**
     * rounding
     *
     * @param v p
     * @param scale scale
     * @return result
     */
    public static double round(double v, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        java.math.BigDecimal b = new java.math.BigDecimal(Double.toString(v));
        java.math.BigDecimal one = new java.math.BigDecimal("1");
        return b.divide(one, scale, java.math.BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static String round(String v, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        java.math.BigDecimal b = new java.math.BigDecimal(v);
        java.math.BigDecimal one = new java.math.BigDecimal("1");
        return String.valueOf(b.divide(one, scale, java.math.BigDecimal.ROUND_HALF_UP));
    }
}
