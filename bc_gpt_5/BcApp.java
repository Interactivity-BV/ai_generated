package bc_gpt_5;

import java.math.BigDecimal;
import java.util.List;

public class BcApp {

    private static final String ERROR_PREFIX = "Error: ";

    private final Bc bc;

    public BcApp() {
        this.bc = new Bc();
    }

    public BcApp(Bc bc) {
        if (bc == null) {
            throw new IllegalArgumentException("Bc must not be null");
        }
        this.bc = bc;
    }

    public static void main(String[] args) {
        BcApp app = new BcApp();
        if (args != null && args.length > 0) {
            app.evaluateOnce(String.join(" ", args));
        } else {
            app.repl();
        }
    }

    public void repl() {
        while (true) {
            bc.prompt();
            String line = bc.readLine();
            if (line == null) {
                break; // EOF
            }
            if (bc.isExitCommand(line)) {
                break;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            evaluateAndPrint(trimmed);
        }
    }

    public void evaluateOnce(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            bc.printError(ERROR_PREFIX + "Empty expression");
            return;
        }
        evaluateAndPrint(expression.trim());
    }

    private void evaluateAndPrint(String input) {
        try {
            List<Bc.Token> tokens = bc.tokenize(input);
            List<Bc.Token> rpn = bc.toRpn(tokens);
            BigDecimal result = bc.evaluateRpn(rpn);
            String formatted = bc.formatResult(result);
            bc.printResult(formatted);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Invalid expression";
            }
            bc.printError(ERROR_PREFIX + message);
        }
    }
}
