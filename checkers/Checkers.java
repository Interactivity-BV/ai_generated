package checkers;

import java.util.*;

public class Checkers {

    private int n;
    private String initialBoard;
    private String goalBoard;
    private Queue<String> queue;
    private Set<String> visited;
    private Map<String, Move> moveMap;

    public Checkers(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be an integer greater than or equal to 1.");
        }
        this.n = n;
        this.initialBoard = initBoard(n);
        this.goalBoard = goalBoard(n);
        this.queue = new LinkedList<>();
        this.visited = new HashSet<>();
        this.moveMap = new HashMap<>();
    }

    public String applyMove(String board, Move move) {
        char[] boardArray = board.toCharArray();
        char temp = boardArray[move.getFrom()];
        boardArray[move.getFrom()] = boardArray[move.getTo()];
        boardArray[move.getTo()] = temp;
        return new String(boardArray);
    }

    public List<Move> getValidMoves(String board) {
        List<Move> validMoves = new ArrayList<>();
        int emptyIndex = board.indexOf('_');

        for (int i = 0; i < board.length(); i++) {
            if (board.charAt(i) == 'R') {
                if (i + 1 == emptyIndex) {
                    validMoves.add(new Move(i, i + 1));
                } else if (i + 2 == emptyIndex && board.charAt(i + 1) == 'B') {
                    validMoves.add(new Move(i, i + 2));
                }
            } else if (board.charAt(i) == 'B') {
                if (i - 1 == emptyIndex) {
                    validMoves.add(new Move(i, i - 1));
                } else if (i - 2 == emptyIndex && board.charAt(i - 1) == 'R') {
                    validMoves.add(new Move(i, i - 2));
                }
            }
        }

        return validMoves;
    }

    public String goalBoard(int n) {
        StringBuilder goalBoardBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            goalBoardBuilder.append('B');
        }
        goalBoardBuilder.append('_');
        for (int i = 0; i < n; i++) {
            goalBoardBuilder.append('R');
        }
        return goalBoardBuilder.toString();
    }

    public String initBoard(int n) {
        StringBuilder initialBoard = new StringBuilder();
        for (int i = 0; i < n; i++) {
            initialBoard.append('R');
        }
        initialBoard.append('_');
        for (int i = 0; i < n; i++) {
            initialBoard.append('B');
        }
        return initialBoard.toString();
    }

    public List<Move> solveBFS() {
        return solveBFS(this.initialBoard, this.goalBoard);
    }

    public List<Move> solveBFS(String start, String goal) {
        if (start.equals(goal)) {
            return Collections.emptyList();
        }

        queue.add(start);
        visited.add(start);
        Map<String, String> parentMap = new HashMap<>();

        while (!queue.isEmpty()) {
            String currentBoard = queue.poll();
            List<Move> validMoves = getValidMoves(currentBoard);

            for (Move move : validMoves) {
                String newBoard = applyMove(currentBoard, move);

                if (!visited.contains(newBoard)) {
                    queue.add(newBoard);
                    visited.add(newBoard);
                    moveMap.put(newBoard, move);
                    parentMap.put(newBoard, currentBoard);

                    if (newBoard.equals(goal)) {
                        return constructSolution(goal, moveMap, parentMap);
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private List<Move> constructSolution(String goal, Map<String, Move> moveMap, Map<String, String> parentMap) {
        List<Move> solution = new LinkedList<>();
        String current = goal;

        while (parentMap.containsKey(current)) {
            Move move = moveMap.get(current);
            solution.add(0, move);
            current = parentMap.get(current);
        }

        return solution;
    }

    public void printMoves(List<Move> moves) {
        if (moves == null || moves.isEmpty()) {
            System.out.println("No moves to display.");
            return;
        }

        int moveNumber = 1;
        for (Move move : moves) {
            System.out.printf("Move %d: from %d to %d%n", moveNumber, move.getFrom(), move.getTo());
            moveNumber++;
        }

        System.out.printf("Total number of moves: %d%n", moves.size());
    }

    public static class Move {
        private final int from;
        private final int to;

        public Move(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Move move = (Move) obj;
            return from == move.from && to == move.to;
        }

        @Override
        public int hashCode() {
            return 31 * from + to;
        }
    }
}