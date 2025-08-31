package bc_gpt_5;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Represents a lexical token with a type and lexeme.
 * Provides utility accessors for text, symbol, operator normalization, and numeric value.
 */
public final class Token {

    private final TokenType type;
    private final String lexeme;
    private final BigDecimal number;
	public enum TokenType {
        NUMBER,
        OPERATOR,
        UNARY_OPERATOR,
        LPAREN,
        RPAREN;

        public boolean accepts(bc_gpt_5.Token token) {
            if (token == null) {
                return false;
            }

            // Safely read operator first (preferred)
            String op = null;
            try {
                op = token.operator();
            } catch (Throwable ignore) {
                // no-op
            }
            String opTrim = op == null ? null : op.trim();
            String opLower = opTrim == null ? null : opTrim.toLowerCase(Locale.ROOT);

            // Fallback: try to read symbol/lexeme using reflection in the order: getSymbol, symbol, getLexeme, lexeme
            String sym = null;
            try {
                String[] candidates = {"getSymbol", "symbol", "getLexeme", "lexeme"};
                for (String name : candidates) {
                    try {
                        Method m = token.getClass().getMethod(name);
                        Object v = m.invoke(token);
                        if (v != null) {
                            sym = v.toString();
                            break;
                        }
                    } catch (Throwable ignore) {
                        // try next
                    }
                }
            } catch (Throwable ignore) {
                // no-op
            }
            String symTrim = sym == null ? null : sym.trim();
            String symLower = symTrim == null ? null : symTrim.toLowerCase(Locale.ROOT);

            try {
                switch (this) {
                    case NUMBER: {
                        try {
                            return token.number() != null;
                        } catch (Throwable ignore) {
                            return false;
                        }
                    }
                    case OPERATOR: {
                        // Accept standard binary operators
                        boolean isBinaryOpFromOperator =
                                "+".equals(opTrim) || "-".equals(opTrim) || "*".equals(opTrim)
                                        || "/".equals(opTrim) || "%".equals(opTrim) || "^".equals(opTrim);
                        if (isBinaryOpFromOperator) {
                            return true;
                        }
                        boolean isBinaryOpFromSymbol =
                                "+".equals(symTrim) || "-".equals(symTrim) || "*".equals(symTrim)
                                        || "/".equals(symTrim) || "%".equals(symTrim) || "^".equals(symTrim);
                        return isBinaryOpFromSymbol;
                    }
                    case UNARY_OPERATOR: {
                        // Accept u+, u-, raw +/-, and optionally neg/sqrt
                        boolean isUnaryFromOperator =
                                "u+".equals(opLower) || "u-".equals(opLower)
                                        || "+".equals(opTrim) || "-".equals(opTrim)
                                        || "neg".equals(opLower) || "sqrt".equals(opLower);
                        if (isUnaryFromOperator) {
                            return true;
                        }
                        boolean isUnaryFromSymbol =
                                "u+".equals(symLower) || "u-".equals(symLower)
                                        || "+".equals(symTrim) || "-".equals(symTrim)
                                        || "neg".equals(symLower) || "sqrt".equals(symLower);
                        return isUnaryFromSymbol;
                    }
                    case LPAREN: {
                        return "(".equals(symTrim) || "(".equals(opTrim);
                    }
                    case RPAREN: {
                        return ")".equals(symTrim) || ")".equals(opTrim);
                    }
                    default:
                        return false;
                }
            } catch (Throwable ignore) {
                // Never throw; return false on any unexpected issue
                return false;
            }
        }

        public boolean isRightAssociativeHint(String symbol) {
            switch (this) {
                case UNARY_OPERATOR:
                    return true;
                case OPERATOR:
                    return "^".equals(symbol);
                default:
                    return false;
            }
        }

        public boolean isParenthesis() {
            switch (this) {
                case LPAREN:
                case RPAREN:
                    return true;
                default:
                    return false;
            }
        }

