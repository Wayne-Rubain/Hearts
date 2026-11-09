package hearts_1;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class PokerHand extends Frame implements ActionListener {
    Button b = new Button("Click for new hand");
    boolean dealt[] = new boolean[52];
    String[] playercard = new String[10];
    String[] playersuit = new String[10];
    Random r = new Random();
    int drawn;

    public static void main(String args[]) {
        PokerHand ph = new PokerHand();
        ph.doIt();
    }

    public void doIt() {
        int ct;
        for (ct = 0; ct <= 9; ++ct) { // first loop that you learned tonight
            playercard[ct] = " ";
            playersuit[ct] = " ";
        } // ends for loop
        b.addActionListener(this);
        this.setLayout(new FlowLayout());
        this.add(b);
        this.setSize(500, 500);
        this.setVisible(true);
    }// ends doIt method

    public void paint(Graphics g) {
        g.setFont(new Font(null, 1, 30));
        g.drawString("Me", 400, 100);
        g.drawString(playercard[0], 30, 100);
        g.drawString(playersuit[0], 60, 100);
        g.drawString(playercard[1], 90, 100);
        g.drawString(playersuit[1], 120, 100);
        g.drawString(playercard[2], 150, 100);
        g.drawString(playersuit[2], 180, 100);
        g.drawString(playercard[3], 210, 100);
        g.drawString(playersuit[3], 240, 100);
        g.drawString(playercard[4], 270, 100);
        g.drawString(playersuit[4], 300, 100);
        g.drawString("You", 400, 200);
        g.drawString(playercard[5], 30, 200);
        g.drawString(playersuit[5], 60, 200);
        g.drawString(playercard[6], 90, 200);
        g.drawString(playersuit[6], 120, 200);
        g.drawString(playercard[7], 150, 200);
        g.drawString(playersuit[7], 180, 200);
        g.drawString(playercard[8], 210, 200);
        g.drawString(playersuit[8], 240, 200);
        g.drawString(playercard[9], 270, 200);
        g.drawString(playersuit[9], 300, 200);
    }

    public void actionPerformed(ActionEvent ae) {
        int ct;
        ct = 0;
        int card;
        int suit;
        // we draw 10 cards here
        while (ct < 10) { // a while loop in practice
            drawn = r.nextInt(52);
            if (dealt[drawn] != true) { // if in practice
                card = drawn % 13 + 1;
                suit = drawn / 13;
                dealt[drawn] = true;
                playercard[ct] = String.valueOf(card);
                if (card == 1) {
                    playercard[ct] = "A";
                }
                if (card == 11) {
                    playercard[ct] = "J";
                }
                if (card == 12) {
                    playercard[ct] = "Q";
                }
                if (card == 13) {
                    playercard[ct] = "K";
                }
                if (suit == 0) {
                    playersuit[ct] = "\u2660";
                }
                if (suit == 1) {
                    playersuit[ct] = "\u2661"; //change to red heart
                }
                if (suit == 2) {
                    playersuit[ct] = "\u2662"; //change to red diamond
                }
                if (suit == 3) {
                    playersuit[ct] = "\u2663";
                }
                ct = ct + 1;
            } // ends if
        }// ends while
        repaint();
        for (int x = 0; x <= 51; ++x)
            dealt[x] = false;
    }// ends method
}// ends the class

