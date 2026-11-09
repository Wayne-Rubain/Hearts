package hearts_1;

import java.util.EnumSet;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.CardValue2;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;



public class All_Hands  {

	
	
	public Hand	n_hand, e_hand,	s_hand,	w_hand;
	
//	EnumSet<CardValue2> n_hand2 = EnumSet.noneOf(CardValue2.class);
//	EnumSet<CardValue2> e_hand2 = EnumSet.noneOf(CardValue2.class);
//	EnumSet<CardValue2> s_hand2 = EnumSet.noneOf(CardValue2.class);
//	EnumSet<CardValue2> w_hand2 = EnumSet.noneOf(CardValue2.class);
	
	
	public All_Hands () {
		
	}
	public Card deuce_club = new Card(CardValue.TWO,Suit.CLUB, null, CardValue2.C2);
	
	public void n_hand (Hand in_hand)
		{  this.n_hand =     in_hand; }
	public void e_hand (Hand in_hand)
		{  this.e_hand =     in_hand; }
	public void s_hand (Hand in_hand)
		{  this.s_hand =     in_hand; }
	public void w_hand (Hand in_hand)
		{  this.w_hand =     in_hand; }
	
	public Hand n_hand_get (All_Hands in_hand)
		{ final Hand h_deck;
	                 h_deck = in_hand.n_hand;
	          return h_deck; }
	
	public Hand e_hand_get (All_Hands in_hand)
		{ final Hand h_deck;
	                 h_deck = in_hand.e_hand;
	          return h_deck; }
	
	public Hand s_hand_get (All_Hands in_hand)
		{ final Hand h_deck;
		             h_deck = in_hand.s_hand;
		  	  return h_deck; }
	
	public Hand w_hand_get (All_Hands in_hand)
		{ final Hand h_deck;
		             h_deck = in_hand.w_hand;
		      return h_deck; }
	
	public  PLAYER Find_Lead (All_Hands in_all_hands) {
		
		Hand h_hand = null;
		PLAYER out_player = null;
		
		h_hand = this.n_hand_get(in_all_hands);
		
		if (h_hand.find_card(deuce_club) >= 0) {
			out_player = PLAYER.NORTH;
			System.out.println("+++++++++++++++++North has duece club ");
		} 
		 
		h_hand = this.e_hand_get(in_all_hands);
		if (h_hand.find_card(deuce_club) >= 0) {
			out_player = PLAYER.EAST;
			System.out.println("++++++++++++++++East has duece club ");
		} 
		h_hand = this.s_hand_get(in_all_hands);	
		if (h_hand.find_card(deuce_club) >= 0) {
			out_player = PLAYER.SOUTH;
			System.out.println("++++++++++++++South has duece club ");
		} 
			
		 h_hand = this.w_hand_get(in_all_hands);
		if (h_hand.find_card(deuce_club) >= 0) {
			out_player = PLAYER.WEST; 
			System.out.println("++++++++++++++West has duece club ");	
		}
		
		return out_player;
	}
	
		
	
}  

