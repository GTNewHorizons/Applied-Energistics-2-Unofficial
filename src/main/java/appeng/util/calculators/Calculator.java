package appeng.util.calculators;

import java.util.Collections;
import java.util.Stack;

import com.gtnewhorizon.gtnhlib.util.parsing.MathExpressionParser;
import com.gtnewhorizon.gtnhlib.util.parsing.MathExpressionParser.Context;

import cpw.mods.fml.common.Loader;

public class Calculator {

    private final Stack<String> postfixStack = new Stack<>();
    private final Stack<Character> opStack = new Stack<>();
    private final int[] operatPriority = new int[] { 0, 3, 2, 1, -1, 1, 0, 2 };
    private static final boolean isGTNHLibLoaded = Loader.isModLoaded("gtnhlib");
    private static final MathExpressionParser.Context ctx = isGTNHLibLoaded
            ? new Context().setEmptyValue(0).setErrorValue(Double.NaN)
            : null;

    public static double conversion(String expression) {
        if (isGTNHLibLoaded) {
            double result = MathExpressionParser.parse(expression, ctx);
            if (ctx.wasSuccessful()) return result;
        }

        double result = 0;

        if (expression == null) return Double.NaN;

        expression = expression.replace(" ", "");

        Calculator cal = new Calculator();
        if (expression.length() == 1 && Character.isDigit(expression.charAt(0))) {
            return expression.charAt(0) - '0';
        }
        try {
            expression = transform(expression);
            result = cal.calculate(expression);
        } catch (Exception e) {
            return Double.NaN;
        }
        // return new String().valueOf(result);
        return result;
    }

    /**
     * replace '-' with '~'
     *
     * @param expression e.g.-2+-1*(-3E-2)-(-1) -> ~2+~1*(~3E~2)-(~1)
     * @return
     */
    private static String transform(String expression) {
        char[] arr = expression.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == '-') {
                if (i == 0) {
                    arr[i] = '~';
                } else {
                    char c = arr[i - 1];
                    if (c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == 'E' || c == 'e') {
                        arr[i] = '~';
                    }
                }
            }
        }
        if (arr[0] == '~' || arr[1] == '(') {
            arr[0] = '-';
            return "0" + new String(arr);
        } else {
            return new String(arr);
        }
    }

    /**
     * Do calculation
     *
     * @param expression e.g.5+12*(3+5)/7
     * @return
     */
    public double calculate(String expression) {
        Stack<String> resultStack = new Stack<>();
        prepare(expression);
        Collections.reverse(postfixStack);
        String firstValue, secondValue, currentValue;
        while (!postfixStack.isEmpty()) {
            currentValue = postfixStack.pop();
            if (!isOperator(currentValue.charAt(0))) {
                currentValue = currentValue.replace("~", "-");
                resultStack.push(currentValue);
            } else {
                secondValue = resultStack.pop();
                firstValue = resultStack.pop();

                firstValue = firstValue.replace("~", "-");
                secondValue = secondValue.replace("~", "-");

                String tempResult = calculate(firstValue, secondValue, currentValue.charAt(0));
                resultStack.push(String.valueOf(tempResult));
            }
        }
        return Double.parseDouble(resultStack.pop());
    }

    /**
     * Turn expression into postfix stack
     *
     * @param expression
     */
    private void prepare(String expression) {
        opStack.push(',');
        char[] arr = expression.toCharArray();
        int currentIndex = 0;
        int count = 0;
        char currentOp, peekOp;
        for (int i = 0; i < arr.length; i++) {
            currentOp = arr[i];
            if (isOperator(currentOp)) {
                if (count > 0) {
                    postfixStack.push(new String(arr, currentIndex, count));
                }
                peekOp = opStack.peek();
                if (currentOp == ')') {
                    while (opStack.peek() != '(') {
                        postfixStack.push(String.valueOf(opStack.pop()));
                    }
                    opStack.pop();
                } else {
                    while (currentOp != '(' && peekOp != ',' && compare(currentOp, peekOp)) {
                        postfixStack.push(String.valueOf(opStack.pop()));
                        peekOp = opStack.peek();
                    }
                    opStack.push(currentOp);
                }
                count = 0;
                currentIndex = i + 1;
            } else {
                count++;
            }
        }
        if (count > 1 || (count == 1 && !isOperator(arr[currentIndex]))) {
            postfixStack.push(new String(arr, currentIndex, count));
        }

        while (opStack.peek() != ',') {
            postfixStack.push(String.valueOf(opStack.pop()));
        }
    }

    /**
     * Check is it a valid operator
     *
     * @param c
     * @return
     */
    public static boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')';
    }

    /**
     * priority
     *
     * @param cur
     * @param peek
     * @return
     */
    public boolean compare(char cur, char peek) {
        boolean result = false;
        if (operatPriority[(peek) - 40] >= operatPriority[(cur) - 40]) {
            result = true;
        }
        return result;
    }

    /**
     * Do calculator with operater
     *
     * @param firstValue
     * @param secondValue
     * @param currentOp
     * @return
     */
    private String calculate(String firstValue, String secondValue, char currentOp) {
        String result = switch (currentOp) {
            case '+' -> ArithHelper.add(firstValue, secondValue);
            case '-' -> ArithHelper.sub(firstValue, secondValue);
            case '*' -> ArithHelper.mul(firstValue, secondValue);
            case '/' -> ArithHelper.div(firstValue, secondValue);
            default -> "";
        };
        return result;
    }
}
