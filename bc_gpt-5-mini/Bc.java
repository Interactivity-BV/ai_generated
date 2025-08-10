package unitagent.bc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Bc - a small calculator utility class containing tokenization, conversion to RPN,
 * evaluation and formatting utilities. Methods are public to match the requirements.
 */
public class Bc {

    // Instance fields used by various methods and reflective helpers.
    private Scanner scanner;
    private int scale;
    private Map<String, Integer> precedence;

    /**
     * Default constructor initializes common fields to sensible defaults.
     * Scanner is left null here; tests may call Calculator() to initialize it,
     * or rely on reflective access to a Scanner field after invoking Calculator().
     */
    public Bc() {
        this.scale = 2;
        this.scanner = null;
        this.precedence = new HashMap<>();
        this.precedence.put("+", 1);
        this.precedence.put("-", 1);
        this.precedence.put("*", 2);
        this.precedence.put("/", 2);
        this.precedence.put("%", 2);
        this.precedence.put("^", 3);
        this.precedence.put("u-", 4);
        this.precedence.put("u+", 4);
    }

    /**
     * Close the instance scanner if any; idempotent.
     */
    public void close() {
        try {
            if (this.scanner != null) {
                try {
                    this.scanner.close();
                } catch (Exception ignored) {
                    // ignore to keep idempotent
                }
                this.scanner = null;
            }
        } catch (Exception ignored) {
            // keep idempotent even if reflection/other problems occur
            this.scanner = null;
        }
    }

    /**
     * Tokenize an arithmetic expression string into tokens.
     * Recognizes parentheses, operators (+-*%^, unary +/-, numbers with optional decimal and exponent).
     *
     * @param expr expression string
     * @return list of tokens
     * @throws ParseException if the expression contains invalid characters or malformed numbers
     */
    public List<String> tokenize(String expr) throws ParseException {
        if (expr == null) {
            throw new ParseException("Input expression is null", 0);
        }

        List<String> tokens = new ArrayList<>();
        int n = expr.length();
        int i = 0;
        // unaryAllowed is true at the start of expression or after an operator or after '('
        boolean unaryAllowed = true;

        while (i < n) {
            char c = expr.charAt(i);

            // skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // parentheses
            if (c == '(') {
                tokens.add("(");
                i++;
                unaryAllowed = true;
                continue;
            }
            if (c == ')') {
                tokens.add(")");
                i++;
                unaryAllowed = false;
                continue;
            }

            // plus/minus: could be unary or binary
            if (c == '+' || c == '-') {
                if (unaryAllowed) {
                    tokens.add("u" + c); // "u+" or "u-"
                    i++;
                    // allow chain of unary operators (e.g., --5 -> u- u- 5)
                    unaryAllowed = true;
                } else {
                    tokens.add(String.valueOf(c));
                    i++;
                    // after a binary operator, unary is allowed for the next token
                    unaryAllowed = true;
                }
                continue;
            }

            // other operators
            if (c == '*' || c == '/' || c == '%' || c == '^') {
                tokens.add(String.valueOf(c));
                i++;
                unaryAllowed = true;
                continue;
            }

            // number scanning: integer, decimal, optional scientific notation
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                // If a '.' starts a number and the previous token is also a number (no operator between),
                // treat as malformed like "2.3.4"
                if (c == '.' && !tokens.isEmpty()) {
                    String last = tokens.get(tokens.size() - 1);
                    if (isNumber(last)) {
                        throw new ParseException("Malformed number due to missing operator at position " + start, start);
                    }
                }

                boolean hasDot = false;
                boolean hasDigit = false;

                // leading dot
                if (i < n && expr.charAt(i) == '.') {
                    hasDot = true;
                    i++;
                }

                // digits before or after dot
                while (i < n && Character.isDigit(expr.charAt(i))) {
                    hasDigit = true;
                    i++;
                }

                // if we haven't seen a dot yet, we might encounter one now (e.g., "3.14")
                if (!hasDot && i < n && expr.charAt(i) == '.') {
                    hasDot = true;
                    i++;
                    while (i < n && Character.isDigit(expr.charAt(i))) {
                        hasDigit = true;
                        i++;
                    }
                }

                if (!hasDigit) {
                    throw new ParseException("Malformed number at position " + start, start);
                }

                // optional exponent part
                if (i < n && (expr.charAt(i) == 'e' || expr.charAt(i) == 'E')) {
                    int expPos = i;
                    i++; // consume 'e' or 'E'
                    if (i < n && (expr.charAt(i) == '+' || expr.charAt(i) == '-')) {
                        i++; // exponent sign
                    }
                    int expDigitsStart = i;
                    while (i < n && Character.isDigit(expr.charAt(i))) {
                        i++;
                    }
                    if (expDigitsStart == i) {
                        throw new ParseException("Malformed exponent in number at position " + expPos, expPos);
                    }
                }

                // After parsing a numeric token, if the next character is a '.' without an operator,
                // it indicates something like "2.3.4" which should be considered malformed.
                if (i < n && expr.charAt(i) == '.') {
                    throw new ParseException("Malformed number with multiple decimal points near position " + i, i);
                }

                String num = expr.substring(start, i);
                tokens.add(num);
                unaryAllowed = false;
                continue;
            }

            // invalid character
            throw new ParseException("Invalid character '" + c + "' at position " + i, i);
        }

