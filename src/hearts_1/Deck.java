package hearts_1;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.util.ArrayList;
import javax.swing.ImageIcon;

import hearts_1.Hearts_1.CardValue2;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;

/**
 *
 * @author Wayne
 */
public class Deck   {


private ArrayList<Card> deck;

//  EnumSet<CardValue2> deck2 = EnumSet.noneOf(CardValue2.class);

//	SortedSet<Card> deck = TreeSet<Card>(;)
 
  
private static int n_p, e_p, s_p, w_p;
  

 
/*	private Hand north_hand;   */
	
	
	
	
	
 private static int dealer_hold;
 

 
 
public static ImageIcon get_icon1 (Card in_card) {
		
		ImageIcon ii   = null;
		ImageIcon icon = null;
		
//		System.out.println("***** Getting Icon *******"); 

		
		String imagedir = "C:/JAVA/BIN/";
		String h_value = null, h_suit, h_fileName;

		switch (in_card.cardValue.toString())  {
			case "TWO":		h_value = "2"; 	break;
			case "THREE":	h_value = "3"; 	break;
			case "FOUR":	h_value = "4"; 	break;
			case "FIVE":	h_value = "5"; 	break;
			case "SIX":		h_value = "6"; 	break;
			case "SEVEN":	h_value = "7"; 	break;
			case "EIGHT":	h_value = "8"; 	break;
			case "NINE":	h_value = "9"; 	break;
			case "TEN":		h_value = "T"; 	break;
			case "JACK":	h_value = "J"; 	break;
			case "QUEEN":	h_value = "Q"; 	break;
			case "KING":	h_value = "K"; 	break;
			case "ACE":		h_value = "A"; 	break;
			}
						 
		h_suit	 = in_card.suit.toString();
		h_fileName = h_value + h_suit.substring(0, 1);
//		h_a = h_fileName;
		imagedir = imagedir + h_fileName + ".gif";
	
		icon = new ImageIcon(imagedir, h_fileName);
			     
		
		ii = icon;
				
		return ii;
	}
		
		
 
  public Deck ()
  {
	  	  
	ImageIcon icon; 
	String h_value = null, h_suit, h_fileName;
		  
    this.deck = new ArrayList<Card>();
    
    for (int j=0; j<4; j++)
        for (int i=0; i<13; i++)
    {
      Hearts_1.CardValue value = Hearts_1.CardValue.values()[i];
      {
    	Card card = new Card(value, Hearts_1.Suit.values()[j], null, null);
        h_suit 	= (card.suit.toString()).substring(0, 1);

        switch (card.cardValue.toString())  {
		case "TWO":		h_value = "2"; 	break;
		case "THREE":	h_value = "3"; 	break;
		case "FOUR":	h_value = "4"; 	break;
		case "FIVE":	h_value = "5"; 	break;
		case "SIX":		h_value = "6"; 	break;
		case "SEVEN":	h_value = "7"; 	break;
		case "EIGHT":	h_value = "8"; 	break;
		case "NINE":	h_value = "9"; 	break;
		case "TEN":		h_value = "T"; 	break;
		case "JACK":	h_value = "J"; 	break;
		case "QUEEN":	h_value = "Q"; 	break;
		case "KING":	h_value = "K"; 	break;
		case "ACE":		h_value = "A"; 	break;
		}
	    
    	card.setCardValue2(h_suit + h_value);
        icon = get_icon1(card); 
        card.setIcon(icon);
        this.deck.add(card);
       
      }
    }
//    System.out.println("****** New Deck (in Deck)   *******"); 
      this.display();
      
  }  
  public Card  get_card(int i) {
	return  this.deck.get(i);
  }
  
  
   /** Shuffle with a caller-supplied generator, silently. Added 2026-10-09
    * for paired A/B simulations: seeding this (and AI.RNG) lets the same
    * deal be replayed under two versions of a tactic. Not used by the game. */
   void shuffle_now(java.util.Random rng)
   {
	Collections.shuffle(deck, rng);
   }

   public void shuffle_now()
   {
	
	Collections.shuffle(deck);

    System.out.println("****** Shuffled Deck I   *******"); 
    this.display();
    
        }
  
   
    public int find_card(Card in_card)
    {
    	int yy = -1;
    
    	int x;
    	for (x = 0; x < 52; x++)
    	    if (this.get_card(x).cardValue == in_card.cardValue
    	    && this.get_card(x).suit      == in_card.suit)
    	    {
    	    	yy = x;
    	    	break;
    	    }
    	    	return yy;
    	 //   	return x;
    	    			    }
    
    
    public void display()
    {
    	Iterator<Card> cardIterator = deck.iterator();
        /*    cardIterator = deck.listIterator(0);    */
         
        while (cardIterator.hasNext())
        {
          Card aCard = cardIterator.next();
          System.out.println(aCard.getCardValue() + " of " + aCard.getSuit());
        }	
    }
    