        public bc_gpt_5.Bc.TokenType toBcTokenType() {
            switch (this) {
                case NUMBER:
                    return bc_gpt_5.Bc.TokenType.NUMBER;
                case OPERATOR:
                    return bc_gpt_5.Bc.TokenType.OPERATOR;
                case UNARY_OPERATOR:
                    return bc_gpt_5.Bc.TokenType.UNARY_OPERATOR;
                case LPAREN:
                    return bc_gpt_5.Bc.TokenType.LPAREN;
                case RPAREN:
                    return bc_gpt_5.Bc.TokenType.RPAREN;
                default:
                    throw new IllegalStateException("Unsupported TokenType: " + this);
            }
        }

        public boolean isValue() {
            switch (this) {
                case NUMBER:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Returns an arity hint for this token type.
         * Hint values:
         * - UNARY_OPERATOR: 1
         * - OPERATOR: 2
         * - NUMBER, LPAREN, RPAREN: 0
         * This is a constant-time hint; actual arity for OPERATOR depends on the specific operator.
         *
         * @return the arity hint for this token type
         */
        public int arityHint() {
            switch (this) {
                case UNARY_OPERATOR:
                    return 1;
                case OPERATOR:
                    return 2;
                case NUMBER:
                case LPAREN:
                case RPAREN:
                    return 0;
                default:
                    return 0;
            }
        }

        public String normalizeOperator(String input) {
            if (input == null) {
                return null;
            }

            if (this == UNARY_OPERATOR) {
                switch (input) {
                    case "+":
                        return "u+";
                    case "-":
                    case "neg":
                        return "u-";
                    default:
                        return input; // keep exact input for non-aliases (e.g., "u+", "u-", "sqrt", " neg")
                }
            }

            return input;
        }

        public String describe() {
            final String n = name();
            String description;

            // Fixed, human-readable descriptions for known tokens
            if ("OPERATOR".equals(n)) {
                description = "OPERATOR (binary)";
            } else if ("UNARY_OPERATOR".equals(n)) {
                description = "UNARY_OPERATOR (right-associative)";
            } else if ("NUMBER".equals(n) || "LPAREN".equals(n) || "RPAREN".equals(n)) {
                description = n;
            } else {
                // Default: use the enum constant name
                description = n;
            }

            // Ensure conciseness for any unexpected, overly long names
            final int MAX_LEN = 40;
            return description.length() <= MAX_LEN ? description : description.substring(0, MAX_LEN);
        }

        public boolean isUnary() {
            return this == UNARY_OPERATOR;
        }

        public List<String> examples() {
            // Use JVM-wide properties map as a simple global cache to ensure same instance is returned across calls.
            final String cacheKey = "bc_gpt_5.Token.TokenType.examples." + this.name();
            Properties props = System.getProperties();
            Object cached = props.get(cacheKey);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) cached;
                return list;
            }

            final List<String> computed;
            switch (this) {
                case NUMBER:
                    computed = Collections.emptyList(); // shared immutable empty list
                    break;
                case OPERATOR:
                    computed = Collections.unmodifiableList(
                            Arrays.asList("+", "-", "*", "/", "%", "^"));
                    break;
                case UNARY_OPERATOR:
                    computed = Collections.unmodifiableList(
                            Arrays.asList("u+", "u-", "+", "-", "neg"));
                    break;
                case LPAREN:
                    computed = Collections.unmodifiableList(
                            Collections.singletonList("("));
                    break;
                case RPAREN:
                    computed = Collections.unmodifiableList(
                            Collections.singletonList(")"));
                    break;
                default:
                    computed = Collections.emptyList();
            }

            // Cache and return
            props.put(cacheKey, computed);
            return computed;
        }

        public boolean isCompatibleWithOperator(bc_gpt_5.Operator operator) {
            return this == OPERATOR && operator != null && operator.isBinary();
        }

        public boolean isOperator() {
            switch (this) {
                case OPERATOR:
                case UNARY_OPERATOR:
                    return true;
                case NUMBER:
                case LPAREN:
                case RPAREN:
                default:
                    return false;
            }
        }

