package hearts_1;



import java.util.ArrayList;


import java.util.Collections;
import java.util.Iterator;
import java.util.Random;

import javax.print.DocFlavor.STRING;
import javax.swing.plaf.basic.BasicLabelUI;
/**
 *
 * @author Wayne
 */

public class Hearts_1 {
	


 public enum CardValue2 {
	 H2,H3,H4,H5,H6,H7,H8,H9,H10,HJ,HQ,HK,HA,
	 S2,S3,S4,S5,S6,S7,S8,S9,S10,SJ,SQ,SK,SA,
	 D2,D3,D4,D5,D6,D7,D8,D9,D10,DJ,DQ,DK,DA,
	 C2,C3,C4,C5,C6,C7,C8,C9,C10,CJ,CQ,CK,CA;
	 
//	 private int cardValue;
//	  
//	  private CardValue (int value)
//	  {
//	    this.cardValue = value;
//	  }
//	 
//	  public int getCardValue() {
//	    return cardValue;
//	  }
	 
	 
 }

 public enum displayInfoBoxes
  {
	  DEAL,
	  PASS,
	  WEST,
	  NORTH,
	  EAST,
	  SOUTH, 
	  HAND;
  }
	  

public enum  Suit {
        HEART,
        SPADE, 
        DIAMOND,
        CLUB;  
    }



    
public enum CardValue
{
  TWO(2),
  THREE(3),
  FOUR(4),
  FIVE(5),
  SIX(6),
  SEVEN(7),
  EIGHT(8),
  NINE(9),
  TEN(10),
  JACK(11),
  QUEEN(12),
  KING(13),
  ACE(14);
	
  private int cardValue;
  
  private CardValue (int value)
  {
    this.cardValue = value;
  }
 
  public int getCardValue() {
    return cardValue;
  }
  

}


//  public void applyTraits(set<Player_Traits>Player_Traits) {}

//EnumSet - a modern replacement for bit fields 


//  public All_Hands all_hands ;
   

public enum PASS_DIRECTION {
	LEFT(1),
	RIGHT(2),
	ACROSS(3),
	KEEP(4);
	
    private int value;
	
	private PASS_DIRECTION (int value) {
		this.value = value;
	}
	public int getIt() {
	    return this.value;
	}
	public PASS_DIRECTION Next_Direction() {
		return this.ordinal() < PASS_DIRECTION.values().length - 1
			? PASS_DIRECTION.values()[this.ordinal() + 1]
//			: null;
			: PASS_DIRECTION.values()[this.ordinal()- 3];
	}
}
   
public enum PLAYER {
	NORTH(1),
	EAST(2),
	SOUTH(3),
	WEST(4);
	
	private int value;
	
	private PLAYER(int value) {
		this.value = value;
	}
	public int getIt() {
	    return this.value;
	}
	public PLAYER Next_Dealer() {
		return this.ordinal() < PLAYER.values().length - 1
			? PLAYER.values()[this.ordinal() + 1]
//			: null;
			: PLAYER.values()[this.ordinal()- 3];
	}
	
}


public PLAYER Who_Deals, N_Player, E_Player, S_Player, W_Player, Dealer, Lead;


//public VTextIcon(Component component, String label) {
//	this(component, label, ROTATE_DEFAULT);
//}
//
//	public VTextIcon(Component component, String label, 
//			 int rotateHint) {
//	fComponent = component;
//	fLabel = label;
//	fRotation = verifyRotation(label, rotateHint);
//	calcDimensions();
//	fComponent.addPropertyChangeListener(this);
//}
//
//public void setLabel(String label) {
//	fLabel = label;
//	// Make sure the current rotation is still legal
//	fRotation = verifyRotation(label, fRotation); 
//	recalcDimensions();
//}





private static Hearts_JFrame h_jframe;


    /*
    /* Creating all possible cards... */
  
 
//	// TODO code application logic here
//	        
//	        
//	    
//
//	    }
//	}


    
   

    /**
     * @param args the command line arguments
     */

    public static void main(String[] args)  {
    	
		Hearts_JFrame.main(null);
    	
   
    
        
    

    }
}
