package hearts_1;

import java.util.EnumSet;

import javax.swing.ImageIcon;

import hearts_1.Hearts_1.*;
//import hearts_1.Hearts_1.CardValue;
//import hearts_1.Hearts_1.Suit;


public class Card
{
  public Suit suit;
  public CardValue cardValue;
  public ImageIcon  cardIcon;
  public CardValue2 cardValue2;
 
  public Card (CardValue cardValue, Suit suit, ImageIcon cardIcon,
		       CardValue2 cardValue2)
  {
    this.cardValue 	= cardValue;
    this.suit 		= suit;
    this.cardIcon 	= cardIcon;
    this.cardValue2 = cardValue2;
  }
  public Card (CardValue cardValue, Suit suit, ImageIcon cardIcon)
	   {
	CardValue2 h_cardValue2;
	
	this.cardValue 	= cardValue;
	this.suit 		= suit;
	this.cardIcon 	= cardIcon;
	
	switch (suit.name()) {
	   case "SPADE":
	   		switch (cardValue) {
	   			case TWO:  	h_cardValue2 = CardValue2.S2; break;
	   			case THREE: h_cardValue2 = CardValue2.S3; break;
	   			case FOUR:  h_cardValue2 = CardValue2.S4; break;
	   			case FIVE:  h_cardValue2 = CardValue2.S5; break;
	   			case SIX:  	h_cardValue2 = CardValue2.S6; break;
	   			case SEVEN: h_cardValue2 = CardValue2.S7; break;
	   			case EIGHT: h_cardValue2 = CardValue2.S8; break;
	   			case NINE:  h_cardValue2 = CardValue2.S9; break;
	   			case TEN:  	h_cardValue2 = CardValue2.S10; break;
	   			case JACK:  h_cardValue2 = CardValue2.SJ; break;
	   			case QUEEN: h_cardValue2 = CardValue2.SQ; break;
	   			case KING:  h_cardValue2 = CardValue2.SK; break;
	   			case ACE: 	h_cardValue2 = CardValue2.SA; break;
	   			default:  	h_cardValue2 =  null;  break;
	   			 		}
		   break;
	   case "CLUB":
			switch (cardValue) {
	   			case TWO:  	h_cardValue2 = CardValue2.C2; break;
	   			case THREE: h_cardValue2 = CardValue2.C3; break;
	   			case FOUR:  h_cardValue2 = CardValue2.C4; break;
	   			case FIVE:  h_cardValue2 = CardValue2.C5; break;
	   			case SIX:  	h_cardValue2 = CardValue2.C6; break;
	   			case SEVEN: h_cardValue2 = CardValue2.C7; break;
	   			case EIGHT: h_cardValue2 = CardValue2.C8; break;
	   			case NINE:  h_cardValue2 = CardValue2.C9; break;
	   			case TEN:  	h_cardValue2 = CardValue2.C10; break;
	   			case JACK:  h_cardValue2 = CardValue2.CJ; break;
	   			case QUEEN: h_cardValue2 = CardValue2.CQ; break;
	   			case KING:  h_cardValue2 = CardValue2.CK; break;
	   			case ACE: 	h_cardValue2 = CardValue2.CA; break;
	   			default:  	h_cardValue2 =  null;  break;
   			 		}
		   break;
	   case "HEART":
			switch (cardValue) {
				case TWO:  	h_cardValue2 = CardValue2.H2; break;
	   			case THREE: h_cardValue2 = CardValue2.H3; break;
	   			case FOUR:  h_cardValue2 = CardValue2.H4; break;
	   			case FIVE:  h_cardValue2 = CardValue2.H5; break;
	   			case SIX:  	h_cardValue2 = CardValue2.H6; break;
	   			case SEVEN: h_cardValue2 = CardValue2.H7; break;
	   			case EIGHT: h_cardValue2 = CardValue2.H8; break;
	   			case NINE:  h_cardValue2 = CardValue2.H9; break;
	   			case TEN:  	h_cardValue2 = CardValue2.H10; break;
	   			case JACK:  h_cardValue2 = CardValue2.HJ; break;
	   			case QUEEN: h_cardValue2 = CardValue2.HQ; break;
	   			case KING:  h_cardValue2 = CardValue2.HK; break;
	   			case ACE: 	h_cardValue2 = CardValue2.HA; break;
	   			default:  	h_cardValue2 =  null;  break;
   			 		}
		    break;
	   case "DIAMOND":
			switch (cardValue) {
				case TWO:  	h_cardValue2 = CardValue2.D2; break;
	   			case THREE: h_cardValue2 = CardValue2.D3; break;
	   			case FOUR:  h_cardValue2 = CardValue2.D4; break;
	   			case FIVE:  h_cardValue2 = CardValue2.D5; break;
	   			case SIX:  	h_cardValue2 = CardValue2.D6; break;
	   			case SEVEN: h_cardValue2 = CardValue2.D7; break;
	   			case EIGHT: h_cardValue2 = CardValue2.D8; break;
	   			case NINE:  h_cardValue2 = CardValue2.D9; break;
	   			case TEN:  	h_cardValue2 = CardValue2.D10; break;
	   			case JACK:  h_cardValue2 = CardValue2.DJ; break;
	   			case QUEEN: h_cardValue2 = CardValue2.DQ; break;
	   			case KING:  h_cardValue2 = CardValue2.DK; break;
	   			case ACE: 	h_cardValue2 = CardValue2.DA; break;
	   			default:  	h_cardValue2 =  null;  break;
	   			
   			 		}
	   default:  h_cardValue2 =  null;  break;
	   }
	this.cardValue2 = h_cardValue2;
}
  public Suit getSuit()
  {
    return suit;
  }
  