    public void import_deck (Deck in_deck)
    {
    	this.deck.clear();
    	
    	
    	this.deck.addAll(in_deck.deck);
    	
    	
    //	for (int x=0; x<52; x++)
    //		this.deck.add(in_deck.deck.get(x));
                	
   
    System.out.println("****** Imported Deck   *******"); 
    this.display();
    
    	}
    

	
/*	private  Hearts_1.PLAYER Who_Deals_Next;  */
	
	
	public All_Hands deal (Hearts_1.PLAYER dealer, Deck in_deck)
	{
		
		switch (dealer)	{
		case NORTH:
			n_p  =  1;
			e_p  =  2;
        	s_p  =  3;
        	w_p  =  4;
        	dealer_hold = 1;
        	break;
		case EAST:
			n_p  =  4;
			e_p  =  1;
        	s_p  =  2;
        	w_p  =  3;
        	dealer_hold = 2;
			break;
		case SOUTH:
			n_p  =  3;
			e_p  =  4;
        	s_p  =  1;
        	w_p  =  2;
        	dealer_hold = 3;
			break;
		case WEST:
			n_p  =  2;
			e_p  =  3;
        	s_p  =  4;
        	w_p  =  1;
        	dealer_hold = 4;
			break;
				
			
		}
		return  this.deal(dealer_hold, in_deck);
	
	}
	
	public All_Hands deal (int dealer_nbr, Deck in_deck) {
	
	
		 
		{
		
/*		Hand north_hand 	= new Hand();
		Hand east_hand 		= new Hand();
		Hand south_hand 	= new Hand();
		Hand west_hand 		= new Hand();
		
		
		
	*/  
		final All_Hands a_hands;  
		
	/*	final Hand h_hand;  */ 
		
			
		 a_hands = new All_Hands();
		 Hand h_n_hand = new Hand();
		 Hand h_e_hand = new Hand();
		 Hand h_s_hand = new Hand();
		 Hand h_w_hand = new Hand();
		 Hand h_n_hand2 = new Hand();
		 Hand h_e_hand2 = new Hand();
		 Hand h_s_hand2 = new Hand();
		 Hand h_w_hand2 = new Hand();
			
		for   (int x=1; x<14; x++)
			
		{	
			h_n_hand.addX(in_deck.get_card(((x-1) * 4 - 1) + n_p));  
			h_e_hand.addX(in_deck.get_card(((x-1) * 4 - 1) + e_p));
			h_s_hand.addX(in_deck.get_card(((x-1) * 4 - 1) + s_p));
			h_w_hand.addX(in_deck.get_card(((x-1) * 4 - 1) + w_p));
							
			}
			
	/*	a_hands.n_hand(h_n_hand);
		a_hands.e_hand(h_e_hand);
		a_hands.s_hand(h_s_hand);
		a_hands.w_hand(h_w_hand);   */
		
		h_n_hand2 = Hand.sortem2(h_n_hand);
		h_e_hand2 = Hand.sortem2(h_e_hand);
		h_s_hand2 = Hand.sortem2(h_s_hand);
		h_w_hand2 = Hand.sortem2(h_w_hand);
	
		System.out.println("*****   first sortem  Deck (In deal) North *******");
		h_n_hand2.display();
		System.out.println("*****   first sortem  Deck (In deal) East *******");
		h_e_hand2.display();
		System.out.println("*****   first sortem  Deck (In deal) South *******");
		h_s_hand2.display();
		System.out.println("*****   first sortem  Deck (In deal) West *******");
		h_w_hand2.display();
		System.out.println("*****   first sortem  Deck end (In deal)*******");
		
		a_hands.n_hand(h_n_hand2);
		a_hands.e_hand(h_e_hand2);
		a_hands.s_hand(h_s_hand2);
		a_hands.w_hand(h_w_hand2);   
		         
		return a_hands;
		}
	   
	}
	
//	 public void display_hands(All_Hands a_hands)
//	    {
//		 final Hand in_hand;
//		 
//		 for   (int x=1; x<5; x++)
//		 {
//			 for (int y=1; x<14; y++)
//			 {
//				 
//				 Iterator<Card> cardIterator = a_hands.n_hand(in_hand).iterator();	 
//	    	Iterator<Card> cardIterator = a_hands.n_hand(in_hand).iterator();
//	        /*    cardIterator = deck.listIterator(0);    */
//	         
//	        while (cardIterator.hasNext())
//	        {
//	          Card aCard = cardIterator.next();
//	          System.out.println(aCard.getCardValue() + " of " + aCard.getSuit());
//	        }
//			 }
//		 }
//	    }

    
    
    }
    
   
  
  

  
  