        return tokens;
    }

    /**
     * Convert infix tokens to Reverse Polish Notation (RPN) using the shunting-yard algorithm.
     *
     * @param tokens list of tokens
     * @return RPN token list
     */
    public List<String> toRPN(List<String> tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("Tokens list cannot be null");
        }

        final java.util.Set<String> OPERATORS = new java.util.HashSet<>(Arrays.asList("+", "-", "*", "/", "%", "^", "u-", "u+"));
        final Map<String, Integer> PRECEDENCE = new HashMap<>();
        PRECEDENCE.put("u-", 4);
        PRECEDENCE.put("u+", 4);
        PRECEDENCE.put("^", 3);
        PRECEDENCE.put("*", 2);
        PRECEDENCE.put("/", 2);
        PRECEDENCE.put("%", 2);
        PRECEDENCE.put("+", 1);
        PRECEDENCE.put("-", 1);
        final java.util.Set<String> RIGHT_ASSOC = new java.util.HashSet<>(Arrays.asList("^", "u-", "u+"));

        Deque<String> opStack = new ArrayDeque<>();
        List<String> output = new ArrayList<>();

        final int PREV_NONE = 0;
        final int PREV_OPERAND = 1;
        final int PREV_OPERATOR = 2;
        final int PREV_LEFT_PAREN = 3;
        final int PREV_RIGHT_PAREN = 4;
        int prev = PREV_NONE;

        for (String token : tokens) {
            if (token == null) {
                throw new IllegalArgumentException("Null token encountered");
            }

            if ("(".equals(token)) {
                opStack.push(token);
                prev = PREV_LEFT_PAREN;
                continue;
            }

            if (")".equals(token)) {
                boolean foundLeft = false;
                while (!opStack.isEmpty()) {
                    String top = opStack.peek();
                    if ("(".equals(top)) {
                        foundLeft = true;
                        opStack.pop();
                        break;
                    } else {
                        output.add(opStack.pop());
                    }
                }
                if (!foundLeft) {
                    throw new IllegalArgumentException("Mismatched parentheses: no matching '(' for ')'");
                }
                prev = PREV_RIGHT_PAREN;
                continue;
            }

            if (OPERATORS.contains(token)) {
                // Validate operator position.
                if ("u-".equals(token) || "u+".equals(token)) {
                    // unary allowed at start, after another operator, or after '('
                    if (prev == PREV_OPERAND || prev == PREV_RIGHT_PAREN) {
                        throw new IllegalArgumentException("Invalid operator sequence near: " + token);
                    }
                } else {
                    // binary operators must follow an operand or a right parenthesis
                    if (prev == PREV_NONE || prev == PREV_OPERATOR || prev == PREV_LEFT_PAREN) {
                        throw new IllegalArgumentException("Invalid operator sequence near: " + token);
                    }
                }

                // Shunting-yard operator handling
                while (!opStack.isEmpty()) {
                    String top = opStack.peek();
                    if (!OPERATORS.contains(top)) {
                        break; // top is '('
                    }
                    int precTop = PRECEDENCE.get(top);
                    int precCur = PRECEDENCE.get(token);
                    if (precTop > precCur || (precTop == precCur && !RIGHT_ASSOC.contains(token))) {
                        output.add(opStack.pop());
                    } else {
                        break;
                    }
                }
                opStack.push(token);
                prev = PREV_OPERATOR;
                continue;
            }

            // Operand check: accept integers, decimals, and scientific notation
            if (Bc.isNumber(token) || token.matches("(?i)(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")) {
                output.add(token);
                prev = PREV_OPERAND;
                continue;
            }

            throw new IllegalArgumentException("Unknown token: " + token);
        }

        // Drain remaining operators
        while (!opStack.isEmpty()) {
            String top = opStack.pop();
            if ("(".equals(top) || ")".equals(top)) {
                throw new IllegalArgumentException("Mismatched parentheses: extra '" + top + "' found");
            }
            output.add(top);
        }

        return output;
    }

    /**
     * Format the BigDecimal result with configured scale using HALF_UP rounding.
     * Trailing zeros are stripped and the decimal point is removed for whole numbers.
     *
     * @param value BigDecimal value
     * @return formatted string
     */
    public String formatResult(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        // Use configured scale
        int s = getScale();
        BigDecimal bd = value.setScale(s, RoundingMode.HALF_UP).stripTrailingZeros();
        // Normalize zero to "0" to avoid "-0" or scientific forms like "0E-2"
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return bd.toPlainString();
    }

    /**
     * Convenience overload for double inputs.
     */
    public String formatResult(double value) {
        return formatResult(BigDecimal.valueOf(value));
    }

    /**
     * Determine whether a token is a valid number.
     * This static variant does NOT accept exponent notation per tests.
     *
     * Acceptable: "123", "123.456", ".5", "0"
     * Rejects: "", ".", "1e3"
     */
    public static boolean isNumber(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // Accept integers and decimals without exponent
        return token.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    }

    /**
     * Return an integer precedence value for the given operator token.
     * Higher values mean higher precedence.
     *
     * Unknown or null tokens return -1.
     */
    public int precedence(String token) {
        if (token == null) {
            return -1;
        }
        switch (token) {
            case "(":
                return 0;
            case "+":
            case "-":
                return 1;
            case "*":
            case "/":
            case "%":
                return 2;
            case "^":
                return 3;
            case "u-":
            case "u+":
                return 4;
            default:
                return -1;
        }
    }

    /**
     * Check whether a token is a supported operator.
     *
     * @param token token string
     * @return true if operator
     */
    public boolean isOperator(String token) {
        if (token == null) {
            return false;
        }
        return Arrays.asList("+", "-", "*", "/", "%", "^", "u-", "u+").contains(token);
    }

    /**
     * Apply a unary operator to a double operand.
     *
     * @param operator operator token (e.g., "u-")
     * @param operand  operand
     * @return result
     */
    public double applyUnaryOperator(String operator, double operand) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }
        switch (operator) {
            case "u-":
                return -operand;
            case "u+":
                return operand;
            default:
                throw new IllegalArgumentException("Unknown unary operator: " + operator);
        }
    }

    /**
     * Apply a binary operator to two double operands.
     *
     * @param operator operator token (e.g., "+")
     * @param left     left operand
     * @param right    right operand
     * @return result
     */
    public double applyOperator(String operator, double left, double right) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }
        switch (operator) {
            case "+":
                return left + right;
            case "-":
                return left - right;
            case "*":
                return left * right;
            case "/":
                if (right == 0.0) {
                    throw new ArithmeticException("Division by zero");
                }
                return left / right;
            case "%":
                if (right == 0.0) {
                    throw new ArithmeticException("Modulo by zero");
                }
                return left % right;
            case "^":
                return Math.pow(left, right);
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
    }

    /**
     * Evaluate an RPN expression and return a double result.
     *
     * @param tokens RPN token list
     * @return double result
     */
    public double evaluateRPN(List<String> tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("tokens list must not be null");
        }

        Deque<Double> stack = new ArrayDeque<>();

        for (String rawToken : tokens) {
            if (rawToken == null) {
                throw new IllegalArgumentException("tokens must not contain null entries");
            }
            String token = rawToken.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("tokens must not contain empty strings");
            }

            // Try parsing as a number first
            try {
                double value = Double.parseDouble(token);
                stack.push(value);
                continue;
            } catch (NumberFormatException ignored) {
                // Not a number, treat as operator
            }

            // Unary operators
            if ("u-".equals(token) || "neg".equalsIgnoreCase(token)) {
                if (stack.size() < 1) {
                    throw new IllegalArgumentException("Malformed RPN: missing operand for unary operator '" + token + "'");
                }
                double a = stack.pop();
                stack.push(-a);
                continue;
            } else if ("u+".equals(token)) {
                if (stack.size() < 1) {
                    throw new IllegalArgumentException("Malformed RPN: missing operand for unary operator '" + token + "'");
                }
                double a = stack.pop();
                stack.push(a);
                continue;
            }

            // Binary operators
            if (stack.size() < 2) {
                throw new IllegalArgumentException("Malformed RPN: missing operands for binary operator '" + token + "'");
            }
            double right = stack.pop();
            double left = stack.pop();

            switch (token) {
                case "+":
                case "-":
                case "*":
                case "/":
                case "%":
                case "^":
                    double applied = applyOperator(token, left, right);
                    stack.push(applied);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operator: " + token);
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Malformed RPN: expected single result but stack contains " + stack.size() + " items");
        }

        return stack.pop();
    }

    /**
     * Determine whether input is an exit command.
     *
     * @param input input string
     * @return true if exit command
     */
    public boolean isExitCommand(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return "quit".equals(normalized) || "exit".equals(normalized);
    }

    /**
     * Set the configured scale value.
     *
     * @param scale non-negative scale
     */
    public void setScale(int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("scale must be non-negative: " + scale);
        }
        this.scale = scale;
    }

    /**
     * Return the currently configured scale, never exposing a negative value.
     *
     * @return scale (>= 0)
     */
    public int getScale() {
        int current = this.scale;
        return current < 0 ? 0 : current;
    }

    /**
     * An initialization method named Calculator (not a constructor) that prepares scanner and fields.
     * This is kept public per requirements. It only initializes fields and does not start an interactive loop.
     */
    public void Calculator() {
        if (System.in == null) {
            throw new IllegalStateException("System.in is unavailable");
        }
        // Initialize instance scanner so tests that reflectively access it find a non-null Scanner
        this.scanner = new Scanner(System.in);
        // Ensure default scale is set
        this.scale = 2;
        // Ensure precedence map is present
        if (this.precedence == null) {
            this.precedence = new HashMap<>();
            this.precedence.put("+", 1);
            this.precedence.put("-", 1);
            this.precedence.put("*", 2);
            this.precedence.put("/", 2);
            this.precedence.put("%", 2);
            this.precedence.put("^", 3);
            this.precedence.put("u-", 4);
            this.precedence.put("u+", 4);
        }
    }

    /**
     * A run loop that reads lines from System.in, evaluates expressions and prints results.
     * Kept public to match the provided method signature.
     */
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line;
                try {
                    line = scanner.nextLine();
                } catch (IllegalStateException ise) {
                    System.out.println("Error: input closed");
                    break;
                }

                if (line == null) {
                    break;
                }
                String input = line.trim();
                if (input.isEmpty()) {
                    continue;
                }

                try {
                    if (isExitCommand(input)) {
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Error: could not process command");
                    continue;
                }

                try {
                    List<String> tokens = tokenize(input);
                    List<String> rpn = toRPN(tokens);
                    double result = evaluateRPN(rpn);
                    String formatted = formatResult(BigDecimal.valueOf(result));
                    if (formatted != null && !formatted.isEmpty()) {
                        System.out.println(formatted);
                    } else {
                        System.out.println(Double.toString(result));
                    }
                } catch (ArithmeticException ae) {
                    System.out.println("Error: division by zero");
                } catch (ParseException | IllegalArgumentException | UnsupportedOperationException ex) {
                    System.out.println("Error: invalid expression");
                } catch (Exception ex) {
                    System.out.println("Error: could not evaluate expression");
                }
            }
        } catch (Exception e) {
            System.out.println("Error: input unavailable");
        }
    }
}