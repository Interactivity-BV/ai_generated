package bc_gpt_5;


import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public class UnaryOperator {

    // Predefined singletons for convenience in tests and general use
    public static final UnaryOperator U_PLUS = new UnaryOperator("u+");
    public static final UnaryOperator U_MINUS = new UnaryOperator("u-");
    public static final UnaryOperator SQRT = new UnaryOperator("sqrt");

    // Fields
    private final String symbol;             // Canonical symbol: "u+", "u-", or "sqrt"
    private final String operator;           // Same as symbol for compatibility
    private final String canonicalSymbol;    // Same as symbol
    private final int precedence;            // Unary precedence = 3
    private final String associativity;      // "RIGHT"
    private final int arity;                 // Always 1 for unary
    private final MathContext mathContext;   // Math context for operations

    // No-arg constructor for tests that only care about fixed unary characteristics
    public UnaryOperator() {
        this("u+", MathContext.DECIMAL128);
    }

    // Static factory used by tests
    public static UnaryOperator sqrt(MathContext mathContext) {
        return new UnaryOperator("sqrt", mathContext);
    }

    public boolean accepts(Object token) {
        if (token == null) {
            return false;
        }

        // Determine this instance's canonical unary operator symbol ("u+", "u-", or "sqrt")
        String instanceCanonical = null;

        // Try common accessor methods on this instance
        String[] selfMethodNames = { "operator", "getOperator", "getSymbol", "symbol", "getLexeme", "lexeme" };
        for (String methodName : selfMethodNames) {
            try {
                java.lang.reflect.Method m = this.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                    Object val = m.invoke(this);
                    if (val instanceof String) {
                        String s = ((String) val);
                        if (s != null) {
                            String t = s.trim();
                            if (t.equals("+")) {
                                instanceCanonical = "u+";
                            } else if (t.equals("-")) {
                                instanceCanonical = "u-";
                            } else if (t.equals("u+") || t.equals("u-") || t.equals("sqrt")) {
                                instanceCanonical = t;
                            }
                            if (instanceCanonical != null) {
                                break;
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignore) {
                // Ignore and try next possibility
            }
        }

        // If still unknown, try to discover from String fields of this instance
        if (instanceCanonical == null) {
            for (java.lang.reflect.Field f : this.getClass().getDeclaredFields()) {
                if (f.getType() == String.class) {
                    try {
                        if (!f.canAccess(this)) {
                            f.setAccessible(true);
                        }
                        Object val = f.get(this);
                        if (val instanceof String) {
                            String t = ((String) val).trim();
                            if (t.equals("+")) {
                                instanceCanonical = "u+";
                            } else if (t.equals("-")) {
                                instanceCanonical = "u-";
                            } else if (t.equals("u+") || t.equals("u-") || t.equals("sqrt")) {
                                instanceCanonical = t;
                            }
                            if (instanceCanonical != null) {
                                break;
                            }
                        }
                    } catch (IllegalAccessException ignore) {
                        // continue
                    }
                }
            }
        }

        if (instanceCanonical == null) {
            // Could not determine what this operator represents; be safe.
            return false;
        }

        // Resolve token's operator
        String tokenCanonical = null;

        // First, try token.operator()
        try {
            java.lang.reflect.Method m = token.getClass().getMethod("operator");
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                Object val = m.invoke(token);
                if (val instanceof String) {
                    String s = (String) val;
                    if (s != null) {
                        String t = s.trim();
                        if (t.equals("+")) {
                            tokenCanonical = "u+";
                        } else if (t.equals("-")) {
                            tokenCanonical = "u-";
                        } else if (t.equals("u+") || t.equals("u-") || t.equals("sqrt")) {
                            tokenCanonical = t;
                        }
                        // If operator() returned a non-null but unrecognized string,
                        // we keep tokenCanonical as null so it won't match.
                    }
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignore) {
            // Ignore and fallback below if needed
        }

        // If operator() not available or returned null, fallback to symbol/lexeme
        if (tokenCanonical == null) {
            String[] tokenMethodNames = { "getSymbol", "symbol", "getLexeme", "lexeme" };
            for (String methodName : tokenMethodNames) {
                try {
                    java.lang.reflect.Method m = token.getClass().getMethod(methodName);
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        Object val = m.invoke(token);
                        if (val instanceof String) {
                            String s = (String) val;
                            if (s != null) {
                                String t = s.trim();
                                if (t.equals("+")) {
                                    tokenCanonical = "u+";
                                } else if (t.equals("-")) {
                                    tokenCanonical = "u-";
                                } else if (t.equals("u+") || t.equals("u-") || t.equals("sqrt")) {
                                    tokenCanonical = t;
                                }
                                // If recognized, stop searching
                                if (tokenCanonical != null) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignore) {
                    // Try next method
                }
            }
        }

        return instanceCanonical.equals(tokenCanonical);
    }

    public MathContext getMathContext() {
        // Return the configured MathContext for this operator instance
        return this.mathContext;
    }

    public boolean isLeftAssociative() {
        return false;
    }

    public BigDecimal apply(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");

        final String op = this.operator;
        if ("u+".equals(op)) {
            return value;
        } else if ("u-".equals(op) || "neg".equals(op)) {
            return value.negate();
        } else if ("sqrt".equals(op)) {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Square root of negative value: " + value);
            }
            if (BigDecimal.ZERO.compareTo(value) == 0) {
                return BigDecimal.ZERO;
            }

            final MathContext mc = this.mathContext;
            final MathContext workMc =
                    new MathContext(mc.getPrecision() + 4, mc.getRoundingMode());
            final BigDecimal TWO = new BigDecimal(2);

            double dv = value.doubleValue();
            BigDecimal x = (dv > 0d)
                    ? BigDecimal.valueOf(Math.sqrt(dv))
                    : BigDecimal.ONE;

            for (int i = 0; i < 100; i++) {
                BigDecimal prev = x;
                x = x.add(value.divide(x, workMc), workMc).divide(TWO, workMc);
                if (x.compareTo(prev) == 0) {
                    break;
                }
            }

            BigDecimal result = x.round(mc).stripTrailingZeros();
            if (result.scale() < 0) {
                result = result.setScale(0, mc.getRoundingMode());
            }
            return result;
        } else {
            throw new IllegalArgumentException("Unsupported unary operator: " + op);
        }
    }

    public UnaryOperator(bc_gpt_5.Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator must not be null");
        }

        final int arity = operator.getArity();
        final String symbol = operator.getSymbol();

        final boolean isUnaryPlus =
                operator == bc_gpt_5.Operator.UNARY_PLUS
                || (arity == 1 && ("u+".equals(symbol) || "+".equals(symbol)));

        final boolean isUnaryMinus =
                operator == bc_gpt_5.Operator.UNARY_MINUS
                || (arity == 1 && ("u-".equals(symbol) || "-".equals(symbol) || "neg".equalsIgnoreCase(symbol)));

        if (!isUnaryPlus && !isUnaryMinus) {
            throw new IllegalArgumentException("Unsupported unary operator: " + symbol);
        }

        String normalized = isUnaryPlus ? "u+" : "u-";
        this.symbol = normalized;
        this.operator = normalized;
        this.canonicalSymbol = normalized;
        this.precedence = 3;
        this.associativity = "RIGHT";
        this.arity = 1;
        this.mathContext = MathContext.DECIMAL128;
    }

    public String getSymbol() {
        String raw = this.symbol;
        if (raw == null) {
            return null;
        }

        String s = raw.trim();
        if ("u+".equals(s) || "+".equals(s)) {
            return "u+";
        }
        if ("u-".equals(s) || "-".equals(s) || "neg".equalsIgnoreCase(s)) {
            return "u-";
        }
        if ("sqrt".equalsIgnoreCase(s)) {
            return "sqrt";
        }

        return s;
    }

    public int getArity() {
        return 1;
    }

    public int comparePrecedenceTo(UnaryOperator other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return Integer.compare(this.getPrecedence(), other.getPrecedence());
    }

    public int getPrecedence() {
        // Shunting-yard precedence: +/− = 1, *//% = 2, unary = 3, ^ = 4
        return 3;
    }

    public String toString() {
        String mcText;
        MathContext mc = this.mathContext;
        if (mc == null) {
            mcText = "null";
        } else if (mc.equals(MathContext.UNLIMITED)) {
            mcText = "UNLIMITED";
        } else if (mc.equals(MathContext.DECIMAL32)) {
            mcText = "DECIMAL32";
        } else if (mc.equals(MathContext.DECIMAL64)) {
            mcText = "DECIMAL64";
        } else if (mc.equals(MathContext.DECIMAL128)) {
            mcText = "DECIMAL128";
        } else {
            mcText = mc.toString();
        }

        return "UnaryOperator{"
                + "symbol='" + symbol + '\''
                + ", precedence=" + precedence
                + ", associativity=" + (associativity == null ? "null" : associativity)
                + ", arity=" + arity
                + ", mathContext=" + mcText
                + '}';
    }

    public UnaryOperator withMathContext(MathContext mathContext) {
        Objects.requireNonNull(mathContext, "mathContext must be non-null.");
        // Always return a new instance to preserve immutability, even if the context is equal
        return new UnaryOperator(getSymbol(), mathContext);
    }

    public bc_gpt_5.Bc.UnaryOperator asBcUnary() {
        String operatorSymbol = null;

        // Try common zero-arg getters that may return the operator symbol
        try {
            for (String getter : new String[] { "getOperator", "operator", "getSymbol", "symbol", "getToken", "token", "getOp", "op", "getValue", "value" }) {
                try {
                    java.lang.reflect.Method m = getClass().getDeclaredMethod(getter);
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        m.setAccessible(true);
                        Object val = m.invoke(this);
                        if (val != null) {
                            operatorSymbol = ((String) val).trim();
                            break;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue searching
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall back to fields
        }

        // Fallback: search for a String field that holds the operator symbol
        if (operatorSymbol == null) {
            for (java.lang.reflect.Field f : getClass().getDeclaredFields()) {
                if (f.getType() == String.class) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(this);
                        if (val != null) {
                            String s = ((String) val).trim();
                            if ("u-".equals(s) || "u+".equals(s)) {
                                operatorSymbol = s;
                                break;
                            }
                            if (operatorSymbol == null) {
                                operatorSymbol = s; // keep as a candidate
                            }
                        }
                    } catch (IllegalAccessException ignored) {
                        // continue searching
                    }
                }
            }
        }

        // Last resort: try toString if it directly matches a known token
        if (operatorSymbol == null) {
            String ts = String.valueOf(this).trim();
            if ("u-".equals(ts) || "u+".equals(ts)) {
                operatorSymbol = ts;
            }
        }

        if ("u-".equals(operatorSymbol)) {
            return bc_gpt_5.Bc.UnaryOperator.NEGATE;
        }
        if ("u+".equals(operatorSymbol)) {
            return bc_gpt_5.Bc.UnaryOperator.POSITIVE;
        }

        throw new UnsupportedOperationException("Unsupported unary operator: " + operatorSymbol);
    }

    public boolean equalsOperatorSymbol(String rawSymbol) {
        if (rawSymbol == null) {
            return false;
        }

        // Resolve this instance's canonical symbol as robustly as possible without assuming field names
        String canonical = null;

        // Try common zero-arg accessors first
        String[] accessorNames = {
                "getSymbol", "symbol", "getOperatorSymbol", "operatorSymbol", "getOperator", "getName", "name"
        };
        for (String name : accessorNames) {
            try {
                java.lang.reflect.Method m = this.getClass().getDeclaredMethod(name);
                if (m.getParameterCount() == 0 && String.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object value = m.invoke(this);
                    if (value instanceof String) {
                        String s = (String) value;
                        if ("u+".equals(s) || "u-".equals(s) || "sqrt".equals(s)) {
                            canonical = s;
                            break;
                        } else if (canonical == null) {
                            canonical = s; // Fallback to first String if canonical not found yet
                        }
                    }
                }
            } catch (NoSuchMethodException ignore) {
                // Try next accessor
            } catch (ReflectiveOperationException ignore) {
                // Ignore and try other options
            }
        }

        // If not resolved via accessors, try inspecting String fields
        if (canonical == null || !(canonical.equals("u+") || canonical.equals("u-") || canonical.equals("sqrt"))) {
            java.lang.reflect.Field[] fields = this.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                if (String.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(this);
                        if (value instanceof String) {
                            String s = (String) value;
                            if ("u+".equals(s) || "u-".equals(s) || "sqrt".equals(s)) {
                                canonical = s;
                                break;
                            } else if (canonical == null) {
                                canonical = s; // Fallback candidate
                            }
                        }
                    } catch (IllegalAccessException ignore) {
                        // Continue scanning other fields
                    }
                }
            }
        }

        // As a final fallback, use toString if still unresolved
        if (canonical == null) {
            String s = this.toString();
            if ("u+".equals(s) || "u-".equals(s) || "sqrt".equals(s)) {
                canonical = s;
            } else {
                canonical = s; // Exact-match fallback
            }
        }

        // Apply alias rules with case sensitivity:
        // '+' -> "u+"
        // '-' or lowercase "neg" -> "u-"
        // "sqrt" -> only "sqrt"
        if ("u+".equals(canonical)) {
            return "u+".equals(rawSymbol) || "+".equals(rawSymbol);
        } else if ("u-".equals(canonical)) {
            return "u-".equals(rawSymbol) || "-".equals(rawSymbol) || "neg".equals(rawSymbol);
        } else if ("sqrt".equals(canonical)) {
            return "sqrt".equals(rawSymbol);
        }

        // Unknown operator type: fall back to exact, case-sensitive comparison
        return canonical.equals(rawSymbol);
    }

    public boolean isAssociative() {
        return false;
    }

    public String toHumanReadable() {
        final int arity = 1;
        final int precedence = 3;
        final String associativity = "right";

        return getClass().getSimpleName()
                + " { symbol, arity=" + arity
                + ", precedence=" + precedence
                + ", associativity=" + associativity
                + " }";
    }

    public boolean isRightAssociative() {
        return true;
    }

    public String normalizeSymbol(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "+":
                return "u+";
            case "-":
            case "neg":
                return "u-";
            case "sqrt":
                return "sqrt";
            default:
                return raw;
        }
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (canonicalSymbol == null ? 0 : canonicalSymbol.hashCode());
        result = 31 * result + (mathContext == null ? 0 : mathContext.hashCode());
        return result;
    }

    public boolean isCommutative() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UnaryOperator)) {
            return false;
        }
        UnaryOperator other = (UnaryOperator) obj;
        return Objects.equals(this.getSymbol(), other.getSymbol())
                && Objects.equals(this.getMathContext(), other.getMathContext());
    }

    public bc_gpt_5.Operator toOperator() {
        // Discover the operator token ("u+" or "u-") without assuming field/method names
        String operatorToken = null;
        Class<?> cls = this.getClass();

        // Try zero-arg methods returning String/CharSequence
        for (java.lang.reflect.Method method : cls.getMethods()) {
            if (method.getParameterCount() == 0
                    && (method.getReturnType() == String.class
                        || CharSequence.class.isAssignableFrom(method.getReturnType()))) {
                try {
                    Object value = method.invoke(this);
                    if (value != null) {
                        String str = value.toString();
                        if ("u+".equals(str) || "u-".equals(str)) {
                            operatorToken = str;
                            break;
                        }
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Continue probing
                }
            }
        }

        // If not found via methods, try declared fields of type String/CharSequence
        if (operatorToken == null) {
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (field.getType() == String.class || CharSequence.class.isAssignableFrom(field.getType())) {
                    boolean accessible = field.canAccess(this);
                    try {
                        if (!accessible) {
                            field.setAccessible(true);
                        }
                        Object value = field.get(this);
                        if (value != null) {
                            String str = value.toString();
                            if ("u+".equals(str) || "u-".equals(str)) {
                                operatorToken = str;
                                break;
                            }
                        }
                    } catch (IllegalAccessException ignored) {
                        // Continue probing
                    }
                }
            }
        }

        // Discover MathContext without assuming field/method names
        MathContext mathContext = null;

        // Try zero-arg methods returning MathContext
        for (java.lang.reflect.Method method : cls.getMethods()) {
            if (method.getParameterCount() == 0
                    && MathContext.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Object value = method.invoke(this);
                    if (value instanceof MathContext) {
                        mathContext = (MathContext) value;
                        break;
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Continue probing
                }
            }
        }

        // If not found via methods, try declared fields of type MathContext
        if (mathContext == null) {
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (MathContext.class.isAssignableFrom(field.getType())) {
                    boolean accessible = field.canAccess(this);
                    try {
                        if (!accessible) {
                            field.setAccessible(true);
                        }
                        Object value = field.get(this);
                        if (value instanceof MathContext) {
                            mathContext = (MathContext) value;
                            break;
                        }
                    } catch (IllegalAccessException ignored) {
                        // Continue probing
                    }
                }
            }
        }

        if (mathContext == null) {
            throw new IllegalStateException("MathContext not available for " + cls.getName());
        }

        final bc_gpt_5.Operator baseOperator;
        if ("u+".equals(operatorToken)) {
            baseOperator = bc_gpt_5.Operator.UNARY_PLUS;
        } else if ("u-".equals(operatorToken)) {
            baseOperator = bc_gpt_5.Operator.UNARY_MINUS;
        } else {
            throw new UnsupportedOperationException("Unsupported unary operator: " + operatorToken);
        }

        return baseOperator.withMathContext(mathContext);
    }

    public UnaryOperator(String symbol, MathContext mathContext) {
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol must not be null");
        }
        if (mathContext == null) {
            throw new IllegalArgumentException("mathContext must be non-null");
        }

        String normalized = normalizeSymbol(symbol);
        if (!"u+".equals(normalized) && !"u-".equals(normalized) && !"sqrt".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported unary operator symbol: " + symbol);
        }

        this.symbol = normalized;
        this.operator = normalized;
        this.canonicalSymbol = normalized;
        this.precedence = 3;
        this.associativity = "RIGHT";
        this.arity = 1;
        this.mathContext = mathContext;
    }

    public UnaryOperator(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol must not be null");
        }

        final String normalized;
        switch (symbol) {
            case "u+":
            case "u-":
            case "sqrt":
                normalized = symbol;
                break;
            case "+":
                normalized = "u+";
                break;
            case "-":
            case "neg":
                normalized = "u-";
                break;
            default:
                throw new IllegalArgumentException("Unsupported unary operator symbol: " + symbol);
        }

        // Initialize with default MathContext
        this.symbol = normalized;
        this.operator = normalized;
        this.canonicalSymbol = normalized;
        this.precedence = 3;
        this.associativity = "RIGHT";
        this.arity = 1;
        this.mathContext = MathContext.DECIMAL128;
    }
}