        public int precedenceHint(String symbol) {
            if (this == UNARY_OPERATOR) {
                return 3;
            }
            if (this == OPERATOR) {
                if (symbol == null) {
                    return -1;
                }
                switch (symbol) {
                    case "+":
                    case "-":
                        return 1;
                    case "*":
                    case "/":
                    case "%":
                        return 2;
                    case "^":
                        return 4;
                    default:
                        return -1;
                }
            }
            return -1;
        }

        public boolean isCompatibleWithUnary(bc_gpt_5.UnaryOperator unary) {
            return unary != null && this == UNARY_OPERATOR;
        }

        public boolean acceptsLexeme(String lexeme) {
            if (lexeme == null) {
                return false;
            }
            switch (this) {
                case NUMBER:
                    return false;
                case OPERATOR:
                    switch (lexeme) {
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
                case UNARY_OPERATOR:
                    switch (lexeme) {
                        case "u+":
                        case "u-":
                        case "+":
                        case "-":
                        case "neg":
                        case "sqrt":
                            return true;
                        default:
                            return false;
                    }
                case LPAREN:
                    return "(".equals(lexeme);
                case RPAREN:
                    return ")".equals(lexeme);
                default:
                    return false;
            }
        }
    }
	
    /**
     * Primary constructor: creates a token with the given type and lexeme.
     * For NUMBER tokens, eagerly parses and caches the BigDecimal; for all other types, the cached number is null.
     *
     * @param type   token type (must not be null)
     * @param lexeme token text (must not be null)
     */
    public Token(TokenType type, String lexeme) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.lexeme = Objects.requireNonNull(lexeme, "lexeme must not be null");

        if (type == TokenType.NUMBER) {
            try {
                this.number = new BigDecimal(lexeme);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric format for lexeme: " + lexeme, ex);
            }
        } else {
            this.number = null;
        }
    }

    public TokenType getType() {
        return type;
    }

    /**
     * Alias for getType() provided for compatibility with reflection-based consumers.
     */
    public TokenType getTokenType() {
        return getType();
    }

    public String getLexeme() {
        return this.lexeme;
    }

    /**
     * Alias for lexeme getter: getText()
     */
    public String getText() {
        return this.lexeme;
    }

    public String symbol() {
        return this.lexeme;
    }

    public String getSymbol() {
        return this.lexeme;
    }

    public String lexeme() {
        return this.lexeme;
    }

    public String text() {
        return this.lexeme;
    }

    @Override
    public String toString() {
        return this.lexeme;
    }

    public String value() {
        return this.lexeme;
    }

    /**
     * Alias for value(): getValue()
     */
    public String getValue() {
        return this.lexeme;
    }

    /**
     * Structured accessor that returns the normalized operator symbol or null for non-operator tokens.
     * - For type == OPERATOR: returns the lexeme as-is.
     * - For type == UNARY_OPERATOR: "+" -> "u+", "-" -> "u-", others unchanged.
     * - For all other types: returns null.
     */
    public String operator() {
        switch (type) {
            case OPERATOR:
                return lexeme;
            case UNARY_OPERATOR:
                if ("+".equals(lexeme)) {
                    return "u+";
                }
                if ("-".equals(lexeme)) {
                    return "u-";
                }
                return lexeme;
            default:
                return null;
        }
    }

    /**
     * Returns the cached BigDecimal only for NUMBER tokens; otherwise null.
     */
    public BigDecimal number() {
        return this.type == TokenType.NUMBER ? this.number : null;
    }

    /**
     * True only for OPERATOR or UNARY_OPERATOR; false otherwise.
     */
    public boolean isOperator() {
        return this.type == TokenType.OPERATOR || this.type == TokenType.UNARY_OPERATOR;
    }

    @Override
    public int hashCode() {
        int result = 31;
        result = 31 * result + type.hashCode();
        result = 31 * result + lexeme.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Token other = (Token) obj;
        return this.type == other.type && this.lexeme.equals(other.lexeme);
    }
}
