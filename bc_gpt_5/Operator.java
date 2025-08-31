package bc_gpt_5;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Objects;
/**
 * An immutable arithmetic operator with symbol, precedence, associativity, and arity.
 * Supports unary (u+, u-) and binary (+, -, *, /, %, ^) operators on BigDecimal operands.
 */
public final class Operator {

    public enum Associativity {
        LEFT, RIGHT
    }

    // Static predefined operators
    public static final Operator PLUS;
    public static final Operator MINUS;
    public static final Operator MULTIPLY;
    public static final Operator DIVIDE;
    public static final Operator MODULO;
    public static final Operator POWER;
    public static final Operator UNARY_PLUS;
    public static final Operator UNARY_MINUS;
    // Aliases to match both naming variations used in provided code
    public static final Operator UPLUS;
    public static final Operator UMINUS;

    static {
        MathContext mc = MathContext.DECIMAL64;

        PLUS = new Operator("+", 1, false, 2, mc);
        MINUS = new Operator("-", 1, false, 2, mc);
        MULTIPLY = new Operator("*", 2, false, 2, mc);
        DIVIDE = new Operator("/", 2, false, 2, mc);
        MODULO = new Operator("%", 2, false, 2, mc);
        POWER = new Operator("^", 4, true, 2, mc);

        Operator up = new Operator("u+", 3, true, 1, mc);
        Operator um = new Operator("u-", 3, true, 1, mc);
        UNARY_PLUS = up;
        UNARY_MINUS = um;
        UPLUS = up;   // alias to accommodate "UPLUS" usage
        UMINUS = um;  // alias to accommodate "UMINUS" usage
    }

    private final String symbol;
    private final int precedence;
    private final boolean rightAssociative;
    private final int arity;
    private final MathContext mathContext;
    private final Associativity associativity;

    /**
     * Strict constructor. Validates symbol, arity (1 for unary u+/u-, 2 otherwise),
     * precedence mapping, and associativity for known operators.
     */
    public Operator(String symbol, int precedence, boolean rightAssociative, int arity, MathContext mathContext) {
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must not be null");
        }
        // Validate supported symbols
        switch (symbol) {
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
            case "^":
            case "u+":
            case "u-":
                break;
            default:
                throw new IllegalArgumentException("Unsupported operator symbol: " + symbol);
        }

        // Validate arity
        if (arity != 1 && arity != 2) {
            throw new IllegalArgumentException("arity must be 1 (unary) or 2 (binary)");
        }
        boolean isUnary = "u+".equals(symbol) || "u-".equals(symbol);
        if (isUnary && arity != 1) {
            throw new IllegalArgumentException("Unary operators 'u+' and 'u-' must have arity 1");
        }
        if (!isUnary && arity != 2) {
            throw new IllegalArgumentException("Binary operators '+', '-', '*', '/', '%', '^' must have arity 2");
        }

        // Validate precedence matches the operator
        int expectedPrecedence;
        if ("+".equals(symbol) || "-".equals(symbol)) {
            expectedPrecedence = 1;
        } else if ("*".equals(symbol) || "/".equals(symbol) || "%".equals(symbol)) {
            expectedPrecedence = 2;
        } else if (isUnary) {
            expectedPrecedence = 3;
        } else if ("^".equals(symbol)) {
            expectedPrecedence = 4;
        } else {
            throw new IllegalArgumentException("Invalid operator configuration");
        }
        if (precedence != expectedPrecedence) {
            throw new IllegalArgumentException(
                    "Invalid precedence for symbol '" + symbol + "': expected " + expectedPrecedence);
        }

        // Validate associativity for known operators
        boolean mustBeRight = "^".equals(symbol) || isUnary;
        if (mustBeRight && !rightAssociative) {
            throw new IllegalArgumentException("Operator '" + symbol + "' must be right-associative");
        }
        if (!mustBeRight && rightAssociative) {
            throw new IllegalArgumentException("Operator '" + symbol + "' must be left-associative");
        }

        if (mathContext == null) {
            throw new IllegalArgumentException("mathContext must not be null");
        }

