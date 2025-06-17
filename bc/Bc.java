package bc;

import bc.Bc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Bc {
    private static final String EXIT_COMMAND_1 = "quit";
    private static final String EXIT_COMMAND_2 = "exit";
    private static final int DEFAULT_SCALE = 2;
    private final Map<String, Integer> operatorPrecedence;
    private Scanner scanner;

    public Bc() {
        operatorPrecedence = new HashMap<>();
        operatorPrecedence.put("+", 1);
        operatorPrecedence.put("-", 1);
        operatorPrecedence.put("*", 2);
        operatorPrecedence.put("/", 2);
        operatorPrecedence.put("%", 2);
        operatorPrecedence.put("^", 3);
    }

    public void interactiveMode() {
        scanner = new Scanner(System.in);
        System.out.println("Enter expressions to evaluate or type 'quit' or 'exit' to terminate.");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase(EXIT_COMMAND_1) || input.equalsIgnoreCase(EXIT_COMMAND_2)) {
                System.out.println("Exiting");
                break;
            }

            if (input.isEmpty()) {
                System.out.println("No input provided. Please enter a valid expression.");
                continue;
            }

            try {
                double result = evaluateExpression(input);
                System.out.println("Result: " + formatResult(result, DEFAULT_SCALE));
            } catch (ArithmeticException e) {
                System.out.println("Error: Division by zero.");
            } catch (Exception e) {
                System.out.println("Error: Invalid expression.");
            }
        }
    }

    public double exponentiate(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    public List<String> parseExpression(String expression) {
        List<String> tokens = new ArrayList<>();
        StringBuilder numberBuffer = new StringBuilder();

        for (char ch : expression.toCharArray()) {
            if (Character.isDigit(ch) || ch == '.') {
                numberBuffer.append(ch);
            } else if (Character.isWhitespace(ch)) {
                continue;
            } else {
                if (numberBuffer.length() > 0) {
                    tokens.add(numberBuffer.toString());
                    numberBuffer.setLength(0);
                }
                tokens.add(String.valueOf(ch));
            }
        }

        if (numberBuffer.length() > 0) {
            tokens.add(numberBuffer.toString());
        }

        return tokens;
    }

    public int modulo(int x, int y) {
        if (y == 0) {
            throw new ArithmeticException("Division by zero is not allowed.");
        }
        return x % y;
    }

    public double add(double x, double y) {
        return x + y;
    }

    public double multiply(double x, double y) {
        return x * y;
    }

    public List<String> applyOperatorPrecedence(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Stack<String> operators = new Stack<>();

        for (String token : tokens) {
            if (isNumeric(token)) {
                output.add(token);
            } else if (operatorPrecedence.containsKey(token)) {
                while (!operators.isEmpty() && operatorPrecedence.get(operators.peek()) >= operatorPrecedence.get(token)) {
                    output.add(operators.pop());
                }
                operators.push(token);
            }
        }

        while (!operators.isEmpty()) {
            output.add(operators.pop());
        }

        return output;
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public double divide(double x, double y) {
        if (y == 0) {
            throw new ArithmeticException("Division by zero is not allowed.");
        }
        return x / y;
    }

    public double evaluateExpression(String expression) {
        expression = expression.trim();

        if (expression.isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be empty");
        }

        List<String> tokens = parseExpression(expression);
        List<String> orderedTokens = applyOperatorPrecedence(tokens);

        Stack<Double> values = new Stack<>();
        Stack<String> operators = new Stack<>();

        for (String token : orderedTokens) {
            if (isNumeric(token)) {
                values.push(Double.parseDouble(token));
            } else if (isOperator(token)) {
                while (!operators.isEmpty() && hasPrecedence(token, operators.peek())) {
                    String op = operators.pop();
                    double b = values.pop();
                    double a = values.pop();
                    values.push(applyOperator(op, a, b));
                }
                operators.push(token);
            }
        }

        while (!operators.isEmpty()) {
            String op = operators.pop();
            double b = values.pop();
            double a = values.pop();
            values.push(applyOperator(op, a, b));
        }

        return values.pop();
    }

    private boolean isOperator(String token) {
        return operatorPrecedence.containsKey(token);
    }

    private boolean hasPrecedence(String op1, String op2) {
        return operatorPrecedence.get(op1) <= operatorPrecedence.get(op2);
    }

    private double applyOperator(String operator, double a, double b) {
        switch (operator) {
            case "+":
                return add(a, b);
            case "-":
                return subtract(a, b);
            case "*":
                return multiply(a, b);
            case "/":
                if (b == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return divide(a, b);
            case "%":
                return modulo((int) a, (int) b);
            case "^":
                return exponentiate(a, b);
            default:
                throw new UnsupportedOperationException("Unsupported operator: " + operator);
        }
    }

    public double subtract(double x, double y) {
        return x - y;
    }

    public String formatResult(double result, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("Scale must be non-negative");
        }
        BigDecimal bigDecimalResult = BigDecimal.valueOf(result);
        bigDecimalResult = bigDecimalResult.setScale(scale, RoundingMode.HALF_UP);
        return bigDecimalResult.toPlainString();
    }
}