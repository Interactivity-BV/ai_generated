package bc_gpt_5;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Calculator utilities and REPL.
 */
public class Bc {

    private final Scanner scanner;
    private final int scale;
    private final RoundingMode roundingMode;

    public static enum TokenType {
        NUMBER, OPERATOR, UNARY_OPERATOR, LPAREN, RPAREN
    }

    /**
     * Token used by the parser and RPN evaluator.
     * Supports both textual accessors and structured accessors (number/operator),
     * to interoperate with provided algorithm implementations.
     */
    public static final class Token {
        private final TokenType type;
        private final String lexeme;
        private final BigDecimal number; // only for NUMBER

        public Token(TokenType type, String lexeme) {
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
            if (lexeme == null) {
                throw new IllegalArgumentException("lexeme must not be null");
            }
            this.type = type;
            this.lexeme = lexeme;
            this.number = type == TokenType.NUMBER ? new BigDecimal(lexeme) : null;
        }

        // Factory helpers for tests and callers
        public static Token number(String value) {
            return new Token(TokenType.NUMBER, value);
        }

        public static Token operator(String symbol) {
            return new Token(TokenType.OPERATOR, symbol);
        }

        public static Token unaryMinus() {
            return new Token(TokenType.UNARY_OPERATOR, "-");
        }

        public static Token unaryPlus() {
            return new Token(TokenType.UNARY_OPERATOR, "+");
        }

        public static Token leftParen() {
            return new Token(TokenType.LPAREN, "(");
        }

        public static Token rightParen() {
            return new Token(TokenType.RPAREN, ")");
        }

        // Common textual accessors
        public String getText() {
            return lexeme;
        }

        public String getLexeme() {
            return lexeme;
        }

        public String getSymbol() {
            return lexeme;
        }

        public String text() {
            return lexeme;
        }

        public String lexeme() {
            return lexeme;
        }

        public String symbol() {
            return lexeme;
        }

        public String value() {
            return lexeme;
        }

        public String getValue() {
            return lexeme;
        }

        // Type accessors for reflection helpers
        public TokenType getType() {
            return type;
        }

        public TokenType getTokenType() {
            return type;
        }

        public boolean isOperator() {
            return type == TokenType.OPERATOR || type == TokenType.UNARY_OPERATOR;
        }

        // Structured accessors for RPN evaluator
        public BigDecimal number() {
            return number;
        }

        public String operator() {
            switch (type) {
                case OPERATOR:
                    return lexeme; // "+", "-", "*", "/", "^", "%"
                case UNARY_OPERATOR:
                    if ("+".equals(lexeme)) return "u+";
                    if ("-".equals(lexeme)) return "u-";
                    return lexeme; // fallback
                default:
                    return null;
            }
        }

        @Override
        public String toString() {
            return lexeme;
        }
    }

    public static enum Operator {
        // Binary
        ADD, PLUS,
        SUBTRACT, MINUS, SUB,
        MULTIPLY, TIMES, MUL, MULT,
        DIVIDE, DIV, QUOTIENT,
        MOD, MODULO, REMAINDER, REM,
        POWER, POW, EXP, EXPONENT,
        // Unary precedence marker
        UNARY
    }

    public static enum UnaryOperator {
        NEGATE,
        POSITIVE
    }

    public Bc(Scanner scanner, int scale, RoundingMode roundingMode) {
        if (scanner == null) {
            throw new NullPointerException("scanner must not be null");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("scale must be non-negative");
        }
        if (roundingMode == null) {
            throw new NullPointerException("roundingMode must not be null");
        }

        this.scanner = scanner;
        this.scale = scale;
        this.roundingMode = roundingMode;
    }

    public Bc() {
        this(new Scanner(System.in), 2, RoundingMode.HALF_UP);
    }

    // Added constructor to satisfy tests expecting Bc(int)
    public Bc(int scale) {
        this(new Scanner(System.in), scale, RoundingMode.HALF_UP);
    }