        this.symbol = symbol;
        this.precedence = precedence;
        this.rightAssociative = rightAssociative;
        this.arity = arity;
        this.mathContext = mathContext;
        this.associativity = rightAssociative ? Associativity.RIGHT : Associativity.LEFT;
    }

    /**
     * Lenient constructor for testing/value-based usage. Validates inputs are non-null and within basic ranges,
     * but does not enforce precedence/arity/associativity mapping for the symbol.
     */
    public Operator(String symbol, int precedence, Associativity associativity, int arity, MathContext mathContext) {
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must not be null");
        }
        switch (symbol) {
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
            case "^":
            case "u+":
            case "u-":
                break;
            default:
                throw new IllegalArgumentException("Unsupported operator symbol: " + symbol);
        }
        if (arity != 1 && arity != 2) {
            throw new IllegalArgumentException("arity must be 1 (unary) or 2 (binary)");
        }
        if (mathContext == null) {
            throw new IllegalArgumentException("mathContext must not be null");
        }
        this.symbol = symbol;
        this.precedence = precedence;
        this.rightAssociative = associativity == Associativity.RIGHT;
        this.arity = arity;
        this.mathContext = mathContext;
        this.associativity = associativity;
    }

    public BigDecimal apply(BigDecimal value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if ("u+".equals(this.symbol)) {
            return value;
        }
        if ("u-".equals(this.symbol)) {
            return value.negate();
        }
        throw new IllegalStateException("Operator " + this + " is not unary");
    }

    public BigDecimal apply(BigDecimal left, BigDecimal right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");

        final String op = getSymbol();

        // Validate this is a binary operator
        if (!"+".equals(op) && !"-".equals(op) && !"*".equals(op) && !"/".equals(op) && !"%".equals(op) && !"^".equals(op)) {
            throw new IllegalStateException("This operator is not binary: " + op);
        }

        switch (op) {
            case "+":
                return left.add(right);
            case "-":
                return left.subtract(right);
            case "*":
                return left.multiply(right);
            case "/": {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("Division by zero");
                }
                MathContext mc = getMathContext();
                return left.divide(right, mc);
            }
            case "%": {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("Modulo by zero");
                }
                return left.remainder(right);
            }
            case "^": {
                // Exponent must be an exact integer
                BigDecimal stripped = right.stripTrailingZeros();
                if (stripped.scale() != 0) {
                    throw new IllegalArgumentException("Exponent must be an integer");
                }
                final int exp;
                try {
                    exp = right.intValueExact();
                } catch (ArithmeticException ex) {
                    throw new IllegalArgumentException("Exponent out of int range or not an integer", ex);
                }
                MathContext mc = getMathContext();
                if (exp >= 0) {
                    return left.pow(exp, mc);
                }
                // Negative exponent: 1 / (left ^ |exp|)
                if (left.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("Division by zero for negative exponent");
                }
                BigDecimal denom = left.pow(Math.abs(exp), mc);
                return BigDecimal.ONE.divide(denom, mc);
            }
            default:
                throw new IllegalStateException("Unknown binary operator: " + op);
        }
    }

    public boolean isUnary() {
        return getArity() == 1;
    }

    public boolean equalsOperatorSymbol(String rawSymbol) {
        return rawSymbol != null && this.symbol.equals(rawSymbol);
    }

    public boolean isRightAssociative() {
        return rightAssociative;
    }

    public String normalizeSymbol(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "+":
                return "u+";
            case "-":
                return "u-";
            default:
                return raw;
        }
    }

    public int getPrecedence() {
        return precedence;
    }

    /**
     * Returns true for algebraically associative operators, specifically PLUS and MULTIPLY.
     * Returns false for non-associative operators such as MINUS, DIVIDE, MODULO, POWER,
     * and for any unary operators.
     *
     * @return true if the operator is associative; false otherwise
     */
    public boolean isAssociative() {
        return this == PLUS || this == MULTIPLY;
    }

    /**
     * Returns true only for commutative binary operators: PLUS and MULTIPLY.
     */
    public boolean isCommutative() {
        return "+".equals(this.symbol) || "*".equals(this.symbol);
    }

    public boolean isBinary() {
        return getArity() == 2;
    }

    public int getArity() {
        return arity;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + Integer.hashCode(precedence);
        result = 31 * result + (associativity != null ? associativity.hashCode() : 0);
        result = 31 * result + Integer.hashCode(arity);
        result = 31 * result + (mathContext != null ? mathContext.hashCode() : 0);
        return result;
    }

    public int comparePrecedenceTo(Operator other) {
        Objects.requireNonNull(other, "other");
        if (this == other) {
            return 0;
        }
        return Integer.compare(this.precedence, other.precedence);
    }

    public Operator withMathContext(MathContext mathContext) {
        Objects.requireNonNull(mathContext, "mathContext must not be null");
        return new Operator(getSymbol(), getPrecedence(), getAssociativityEnum(), getArity(), mathContext);
    }

    /**
     * Value-based equality. Returns true when the other object is an Operator with the same
     * symbol, precedence, associativity, arity, and MathContext. Ignores transient or computed data.
     *
     * @param other the object to compare with this Operator
     * @return true if all defining fields are equal; false otherwise
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Operator that = (Operator) other;
        return this.precedence == that.precedence
                && this.arity == that.arity
                && this.associativity == that.associativity
                && Objects.equals(this.symbol, that.symbol)
                && Objects.equals(this.mathContext, that.mathContext);
    }

    public String toHumanReadable() {
        String symbolStr = "?";
        String arityStr = "?";
        String precedenceStr = "?";
        String associativityStr = "associative=unknown";

        Class<?> clazz = this.getClass();

        // Try to resolve symbol
        try {
            String[] symbolMethods = {"getSymbol", "symbol", "getOperatorSymbol", "operatorSymbol"};
            for (String m : symbolMethods) {
                try {
                    Method method;
                    try {
                        method = clazz.getMethod(m);
                    } catch (NoSuchMethodException e) {
                        method = clazz.getDeclaredMethod(m);
                        method.setAccessible(true);
                    }
                    Object val = method.invoke(this);
                    if (val != null) {
                        String s = val.toString();
                        if (!s.isBlank()) {
                            symbolStr = s;
                            break;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
            if ("?".equals(symbolStr)) {
                String[] symbolFields = {"symbol", "SYMBOL", "OPERATOR_SYMBOL"};
                for (String f : symbolFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object val = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (val != null) {
                            String s = val.toString();
                            if (!s.isBlank()) {
                                symbolStr = s;
                                break;
                            }
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
        } catch (SecurityException ignored) {
        }

        // Try to resolve arity
        try {
            String[] arityMethods = {"getArity", "arity"};
            for (String m : arityMethods) {
                try {
                    Method method;
                    try {
                        method = clazz.getMethod(m);
                    } catch (NoSuchMethodException e) {
                        method = clazz.getDeclaredMethod(m);
                        method.setAccessible(true);
                    }
                    Object val = method.invoke(this);
                    if (val != null) {
                        arityStr = String.valueOf(val);
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
            if ("?".equals(arityStr)) {
                String[] arityFields = {"arity", "OPERAND_COUNT", "ARGUMENT_COUNT"};
                for (String f : arityFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object val = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (val != null) {
                            arityStr = String.valueOf(val);
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
        } catch (SecurityException ignored) {
        }

        // Try to resolve precedence
        try {
            String[] precedenceMethods = {"getPrecedence", "precedence", "getPriority", "priority"};
            for (String m : precedenceMethods) {
                try {
                    Method method;
                    try {
                        method = clazz.getMethod(m);
                    } catch (NoSuchMethodException e) {
                        method = clazz.getDeclaredMethod(m);
                        method.setAccessible(true);
                    }
                    Object val = method.invoke(this);
                    if (val != null) {
                        precedenceStr = String.valueOf(val);
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
            if ("?".equals(precedenceStr)) {
                String[] precedenceFields = {"precedence", "PRECEDENCE", "PRIORITY"};
                for (String f : precedenceFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object val = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (val != null) {
                            precedenceStr = String.valueOf(val);
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
        } catch (SecurityException ignored) {
        }

        // Try to resolve associativity
        boolean assocResolved = false;
        try {
            // isLeftAssociative()
            try {
                Method m = clazz.getMethod("isLeftAssociative");
                Object v = m.invoke(this);
                if (v instanceof Boolean) {
                    associativityStr = ((Boolean) v) ? "left-associative" : "right-associative";
                    assocResolved = true;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException | SecurityException ignored) {
            }
            // isRightAssociative()
            if (!assocResolved) {
                try {
                    Method m = clazz.getMethod("isRightAssociative");
                    Object v = m.invoke(this);
                    if (v instanceof Boolean) {
                        associativityStr = ((Boolean) v) ? "right-associative" : "left-associative";
                        assocResolved = true;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
            // getAssociativity()/associativity()
            if (!assocResolved) {
                String[] assocMethods = {"getAssociativity", "associativity"};
                for (String mn : assocMethods) {
                    try {
                        Method m;
                        try {
                            m = clazz.getMethod(mn);
                        } catch (NoSuchMethodException e) {
                            m = clazz.getDeclaredMethod(mn);
                            m.setAccessible(true);
                        }
                        Object v = m.invoke(this);
                        if (v != null) {
                            String s = v.toString().toLowerCase(Locale.ROOT);
                            if (s.contains("left")) {
                                associativityStr = "left-associative";
                            } else if (s.contains("right")) {
                                associativityStr = "right-associative";
                            } else if ("true".equals(s)) {
                                associativityStr = "right-associative";
                            } else if ("false".equals(s)) {
                                associativityStr = "left-associative";
                            } else {
                                associativityStr = "associative=" + v.toString();
                            }
                            assocResolved = true;
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
            // Fields for associativity
            if (!assocResolved) {
                String[] leftFields = {"leftAssociative", "IS_LEFT_ASSOCIATIVE"};
                for (String f : leftFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object v = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (v instanceof Boolean) {
                            associativityStr = ((Boolean) v) ? "left-associative" : "right-associative";
                            assocResolved = true;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
            if (!assocResolved) {
                String[] rightFields = {"rightAssociative", "IS_RIGHT_ASSOCIATIVE"};
                for (String f : rightFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object v = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (v instanceof Boolean) {
                            associativityStr = ((Boolean) v) ? "right-associative" : "left-associative";
                            assocResolved = true;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
            if (!assocResolved) {
                String[] assocFields = {"associativity", "ASSOCIATIVITY"};
                for (String f : assocFields) {
                    try {
                        Field field;
                        try {
                            field = clazz.getField(f);
                        } catch (NoSuchFieldException e) {
                            field = clazz.getDeclaredField(f);
                            field.setAccessible(true);
                        }
                        Object v = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(this);
                        if (v != null) {
                            String s = v.toString().toLowerCase(Locale.ROOT);
                            if (s.contains("left")) {
                                associativityStr = "left-associative";
                            } else if (s.contains("right")) {
                                associativityStr = "right-associative";
                            } else {
                                associativityStr = "associative=" + v.toString();
                            }
                            assocResolved = true;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (ReflectiveOperationException | SecurityException ignored) {
                    }
                }
            }
        } catch (SecurityException ignored) {
        }

        return "symbol=" + symbolStr + ", arity=" + arityStr + ", precedence=" + precedenceStr + ", " + associativityStr;
    }

    public MathContext getMathContext() {
        return this.mathContext;
    }

    public boolean accepts(bc_gpt_5.Token token) {
        if (token == null) {
            return false;
        }
        String tokenOperator = token.operator();
        if (tokenOperator == null) {
            return false;
        }
        return tokenOperator.equals(this.getSymbol());
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns whether this operator is right-associative.
     * Provided to match the constructor parameter type used in withMathContext.
     *
     * @return true if right-associative, false if left-associative
     */
    public boolean getAssociativity() {
        return rightAssociative;
    }

    public Associativity getAssociativityEnum() {
        return associativity;
    }

    // Optional helpers to aid toHumanReadable() reflection callers if present
    public boolean isLeftAssociative() {
        return !rightAssociative;
    }

    @Override
    public String toString() {
        return "Operator{" +
                "symbol='" + symbol + '\'' +
                ", precedence=" + precedence +
                ", associativity=" + associativity +
                ", arity=" + arity +
                ", mathContext=" + mathContext +
                '}';
    }
}
