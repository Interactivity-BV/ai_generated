package hanoi;

import java.util.Scanner;
import java.util.Stack;

public class Hanoi {

    private int numberOfDisks;
    private Stack<Integer> pegA;
    private Stack<Integer> pegB;
    private Stack<Integer> pegC;

    public Hanoi() {
        pegA = new Stack<>();
        pegB = new Stack<>();
        pegC = new Stack<>();
    }

    public void displayPuzzleSolvedMessage(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Number of disks must be greater than or equal to 1.");
        }
        int totalMoves = (int) Math.pow(2, n) - 1;
        System.out.println("Puzzle solved in " + totalMoves + " moves.");
    }

    public void solve(int n, char source, char auxiliary, char target) {
        if (n == 1) {
            moveDisk(1, source, target);
        } else {
            solve(n - 1, source, target, auxiliary);
            moveDisk(n, source, target);
            solve(n - 1, auxiliary, source, target);
        }
    }

    public void moveDisk(int disk, char fromPeg, char toPeg) {
        System.out.println("Move disk " + disk + " from " + fromPeg + " to " + toPeg);
    }

    public int promptUserForNumberOfDisks() {
        Scanner scanner = new Scanner(System.in);
        int numberOfDisks = 0;

        while (true) {
            System.out.print("Enter the number of disks (minimum 1): ");
            String input = scanner.nextLine();

            try {
                numberOfDisks = Integer.parseInt(input);
                if (numberOfDisks >= 1) {
                    break;
                } else {
                    System.out.println("Please enter a number greater than or equal to 1.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid integer.");
            }
        }

        return numberOfDisks;
    }
}