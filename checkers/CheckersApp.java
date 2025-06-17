package checkers;
import checkers.Checkers;
import checkers.Checkers.Move;

import java.util.List;
import java.util.Scanner;

public class CheckersApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the number of checkers (n): ");
        int n = scanner.nextInt();

        Checkers checkers = new Checkers(n);
        String initialBoard = checkers.initBoard(n);
        String goalBoard = checkers.goalBoard(n);

        System.out.println("Initial Board: " + initialBoard);
        System.out.println("Goal Board: " + goalBoard);

        List<Move> solution = checkers.solveBFS(initialBoard, goalBoard);

        if (solution.isEmpty()) {
            System.out.println("No solution found.");
        } else {
            checkers.printMoves(solution);
        }
    }
}