package unitagent.bc;

import unitagent.bc.Bc;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BcApp - a small application wrapper around the provided Bc calculator class.
 *
 * Usage:
 *  - No arguments: start interactive mode (delegates to Bc.run()).
 *  - -s N or --scale N : set numeric scale to N (non-negative integer).
 *  - --init : run Bc.Calculator() initialization helper (optional).
 *  - --run  : explicitly start interactive run loop.
 *  - Any other arguments are treated as a single expression to evaluate (joined with spaces).
 *
 * The class instantiates itself in main and uses only public Bc methods.
 */
public class BcApp {

    public static void main(String[] args) {
        BcApp app = new BcApp();
        app.start(args == null ? new String[0] : args);
    }

    /**
     * Entry point for application logic. Parses simple options then either evaluates a single
     * expression or starts the interactive loop provided by Bc.
     *
     * This method ensures Bc.close() is called to release resources.
     *
     * @param args command-line arguments
     */
    public void start(String[] args) {
        Bc bc = new Bc();
        try {
            int idx = 0;
            boolean requestedRun = false;
            // parse simple options
            while (idx < args.length && args[idx].startsWith("-")) {
                String opt = args[idx];
                if ("-s".equals(opt) || "--scale".equals(opt)) {
                    if (idx + 1 >= args.length) {
                        System.out.println("Error: missing scale value after " + opt);
                        return;
                    }
                    String val = args[idx + 1];
                    try {
                        int scale = Integer.parseInt(val);
                        bc.setScale(scale);
                    } catch (NumberFormatException nfe) {
                        System.out.println("Error: invalid scale value: " + val);
                        return;
                    } catch (IllegalArgumentException | IllegalStateException iae) {
                        System.out.println("Error: could not set scale: " + iae.getMessage());
                        return;
                    }
                    idx += 2;
                } else if ("--init".equals(opt)) {
                    // initialize resources (may prepare scanner field)
                    try {
                        bc.Calculator();
                    } catch (Exception e) {
                        System.out.println("Error: initialization failed");
                        return;
                    }
                    idx++;
                } else if ("--run".equals(opt)) {
                    requestedRun = true;
                    idx++;
                } else {
                    System.out.println("Error: unknown option: " + opt);
                    printUsage();
                    return;
                }
            }

            // If any non-option arguments remain, treat them as a single expression to evaluate.
            if (idx < args.length) {
                String expr = String.join(" ", Arrays.copyOfRange(args, idx, args.length)).trim();
                if (expr.isEmpty()) {
                    System.out.println("Error: empty expression");
                    return;
                }
                evaluateAndPrint(bc, expr);
                return;
            }

            // No expression provided: if user requested run or no args -> enter interactive mode
            if (requestedRun || args.length == 0) {
                bc.run();
            } else {
                // Fallback: if reached here for any reason, start interactive loop
                bc.run();
            }
        } finally {
            try {
                bc.close();
            } catch (Exception ignored) {
                // close is expected to be safe and idempotent; ignore any exceptions here
            }
        }
    }

    /**
     * Evaluate a single expression string using only public Bc methods and print the result or
     * an appropriate error message.
     *
     * @param bc   Bc instance
     * @param expr expression string
     */
    public void evaluateAndPrint(Bc bc, String expr) {
        try {
            List<String> tokens = bc.tokenize(expr);
            List<String> rpn = bc.toRPN(tokens);
            BigDecimal result = new BigDecimal(bc.evaluateRPN(rpn));
            String formatted = bc.formatResult(result);
            if (formatted != null && !formatted.isEmpty()) {
                System.out.println(formatted);
            } else if (result != null) {
                System.out.println(result.toPlainString());
            } else {
                System.out.println("Error: no result");
            }
        } catch (ArithmeticException ae) {
            // Common predictable arithmetic error (e.g., division by zero)
            System.out.println("Error: division by zero");
        } catch (ParseException | IllegalArgumentException | UnsupportedOperationException ex) {
            System.out.println("Error: invalid expression");
        } catch (Exception ex) {
            System.out.println("Error: could not evaluate expression");
        }
    }

    /**
     * Print simple usage information.
     */
    public void printUsage() {
        String usage = "Usage:\n" +
                "  java BcApp                # interactive mode\n" +
                "  java BcApp --run          # interactive mode\n" +
                "  java BcApp -s N <expr>    # set scale N and evaluate expression\n" +
                "  java BcApp <expr>         # evaluate single expression\n" +
                "Options:\n" +
                "  -s, --scale N   Set decimal scale (non-negative integer)\n" +
                "  --init          Run initialization helper (optional)\n" +
                "  --run           Start interactive run loop\n";
        System.out.println(usage);
    }
}