package appeng.util.calculators;

import com.gtnewhorizon.gtnhlib.util.parsing.MathExpressionParser;
import com.gtnewhorizon.gtnhlib.util.parsing.MathExpressionParser.Context;

public class Calculator {

    private static final MathExpressionParser.Context ctx = new Context().setEmptyValue(0).setErrorValue(Double.NaN);

    public static double conversion(String expression) {
        double result = MathExpressionParser.parse(expression, ctx);
        if (ctx.wasSuccessful()) {
            return result;
        } else {
            return Double.NaN;
        }
    }
}
