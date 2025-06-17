package hanoi;
import hanoi.Hanoi;

public class HanoiApp {

    public static void main(String[] args) {
        Hanoi hanoi = new Hanoi();
        
        int numberOfDisks = hanoi.promptUserForNumberOfDisks();
        
        System.out.println("Solving Tower of Hanoi for " + numberOfDisks + " disks:");
        hanoi.solve(numberOfDisks, 'A', 'B', 'C');
        
        hanoi.displayPuzzleSolvedMessage(numberOfDisks);
    }
}