  public ImageIcon  getIcon()
  {
	  return cardIcon;
  }
 
  public void setSuit(Suit suit)
  {
    this.suit = suit;
  }
  
  public void setIcon(ImageIcon icon)
  {
	  this.cardIcon = icon;
  }
  
  public void setSuit(String in_string) {
	  switch (in_string) {
	  case "CLUB":		this.suit = Suit.CLUB;		break;
	  case "SPADE":		this.suit = Suit.SPADE;		break;
	  case "HEART":		this.suit = Suit.HEART;		break;
	  case "DIAMOND":	this.suit = Suit.DIAMOND;	break;
	  }
  }
 
// public Hearts_1.CardValue getCardValue()
  public CardValue getCardValue()
  {
    return cardValue;
  }
 
  public void setCardValue(CardValue cardValue)
  {
    this.cardValue = cardValue;
  }
  public void setCardValue(String in_string) {
	  switch (in_string) {
	  case "ACE":		this.cardValue = CardValue.ACE;			break;
	  case "TWO":		this.cardValue = CardValue.TWO;			break;
	  case "THREE":		this.cardValue = CardValue.THREE;		break;
	  case "FOUR":		this.cardValue = CardValue.FOUR;		break;
	  case "FIVE":		this.cardValue = CardValue.FIVE;		break;
	  case "SIX":		this.cardValue = CardValue.SIX;			break;
	  case "SEVEN":		this.cardValue = CardValue.SEVEN;		break;
	  case "EIGHT":		this.cardValue = CardValue.EIGHT;		break;
	  case "NINE":		this.cardValue = CardValue.NINE;		break;
	  case "TEN":		this.cardValue = CardValue.TEN;			break;
	  case "JACK":		this.cardValue = CardValue.JACK;		break;
	  case "QUEEN":		this.cardValue = CardValue.QUEEN;		break;
	  case "KING":		this.cardValue = CardValue.KING;		break;
	  
	  }
  }  
  public void setCardValue2(CardValue2 cardValue2)
  {
    this.cardValue2 = cardValue2;
  }
//  public void setCardValue2(CardValue2 cardValue2)
  	public void setCardValue2(String in_str) {
  		
  		CardValue2 hh_cardValue2;
  		
  		switch (in_str) {
 	    		case "S2": 	hh_cardValue2 = CardValue2.S2; break;
 	   			case "S3": 	hh_cardValue2 = CardValue2.S3; break;
 	   			case "S4":  hh_cardValue2 = CardValue2.S4; break;
 	   			case "S5":  hh_cardValue2 = CardValue2.S5; break;
 	   			case "S6": 	hh_cardValue2 = CardValue2.S6; break;
 	   			case "S7": 	hh_cardValue2 = CardValue2.S7; break;
 	   			case "S8": 	hh_cardValue2 = CardValue2.S8; break;
 	   			case "S9":  hh_cardValue2 = CardValue2.S9; break;
 	   			case "ST":  hh_cardValue2 = CardValue2.S10; break;
 	   			case "SJ":  hh_cardValue2 = CardValue2.SJ; break;
 	   			case "SQ":  hh_cardValue2 = CardValue2.SQ; break;
 	   			case "SK":  hh_cardValue2 = CardValue2.SK; break;
 	   			case "SA": 	hh_cardValue2 = CardValue2.SA; break;
 	   			case "C2": 	hh_cardValue2 = CardValue2.C2; break;
 	   			case "C3": 	hh_cardValue2 = CardValue2.C3; break;
				case "C4":  hh_cardValue2 = CardValue2.C4; break;
				case "C5":  hh_cardValue2 = CardValue2.C5; break;
				case "C6": 	hh_cardValue2 = CardValue2.C6; break;
				case "C7": 	hh_cardValue2 = CardValue2.C7; break;
				case "C8": 	hh_cardValue2 = CardValue2.C8; break;
				case "C9":  hh_cardValue2 = CardValue2.C9; break;
				case "CT":  hh_cardValue2 = CardValue2.C10; break;
				case "CJ":  hh_cardValue2 = CardValue2.CJ; break;
				case "CQ":  hh_cardValue2 = CardValue2.CQ; break;
				case "CK":  hh_cardValue2 = CardValue2.CK; break;
				case "CA": 	hh_cardValue2 = CardValue2.CA; break;
				case "H2": 	hh_cardValue2 = CardValue2.H2; break;
 	   			case "H3": 	hh_cardValue2 = CardValue2.H3; break;
				case "H4":  hh_cardValue2 = CardValue2.H4; break;
				case "H5":  hh_cardValue2 = CardValue2.H5; break;
				case "H6": 	hh_cardValue2 = CardValue2.H6; break;
				case "H7": 	hh_cardValue2 = CardValue2.H7; break;
				case "H8": 	hh_cardValue2 = CardValue2.H8; break;
				case "H9":  hh_cardValue2 = CardValue2.H9; break;
				case "HT":  hh_cardValue2 = CardValue2.H10; break;
				case "HJ":  hh_cardValue2 = CardValue2.HJ; break;
				case "HQ":  hh_cardValue2 = CardValue2.HQ; break;
				case "HK":  hh_cardValue2 = CardValue2.HK; break;
				case "HA": 	hh_cardValue2 = CardValue2.HA; break;
				case "D2": 	hh_cardValue2 = CardValue2.D2; break;
 	   			case "D3": 	hh_cardValue2 = CardValue2.D3; break;
				case "D4":  hh_cardValue2 = CardValue2.D4; break;
				case "D5":  hh_cardValue2 = CardValue2.D5; break;
				case "D6": 	hh_cardValue2 = CardValue2.D6; break;
				case "D7": 	hh_cardValue2 = CardValue2.D7; break;
				case "D8": 	hh_cardValue2 = CardValue2.D8; break;
				case "D9":  hh_cardValue2 = CardValue2.D9; break;
				case "DT":  hh_cardValue2 = CardValue2.D10; break;
				case "DJ":  hh_cardValue2 = CardValue2.DJ; break;
				case "DQ":  hh_cardValue2 = CardValue2.DQ; break;
				case "DK":  hh_cardValue2 = CardValue2.DK; break;
				case "DA": 	hh_cardValue2 = CardValue2.DA; break;
 	   			default:  	hh_cardValue2 =  null;  break;
 	   	
    			 		}
  		 this.cardValue2 = hh_cardValue2;
 		 
  }
 
  public Card getCard()
  {/*
	  System.out.println("getCard " + this.cardValue + this.suit);  */
	  return this;
  }
  public void putCard(Card in_card)
  {
	  this.setCardValue(in_card.cardValue);
	  this.setSuit(in_card.suit);
	  this.setIcon(in_card.cardIcon);
	  this.setCardValue2(cardValue2);
  }
}
 
