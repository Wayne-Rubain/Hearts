package hearts_1;

import java.util.ArrayList;
import hearts_1.Hearts_1.CardValue2;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;

public  class Hand  {

	public ArrayList<Card> hand;
	EnumSet<CardValue2> hand2 = EnumSet.noneOf(CardValue2.class);	
	

	public  Hand() {
		this.hand = new ArrayList<Card>();
//		ArrayList<Card> list = new ArrayList<Card>(this.hand);
//		Collections.sort(list);

	}

	public void addX(Card in_card) {
	    this.hand.add(in_card);
	  }

	public void display() {
		Iterator<Card> cardIterator = hand.iterator();
		/* cardIterator = deck.listIterator(0); */

		while (cardIterator.hasNext()) {
			Card aCard = cardIterator.next();
			System.out.println(aCard.getCardValue() + " of " + aCard.getSuit());
		}
	}

	public Card get_it(int i) {
		System.out
				.println("in hand get_card " + this.hand.get(i).getCard().cardValue + this.hand.get(i).getCard().suit);
		return this.hand.get(i).getCard();
	}

	public int find_card(Card in_card) {
		int x = 0;
		int yy = -1;
//		this.hand.remove(in_card);
		for (x = 0; x < this.hand.size(); x++)
			if (this.get_it(x).cardValue == in_card.cardValue && this.get_it(x).suit == in_card.suit) {
				yy = x;
				break;
			}
		return yy;
	}

	public void pull_card(Card in_card) {
		int x;
	
		x = this.find_card(in_card);
		this.hand.remove(x);
	}

	public void pull_card(int x) {
		this.hand.remove(x);

	}

	public void display_hand(Hand in_hand) {
		/* final Hand in_hand; */

		Iterator<Card> cardIterator = in_hand.hand.listIterator(0);
		/* cardIterator = deck.listIterator(0); */

		while (cardIterator.hasNext()) {
			Card aCard = cardIterator.next();
			System.out.println(aCard.getCardValue() + " of " + aCard.getSuit());
		}
	}

	public static Hand sortem2(Hand in_hand) {

		Card h_card;
		Integer count_1, z;

		Hand h_hand = new Hand();

		h_hand.addX(in_hand.get_it(0));
		
     	count_1 = 0;

		for (int i = 1; i < (in_hand.hand.size()); i++) {
			for (int y = 0;; y++) {
				count_1 = count_1 + 1;
				z = h_hand.hand.size();
				if (y == h_hand.hand.size()) {
					h_hand.hand.add(y, in_hand.get_it(i));
					break;
				} else if (in_hand.hand.get(i).suit.compareTo(h_hand.hand.get(y).suit) < 0
						|| ((in_hand.hand.get(i).suit.compareTo(h_hand.hand.get(y).suit) == 0)
								&& (in_hand.hand.get(i).cardValue.compareTo(h_hand.hand.get(y).cardValue) < 0))) {
					h_hand.hand.add(y, in_hand.get_it(i));
					break;
				}
			}
		}
		System.out.println("for loop count=" + count_1);
		System.out.println("array list size = " + h_hand.hand.size());
				
//  	EnumSet<CardValue2> hand2 = EnumSet.	
		add_hand2(h_hand);
		return h_hand;
	   }
	
	public static void add_hand2(Hand in_hand) {
	
        Card aCard;
        
		Iterator<Card> cardIterator = in_hand.hand.listIterator(0);
		/* cardIterator = deck.listIterator(0); */

		while (cardIterator.hasNext()) {
			aCard = cardIterator.next();
			 
			System.out.println(aCard.getCardValue() + " of " + aCard.getSuit());
		}
	}

	}