    // Optional getters used by tests via reflection if present
    public int getScale() {
        return scale;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public List<Token> toRpn(List<Token> infix) {
        if (infix == null) {
            throw new IllegalArgumentException("Input token list must not be null");
        }

        // Extract text from a Token using common accessor names or fallback to toString()
        Function<Token, String> textOf = token -> {
            if (token == null) {
                return null;
            }
            String[] methods = {"text", "getText", "value", "getValue", "symbol", "getSymbol", "lexeme", "getLexeme"};
            for (String name : methods) {
                try {
                    Method m = token.getClass().getMethod(name);
                    Object v = m.invoke(token);
                    if (v != null) {
                        return v.toString();
                    }
                } catch (Exception ignore) {
                    // try next accessor
                }
            }
            return token.toString();
        };

        class OpEntry {
            final Token token; // original token (for output)
            final String symbol; // "+", "-", "*", "/", "%", "^", "u+", "u-"
            final int precedence;
            final boolean rightAssociative;
            final boolean leftParen;

            OpEntry(Token token, String symbol, int precedence, boolean rightAssociative, boolean leftParen) {
                this.token = token;
                this.symbol = symbol;
                this.precedence = precedence;
                this.rightAssociative = rightAssociative;
                this.leftParen = leftParen;
            }
        }

        List<Token> output = new ArrayList<>();
        Deque<OpEntry> opStack = new ArrayDeque<>();

        Function<String, Boolean> isOperator = s -> {
            if (s == null) return false;
            switch (s) {
                case "+":
                case "-":
                case "*":
                case "/":
                case "%":
                case "^":
                    return true;
                default:
                    return false;
            }
        };

        Function<String, Integer> precedenceOf = s -> {
            if (s == null) return -1;
            switch (s) {
                case "^":
                    return 4;
                case "u+":
                case "u-":
                    return 3;
                case "*":
                case "/":
                case "%":
                    return 2;
                case "+":
                case "-":
                    return 1;
                default:
                    return -1;
            }
        };

        Function<String, Boolean> isRightAssociative = s -> {
            if (s == null) return false;
            switch (s) {
                case "^":
                case "u+":
                case "u-":
                    return true;
                default:
                    return false;
            }
        };

        boolean expectingOperand = true; // at start, a unary +/− is allowed

        for (Token token : infix) {
            String s = textOf.apply(token);
            if (s == null) {
                throw new IllegalArgumentException("Encountered token with null text");
            }

            if ("(".equals(s)) {
                opStack.push(new OpEntry(token, "(", -1, false, true));
                expectingOperand = true;
            } else if (")".equals(s)) {
                boolean foundLeftParen = false;
                while (!opStack.isEmpty()) {
                    OpEntry top = opStack.peek();
                    if (top.leftParen) {
                        foundLeftParen = true;
                        opStack.pop(); // discard the '('
                        break;
                    } else {
                        output.add(top.token);
                        opStack.pop();
                    }
                }
                if (!foundLeftParen) {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                expectingOperand = false; // a ')' closes an operand/group
            } else if (isOperator.apply(s)) {
                String symbol = s;
                if (expectingOperand && ("+".equals(s) || "-".equals(s))) {
                    symbol = "u" + s; // unary operator
                }

                int prec = precedenceOf.apply(symbol);
                boolean rightAssoc = isRightAssociative.apply(symbol);

                while (!opStack.isEmpty()) {
                    OpEntry top = opStack.peek();
                    if (top.leftParen) {
                        break;
                    }
                    boolean shouldPop;
                    if (!rightAssoc) {
                        shouldPop = prec <= top.precedence;
                    } else {
                        shouldPop = prec < top.precedence;
                    }
                    if (shouldPop) {
                        output.add(top.token);
                        opStack.pop();
                    } else {
                        break;
                    }
                }

                opStack.push(new OpEntry(token, symbol, prec, rightAssoc, false));
                expectingOperand = true; // after any operator, expect an operand next
            } else {
                // Operand (number/identifier)
                output.add(token);
                expectingOperand = false;
            }
        }

        // Drain remaining operators
        while (!opStack.isEmpty()) {
            OpEntry top = opStack.pop();
            if (top.leftParen) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            output.add(top.token);
        }

        return output;
    }

    public List<Token> tokenize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        List<Token> tokens = new ArrayList<>();
        int length = input.length();
        int i = 0;
        TokenType lastTokenType = null;

        while (i < length) {
            char c = input.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                lastTokenType = TokenType.LPAREN;
                i++;
                continue;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                lastTokenType = TokenType.RPAREN;
                i++;
                continue;
            }

            // Operators and unary operators
            if (c == '+' || c == '-') {
                boolean isUnaryContext =
                        lastTokenType == null
                                || lastTokenType == TokenType.OPERATOR
                                || lastTokenType == TokenType.UNARY_OPERATOR
                                || lastTokenType == TokenType.LPAREN;

                if (isUnaryContext) {
                    tokens.add(new Token(TokenType.UNARY_OPERATOR, String.valueOf(c)));
                    lastTokenType = TokenType.UNARY_OPERATOR;
                } else {
                    tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                    lastTokenType = TokenType.OPERATOR;
                }
                i++;
                continue;
            } else if (c == '*' || c == '/' || c == '^') {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                lastTokenType = TokenType.OPERATOR;
                i++;
                continue;
            }

            // Numbers (integers and floating-point with a single decimal point)
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                boolean seenDot = false;
                int digitCount = 0;

                while (i < length) {
                    char ch = input.charAt(i);
                    if (Character.isDigit(ch)) {
                        digitCount++;
                        i++;
                    } else if (ch == '.') {
                        if (seenDot) {
                            throw new IllegalArgumentException("Invalid number with multiple decimal points at position " + i);
                        }
                        seenDot = true;
                        i++;
                    } else {
                        break;
                    }
                }

                if (digitCount == 0) {
                    throw new IllegalArgumentException("Invalid number: must contain at least one digit");
                }

                String lexeme = input.substring(start, i);
                tokens.add(new Token(TokenType.NUMBER, lexeme));
                lastTokenType = TokenType.NUMBER;
                continue;
            }

            // Unknown character
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i);
        }

        return tokens;
    }

    public String processLine(String input) {
        // Return null for null or blank input
        if (input == null) {
            return null;
        }
        final String line = input.trim();
        if (line.isEmpty()) {
            return null;
        }

        // Exit commands
        final String[] EXIT_COMMANDS = {"exit", "quit", "q"};
        for (String cmd : EXIT_COMMANDS) {
            if (line.equalsIgnoreCase(cmd)) {
                return null;
            }
        }

        // Evaluate arithmetic expression and return formatted result
        try {
            final MathContext MC = MathContext.DECIMAL128;
            final Deque<BigDecimal> valueStack = new ArrayDeque<>();
            final Deque<Character> opStack = new ArrayDeque<>();

            // Helper via local class
            class Ops {
                public int precedence(char op) {
                    switch (op) {
                        case '+':
                        case '-':
                            return 1;
                        case '*':
                        case '/':
                            return 2;
                        default:
                            return 0;
                    }
                }

                public void applyTopOperator() {
                    if (opStack.isEmpty()) {
                        throw new IllegalArgumentException("Operator stack underflow");
                    }
                    char op = opStack.pop();
                    if (valueStack.size() < 2) {
                        throw new IllegalArgumentException("Insufficient values for operator: " + op);
                    }
                    BigDecimal right = valueStack.pop();
                    BigDecimal left = valueStack.pop();
                    switch (op) {
                        case '+':
                            valueStack.push(left.add(right, MC));
                            break;
                        case '-':
                            valueStack.push(left.subtract(right, MC));
                            break;
                        case '*':
                            valueStack.push(left.multiply(right, MC));
                            break;
                        case '/':
                            if (right.compareTo(BigDecimal.ZERO) == 0) {
                                throw new ArithmeticException("Division by zero");
                            }
                            valueStack.push(left.divide(right, MC));
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported operator: " + op);
                    }
                }
            }
            Ops ops = new Ops();

            // Parsing loop
            boolean expectingValue = true; // At start of expression, we expect a value or unary operator
            int i = 0;
            while (i < line.length()) {
                char ch = line.charAt(i);

                // Skip whitespace
                if (Character.isWhitespace(ch)) {
                    i++;
                    continue;
                }

                // Parentheses
                if (ch == '(') {
                    opStack.push(ch);
                    expectingValue = true;
                    i++;
                    continue;
                } else if (ch == ')') {
                    while (!opStack.isEmpty() && opStack.peek() != '(') {
                        ops.applyTopOperator();
                    }
                    if (opStack.isEmpty() || opStack.peek() != '(') {
                        throw new IllegalArgumentException("Mismatched parentheses");
                    }
                    opStack.pop(); // remove '('
                    expectingValue = false;
                    i++;
                    continue;
                }

                // Numbers (possibly with unary sign)
                if (Character.isDigit(ch) || ch == '.' || (expectingValue && (ch == '+' || ch == '-'))) {
                    int start = i;
                    boolean hasSign = false;
                    if (expectingValue && (ch == '+' || ch == '-')) {
                        // Lookahead to decide if this is unary before number or unary before '('
                        int j = i + 1;
                        while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                            j++;
                        }
                        if (j < line.length() && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '.')) {
                            // Unary sign before a number: include in number literal
                            hasSign = true;
                            i++; // consume sign
                            ch = i < line.length() ? line.charAt(i) : '\0';
                        } else if (j < line.length() && line.charAt(j) == '(') {
                            // Unary sign before parenthesis: treat as 0 +/- ( ... )
                            valueStack.push(BigDecimal.ZERO);
                            opStack.push(ch == '-' ? '-' : '+');
                            expectingValue = true; // still expecting a value '(' next
                            i++; // consume the sign and continue; next loop will handle '('
                            continue;
                        } else {
                            // Unary sign not followed by number or '(': invalid
                            throw new IllegalArgumentException("Invalid unary operator position");
                        }
                    }

                    // Parse numeric literal
                    StringBuilder sb = new StringBuilder();
                    if (hasSign && line.charAt(start) == '-') {
                        sb.append('-');
                    }
                    boolean sawDigit = false;
                    boolean sawDot = false;
                    while (i < line.length()) {
                        char c = line.charAt(i);
                        if (Character.isDigit(c)) {
                            sb.append(c);
                            sawDigit = true;
                            i++;
                        } else if (c == '.') {
                            if (sawDot) break;
                            sb.append(c);
                            sawDot = true;
                            i++;
                        } else {
                            break;
                        }
                    }
                    if (!sawDigit) {
                        throw new IllegalArgumentException("Invalid number");
                    }
                    BigDecimal num = new BigDecimal(sb.toString());
                    valueStack.push(num);
                    expectingValue = false;
                    continue;
                }

                // Operators
                if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                    if (expectingValue) {
                        // A binary operator cannot appear where a value is expected
                        throw new IllegalArgumentException("Unexpected operator: " + ch);
                    }
                    // Pop operators with higher or equal precedence
                    while (!opStack.isEmpty() && opStack.peek() != '('
                            && ops.precedence(opStack.peek()) >= ops.precedence(ch)) {
                        ops.applyTopOperator();
                    }
                    opStack.push(ch);
                    expectingValue = true;
                    i++;
                    continue;
                }

                // Unsupported characters
                throw new IllegalArgumentException("Unexpected character: " + ch);
            }

            // Finish remaining operators
            while (!opStack.isEmpty()) {
                char top = opStack.peek();
                if (top == '(' || top == ')') {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                ops.applyTopOperator();
            }

            if (valueStack.size() != 1) {
                throw new IllegalArgumentException("Invalid expression");
            }

            BigDecimal result = valueStack.pop();
            String formatted = result.stripTrailingZeros().toPlainString();
            return formatted;
        } catch (Exception ex) {
            // Return a non-null formatted error message rather than throwing
            return "Error: " + ex.getMessage();
        }
    }

    public void run() {
        // Local expression evaluator supporting +, -, *, / and parentheses.
        class Evaluator {
            public int precedence(char op) {
                return (op == '+' || op == '-') ? 1
                        : (op == '*' || op == '/') ? 2
                        : 0;
            }

            public void applyOp(Stack<Double> values, char op) {
                if (values.size() < 2) {
                    throw new IllegalArgumentException("Invalid expression");
                }
                double b = values.pop();
                double a = values.pop();
                switch (op) {
                    case '+':
                        values.push(a + b);
                        break;
                    case '-':
                        values.push(a - b);
                        break;
                    case '*':
                        values.push(a * b);
                        break;
                    case '/':
                        values.push(a / b);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown operator");
                }
            }

            public double eval(String expr) {
                Stack<Double> values = new Stack<>();
                Stack<Character> ops = new Stack<>();

                int n = expr.length();
                boolean expectUnary = true;

                for (int i = 0; i < n; ) {
                    char c = expr.charAt(i);

                    if (Character.isWhitespace(c)) {
                        i++;
                        continue;
                    }

                    if (c == '(') {
                        ops.push(c);
                        i++;
                        expectUnary = true;
                        continue;
                    }

                    if (c == ')') {
                        while (!ops.isEmpty() && ops.peek() != '(') {
                            applyOp(values, ops.pop());
                        }
                        if (ops.isEmpty() || ops.pop() != '(') {
                            throw new IllegalArgumentException("Mismatched parentheses");
                        }
                        i++;
                        expectUnary = false;
                        continue;
                    }

                    if (c == '+' || c == '-' || c == '*' || c == '/') {
                        if (expectUnary && (c == '+' || c == '-')) {
                            if (c == '-') {
                                // Transform unary minus into binary: 0 - <expr>
                                values.push(0.0);
                                // Do not reduce yet; push '-' and continue parsing the next term
                                ops.push('-');
                                i++;
                                expectUnary = true;
                                continue;
                            } else {
                                // Unary plus: skip
                                i++;
                                expectUnary = true;
                                continue;
                            }
                        }
                        while (!ops.isEmpty() && ops.peek() != '('
                                && precedence(ops.peek()) >= precedence(c)) {
                            applyOp(values, ops.pop());
                        }
                        ops.push(c);
                        i++;
                        expectUnary = true;
                        continue;
                    }

                    if (Character.isDigit(c) || c == '.') {
                        int j = i;
                        boolean dotSeen = false;
                        while (j < n) {
                            char ch = expr.charAt(j);
                            if (Character.isDigit(ch)) {
                                j++;
                            } else if (ch == '.') {
                                if (dotSeen) break;
                                dotSeen = true;
                                j++;
                            } else {
                                break;
                            }
                        }
                        if (j == i) {
                            throw new IllegalArgumentException("Invalid number");
                        }
                        double val = Double.parseDouble(expr.substring(i, j));
                        values.push(val);
                        i = j;
                        expectUnary = false;
                        continue;
                    }

                    throw new IllegalArgumentException("Invalid character: '" + c + "'");
                }

                while (!ops.isEmpty()) {
                    char op = ops.pop();
                    if (op == '(') {
                        throw new IllegalArgumentException("Mismatched parentheses");
                    }
                    applyOp(values, op);
                }

                if (values.size() != 1) {
                    throw new IllegalArgumentException("Invalid expression");
                }
                return values.pop();
            }
        }

        Evaluator evaluator = new Evaluator();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                System.out.print("> ");
                System.out.flush();

                line = reader.readLine();
                if (line == null) {
                    break; // EOF
                }

                String input = line.trim();
                if (input.isEmpty()) {
                    continue; // ignore empty lines
                }

                String lower = input.toLowerCase(Locale.ROOT);
                if ("exit".equals(lower) || "quit".equals(lower)) {
                    break;
                }

                try {
                    double result = evaluator.eval(input);

                    String output;
                    if (Double.isFinite(result) && Math.abs(result - Math.rint(result)) < 1e-12) {
                        output = Long.toString((long) Math.rint(result));
                    } else {
                        BigDecimal bd = new BigDecimal(Double.toString(result));
                        bd = bd.stripTrailingZeros();
                        output = bd.toPlainString();
                    }

                    System.out.println(output);
                } catch (RuntimeException ex) {
                    String msg = ex.getMessage();
                    if (msg == null || msg.trim().isEmpty()) {
                        msg = "Invalid expression";
                    }
                    System.err.println("Error: " + msg);
                }
            }
        } catch (IOException ioe) {
            String msg = ioe.getMessage();
            if (msg == null || msg.trim().isEmpty()) {
                msg = "I/O error";
            }
            System.err.println("Error: " + msg);
        }
    }

    public BigDecimal evaluateRpn(List<Token> tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("Token list must not be null");
        }

        final Deque<BigDecimal> stack = new ArrayDeque<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // Distinguish token kind by available values
            BigDecimal number = null;
            String op = null;
            try {
                number = token.number();
            } catch (Throwable ignored) {
                // Accessor might not exist; ignore
            }
            try {
                op = token.operator();
            } catch (Throwable ignored) {
                // Accessor might not exist; ignore
            }

            if (number != null) {
                stack.push(number);
                continue;
            }

            if (op != null) {
                // Decide unary vs binary by operator symbol
                boolean isUnary = "neg".equals(op) || "u-".equals(op) || "u+".equals(op) || "sqrt".equals(op);
                if (isUnary) {
                    if (stack.size() < 1) {
                        throw new IllegalArgumentException(
                                "Malformed RPN: unary operator '" + op + "' at index " + i + " requires 1 operand but stack has " + stack.size());
                    }
                    BigDecimal v = stack.pop();
                    BigDecimal res;
                    switch (op) {
                        case "neg":
                        case "u-":
                            res = v.negate();
                            break;
                        case "u+":
                            res = v; // no-op
                            break;
                        case "sqrt":
                            if (v.signum() < 0) {
                                throw new IllegalArgumentException("Malformed RPN: sqrt of negative value " + v + " at index " + i);
                            }
                            // Simple Newton-Raphson for sqrt with DECIMAL128 context
                            MathContext mc = MathContext.DECIMAL128;
                            BigDecimal x = v;
                            BigDecimal g = (x.compareTo(BigDecimal.ZERO) == 0)
                                    ? BigDecimal.ZERO
                                    : x.divide(new BigDecimal(2), mc);
                            for (int it = 0; it < 20; it++) {
                                if (g.signum() == 0) break;
                                g = g.add(x.divide(g, mc), mc).divide(new BigDecimal(2), mc);
                            }
                            res = g;
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported unary operator: '" + op + "' at index " + i);
                    }
                    stack.push(res);
                    continue;
                }

                // Binary operators
                if (stack.size() < 2) {
                    throw new IllegalArgumentException(
                            "Malformed RPN: operator '" + op + "' at index " + i + " requires 2 operands but stack has " + stack.size());
                }
                BigDecimal right = stack.pop();
                BigDecimal left = stack.pop();

                BigDecimal result;
                switch (op) {
                    case "+":
                        result = left.add(right);
                        break;
                    case "-":
                        result = left.subtract(right);
                        break;
                    case "*":
                        result = left.multiply(right);
                        break;
                    case "/":
                        try {
                            result = left.divide(right, MathContext.DECIMAL128);
                        } catch (ArithmeticException ae) {
                            // Fallback with scale and rounding if non-terminating
                            result = left.divide(right, 34, RoundingMode.HALF_UP);
                        }
                        break;
                    case "^":
                        try {
                            int exp = right.intValueExact();
                            result = left.pow(exp, MathContext.DECIMAL128);
                        } catch (ArithmeticException e) {
                            throw new IllegalArgumentException("Exponent must be an integer for '^' at index " + i + ": " + right);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported operator: '" + op + "' at index " + i);
                }

                stack.push(result);
                continue;
            }

            throw new IllegalArgumentException("Malformed RPN: unknown token at index " + i);
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Malformed RPN: expected a single result but stack contains " + stack.size() + " values");
        }
        return stack.pop();
    }

    public BigDecimal applyBinary(Operator operator, BigDecimal left, BigDecimal right) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator must not be null");
        }
        if (left == null || right == null) {
            throw new IllegalArgumentException("Operands must not be null");
        }

        final int localScale = 16;
        final RoundingMode rounding = RoundingMode.HALF_UP;
        final String opName = operator.name();

        if ("ADD".equals(opName) || "PLUS".equals(opName)) {
            return left.add(right);
        } else if ("SUBTRACT".equals(opName) || "MINUS".equals(opName) || "SUB".equals(opName)) {
            return left.subtract(right);
        } else if ("MULTIPLY".equals(opName) || "TIMES".equals(opName) || "MUL".equals(opName) || "MULT".equals(opName)) {
            return left.multiply(right);
        } else if ("DIVIDE".equals(opName) || "DIV".equals(opName) || "QUOTIENT".equals(opName)) {
            if (right.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("Division by zero is not allowed");
            }
            return left.divide(right, localScale, rounding);
        } else if ("MOD".equals(opName) || "MODULO".equals(opName) || "REMAINDER".equals(opName) || "REM".equals(opName)) {
            if (right.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("Modulo by zero is not allowed");
            }
            return left.remainder(right);
        } else if ("POWER".equals(opName) || "POW".equals(opName) || "EXP".equals(opName) || "EXPONENT".equals(opName)) {
            BigDecimal expNormalized = right.stripTrailingZeros();
            if (expNormalized.scale() > 0) {
                throw new IllegalArgumentException("Exponent must be an integer value");
            }
            final int exponent;
            try {
                exponent = expNormalized.intValueExact();
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Exponent is out of int range: " + right);
            }

            if (exponent >= 0) {
                return left.pow(exponent);
            } else {
                if (left.signum() == 0) {
                    throw new IllegalArgumentException("Division by zero: zero base with negative exponent");
                }
                BigDecimal pow = left.pow(Math.abs(exponent));
                return BigDecimal.ONE.divide(pow, localScale, rounding);
            }
        } else {
            throw new IllegalArgumentException("Unsupported operator: " + opName);
        }
    }

    public String readLine() {
        try {
            String line = scanner.nextLine();
            int end = line.length();
            while (end > 0 && line.charAt(end - 1) == '\r') {
                end--;
            }
            return (end == line.length()) ? line : line.substring(0, end);
        } catch (NoSuchElementException | IllegalStateException e) {
            return null;
        }
    }

    public void prompt() {
        final String PROMPT = "bc> ";
        System.out.print(PROMPT);
    }

    public BigDecimal applyUnary(UnaryOperator operator, BigDecimal value) {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");

        switch (operator) {
            case NEGATE:
                return value.negate();
            case POSITIVE:
                return value;
            default:
                throw new UnsupportedOperationException("Unsupported unary operator: " + operator);
        }
    }

    public boolean isExitCommand(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        return "quit".equalsIgnoreCase(trimmed) || "exit".equalsIgnoreCase(trimmed);
    }

    /**
     * Prints the formatted result to standard output.
     * Separated to ease testability and centralize output formatting.
     *
     * @param formatted the already formatted text to print; may be null
     */
    public void printResult(String formatted) {
        System.out.println(formatted);
    }

    public boolean isUnaryPosition(List<Token> tokens) {
        // Unary if there are no prior tokens
        if (tokens == null || tokens.isEmpty()) {
            return true;
        }

        // Inspect the last token to determine context
        Object last = tokens.get(tokens.size() - 1);
        if (last == null) {
            // Defensive: treat null as boundary allowing unary
            return true;
        }

        // 1) Try a conventional boolean "isOperator()" method
        try {
            Method isOp = last.getClass().getMethod("isOperator");
            Object val = isOp.invoke(last);
            if (val instanceof Boolean && (Boolean) val) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            // Method not present or not accessible; continue with other strategies
        }

        // 2) Try a conventional "getType()" or "getTokenType()" that returns an enum or string
        String typeName = null;
        for (String mName : new String[]{"getType", "getTokenType"}) {
            try {
                Method m = last.getClass().getMethod(mName);
                Object type = m.invoke(last);
                if (type != null) {
                    if (type instanceof Enum<?>) {
                        typeName = ((Enum<?>) type).name();
                    } else {
                        typeName = String.valueOf(type);
                    }
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next method name
            }
        }
        if (typeName != null) {
            String tn = typeName.trim().toUpperCase(Locale.ROOT);
            // Common type names for operators and left parenthesis
            if ("OPERATOR".equals(tn)) {
                return true;
            }
            if ("LEFT_PAREN".equals(tn) || "LPAREN".equals(tn)
                    || "LEFT_PARENTHESIS".equals(tn) || "OPEN_PAREN".equals(tn)
                    || "OPEN_PARENTHESIS".equals(tn)) {
                return true;
            }
        }

        // 3) Try to extract a symbol/lexeme/text and infer
        String symbol = null;
        for (String mName : new String[]{"getSymbol", "getLexeme", "getValue", "getText", "getContent", "symbol", "lexeme", "value", "text"}) {
            try {
                Method m = last.getClass().getMethod(mName);
                Object v = m.invoke(last);
                if (v != null) {
                    symbol = String.valueOf(v);
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor name
            }
        }
        if (symbol == null) {
            // As a very last resort, use toString() but do not assume any specific format
            symbol = String.valueOf(last);
        }

        if (symbol != null) {
            String s = symbol.trim();
            // '(' implies the next '+' or '-' is unary
            if ("(".equals(s)) {
                return true;
            }
            // If the last token looks like an operator (only operator chars), then next '+'/'-' is unary
            if (!s.isEmpty()) {
                String ops = "+-*/%^&|=!<>"; // typical operator characters
                boolean allOpChars = true;
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (ops.indexOf(c) < 0) {
                        allOpChars = false;
                        break;
                    }
                }
                if (allOpChars) {
                    return true;
                }
            }
        }

        // Default: not a unary position
        return false;
    }

    public void printError(String message) {
        String output = "Error: " + (message != null ? message : "");
        System.err.println(output);
        System.err.flush();
    }

    public String formatResult(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        BigDecimal rounded = value.setScale(this.scale, this.roundingMode);
        BigDecimal normalized = rounded.stripTrailingZeros();
        return normalized.toPlainString();
    }

    public BigDecimal evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty expression");
        }

        final MathContext mc = MathContext.DECIMAL128;

        // 1) Tokenize
        List<String> tokens = new ArrayList<>();
        String expr = expression;
        int n = expr.length();
        int i = 0;
        while (i < n) {
            char c = expr.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Number (with optional unary minus)
            boolean canBeUnary = tokens.isEmpty()
                    || "(".equals(tokens.get(tokens.size() - 1))
                    || "+-*/^".contains(tokens.get(tokens.size() - 1));
            if (Character.isDigit(c) || c == '.' || (c == '-' && canBeUnary && i + 1 < n && (Character.isDigit(expr.charAt(i + 1)) || expr.charAt(i + 1) == '.'))) {
                int start = i;
                boolean negative = false;
                if (c == '-') {
                    negative = true;
                    i++; // consume '-'
                }
                int dotCount = 0;
                int digitsCount = 0;
                while (i < n) {
                    char ch = expr.charAt(i);
                    if (Character.isDigit(ch)) {
                        digitsCount++;
                        i++;
                    } else if (ch == '.') {
                        dotCount++;
                        if (dotCount > 1) {
                            throw new IllegalArgumentException("Invalid number token with multiple decimals near position " + i);
                        }
                        i++;
                    } else {
                        break;
                    }
                }
                if (digitsCount == 0) {
                    throw new IllegalArgumentException("Invalid number token near position " + start);
                }
                String number = expr.substring(negative ? start : (start), i);
                if (negative) {
                    number = "-" + expr.substring(start + 1, i);
                }
                try {
                    // Validate number format
                    new BigDecimal(number);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid number token '" + number + "'", ex);
                }
                tokens.add(number);
                continue;
            }

            // Operators and parentheses
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }

            // Anything else is invalid
            throw new IllegalArgumentException("Invalid token '" + c + "' at position " + i);
        }

        // 2) Shunting-yard to RPN
        List<String> output = new ArrayList<>();
        Deque<String> opStack = new ArrayDeque<>();
        Map<String, Integer> precedence = new HashMap<>();
        precedence.put("+", 2);
        precedence.put("-", 2);
        precedence.put("*", 3);
        precedence.put("/", 3);
        precedence.put("^", 4);

        Predicate<String> isOperator = t -> t.length() == 1 && "+-*/^".indexOf(t.charAt(0)) >= 0;
        Predicate<String> isLeftAssociative = t -> !"^".equals(t); // only ^ is right-associative

        for (String token : tokens) {
            if (!isOperator.test(token) && !"(".equals(token) && !")".equals(token)) {
                // number
                output.add(token);
            } else if (isOperator.test(token)) {
                while (!opStack.isEmpty() && isOperator.test(opStack.peek())) {
                    String top = opStack.peek();
                    int p1 = precedence.get(token);
                    int p2 = precedence.get(top);
                    boolean shouldPop = (isLeftAssociative.test(token) && p1 <= p2) || (!isLeftAssociative.test(token) && p1 < p2);
                    if (shouldPop) {
                        output.add(opStack.pop());
                    } else {
                        break;
                    }
                }
                opStack.push(token);
            } else if ("(".equals(token)) {
                opStack.push(token);
            } else if (")".equals(token)) {
                boolean foundLeftParen = false;
                while (!opStack.isEmpty()) {
                    String top = opStack.pop();
                    if ("(".equals(top)) {
                        foundLeftParen = true;
                        break;
                    } else {
                        output.add(top);
                    }
                }
                if (!foundLeftParen) {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
            }
        }
        while (!opStack.isEmpty()) {
            String top = opStack.pop();
            if ("(".equals(top) || ")".equals(top)) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            output.add(top);
        }

        // 3) Evaluate RPN
        Deque<BigDecimal> valueStack = new ArrayDeque<>();
        for (String token : output) {
            if (isOperator.test(token)) {
                // Binary operators require two operands
                if (valueStack.size() < 2) {
                    throw new IllegalArgumentException("Syntax error: insufficient operands for operator '" + token + "'");
                }
                BigDecimal right = valueStack.pop();
                BigDecimal left = valueStack.pop();
                try {
                    switch (token) {
                        case "+":
                            valueStack.push(left.add(right, mc));
                            break;
                        case "-":
                            valueStack.push(left.subtract(right, mc));
                            break;
                        case "*":
                            valueStack.push(left.multiply(right, mc));
                            break;
                        case "/":
                            if (right.compareTo(BigDecimal.ZERO) == 0) {
                                throw new IllegalArgumentException("Division by zero");
                            }
                            valueStack.push(left.divide(right, mc));
                            break;
                        case "^":
                            // exponent must be an integer
                            BigDecimal expBD = right.stripTrailingZeros();
                            if (expBD.scale() > 0) {
                                throw new IllegalArgumentException("Non-integer exponent: " + right.toPlainString());
                            }
                            int exp;
                            try {
                                exp = expBD.intValueExact();
                            } catch (ArithmeticException ex) {
                                throw new IllegalArgumentException("Exponent out of range", ex);
                            }
                            if (exp >= 0) {
                                valueStack.push(left.pow(exp, mc));
                            } else {
                                if (left.compareTo(BigDecimal.ZERO) == 0) {
                                    throw new IllegalArgumentException("Division by zero due to negative exponent on zero base");
                                }
                                BigDecimal pow = left.pow(-exp, mc);
                                valueStack.push(BigDecimal.ONE.divide(pow, mc));
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid operator: " + token);
                    }
                } catch (ArithmeticException ae) {
                    String msg = ae.getMessage() != null ? ae.getMessage().toLowerCase() : "";
                    if (msg.contains("division") && msg.contains("zero")) {
                        throw new IllegalArgumentException("Division by zero");
                    }
                    throw new IllegalArgumentException("Evaluation error: " + ae.getMessage(), ae);
                }
            } else {
                // number
                valueStack.push(new BigDecimal(token));
            }
        }

        if (valueStack.size() != 1) {
            throw new IllegalArgumentException("Syntax error in expression");
        }

        return valueStack.pop();
    }

    /**
     * Returns a small integer representing operator precedence.
     * Suggested mapping:
     *  - ADD/SUB: 1
     *  - MUL/DIV/MOD: 2
     *  - UNARY: 3
     *  - POW: 4
     *
     * @param operator the operator whose precedence is requested
     * @return the precedence value
     * @throws IllegalArgumentException if operator is null or unrecognized
     */
    public int precedence(Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }

        final int PRECEDENCE_ADD_SUB = 1;
        final int PRECEDENCE_MUL_DIV_MOD = 2;
        final int PRECEDENCE_UNARY = 3;
        final int PRECEDENCE_POW = 4;

        switch (operator) {
            case ADD:
            case SUB:
                return PRECEDENCE_ADD_SUB;
            case MUL:
            case DIV:
            case MOD:
                return PRECEDENCE_MUL_DIV_MOD;
            case UNARY:
                return PRECEDENCE_UNARY;
            case POW:
                return PRECEDENCE_POW;
            default:
                throw new IllegalArgumentException("Unrecognized operator: " + operator);
        }
    }
}
