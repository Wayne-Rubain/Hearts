package hearts_1;

//import java.awt.BorderLayout;
import java.awt.Color;


import hearts_1.Hearts_1.PLAYER;
//import hearts_1.Hearts_1.CardValue.Player_Traits;
import hearts_1.Hearts_JFrame.Table_Cards;
import  hearts_1.Play_Hand;
import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.CardValue2;
import hearts_1.Hearts_1.PASS_DIRECTION;
import hearts_1.Hearts_1.Suit;
import hearts_1.events.ConsoleEventLogger;
import hearts_1.events.GameLedger;
import hearts_1.events.GameLog;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;
import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;
import hearts_1.roster.StatsRecorder;
import hearts_1.roster.StatsReport;
import hearts_1.roster.StatsStore;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;

import java.awt.Font;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Label;

import javax.swing.border.MatteBorder;
import javax.swing.border.LineBorder;
import javax.swing.SwingConstants;
import javax.swing.JSeparator;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Canvas;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import java.util.ArrayList;
import java.awt.CardLayout;
import java.awt.FlowLayout;
// final ArrayList<JLabel> lblA;



	public class Hearts_JFrame extends JFrame {


//	public All_Hands all_hands ;
//	private ArrayList<Card> d_Deck;
	
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	public ArrayList<JButton> jbA;
	public JLabel lblInfoStatus = new JLabel("     INFO");
	public static ArrayList<JLabel> jl_a = new  ArrayList<JLabel>();
	public JPanel jpHand = new JPanel();
	public  Boolean My_Turn;
	private Hand a_hand = new Hand();
	private Hand n_hand = new Hand();
	private Hand e_hand = new Hand();
	private Hand w_hand = new Hand();
	
	// package-private (was private) -- Play_Hand.java (2026-07-30 extraction)
	// needs direct access to update these labels as it plays cards for each seat.
	JLabel lblWest, lblSouth, lblEast, lblNorth;
	/** The seat-name plates around the table; they show the roster member
	 * seated there (2026-10-17) instead of the bare compass point. */
	private JLabel lblNorthSeat, lblWestSeat, lblEastSeat, lblSouthSeat;
	// Promoted from a constructor-local variable (2026-07-30) so End_Of_Hand()
	// can re-enable it -- see the disable calls at both hand-start points below.
	JButton jbStartHand;
	// Promoted from constructor-local variables (2026-07-30) so the two can
	// gate each other: Play Card is only enabled once the table's been
	// cleared, and Clear Table is only enabled once all 4 cards are on the
	// table (see onTrickComplete(), called from Play_Hand.java once a trick
	// resolves, and jbClearTable's own handler).
	JButton jbPlayCard, jbClearTable;
	// Added 2026-07-31 for the human passing UI. jbPassCards is enabled only
	// once south_pass_selection holds exactly 3 cards; passing_selection_mode
	// switches Display_South_Hand()'s button listener between "toggle this
	// card in/out of my pass selection" and its normal "play this card"
	// behavior -- both read the same jbA buttons, just at different points
	// in the hand's lifecycle (see beginPassingPhase()/completePassingAndStartHand()).
	JButton jbPassCards;

	// Added 2026-08-06: per-seat computer-player type selection. Before this,
	// Set_Player(1/2/3) hardcoded North as Brain_Dead and East as Bad for the
	// whole session, so West was the only seat that ever ran the real
	// strategy code -- which made the AI look broken when it was simply never
	// being asked to think. Read at the start of every deal (see
	// applySelectedPlayerTypes(), called from beginPassingPhase()), so a
	// change here takes effect on the NEXT hand rather than mid-hand.
	//
	// Reworked 2026-08-16 for the persistent roster: the dropdowns now
	// select ROSTER MEMBERS (AI_01..AI_10, each carrying one of the same
	// Player_Type presets -- shown in parentheses) instead of raw types, so
	// every game's stats accrue to a stable identity. Raw-type testing is
	// still possible by editing %USERPROFILE%\.hearts2\roster.txt.
	JComboBox<RosterPlayer> jcbNorthType, jcbEastType, jcbWestType;
	// The persistent-roster/stats layer (2026-08-16) -- see
	// DESIGN_roster_and_trashtalk.md and the hearts_1.roster package.
	Roster roster;
	StatsStore statsStore;
	StatsRecorder statsRecorder;
	// Added 2026-08-07 at the user's request, after a live hand where East's
	// King-of-Spades lead looked insane from the human seat purely because
	// the Queen had exited one trick earlier as a quiet mid-trick discard.
	// The tracker surfaces the headline facts the AI is already counting
	// (hearts broken, where the Queen of Spades and Jack of Diamonds are,
	// points captured so far this hand) so the human can sanity-check plays
	// on even footing. Deliberately a player OPTION, not debug-only -- it
	// doubles as a learning aid for someone new to Hearts, which is why the
	// checkbox ships enabled but can be turned off for honest play.
	JCheckBox jchkCardTracker;
	JLabel lblCardTracker;

	// Added 2026-08-09: keyboard card selection for South's turn, an
	// alternative to mouse-clicking jbA's buttons. Two independent
	// highlight mechanisms, matching the two independent input styles
	// researched from established keyboard-driven card games (Solitaire
	// Till Dawn's rank-then-suit typing; Slay the Spire's arrow-key
	// highlight-then-commit) -- a player can use either, and starting one
	// clears the other, since they'd otherwise show conflicting borders on
	// the same hand.
	//
	// kbDisambiguationIndices: set by a rank key that matches more than one
	// legal card (voided in the led suit, so more than one suit shares that
	// rank) -- holds the jbA indices of just those candidates, awaiting a
	// suit key to pick the exact one. Null when not disambiguating.
	//
	// kbHighlightIndex: set by arrow-key navigation while following suit --
	// the single jbA index currently highlighted, committed by Enter. Null
	// when nothing is highlighted.
	private List<Integer> kbDisambiguationIndices;
	private Integer kbHighlightIndex;
	boolean passing_selection_mode = false;
	List<Card> south_pass_selection = new ArrayList<Card>();
	// Added 2026-07-31: true while jbPassCards is showing "Continue" instead
	// of "Pass Cards" -- i.e. the exchange already happened, the received
	// cards are highlighted, and the human just needs to acknowledge them
	// before the first trick actually starts. See completePassingAndStartHand().
	boolean awaiting_continue_click = false;
	public   All_Hands all_hands;
	
	public   Deck gameDeck = new Deck();
	
	public PLAYER who_leads = PLAYER.NORTH;
	public Player table_player_n = new Player();
	public Player table_player_e = new Player();
	public Player table_player_s = new Player();
	public Player table_player_w = new Player();
	
	
	
	
// **************************************************************************************
// *******      General Table variables                                          ********
// **************************************************************************************


	
	public   PASS_DIRECTION  p_d;

	/** The running total a player must REACH for the game to end. Standard
	 * Hearts plays to 100; lowest total then wins. Added 2026-08-09 -- before
	 * this the game had no end condition at all: p_total_points accumulated
	 * forever, nothing ever compared it to anything, and no winner was ever
	 * declared. That also left every standings-aware decision in the AI
	 * (Scoring.standingDelta, the moon-shot threshold, MoonThreat's
	 * queen-denial cost/benefit, Deal_Hand's defensive passing) reasoning
	 * about a game that could never actually resolve. */
	public static final int TARGET_SCORE = 100;

	/** True once some seat has reached TARGET_SCORE and the winner has been
	 * declared. While set, "Start Hand" becomes "New Game" and its handler
	 * zeroes the running totals before dealing again (see its listener). */
	private boolean game_over = false;

	public  int  north_cnt, east_cnt, south_cnt, west_cnt;
	public  PLAYER  current_player,
				    current_dealer,
				    Who_took_QueenSpades,
				    Who_in_the_Lead,
				    Who_in_Last_Place;
	
	public  Hand played_cards_c, played_cards_d, played_cards_h, played_cards_s;
	
	EnumSet<CardValue2> tbl_hand2_n = EnumSet.noneOf(CardValue2.class);
	EnumSet<CardValue2> tbl_hand2_e = EnumSet.noneOf(CardValue2.class);
	EnumSet<CardValue2> tbl_hand2_s = EnumSet.noneOf(CardValue2.class);
	EnumSet<CardValue2> tbl_hand2_w = EnumSet.noneOf(CardValue2.class);

//	EnumSet<CardValue> deduced_NORTH_SPADE 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_NORTH_HEARTS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_NORTH_DIAMONDS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_NORTH_CLUBS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_SOUTH_SPADE 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_SOUTH_HEARTS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_SOUTH_DIAMONDS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_SOUTH_CLUBS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_EAST_SPADE 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_EAST_HEARTS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_EAST_DIAMONDS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_EAST_CLUBS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_WEST_SPADE 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_WEST_HEARTS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_WEST_DIAMONDS 	= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> deduced_WEST_CLUBS 		= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> discard_SPADE 			= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> discard_HEARTS 			= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> discard_DIAMONDS			= EnumSet.noneOf(CardValue.class);
//	EnumSet<CardValue> discard_CLUBS 			= EnumSet.noneOf(CardValue.class);
//	

	
	 
	

	public enum South_Hand_Status
				{first,second,na}
	
//	public Play_Hand_1 p_h_1;
	public Play_Hand p_h = new Play_Hand(this);
	
	public static  class Table_Cards {
		// package-private (was private) -- Play_Hand.java (2026-07-30 extraction)
		// needs direct access to these as it walks a trick turn by turn.
		int 	play_position;
		PLAYER 	who_leads, current_player, who_won, high_card;
		Card	s_card, w_card,	n_card, e_card;
		public Card	p1_card, p2_card,	p3_card, p4_card;
		int		s_points, w_points,	n_points, e_points;
		byte	round_nbr, hand_nbr;
		South_Hand_Status south_status;
		// Added 2026-07-30 for Rules.java (follow-suit / hearts-broken /
		// first-trick). trick_nbr is tricks *resolved* so far this hand --
		// 0 for the whole first trick, incremented once it resolves.
		int trick_nbr;
		boolean hearts_broken;
		// Added 2026-07-31 for VoidTracker.java -- which suits each seat is
		// PROVABLY void in, deduced from renege patterns (see VoidTracker for
		// the full explanation).
		Map<PLAYER, EnumSet<Suit>> void_suits;
		// Added 2026-07-31 for AI.java's moon-shooter-stopping logic --
		// lets pure-logic classes look up any seat's Player object (won_cards,
		// p_total_points) given only a Table_Cards reference. Populated once
		// by the outer Hearts_JFrame constructor (see there for why), not
		// reset per hand -- these 4 Player objects persist for the game.
		Map<PLAYER, Player> players;

	public Table_Cards ( ) {
		this.play_position = 0;
		this.players = new EnumMap<PLAYER, Player>(PLAYER.class);
	    this.s_points   =  0;
	    this.w_points   =  0;
	    this.n_points   =  0;
	    this.e_points   =  0;
	    this.round_nbr =  0;
	    this.hand_nbr  =  0;
		this.south_status = South_Hand_Status.na;
		this.trick_nbr = 0;
		this.hearts_broken = false;
		this.void_suits = VoidTracker.freshVoidMap();
	   }
}
 	
	public Table_Cards g_table_cards = new Table_Cards();
		
	public static int display_icon_cnt = 0;
	public static int i_cnt = 0;
//	public static Deck displayDeck  = new Deck();
	public static ArrayList<ImageIcon> iA = new  ArrayList<ImageIcon>();
	public  int hand_cnt = 1;
	
	public PLAYER Who_Took_QueenSpade, Who_Took_JackDiamond, Who_Took_1st_Diamond;
	/** Card tracker (2026-10-08, user request): who captured the first Heart
	 * this hand, and on which trick. The won piles can't answer this after
	 * the fact -- they don't record which seat's pile got a Heart first -- so
	 * it is captured as each trick resolves (onTrickComplete()) and cleared
	 * at the start of every hand. */
	PLAYER firstHeartTaker;
	int firstHeartTrick;
	
	public  Boolean 	    QueenSpade_Played, 
							JackDiamond_Played, 
							Hearts_Played,
							North_taken_Hearts,
							South_taken_Hearts,
							East_taken_Hearts,
							West_taken_Hearts;
	private EnumSet<CardValue2> temp_1;
	private EnumSet<CardValue2> of;
	

	
// Play_Hand_1 / Play_Hand_2 / Get_Lead_Card moved to Play_Hand.java (2026-07-30
// extraction) -- see that file. Called here via the p_h field.

public void Place_Card_on_Table(int p_pos, Card in_card, PLAYER in_player) {
		
	   switch (p_pos) {
		   case  1:
			    g_table_cards.p1_card = in_card;
			    break; 
		   case  2:
			   g_table_cards.p2_card   = in_card;
		        break;
		   case  3:
			   g_table_cards.p3_card   = in_card;
		        break;
		   case  4:
			   g_table_cards.p4_card   = in_card;
			   break;
	       }
		   switch (in_player) {
		   case  NORTH:
			    g_table_cards.n_card = in_card;
			    break; 
		   case  EAST:
			   g_table_cards.e_card   = in_card;
		        break;
		   case  SOUTH:
			   g_table_cards.s_card   = in_card;
		        break;
		   case  WEST:
			   g_table_cards.w_card  = in_card;
			   break;
   }
	   return;
	}

    public void Display_Hand2()
    {
//    	Iterator<Card> cardIterator = deck.iterator();
    	Iterator<CardValue2> cardIterator = tbl_hand2_n.iterator();
   
    	System.out.println("******  Hand 2  North  ***************"); 
    	
        while (cardIterator.hasNext())
        {
          CardValue2 aCard = cardIterator.next();
          System.out.println(aCard.toString());

        }	
        
        cardIterator = tbl_hand2_e.iterator();
        System.out.println("******  Hand 2  East  ***************"); 
    	
        while (cardIterator.hasNext())
        {
          CardValue2 aCard = cardIterator.next();
          System.out.println(aCard.toString());

        }	
        
        cardIterator = tbl_hand2_w.iterator();
        System.out.println("******  Hand 2  West  ***************"); 
    	
        while (cardIterator.hasNext())
        {
          CardValue2 aCard = cardIterator.next();
          System.out.println(aCard.toString());

        }
        
        cardIterator = tbl_hand2_s.iterator();
        System.out.println("******  Hand 2  South  ***************"); 
    	
        while (cardIterator.hasNext())
        {
          CardValue2 aCard = cardIterator.next();
          System.out.println(aCard.toString());
//          System.out.println(cardIterator.next().toString());
        }	
    }
    

		



	/**
	 * Launch the application.
	 */
	
	public void Play_your_card (ImageIcon h_icon) {
		
		lblSouth.setIcon(h_icon);

	}
	
	public void Start_Hand () {
		Hand work_hand;
		switch (who_leads.toString()) {
		case "NORTH":		work_hand = n_hand;	break;
		case "EAST":		work_hand = e_hand; break;
		case "WEST":		work_hand = w_hand;	break;
		}
	}
	
	public void Add_Hand2(All_Hands in_h) {
		
		EnumSet<CardValue2> all_hearts   =  EnumSet.range(CardValue2.H2, CardValue2.HA);
		EnumSet<CardValue2> all_spades   =  EnumSet.range(CardValue2.S2, CardValue2.SA);
		EnumSet<CardValue2> all_diamonds =  EnumSet.range(CardValue2.D2, CardValue2.DA);
		EnumSet<CardValue2> all_clubs    =  EnumSet.range(CardValue2.C2, CardValue2.CA);

		temp_1 = EnumSet.noneOf(CardValue2.class);
		
		EnumSet<CardValue2> temp_h  = EnumSet.noneOf(CardValue2.class);
		EnumSet<CardValue2> temp_d  = EnumSet.noneOf(CardValue2.class);
		EnumSet<CardValue2> temp_c  = EnumSet.noneOf(CardValue2.class);
		EnumSet<CardValue2> temp_s  = EnumSet.noneOf(CardValue2.class);
		
		int a,b,c;
		boolean chk;
		
		tbl_hand2_n.clear(); tbl_hand2_e.clear(); tbl_hand2_s.clear(); tbl_hand2_w.clear();
		
		for (int i=0; i < 13; i++) {
				
			 tbl_hand2_n.add(in_h.n_hand.get_it(i).cardValue2);
			 tbl_hand2_e.add(in_h.e_hand.get_it(i).cardValue2);
			 tbl_hand2_s.add(in_h.s_hand.get_it(i).cardValue2);
			 tbl_hand2_w.add(in_h.w_hand.get_it(i).cardValue2);
		}
		for (Iterator<CardValue2> it = tbl_hand2_n.iterator(); it.hasNext();) {
			
			temp_1 = EnumSet.of(it.next());
			
			if (all_hearts.containsAll(temp_1)) {
				chk = temp_h.addAll(temp_1); }
			if (all_clubs.containsAll(temp_1)) {
				chk = temp_c.addAll(temp_1); }
			if (all_spades.containsAll(temp_1)) {
				chk = temp_s.addAll(temp_1); }
			if (all_diamonds.containsAll(temp_1)) {
				chk = temp_d.addAll(temp_1); }
		}
		
		System.out.println("****** North size " + tbl_hand2_n.size() + " ********");
		System.out.println("****** Heart's size " + temp_h.size() + " ********");
		for (Iterator<CardValue2> it = temp_h.iterator(); it.hasNext();) {
			System.out.println("****** Heart " + it.next() + " ********"); }
		System.out.println("****** Club's size " + temp_c.size() + " ********");
		for (Iterator<CardValue2> it = temp_c.iterator(); it.hasNext();) {
			System.out.println("****** Club  " + it.next() + " ********"); }
		System.out.println("****** Spade's size " + temp_s.size() + " ********");
		for (Iterator<CardValue2> it = temp_s.iterator(); it.hasNext();) {
			System.out.println("****** Spade " + it.next() + " ********"); }
		System.out.println("****** Diamond's size " + temp_d.size() + " ********");
		for (Iterator<CardValue2> it = temp_d.iterator(); it.hasNext();) {
			System.out.println("****** Diamond " + it.next() + " ********"); }
		
	}
	
	
	public void Display_South_Hand () {
		 
//	        JPanel jpHand = new JPanel();
			jpHand.setBounds(64, 560, 1200, 163);
			
			jpHand.removeAll();
		   
			jbA = new ArrayList<JButton>();
//	        ArrayList<JButton> jbA = null;
			
			int b;
			String h_a;
			
			a_hand = all_hands.s_hand_get(all_hands);
			
//			System.out.println("******  South Hand (Display Hand)  *******");
			a_hand = all_hands.s_hand_get(all_hands);
//		    a_hand.display_hand(a_hand);
	     
			My_Turn = false;
	  			
		   for (int a=0; a < 13; a++)
	          {
			   
			   JButton jb = new JButton();
				jb.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent evt) {
						String cmd = evt.getActionCommand();
						if (passing_selection_mode) {
							toggleCardSelection(cmd, (JButton) evt.getSource());
							return;
						}
						System.out.println("****** button # ********");
						System.out.println("****** button # = " + cmd + " " + cmd + " I'm here, don't forget!!!");
						if (My_Turn == true ) {
							My_Turn = false;
							g_table_cards.s_card = remove_button(cmd);
							p_h.Play_Hand_1(g_table_cards);  }
						  else {
							Not_Your_Turn();   }
							}	});
		
				Card hc;
			
				hc  =  a_hand.get_it(a);
				h_a =  hc.cardValue + " " + hc.suit;

		        jb.setIcon(hc.getIcon());		
				jb.setIconTextGap(0);
				jb.setEnabled(true);
				
				Dimension buttonSize = jb.getSize();
			    buttonSize.setSize(jb.getIcon().getIconWidth(), jb.getIcon().getIconHeight());
			    jb.setPreferredSize(buttonSize);
			    jb.setHorizontalAlignment(SwingConstants.CENTER);
			  
			    
			    jb.setActionCommand(h_a);
			    
			   
			    jbA.add(jb);
				jpHand.add(jbA.get(a));
	        }
			
			jpHand.setEnabled(true);
			contentPane.add(jpHand);
	}

	// Added 2026-07-30 alongside Rules.java. jbA (this hand's buttons) and
	// a_hand.hand (this hand's remaining cards) stay in lock-step by index --
	// remove_button() removes one entry from each at the same relative
	// position on every play -- so it's safe to zip them together by index
	// here rather than needing to match cards up by identity. Called from
	// Play_Hand.java exactly when it becomes South's (the human's) turn, so
	// illegal cards are disabled *before* the player can click them, rather
	// than rejecting an illegal click after the fact.
	/** The one place South's current legal-card set gets computed -- used by
	 * Update_South_Hand_Legality() to enable/disable jbA, and (added
	 * 2026-08-09) by the keyboard card-selection handlers below, so both
	 * always agree on what's actually playable right now. */
	private List<Card> computeSouthLegalCards() {
		Hand hand = all_hands.s_hand_get(all_hands);
		boolean isLead = (g_table_cards.play_position == 1);
		boolean firstTrick = Rules.isFirstTrick(g_table_cards);
		return isLead
				? Rules.legalLeads(hand, g_table_cards.hearts_broken, firstTrick)
				: Rules.legalFollows(hand, g_table_cards.p1_card.suit, firstTrick);
	}

	public void Update_South_Hand_Legality() {
		a_hand = all_hands.s_hand_get(all_hands);
		boolean isLead = (g_table_cards.play_position == 1);
		List<Card> legal = computeSouthLegalCards();
		// A fresh turn invalidates any keyboard highlight/disambiguation
		// left over from the previous one -- the jbA index it pointed at
		// may not even hold the same card anymore.
		clearKeyboardState();

		// Diagnostic added 2026-08-06, chasing a report of South's cards
		// visually disappearing around the last 3 cards of a hand. jbA (the
		// buttons), jpHand (what's actually on screen) and a_hand.hand (the
		// real cards) are kept in lockstep purely by index -- remove_button()
		// pulls the same index out of all three -- so any divergence between
		// their sizes is the bug, and the Math.min() below would quietly hide
		// it by skipping the extra entries. Logs the enable pattern too, since
		// the other way cards "vanish" is every one of them being disabled
		// (Swing greys the icon), which would point at a wrong lead suit
		// rather than a desync.
		if (jbA.size() != a_hand.hand.size() || jpHand.getComponentCount() != a_hand.hand.size()) {
			System.out.println("****** HAND DESYNC -- buttons=" + jbA.size()
					+ " onScreen=" + jpHand.getComponentCount()
					+ " cards=" + a_hand.hand.size() + " ******");
		}
		int n = Math.min(jbA.size(), a_hand.hand.size());
		int enabledCount = 0;
		StringBuilder dbg = new StringBuilder();
		for (int i = 0; i < n; i++) {
			boolean isLegal = legal.contains(a_hand.hand.get(i));
			jbA.get(i).setEnabled(isLegal);
			if (isLegal) {
				enabledCount++;
			}
			Card c = a_hand.hand.get(i);
			dbg.append(c.cardValue).append("-").append(c.suit).append(isLegal ? "(on) " : "(OFF) ");
		}
		// Only worth printing in the situations that could explain vanishing
		// cards: the endgame window where it was reported, or any time every
		// single card came back illegal (Rules is supposed to guarantee at
		// least one legal move, so zero would itself be the bug).
		if (enabledCount == 0 || a_hand.hand.size() <= 4) {
			System.out.println("****** South legality (lead=" + isLead
					+ (isLead ? "" : ", leadSuit=" + g_table_cards.p1_card.suit)
					+ ", legal=" + legal.size() + "/" + a_hand.hand.size() + "): "
					+ dbg + "******");
		}
	}

	// ====================================================================
	// Keyboard card selection (2026-08-09), an alternative to mouse-
	// clicking jbA's buttons. Researched against established keyboard-
	// driven card games before designing this (Solitaire Till Dawn;
	// Slay the Spire's well-regarded keyboard scheme vs. its sequel's
	// widely-criticized one) rather than inventing conventions from
	// scratch -- two independent, well-precedented input styles:
	//
	//   - Rank-then-suit typing (Solitaire Till Dawn's scheme): press a
	//     rank key. If exactly one legal card matches, it plays
	//     immediately. If more than one matches (voided in the led suit,
	//     so more than one suit shares that rank), the candidates get a
	//     visual highlight and the next keypress is read as a suit key to
	//     pick the exact card. Key mapping is the user's explicit
	//     preference over the more common "1=Ace" convention found in
	//     research: A/J/Q/K match the letters printed on those cards, and
	//     Ten is reachable by EITHER 1 or T (both bound to the same rank).
	//   - Arrow-key highlight-then-commit (Slay the Spire 1's scheme,
	//     which players explicitly prefer over its sequel's undocumented
	//     one -- the lesson taken from that research: make the commit step
	//     unambiguous, hence Enter always being the single confirm action
	//     here). Only meaningful while FOLLOWING a led suit South actually
	//     holds a legal card of. Up/Down jump to the highest/lowest legal
	//     card of that suit; Right jumps to the cheapest legal card that
	//     beats the table's current best of that suit; Left steps one
	//     rank down from whatever is currently highlighted.
	//
	// The two styles are mutually exclusive at any moment (starting one
	// clears the other) so their visual highlights never conflict on the
	// same hand. Both ultimately call playCardAtIndex(), the exact same
	// commit path a mouse click already uses.
	// ====================================================================

	/** Wires every keyboard binding onto contentPane at
	 * WHEN_IN_FOCUSED_WINDOW scope, so keys work regardless of which
	 * button currently holds Swing focus. Called once from the
	 * constructor, not per-hand -- jbA gets rebuilt every hand by
	 * Display_South_Hand(), but the bindings read it fresh each time they
	 * fire rather than capturing anything at setup time, so they don't
	 * need rebinding. Key-code-based (KeyEvent.VK_*, not typed characters)
	 * so each binding fires on the physical key regardless of Shift/Caps
	 * Lock state. */
	private void setupKeyboardCardSelection() {
		InputMap im = contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = contentPane.getActionMap();

		bindRank(im, am, KeyEvent.VK_2, CardValue.TWO);
		bindRank(im, am, KeyEvent.VK_3, CardValue.THREE);
		bindRank(im, am, KeyEvent.VK_4, CardValue.FOUR);
		bindRank(im, am, KeyEvent.VK_5, CardValue.FIVE);
		bindRank(im, am, KeyEvent.VK_6, CardValue.SIX);
		bindRank(im, am, KeyEvent.VK_7, CardValue.SEVEN);
		bindRank(im, am, KeyEvent.VK_8, CardValue.EIGHT);
		bindRank(im, am, KeyEvent.VK_9, CardValue.NINE);
		bindRank(im, am, KeyEvent.VK_1, CardValue.TEN);
		bindRank(im, am, KeyEvent.VK_T, CardValue.TEN);
		bindRank(im, am, KeyEvent.VK_J, CardValue.JACK);
		bindRank(im, am, KeyEvent.VK_Q, CardValue.QUEEN);
		bindRank(im, am, KeyEvent.VK_K, CardValue.KING);
		bindRank(im, am, KeyEvent.VK_A, CardValue.ACE);

		bindSuit(im, am, KeyEvent.VK_H, Suit.HEART);
		bindSuit(im, am, KeyEvent.VK_D, Suit.DIAMOND);
		bindSuit(im, am, KeyEvent.VK_C, Suit.CLUB);
		bindSuit(im, am, KeyEvent.VK_S, Suit.SPADE);

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "kbHighlightHighest");
		am.put("kbHighlightHighest", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				highlightHighestOfLedSuit();
			}
		});
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "kbHighlightLowest");
		am.put("kbHighlightLowest", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				highlightLowestOfLedSuit();
			}
		});
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "kbHighlightCheapestWinner");
		am.put("kbHighlightCheapestWinner", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				highlightCheapestWinnerOfLedSuit();
			}
		});
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "kbHighlightOneLower");
		am.put("kbHighlightOneLower", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				highlightOneRankLower();
			}
		});
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "kbCommit");
		am.put("kbCommit", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (kbHighlightIndex != null) {
					playCardAtIndex(kbHighlightIndex.intValue());
				}
			}
		});
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "kbCancel");
		am.put("kbCancel", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				clearKeyboardState();
			}
		});
	}

	private void bindRank(InputMap im, ActionMap am, int keyCode, final CardValue rank) {
		String name = "kbRank" + rank;
		im.put(KeyStroke.getKeyStroke(keyCode, 0), name);
		am.put(name, new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				handleRankKey(rank);
			}
		});
	}

	private void bindSuit(InputMap im, ActionMap am, int keyCode, final Suit suit) {
		String name = "kbSuit" + suit;
		im.put(KeyStroke.getKeyStroke(keyCode, 0), name);
		am.put(name, new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				handleSuitKey(suit);
			}
		});
	}

	/** A rank key was pressed. Only acts during actual trick play -- not
	 * while selecting cards to pass (that flow has its own toggle-select
	 * semantics via toggleCardSelection(), not "play now") and not out of
	 * turn (matches the existing click handler's own My_Turn guard, right
	 * down to reusing Not_Your_Turn() for the same feedback a misplaced
	 * click already gives). */
	private void handleRankKey(CardValue rank) {
		if (passing_selection_mode) {
			return;
		}
		if (!My_Turn) {
			Not_Your_Turn();
			return;
		}
		clearKeyboardState();
		Hand hand = all_hands.s_hand_get(all_hands);
		List<Card> legal = computeSouthLegalCards();
		List<Integer> matches = new ArrayList<Integer>();
		int n = Math.min(jbA.size(), hand.hand.size());
		for (int i = 0; i < n; i++) {
			Card c = hand.hand.get(i);
			if (c.cardValue == rank && legal.contains(c)) {
				matches.add(Integer.valueOf(i));
			}
		}
		if (matches.isEmpty()) {
			return; // no legal card of that rank -- silent no-op
		}
		if (matches.size() == 1) {
			playCardAtIndex(matches.get(0).intValue());
			return;
		}
		// More than one legal card shares this rank (voided in the led
		// suit) -- narrow to those candidates and wait for a suit key.
		kbDisambiguationIndices = matches;
		for (Integer idx : matches) {
			jbA.get(idx.intValue()).setBorder(new LineBorder(Color.YELLOW, 4));
		}
	}

	/** A suit key was pressed -- only meaningful while a rank disambiguation
	 * is pending; otherwise a no-op, since suit keys have no independent
	 * meaning of their own. */
	private void handleSuitKey(Suit suit) {
		if (kbDisambiguationIndices == null) {
			return;
		}
		Hand hand = all_hands.s_hand_get(all_hands);
		for (Integer idx : kbDisambiguationIndices) {
			int i = idx.intValue();
			if (i < hand.hand.size() && hand.hand.get(i).suit == suit) {
				playCardAtIndex(i);
				return;
			}
		}
		// Pressed a suit that isn't among the candidates -- cancel rather
		// than leave the player stuck in a disambiguation they can't
		// resolve with that key.
		clearKeyboardState();
	}

	/** The led suit's legal cards in hand, sorted low to high by rank --
	 * the shared basis for all four arrow-key actions. Empty (not just
	 * "no cards of that suit") whenever arrow-key navigation isn't
	 * meaningful: leading (there is no led suit yet), or void in the led
	 * suit (nothing here to navigate -- Left/Up/Down/Right all read
	 * "of the led suit in hand" in the original design, which presumes
	 * South actually holds one). */
	private List<Integer> legalLedSuitIndices() {
		List<Integer> result = new ArrayList<Integer>();
		if (g_table_cards.play_position == 1 || g_table_cards.p1_card == null) {
			return result;
		}
		Suit leadSuit = g_table_cards.p1_card.suit;
		Hand hand = all_hands.s_hand_get(all_hands);
		List<Card> legal = computeSouthLegalCards();
		int n = Math.min(jbA.size(), hand.hand.size());
		for (int i = 0; i < n; i++) {
			Card c = hand.hand.get(i);
			if (c.suit == leadSuit && legal.contains(c)) {
				result.add(Integer.valueOf(i));
			}
		}
		result.sort(new java.util.Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				return hand.hand.get(a.intValue()).cardValue.compareTo(hand.hand.get(b.intValue()).cardValue);
			}
		});
		return result;
	}

	private void highlightHighestOfLedSuit() {
		if (passing_selection_mode || !My_Turn) {
			return;
		}
		List<Integer> candidates = legalLedSuitIndices();
		if (!candidates.isEmpty()) {
			setKeyboardHighlight(candidates.get(candidates.size() - 1));
		}
	}

	private void highlightLowestOfLedSuit() {
		if (passing_selection_mode || !My_Turn) {
			return;
		}
		List<Integer> candidates = legalLedSuitIndices();
		if (!candidates.isEmpty()) {
			setKeyboardHighlight(candidates.get(0));
		}
	}

	/** The cheapest legal card of the led suit that still beats whatever's
	 * currently winning the trick on the table -- the lowest card able to
	 * take it, spending no more strength than necessary. No highlight
	 * change if nothing in hand can beat the table (there is no answer to
	 * "cheapest winner" when there isn't one). */
	private void highlightCheapestWinnerOfLedSuit() {
		if (passing_selection_mode || !My_Turn) {
			return;
		}
		List<Integer> candidates = legalLedSuitIndices();
		if (candidates.isEmpty()) {
			return;
		}
		Card highestOnTable = AI.highestOfSuitOnTable(g_table_cards, g_table_cards.p1_card.suit, g_table_cards.play_position);
		if (highestOnTable == null) {
			return;
		}
		Hand hand = all_hands.s_hand_get(all_hands);
		for (Integer idx : candidates) {
			if (hand.hand.get(idx.intValue()).cardValue.compareTo(highestOnTable.cardValue) > 0) {
				setKeyboardHighlight(idx);
				return;
			}
		}
	}

	/** One rank step down from whatever's currently highlighted, among the
	 * led suit's legal cards. If nothing is highlighted yet, behaves like
	 * Down (starts at the lowest) -- a sane starting point rather than a
	 * no-op. Clamped at the bottom: repeated presses there just stay put. */
	private void highlightOneRankLower() {
		if (passing_selection_mode || !My_Turn) {
			return;
		}
		List<Integer> candidates = legalLedSuitIndices();
		if (candidates.isEmpty()) {
			return;
		}
		if (kbHighlightIndex == null) {
			setKeyboardHighlight(candidates.get(0));
			return;
		}
		int currentPos = candidates.indexOf(kbHighlightIndex);
		if (currentPos > 0) {
			setKeyboardHighlight(candidates.get(currentPos - 1));
		}
	}

	private void setKeyboardHighlight(Integer index) {
		clearKeyboardBorders();
		kbHighlightIndex = index;
		jbA.get(index.intValue()).setBorder(new LineBorder(Color.YELLOW, 4));
	}

	/** Commits whichever card is at `index` -- the exact same play path
	 * remove_button()/Play_Hand_1() already use for a mouse click, reusing
	 * the button's own action-command string rather than reconstructing
	 * one, so this can never disagree with what a click on the same
	 * button would have played. */
	private void playCardAtIndex(int index) {
		String cmd = jbA.get(index).getActionCommand();
		clearKeyboardState();
		My_Turn = false;
		g_table_cards.s_card = remove_button(cmd);
		p_h.Play_Hand_1(g_table_cards);
	}

	private void clearKeyboardBorders() {
		if (kbHighlightIndex != null && kbHighlightIndex.intValue() < jbA.size()) {
			jbA.get(kbHighlightIndex.intValue()).setBorder(null);
		}
		if (kbDisambiguationIndices != null) {
			for (Integer idx : kbDisambiguationIndices) {
				if (idx.intValue() < jbA.size()) {
					jbA.get(idx.intValue()).setBorder(null);
				}
			}
		}
	}

	private void clearKeyboardState() {
		clearKeyboardBorders();
		kbHighlightIndex = null;
		kbDisambiguationIndices = null;
	}

	// Added 2026-07-31 for the human passing UI. Toggles one hand-card
	// button in/out of south_pass_selection instead of playing it -- only
	// reachable while passing_selection_mode is true (see Display_South_Hand's
	// listener). Rebuilds the Card from the button's action command the same
	// way remove_button() does for a real play; value/suit matching (not
	// object identity) is how the rest of this codebase already compares
	// cards, so this is safe even though it's a freshly-constructed object
	// rather than the exact instance sitting in South's Hand.
	public void toggleCardSelection(String cmd, JButton jb) {
		String[] words = cmd.split("\\s+");
		Card clicked = new Card(null, null, null, null);
		clicked.setCardValue(words[0]);
		clicked.setSuit(words[1]);
		// BUG FIX (2026-07-31, found via live playthrough -- West's played
		// card showed no icon at all, blank on the table, even though the
		// trick resolved correctly): this freshly-constructed Card never had
		// its icon set, unlike remove_button()'s identical reconstruction
		// pattern (which explicitly fetches jbA.get(b).getIcon()). Since
		// south_pass_selection's Card objects go straight into whichever
		// recipient's hand via Deal_Hand.passCards()'s addX(), any card
		// South passed away kept a null icon forever -- invisible the
		// instant its new owner actually played it.
		clicked.setIcon((ImageIcon) jb.getIcon());

		Card alreadySelected = null;
		for (Card c : south_pass_selection) {
			if (c.cardValue == clicked.cardValue && c.suit == clicked.suit) {
				alreadySelected = c;
				break;
			}
		}
		if (alreadySelected != null) {
			south_pass_selection.remove(alreadySelected);
			jb.setBorder(null);
		} else {
			if (south_pass_selection.size() >= 3) {
				lblInfoStatus.setText("    You can only pass 3 cards -- deselect one first");
				return;
			}
			south_pass_selection.add(clicked);
			jb.setBorder(new LineBorder(Color.RED, 4));
		}
		jbPassCards.setEnabled(south_pass_selection.size() == 3);
	}

	// Added 2026-07-31: starts the passing phase for a freshly-dealt hand.
	// Called from both hand-start points (the constructor's own first-hand
	// setup and jbStartHand's handler) right after p_d is set to this hand's
	// real direction. KEEP means no exchange happens at all this hand (see
	// Deal_Hand.passCards()'s own no-op for it), so there's nothing for the
	// human to select -- skip straight through to completePassingAndStartHand().
	/** Loads each computer seat's traits from its dropdown. Added 2026-08-06.
	 * Called at the start of every deal (from beginPassingPhase()) rather
	 * than once at construction, so changing a dropdown actually takes effect
	 * -- but only from the next hand onward, never mid-hand, since
	 * Deal_Hand's passing decisions and AI.java's trick play both read
	 * p_traits continuously and swapping a seat's personality partway through
	 * would produce incoherent play. Null-guarded because the constructor
	 * calls this once before the dropdowns exist. */
	public void applySelectedPlayerTypes() {
		if (jcbNorthType == null || jcbEastType == null || jcbWestType == null) {
			return;
		}
		RosterPlayer north = (RosterPlayer) jcbNorthType.getSelectedItem();
		RosterPlayer east = (RosterPlayer) jcbEastType.getSelectedItem();
		RosterPlayer west = (RosterPlayer) jcbWestType.getSelectedItem();
		table_player_n.Set_Player(north.profile);
		table_player_e.Set_Player(east.profile);
		table_player_w.Set_Player(west.profile);
		// Added 2026-08-16 -- the per-individual counts-cards rate travels
		// with the roster member, not the type; Deal_Hand.beginHand() rolls
		// countsCardsThisHand against this every hand.
		table_player_n.countCardsPercent = north.countCardsPercent;
		table_player_e.countCardsPercent = east.countCardsPercent;
		table_player_w.countCardsPercent = west.countCardsPercent;
		// Added 2026-08-16 -- see Player.moonGamblePercent's doc.
		table_player_n.moonGamblePercent = north.moonGamblePercent;
		table_player_e.moonGamblePercent = east.moonGamblePercent;
		table_player_w.moonGamblePercent = west.moonGamblePercent;
		table_player_n.moonInterestPercent = north.moonInterestPercent;
		table_player_e.moonInterestPercent = east.moonInterestPercent;
		table_player_w.moonInterestPercent = west.moonInterestPercent;
		// Keep the stats recorder's seat->identity mapping in step with the
		// dropdowns (2026-08-16) -- events are seat-keyed, counters are
		// identity-keyed, and this is where the two meet.
		statsRecorder.setSeatId(PLAYER.NORTH, north.id);
		statsRecorder.setSeatId(PLAYER.EAST, east.id);
		statsRecorder.setSeatId(PLAYER.WEST, west.id);
		showSeatNames(north, east, west);
		System.out.println("****** seats -- North: " + north
				+ " | East: " + east
				+ " | West: " + west + " ******");
	}

	/** Selects the roster member with this id when the roster has one; the
	 * starting table is Careful Counter, Steady and Shark (2026-10-17). */
	private static void selectIfPresent(JComboBox<RosterPlayer> box, String id) {
		for (int i = 0; i < box.getItemCount(); i++) {
			if (box.getItemAt(i).id.equals(id)) {
				box.setSelectedIndex(i);
				return;
			}
		}
	}

	/** Puts each roster member's name on the seat plate so the human can tell
	 * the players apart (2026-10-17). South shows the human's roster name. */
	private void showSeatNames(RosterPlayer north, RosterPlayer east, RosterPlayer west) {
		if (lblNorthSeat == null || lblEastSeat == null || lblWestSeat == null || lblSouthSeat == null) {
			return;
		}
		lblNorthSeat.setText(north.displayName);
		lblEastSeat.setText(east.displayName);
		lblWestSeat.setText(west.displayName);
		lblSouthSeat.setText(roster.humanName());
		Font vertical = new Font("Tahoma", Font.PLAIN, 12);
		lblEastSeat.setFont(vertical);
		lblWestSeat.setFont(vertical);
	}

	/** Moves the three computer players one seat around the table (North to
	 * East to West to North) so each new game seats them differently
	 * (2026-10-17). The dropdowns are the single source of truth for who sits
	 * where, so rotating them is the whole job. */
	private void rotateComputerSeats() {
		if (jcbNorthType == null || jcbEastType == null || jcbWestType == null) {
			return;
		}
		Object north = jcbNorthType.getSelectedItem();
		Object east = jcbEastType.getSelectedItem();
		Object west = jcbWestType.getSelectedItem();
		jcbEastType.setSelectedItem(north);
		jcbWestType.setSelectedItem(east);
		jcbNorthType.setSelectedItem(west);
	}

	// Added 2026-08-07 -- rebuilds the card-tracker label from the live table
	// state: hearts-broken, where the two headline cards are, and RAW points
	// captured so far this hand per seat (counted directly rather than via
	// Scoring.computeHandScores(), whose moon-shot inversion only means
	// anything once a hand is complete). Called on every trick resolution
	// (onTrickComplete()) and at each deal's start (beginPassingPhase(),
	// which runs after the new-deal path has already cleared won_cards and
	// hearts_broken, so the reset shows fresh facts, not last hand's).
	/** The human stats screen (2026-08-16) -- a modeless monospace dialog
	 * rendering StatsReport's text. Rebuilt on every click, so it always
	 * shows the store as of the last completed hand (the recorder writes
	 * at HAND_ENDED, not per trick). */
	private void showStatsDialog() {
		JTextArea text = new JTextArea(StatsReport.build(statsStore, roster));
		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		JDialog dialog = new JDialog(this, "Player Stats -- " + roster.humanName(), false);
		dialog.add(new JScrollPane(text));
		dialog.setSize(520, 640);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	public void updateCardTracker() {
		if (lblCardTracker == null) {
			return;
		}
		StringBuilder sb = new StringBuilder("<html><b>Card Tracker</b><br>");
		sb.append("Hearts: ").append(g_table_cards.hearts_broken ? "BROKEN" : "not broken").append("<br>");
		sb.append("1st ♥: ").append(firstHeartTaker == null ? "not taken yet"
				: "taken by " + firstHeartTaker + " (trick " + firstHeartTrick + ")").append("<br>");
		sb.append("Q♠: ").append(capturedCardLocation(CardValue.QUEEN, Suit.SPADE)).append("<br>");
		sb.append("J♦: ").append(capturedCardLocation(CardValue.JACK, Suit.DIAMOND)).append("<br>");
		sb.append("Taken this hand -- N:").append(rawPointsTaken(table_player_n))
				.append("  E:").append(rawPointsTaken(table_player_e))
				.append("  S:").append(rawPointsTaken(table_player_s))
				.append("  W:").append(rawPointsTaken(table_player_w));
		sb.append("</html>");
		lblCardTracker.setText(sb.toString());
	}

	private String capturedCardLocation(CardValue value, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			Player seat = g_table_cards.players.get(p);
			if (seat == null || seat.won_cards == null) {
				continue;
			}
			for (Card c : seat.won_cards.hand) {
				if (c.cardValue == value && c.suit == suit) {
					return "taken by " + p;
				}
			}
		}
		return "still in play";
	}

	/** Omnibus points captured so far this hand: each Heart +1, the Queen of
	 * Spades +13, the Jack of Diamonds -10. */
	private int rawPointsTaken(Player seat) {
		if (seat == null || seat.won_cards == null) {
			return 0;
		}
		int points = 0;
		for (Card c : seat.won_cards.hand) {
			if (Rules.isQueenOfSpades(c)) {
				points += 13;
			} else if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
				points -= 10;
			} else if (c.suit == Suit.HEART) {
				points += 1;
			}
		}
		return points;
	}

	public void beginPassingPhase() {
		applySelectedPlayerTypes();
		// Added 2026-08-09: the once-per-hand work that is independent of the
		// pass direction -- logging every seat's dealt hand, and deciding each
		// seat's moon-shot commitment. This method is the only place BOTH
		// hand-start paths (the constructor's first hand and jbStartHand's
		// every-later-hand) pass through for every direction including KEEP,
		// which is exactly why it belongs here rather than inside
		// Deal_Hand.passCards() where it used to be stranded behind the KEEP
		// early return. Must stay after applySelectedPlayerTypes(): the
		// moon-shot decision is gated by TacticWeights on each seat's p_type.
		Deal_Hand.beginHand(all_hands, g_table_cards, PLAYER.SOUTH);
		updateCardTracker();
		// Keeps the header's PASS column (jl_a index 1) in sync with the
		// real direction for THIS hand -- previously only jbStartHand's own
		// handler did this, so hand 1 (which starts here, from the
		// constructor, not from a Start Hand click) never updated it at all
		// and was stuck showing whatever literal string the header was
		// first built with.
		jl_a.get(1).setText(p_d.toString());

		if (p_d == PASS_DIRECTION.KEEP) {
			lblInfoStatus.setText("    No passing this hand (KEEP)");
			completePassingAndStartHand();
			return;
		}
		passing_selection_mode = true;
		south_pass_selection.clear();
		jbPassCards.setEnabled(false);
		jbPlayCard.setEnabled(false);
		jbClearTable.setEnabled(false);
		Display_South_Hand();
		lblInfoStatus.setText("    Select 3 cards to pass " + p_d.toString());
	}

	// Added 2026-07-31: finishes the passing phase and starts the hand's
	// first trick -- the second half of what used to be one inline block at
	// each hand-start point, split so jbPassCards's own click (once the
	// human has picked exactly 3) can trigger the same continuation a KEEP
	// direction reaches immediately from beginPassingPhase(). Performs the
	// actual card exchange (all 4 seats, South's using whatever the human
	// picked) before determining who leads, since passing can move the 2 of
	// Clubs to a different hand than the original deal.
	public void completePassingAndStartHand() {
		List<Card> receivedBySouth = new ArrayList<Card>();
		boolean didExchange = passing_selection_mode;
		if (passing_selection_mode) {
			receivedBySouth = Deal_Hand.passCards(all_hands, g_table_cards, p_d, PLAYER.SOUTH, south_pass_selection);
			passing_selection_mode = false;
			south_pass_selection.clear();
		}

		Display_South_Hand();
		// Added 2026-07-31 per the user's request: mark the 3 cards South
		// just received the same way selecting cards to pass away marks them
		// (a red border), so they're easy to spot in the freshly re-sorted
		// hand rather than needing to be hunted down by eye.
		highlightCards(receivedBySouth);

		if (didExchange) {
			// Added 2026-07-31: pause here with a real "Continue" step
			// instead of jumping straight into Play_Hand_1 -- that used to
			// fire immediately, which could start showing computer plays on
			// the table before the human had a real chance to look at what
			// they just received (the highlight above). KEEP hands (no
			// exchange happened, didExchange is false) skip this entirely --
			// there's nothing to review.
			StringBuilder summary = new StringBuilder("    You received: ");
			for (int i = 0; i < receivedBySouth.size(); i++) {
				if (i > 0) {
					summary.append(", ");
				}
				Card c = receivedBySouth.get(i);
				summary.append(c.cardValue).append(" of ").append(c.suit);
			}
			summary.append(" -- click Continue to start the hand");
			lblInfoStatus.setText(summary.toString());

			awaiting_continue_click = true;
			jbPassCards.setText("Continue");
			jbPassCards.setEnabled(true);
			return;
		}

		startFirstTrick();
	}

	// Added 2026-07-31: the actual "begin play" step, split out of
	// completePassingAndStartHand() so it can be reached two ways -- directly
	// (KEEP hands, nothing to review) or after the human clicks "Continue"
	// once they've seen their received cards (see jbPassCards's listener).
	private void startFirstTrick() {
		who_leads = all_hands.Find_Lead(all_hands);
		current_player = who_leads;
		g_table_cards.who_leads = who_leads;
		g_table_cards.current_player = who_leads;
		g_table_cards.play_position = 1;
		System.out.println("****** who_leads = " + who_leads.toString() + " *******");
		System.out.println("****** current_player = " + current_player.toString() + "   *******");

		// BUG FIX (2026-07-31, found via live playthrough -- South held the
		// 2 of Clubs and should have led hand 3, but the trick recorded a
		// stale card from South's last play of the PREVIOUS hand instead):
		// this used to unconditionally re-enable Play Card right here,
		// before calling Play_Hand_1 below. Play_Hand_1 is being invoked
		// DIRECTLY (not via a Play Card click) -- if who_leads is SOUTH, it
		// immediately pauses waiting for South's own hand-card click, and
		// Play Card sitting enabled in that exact window is the same
		// one-shot-button violation fixed earlier this session (a click
		// there hijacks the pending turn using whatever stale card is left
		// over from South's last real play). Every earlier test session
		// happened to have a computer seat lead first, so this never
		// actually got exercised until now. Leave it disabled here --
		// beginPassingPhase() already disabled it, and it's only meant to
		// come back via Clear Table once a trick actually resolves.
		jbPlayCard.setText("Play Card 1");
		jbClearTable.setText("Clear Table");
		jbClearTable.setEnabled(false);
		p_h.Play_Hand_1(g_table_cards);
	}

	// Added 2026-07-31. Zips jbA (this hand's buttons) against the current
	// South hand by index, same lockstep convention Update_South_Hand_Legality()
	// already relies on, and border-highlights whichever buttons match a
	// card in `cards` by value/suit.
	private void highlightCards(List<Card> cards) {
		if (cards.isEmpty()) {
			return;
		}
		Hand current = all_hands.s_hand_get(all_hands);
		int n = Math.min(jbA.size(), current.hand.size());
		for (int i = 0; i < n; i++) {
			Card handCard = current.hand.get(i);
			for (Card c : cards) {
				if (c.cardValue == handCard.cardValue && c.suit == handCard.suit) {
					jbA.get(i).setBorder(new LineBorder(Color.RED, 4));
					break;
				}
			}
		}
	}

	// Added 2026-07-30: Play Card and Clear Table gate each other -- Play
	// Card is only enabled once the table's been cleared (see jbClearTable's
	// own handler), and Clear Table is only enabled once all 4 cards are
	// actually on the table, which is exactly this moment (called from
	// Play_Hand.java right after resolveTrick(), both call sites).
	public void onTrickComplete() {
		jbPlayCard.setEnabled(false);
		jbClearTable.setEnabled(true);
		// Both of Play_Hand's trick-resolution call sites come through here
		// right after resolveTrick(), so this is the one spot that catches
		// every captured card -- including a Queen quietly discarded
		// mid-trick, the exact event the tracker exists to surface.
		recordFirstHeart(g_table_cards);
		updateCardTracker();
	}

	/** If nobody has taken a Heart yet this hand and the trick that just
	 * resolved held one, its winner took the first Heart. */
	private void recordFirstHeart(Table_Cards tc) {
		if (firstHeartTaker != null || tc.who_won == null) {
			return;
		}
		Card[] trick = { tc.n_card, tc.e_card, tc.s_card, tc.w_card };
		for (Card c : trick) {
			if (c != null && c.suit == Suit.HEART) {
				firstHeartTaker = tc.who_won;
				firstHeartTrick = tc.trick_nbr;
				return;
			}
		}
	}

	// Added 2026-07-30, wiring up the scoring TODO the original author left
	// as a comment in jbPlayCard's handler ("If this was last round update
	// score / Display pop-up with round score"). Called from Play_Hand.java
	// once tc.trick_nbr reaches 13 (all tricks of the hand played).
	/** Every seat's running game total, for HAND_ENDED's `totals` fact
	 * (2026-10-10, the comeback stat's midpoint snapshot). */
	private Map<PLAYER, Integer> runningTotals() {
		Map<PLAYER, Integer> totals = new EnumMap<PLAYER, Integer>(PLAYER.class);
		totals.put(PLAYER.NORTH, Integer.valueOf(table_player_n.p_total_points));
		totals.put(PLAYER.EAST, Integer.valueOf(table_player_e.p_total_points));
		totals.put(PLAYER.SOUTH, Integer.valueOf(table_player_s.p_total_points));
		totals.put(PLAYER.WEST, Integer.valueOf(table_player_w.p_total_points));
		return totals;
	}

	public void End_Of_Hand(Table_Cards tc) {
		Map<PLAYER, Hand> wonCardsByPlayer = new EnumMap<PLAYER, Hand>(PLAYER.class);
		wonCardsByPlayer.put(PLAYER.NORTH, table_player_n.won_cards);
		wonCardsByPlayer.put(PLAYER.EAST, table_player_e.won_cards);
		wonCardsByPlayer.put(PLAYER.SOUTH, table_player_s.won_cards);
		wonCardsByPlayer.put(PLAYER.WEST, table_player_w.won_cards);

		Map<PLAYER, Integer> handScores = Scoring.computeHandScores(wonCardsByPlayer);

		table_player_n.p_round_points = handScores.get(PLAYER.NORTH);
		table_player_e.p_round_points = handScores.get(PLAYER.EAST);
		table_player_s.p_round_points = handScores.get(PLAYER.SOUTH);
		table_player_w.p_round_points = handScores.get(PLAYER.WEST);

		table_player_n.p_total_points += handScores.get(PLAYER.NORTH);
		table_player_e.p_total_points += handScores.get(PLAYER.EAST);
		table_player_s.p_total_points += handScores.get(PLAYER.SOUTH);
		table_player_w.p_total_points += handScores.get(PLAYER.WEST);

		// BUG FIX (2026-07-30, found via live playthrough): the running-total
		// cells in the top info bar (WEST/NORTH/EAST/SOUTH row) were only ever
		// initialized to "0" as literal strings when jl_a was built -- nothing
		// ever updated them afterward, so they stayed "0" forever even though
		// the correct totals were being computed and shown in lblInfoStatus's
		// text summary the whole time. jl_a index 2/3/4/5 = WEST/NORTH/EAST/
		// SOUTH (see the a=7..13 loop that builds jl_a: case 9=WEST, 10=NORTH,
		// 11=EAST, 12=SOUTH -- jl_a.add() happens in that same order).
		jl_a.get(2).setText(Integer.toString(table_player_w.p_total_points));
		jl_a.get(3).setText(Integer.toString(table_player_n.p_total_points));
		jl_a.get(4).setText(Integer.toString(table_player_e.p_total_points));
		jl_a.get(5).setText(Integer.toString(table_player_s.p_total_points));

		// Re-enable "Start Hand" now that this hand is genuinely over -- it's
		// disabled the moment a hand starts (see jbStartHand's own
		// setEnabled(false) calls) specifically to stop a mid-hand click from
		// silently reshuffling and discarding whatever's in progress.
		jbStartHand.setEnabled(true);

		// Spelled out instead of the old "N:+14 E:-8 ..." shorthand -- that
		// read as cryptic to a non-programmer, and the running totals now
		// have their own home in the header row (see the jl_a updates
		// above), so this box only needs to explain this one hand's points.
		String summary = String.format(
				"Hand over -- North: %+d, East: %+d, South: %+d, West: %+d",
				handScores.get(PLAYER.NORTH), handScores.get(PLAYER.EAST),
				handScores.get(PLAYER.SOUTH), handScores.get(PLAYER.WEST));
		System.out.println("****** " + summary + " ******");

		// Added 2026-08-15 -- see GameEvent.HAND_ENDED/MOON_SUCCEEDED.
		GameEventBus.publish(GameEvent.HAND_ENDED, GameEventBus.facts()
				.with("handNumber", Integer.valueOf(hand_cnt)).with("scores", handScores)
				.with("totals", runningTotals()).build());
		for (PLAYER seat : PLAYER.values()) {
			if (Scoring.isShootingTheMoon(wonCardsByPlayer.get(seat))) {
				GameEventBus.publish(GameEvent.MOON_SUCCEEDED, GameEventBus.facts()
						.with("shooter", seat)
						.with("capturedJackToo", Boolean.valueOf(Scoring.hasJackOfDiamonds(wonCardsByPlayer.get(seat))))
						.build());
			}
		}

		// Added 2026-08-09: the actual end of the GAME, which never existed
		// before -- hands simply kept being dealt forever. Standard Hearts:
		// once any seat reaches the target, play stops and the LOWEST total
		// wins (ties share the win).
		String gameOverText = checkForGameOver();
		lblInfoStatus.setText(gameOverText == null ? summary : summary + "   |   " + gameOverText);
	}

	/** Clears the running game totals so the next deal starts a fresh game.
	 * Added 2026-08-09 alongside the win condition -- p_total_points is the
	 * one piece of state that deliberately survives End_Of_Hand (it's the
	 * running total, not a per-hand value), so it needs an explicit reset and
	 * this is the only place that does it. */
	private void startNewGame() {
		for (PLAYER seat : PLAYER.values()) {
			g_table_cards.players.get(seat).p_total_points = 0;
			g_table_cards.players.get(seat).p_round_points = 0;
			// Grudges last for one game only (2026-10-10, Payback.java).
			g_table_cards.players.get(seat).grudges.clear();
			// Wildness's opening hands count from each new game (2026-10-11).
			g_table_cards.players.get(seat).handsThisGame = 0;
		}
		// jl_a index 2/3/4/5 = WEST/NORTH/EAST/SOUTH, same mapping End_Of_Hand
		// writes the running totals into.
		for (int i = 2; i <= 5; i++) {
			jl_a.get(i).setText("0");
		}
		// Seeded to 0, not 1: this runs inside jbStartHand's handler BEFORE the
		// deal below, and that path increments hand_cnt on its way through, so
		// 0 here is what makes a new game's first hand display as 1.
		hand_cnt = 0;
		game_over = false;
		jbStartHand.setText("Start Hand");
		// Restart the pass-direction cycle so a new game's first hand passes
		// LEFT, exactly as the very first hand of a session does. The deal
		// below advances it via Next_Direction(), and KEEP's successor is
		// LEFT -- so seeding KEEP here is what lands hand 1 on LEFT.
		p_d = PASS_DIRECTION.KEEP;
		rotateComputerSeats();
		System.out.println("****** NEW GAME -- totals reset ******");
	}

	/** If any seat has reached TARGET_SCORE, ends the game and returns the
	 * announcement text; otherwise returns null and play continues. Split out
	 * of End_Of_Hand() so the win condition is stated in exactly one place.
	 * Added 2026-08-09. */
	private String checkForGameOver() {
		int highest = Integer.MIN_VALUE;
		int lowest = Integer.MAX_VALUE;
		for (PLAYER seat : PLAYER.values()) {
			int total = g_table_cards.players.get(seat).p_total_points;
			highest = Math.max(highest, total);
			lowest = Math.min(lowest, total);
		}
		if (highest < TARGET_SCORE) {
			return null;
		}

		// Lowest total wins. A tie is a genuine possibility (negative Jack of
		// Diamonds scores make exact ties more likely here than in standard
		// Hearts), so report every seat on the low score rather than silently
		// picking whichever the enum happens to list first.
		StringBuilder winners = new StringBuilder();
		for (PLAYER seat : PLAYER.values()) {
			if (g_table_cards.players.get(seat).p_total_points == lowest) {
				if (winners.length() > 0) {
					winners.append(" and ");
				}
				winners.append(seat.toString());
			}
		}

		game_over = true;
		jbStartHand.setText("New Game");
		String announcement = (winners.indexOf(" and ") >= 0 ? "GAME OVER -- tie between " : "GAME OVER -- ")
				+ winners + " wins with " + lowest;
		System.out.println("****** " + announcement + " ******");
		// Added 2026-08-15 -- see GameEvent.GAME_ENDED. `totals` added
		// 2026-08-16 so the stats store can compute placements.
		Map<PLAYER, Integer> finalTotals = new EnumMap<PLAYER, Integer>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			finalTotals.put(seat, Integer.valueOf(g_table_cards.players.get(seat).p_total_points));
		}
		GameEventBus.publish(GameEvent.GAME_ENDED, GameEventBus.facts()
				.with("winners", winners.toString()).with("lowestTotal", Integer.valueOf(lowest))
				.with("totals", finalTotals).build());
		return announcement;
	}

	public static  ImageIcon get_icon (Card in_card) {
		ImageIcon ii = null;
// 		int b;
		String h_a;
		ImageIcon icon = null;
		System.out.println("***** Getting Icon *******"); 

		if (display_icon_cnt == 0) {
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
 			h_a = h_fileName;
			imagedir = imagedir + h_fileName + ".gif";
		
			icon = new ImageIcon(imagedir, h_fileName);
				     
			iA.add(icon);
		 }	
		return ii;
	}
	
	public  void Not_Your_Turn() {
		
		lblInfoStatus.setText("    Not Your Turn");  
		
	}
	
	
//	public  ImageIcon remove_button (String a) {
	public  Card remove_button (String a) {
		ImageIcon icon = null;
		int b;
		String c;
		
		
		a_hand = all_hands.s_hand_get(all_hands);
		
		Card hc = new Card(null, null, null, null);
		
		String[] words = a.split("\\s+");
		c = words[0];
		
		hc.setCardValue(c);
		c = words[1];
		hc.setSuit(c);
		
		b = a_hand.find_card(hc);

		// Diagnostic added 2026-08-06 (see Update_South_Hand_Legality). A -1
		// here means the clicked card wasn't found in the hand at all, which
		// would throw below and leave the three lists permanently out of step
		// -- exactly the shape of "my cards started disappearing".
		System.out.println("****** remove_button '" + a + "' -> index " + b
				+ " (buttons=" + jbA.size() + " onScreen=" + jpHand.getComponentCount()
				+ " cards=" + a_hand.hand.size() + ") ******");
		if (b < 0) {
			System.out.println("****** remove_button COULD NOT FIND THE CARD -- aborting removal ******");
			return hc;
		}

 		a_hand.pull_card(b);
//		a_hand.hand.remove(hc);
		
	
		jpHand.remove(b);
		jbA.indexOf(b);
		icon = (ImageIcon) jbA.get(b).getIcon();
		
		hc.setIcon((ImageIcon) jbA.get(b).getIcon());
		
		Play_your_card(hc.cardIcon);
		
		jbA.remove(b);
		
		revalidate();
		repaint();
		return hc;
		}
	
	
	public static void main(String[] args) {

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {

					Hearts_JFrame frame = new Hearts_JFrame();
					frame.setVisible(true);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	

/**
 * Create the frame.
 */

	@SuppressWarnings("null")
	public Hearts_JFrame()
	{
		// Added 2026-08-15 -- proves GameEventBus actually fires; see
		// ConsoleEventLogger's own comment. As of 2026-08-16 it is no
		// longer the only listener: the StatsRecorder below is the first
		// real consumer the bus was built for.
		ConsoleEventLogger.install();
		// 2026-10-17 -- keep each game's console play-by-play on disk for review.
		GameLog.install();
		// Persistent roster + shared stats store (2026-08-16). Loaded from
		// %USERPROFILE%\.hearts2\ (created with a starter roster on first
		// run). SOUTH's identity never changes; the three AI seats get
		// theirs whenever applySelectedPlayerTypes() runs.
		roster = Roster.loadOrCreate(Roster.defaultDirectory());
		statsStore = StatsStore.loadOrCreate(Roster.defaultDirectory());
		statsRecorder = new StatsRecorder(statsStore, Roster.defaultDirectory());
		statsRecorder.setSeatId(PLAYER.SOUTH, Roster.HUMAN_ID);
		GameEventBus.subscribe(statsRecorder);
		// 2026-10-17 -- one CSV row per finished game (win rate, moon tries over time).
		GameEventBus.subscribe(new GameLedger(GameLedger.defaultFile(), seat -> {
			if (seat == PLAYER.SOUTH) {
				return roster.humanName();
			}
			RosterPlayer rp = roster.byId(statsRecorder.seatId(seat));
			return rp == null ? String.valueOf(statsRecorder.seatId(seat)) : rp.displayName;
		}));
		this.setTitle("This is the Title");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 1400, 784);
		this.setVisible(true);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		getContentPane().setBackground(new Color(51, 153, 51));
	
		JLabel lblNorth_1 = new JLabel("NORTH");
		lblNorthSeat = lblNorth_1;
		lblNorth_1.setBounds(355, 139, 140, 24);
		lblNorth_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblNorth_1.setBorder(new LineBorder(new Color(0, 0, 0), 3));
		lblNorth_1.setFont(new Font("Tahoma", Font.PLAIN, 14));
		contentPane.add(lblNorth_1);
		
		JLabel lblWest_1 = new JLabel("WEST");
		lblWestSeat = lblWest_1;
		lblWest_1.setBounds(227, 222, 31, 127);
		lblWest_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblWest_1.setFont(new Font("Tahoma", Font.PLAIN, 14));
		lblWest_1.setBorder(new LineBorder(new Color(0, 0, 0), 3));
		lblWest_1.setUI(new VerticalLabelUI(true));
		
		contentPane.add(lblWest_1);
		
		JLabel lblEAST_1 = new JLabel("EAST");
		lblEastSeat = lblEAST_1;
		lblEAST_1.setBounds(602, 222, 31, 127);
		lblEAST_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblEAST_1.setFont(new Font("Tahoma", Font.PLAIN, 14));
		lblEAST_1.setBorder(new LineBorder(new Color(0, 0, 0), 3));
		lblEAST_1.setUI(new VerticalLabelUI(false));
		
		contentPane.add(lblEAST_1);
		
		JLabel lblSouth_1 = new JLabel("SOUTH");
		lblSouthSeat = lblSouth_1;
		lblSouth_1.setBounds(355, 450, 140, 24);
		lblSouth_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblSouth_1.setBorder(new LineBorder(new Color(0, 0, 0), 3));
		lblSouth_1.setFont(new Font("Tahoma", Font.PLAIN, 14));
		contentPane.add(lblSouth_1);
		

    
     System.out.println("*******  New Deck  JFrame   ***********");
     System.out.println("******  Shuffle New Deck   *******");
     
     gameDeck.shuffle_now();
          
     System.out.println("******  Shuffle New Deck end   *******");
     System.out.println("******  New a_hands   *******");
     
     all_hands = gameDeck.deal(PLAYER.NORTH, gameDeck);
     // 2026-10-17 -- replay a specific deal if ~/.hearts2/forced_deal.txt exists.
     all_hands = ForcedDeal.applyIfPending(all_hands, gameDeck);

//*********   Add hands to EnumSet Hands    *********************
     Add_Hand2(all_hands);
     
     Display_Hand2();
             
     System.out.println("******  New a_hands end   *******");
     
     
     current_dealer = PLAYER.NORTH;
    
      System.out.println("******  Show Hands   *******");
      System.out.println("******  North Hand   *******");
 
      a_hand = all_hands.n_hand_get(all_hands);
      a_hand.display_hand(a_hand);
     

      System.out.println("******  East Hand   *******");

      a_hand = all_hands.e_hand_get(all_hands);
      a_hand.display_hand(a_hand);

      System.out.println("******  South Hand   *******");
	
      a_hand = all_hands.s_hand_get(all_hands);
      a_hand.display_hand(a_hand);
	
		System.out.println("******  West Hand   *******");
	
		a_hand = all_hands.w_hand_get(all_hands);
	      a_hand.display_hand(a_hand);
		
		
//  ***********************************************
//		JLabel lblInfoStatus = new JLabel("     INFO");
		lblInfoStatus.setHorizontalAlignment(SwingConstants.LEFT);
		lblInfoStatus.setBackground(new Color(102, 255, 153));
		lblInfoStatus.setBounds(546, 457, 600, 75);
//			lblInfoStatus.setBackground(bg);
		lblInfoStatus.setOpaque(true);
		contentPane.add(lblInfoStatus);

// *********************************************************************************************
// ********        Configure the 3 computer players                                     ********
//**********************************************************************************************

		// Seat dropdowns (2026-08-06, reworked 2026-08-16 for the roster).
		// Each seat picks a persistent roster member; the member's
		// Player_Type is what applySelectedPlayerTypes() loads at the start
		// of each deal. Defaults to the first three roster entries.
		JLabel lblPlayerTypes = new JLabel("Computer Players");
		lblPlayerTypes.setBounds(1000, 150, 200, 24);
		contentPane.add(lblPlayerTypes);

		RosterPlayer[] rosterChoices = roster.players().toArray(new RosterPlayer[0]);

		JLabel lblNorthType = new JLabel("North:");
		lblNorthType.setBounds(1000, 180, 60, 24);
		contentPane.add(lblNorthType);
		jcbNorthType = new JComboBox<RosterPlayer>(rosterChoices);
		jcbNorthType.setSelectedIndex(0);
		selectIfPresent(jcbNorthType, "AI_09");
		jcbNorthType.setBounds(1062, 180, 220, 24);
		contentPane.add(jcbNorthType);

		JLabel lblEastType = new JLabel("East:");
		lblEastType.setBounds(1000, 212, 60, 24);
		contentPane.add(lblEastType);
		jcbEastType = new JComboBox<RosterPlayer>(rosterChoices);
		jcbEastType.setSelectedIndex(Math.min(1, rosterChoices.length - 1));
		selectIfPresent(jcbEastType, "AI_03");
		jcbEastType.setBounds(1062, 212, 220, 24);
		contentPane.add(jcbEastType);

		JLabel lblWestType = new JLabel("West:");
		lblWestType.setBounds(1000, 244, 60, 24);
		contentPane.add(lblWestType);
		jcbWestType = new JComboBox<RosterPlayer>(rosterChoices);
		jcbWestType.setSelectedIndex(Math.min(2, rosterChoices.length - 1));
		selectIfPresent(jcbWestType, "AI_10");
		jcbWestType.setBounds(1062, 244, 220, 24);
		contentPane.add(jcbWestType);

		// Stats screen (2026-08-16) -- renders the shared StatsStore's
		// counters for the human player; see StatsReport.
		JButton jbStats = new JButton("Stats");
		jbStats.setBounds(1062, 276, 100, 24);
		jbStats.addActionListener(e -> showStatsDialog());
		contentPane.add(jbStats);

		JLabel lblTypeNote = new JLabel("(applies at the start of the next hand)");
		lblTypeNote.setBounds(1000, 274, 300, 20);
		contentPane.add(lblTypeNote);

		// Card tracker (2026-08-07) -- see the field comment for why this
		// exists. The checkbox only controls VISIBILITY; the tracker text is
		// still refreshed every trick either way, so re-enabling it mid-hand
		// shows current facts, not stale ones.
		jchkCardTracker = new JCheckBox("Show card tracker", true);
		jchkCardTracker.setBounds(1000, 304, 300, 24);
		jchkCardTracker.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				lblCardTracker.setVisible(jchkCardTracker.isSelected());
			}
		});
		contentPane.add(jchkCardTracker);

		lblCardTracker = new JLabel();
		lblCardTracker.setVerticalAlignment(SwingConstants.TOP);
		lblCardTracker.setBounds(1000, 334, 300, 140);
		lblCardTracker.setOpaque(true);
		lblCardTracker.setBackground(new Color(102, 255, 153));
		contentPane.add(lblCardTracker);
		updateCardTracker();

		// No need to load the traits here -- beginPassingPhase() at the end of
		// this constructor does it for hand 1, the same way jbStartHand does
		// for every later hand.
		table_player_n.tbl_player = PLAYER.NORTH;
		table_player_e.tbl_player = PLAYER.EAST;
		table_player_w.tbl_player = PLAYER.WEST;
		table_player_s.tbl_player = PLAYER.SOUTH;

		// Added 2026-07-31 for AI.java's moon-shooter-stopping logic -- it
		// needs to inspect OTHER seats' won_cards/p_total_points, not just
		// the acting player's, but Table_Cards (a static nested class) can't
		// reach these outer-class fields on its own. These 4 Player objects
		// persist for the whole game session (never recreated per hand), so
		// this only needs populating once, here, not reset per hand like
		// void_suits. MOVED UP (2026-07-31) from after Find_Lead/
		// Display_South_Hand to before them -- the new passing phase below
		// needs this map (standings/traits per seat) before it can decide
		// what to pass.
		g_table_cards.players.put(PLAYER.NORTH, table_player_n);
		g_table_cards.players.put(PLAYER.EAST, table_player_e);
		g_table_cards.players.put(PLAYER.SOUTH, table_player_s);
		g_table_cards.players.put(PLAYER.WEST, table_player_w);

		// Added 2026-07-30 for Scoring.java -- won_cards accumulates across
		// a whole hand's 13 tricks, so it must be cleared at the start of
		// every new hand (these Player objects persist for the whole game
		// session, they're not recreated per hand).
		table_player_n.won_cards = new Hand();
		table_player_e.won_cards = new Hand();
		table_player_s.won_cards = new Hand();
		table_player_w.won_cards = new Hand();

		// NOTE (2026-07-31): the passing phase, Find_Lead(), and the actual
		// hand-start trigger all moved to the very end of this constructor
		// (see the beginPassingPhase() call there) -- they need jbPlayCard/
		// jbClearTable/jbPassCards to already exist (beginPassingPhase()
		// disables them while South is selecting cards to pass), and those
		// buttons aren't built until later in this same constructor. Building
		// them here, this early, would NullPointerException.
		System.out.println("******  end Special Display Deck   *******");
	
		
		Color defaultColor 		= Color.WHITE;
		Color darkRedColor 		= Color.RED;
		Color darkBlueColor 	= Color.BLUE;
		Color lightBlueColor 	= Color.CYAN;
		Color lightRedColor 	= Color.PINK;
		
		
		final JPanel jpInfoBoxes = new JPanel();
		jpInfoBoxes.setBounds(64, 25, 759, 75);
		
		GridLayout glInfoBoxes = new GridLayout(2,7);
		
		jpInfoBoxes.setLayout(glInfoBoxes);
		

		for (int a=0; a<7; a++)
	        {
				JLabel jlNew = new JLabel("" + Hearts_1.displayInfoBoxes.values()[a]);
				jlNew.setHorizontalAlignment(SwingConstants.CENTER);
				jlNew.setBorder(new LineBorder(new Color(0, 0, 0), 3));
				jpInfoBoxes.add(jlNew);
			
	        }  
	      
		String h_string = null;
		
		for (int a=7; a<14; a++)
        {
			
			switch (a)  {
				case 7:		h_string = "NORTH"; 	break;
				case 8:		h_string = "LEFT"; 	    break;
				case 9:     h_string = "0";			break;
				case 10:    h_string = "0";			break;
				case 11:    h_string = "0";			break;
				case 12:	h_string = "0";			break;
				case 13:	h_string = "1";			break;
			}
				
			JLabel jlNew = new JLabel(h_string);
			jlNew.setHorizontalAlignment(SwingConstants.CENTER);
			jlNew.setBorder(new LineBorder(new Color(0, 0, 0), 3));
			
			jl_a.add(jlNew);
			jpInfoBoxes.add(jlNew);
				
        }  
	    
//  This informs who's the current dealer 		
//		JLabel h_jl = (JLabel) jpInfoBoxes.getComponent(7);
		
		
//  	 jl_a.get(0).setText(who_leads.name());
		 jl_a.get(0).setText(current_dealer.toString());

		// p_d is now initialized earlier, right before the passing phase
		// runs (see "Added 2026-07-31: the passing phase" above) -- it needs
		// a real value before that point, not after it.

	//	h_jl.setText(who_leads.name());
		
		JLabel lblDeal = new JLabel("Deal");
		lblDeal.setHorizontalAlignment(SwingConstants.CENTER);
		lblDeal.setBorder(new LineBorder(new Color(0, 0, 0), 3));
	
		contentPane.add(jpInfoBoxes);

//  *****   West Label   ************************************
		System.out.println("****** New label West start  *****");
		lblWest = new JLabel("New label");
		lblWest.setBounds(287, 237, 76, 105);
		a_hand = all_hands.w_hand_get(all_hands);
		contentPane.add(lblWest);    
		System.out.println("****** New label West end  *****");

//  *****   North Label   ************************************
		System.out.println("****** New label North start  *****");
		lblNorth = new JLabel("New label");
		lblNorth.setBounds(390, 174, 76, 105);
		a_hand = all_hands.n_hand_get(all_hands);
		contentPane.add(lblNorth);
		System.out.println("****** New label North end  *****");
		
//  *****   East Label   ************************************
		System.out.println("****** New label East start  *****");
		lblEast = new JLabel("New label");
		lblEast.setBounds(500, 235, 76, 105);
		a_hand = all_hands.e_hand_get(all_hands);
		contentPane.add(lblEast);
		System.out.println("****** New label East end  *****");
		
		lblSouth = new JLabel("");
		lblSouth.setBounds(390, 320, 76, 105);
		contentPane.add(lblSouth);
		
//		p_h.Play_Hand_1(g_table_cards);
		
		
		
		
		
		jbPlayCard = new JButton("Play Card 1");
		// Table starts clear at hand-start, so Play Card starts enabled;
		// disabled the moment a trick completes (see onTrickComplete()),
		// re-enabled only by Clear Table's own handler.
		jbPlayCard.setEnabled(true);
		jbPlayCard.addActionListener(new ActionListener() {
			@SuppressWarnings("deprecation")
			public void actionPerformed(ActionEvent arg0) {
			//	Play_Card();

				// BUG FIX (2026-07-30, found via live playthrough -- South
				// held the 2 of Clubs and should have led, but West ended up
				// leading with the King of Clubs instead): Play Card is meant
				// to be clicked exactly ONCE per trick, to kick off
				// Play_Hand_1's processing after Clear Table. It used to stay
				// enabled the whole time a trick was in progress, including
				// the window where South's own turn is waiting on a real
				// hand-card click (My_Turn=true, Update_South_Hand_Legality()
				// already run). A second Play Card click during that window
				// increments play_position AGAIN and re-enters Play_Hand_1 --
				// since south_status is now "second" (not na/first), that
				// takes the "South already played" branch using whatever
				// STALE tc.s_card was left over from the previous trick,
				// skipping South's real lead/follow entirely and corrupting
				// play_position/rotation for the rest of the trick. Disabling
				// immediately on use (re-enabled only by Clear Table) makes
				// this a true one-shot-per-trick button.
				jbPlayCard.setEnabled(false);

				// BUG FIX (2026-08-10, found via live playthrough): hand_cnt++ and
				// the HAND cell update used to live right here, inside the Play
				// Card handler -- so the counter advanced once per CARD PLAYED
				// rather than once per hand, and the cell labelled HAND read 120
				// on the tenth hand of a game. Moved to the hand-start path (see
				// jbStartHand's handler), which is the one place a new hand
				// actually begins.


//  **************************************************************************
//  ***************    Logic for Playing Hands goes here   *******************
//  **************************************************************************

				i_cnt++;
				
//    Create new Class to Play Player's Hand
//
				
				g_table_cards.play_position++;
//				g_table_cards.current_player  = g_table_cards.current_player.Next_Dealer();
				System.out.println("****** Start Who Leads " + g_table_cards.who_leads + "   *****");
				System.out.println("****** Start Current   " + g_table_cards.current_player + "   *****");
			   
               p_h.Play_Hand_1(g_table_cards);
				
               
             

				
				
//  **************************************************************************
//  ***************    Set flag as player's play card to signal who won ******
//	***************    who_won                                          ******
//  **************************************************************************						
				

//				lblNorth.setIcon(all_hands.n_hand_get(all_hands).get_it(i_cnt).getCard().getIcon());
//				lblWest.setIcon(all_hands.w_hand_get(all_hands).get_it(i_cnt).getCard().getIcon());
//				lblEast.setIcon(all_hands.e_hand_get(all_hands).get_it(i_cnt).getCard().getIcon());
//				
//				
				
//				if (i_cnt == 13) {
//					jbPlayCard.setEnabled(false);  }
//					else {
//						jbPlayCard.setText("Play Card " + (i_cnt + 1));
//						}
	
//  *********************************************************************************
//  ***************    Display who won hand, Say Play next card   *******************	
//  ***************    If this was last round update score        *******************	
//  ***************    Display pop-up with round score            *******************		
//  *********************************************************************************						
		
		}

			
		});
		jbPlayCard.setBounds(763, 300, 200, 50);
		contentPane.add(jbPlayCard);
		
		
		
		
		jbClearTable = new JButton("Clear Table");
		// No cards on the table yet at hand-start, so Clear Table starts
		// disabled; only enabled by onTrickComplete() once all 4 are down.
		jbClearTable.setEnabled(false);
		jbClearTable.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				jbPlayCard.setText("Play Card");
				jbClearTable.setText("Clear Table");
				lblNorth.setIcon(null);
				lblWest.setIcon(null);
				lblEast.setIcon(null);
				lblSouth.setIcon(null);
				lblNorth.setText(null);
				lblNorth.setText(null);
				lblWest.setText(null);
				lblEast.setText(null);
				lblSouth.setText(null);
				revalidate();
				repaint();

				// BUG FIX (2026-07-30, found via live playthrough -- crashed
				// with IndexOutOfBoundsException in AI.longestSuitAmong()):
				// Clear Table used to unconditionally re-enable Play Card,
				// even when the hand it just cleared was the LAST trick
				// (End_Of_Hand() already fired, all 4 hands empty). Clicking
				// Play Card at that point tried to process another lead/
				// follow with an empty hand -- Rules.legalLeads()/legalFollows()
				// can't manufacture a non-empty legal-move list from an empty
				// hand, so AI.chooseLead() crashed trying to read cards.get(0)
				// off an empty list. Once a hand is over, Start Hand (not
				// Play Card) is the only valid next action -- it already
				// re-enables Play Card itself once the new hand is dealt.
				if (g_table_cards.trick_nbr < 13) {
					jbPlayCard.setEnabled(true);
				}
				jbClearTable.setEnabled(false);

				g_table_cards.play_position = 0;
			    g_table_cards.south_status = South_Hand_Status.first;
			}
		});
		jbClearTable.setBounds(763, 361, 200, 50);
		contentPane.add(jbClearTable);

		// Added 2026-07-31 for the human passing UI -- see
		// toggleCardSelection()/beginPassingPhase()/completePassingAndStartHand().
		// Starts disabled; only enabled once south_pass_selection holds
		// exactly 3 cards, and only reachable at all while
		// passing_selection_mode is true.
		jbPassCards = new JButton("Pass Cards");
		jbPassCards.setEnabled(false);
		jbPassCards.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Added 2026-07-31: this button does double duty -- first
				// click ("Pass Cards") performs the exchange and pauses with
				// the received cards highlighted; second click ("Continue",
				// only reachable via that pause) actually starts the trick.
				if (awaiting_continue_click) {
					awaiting_continue_click = false;
					jbPassCards.setEnabled(false);
					jbPassCards.setText("Pass Cards");
					startFirstTrick();
					return;
				}
				if (south_pass_selection.size() != 3) {
					return; // shouldn't happen -- the button's only enabled at exactly 3
				}
				jbPassCards.setEnabled(false);
				completePassingAndStartHand();
			}
		});
		jbPassCards.setBounds(763, 422, 200, 50);
		contentPane.add(jbPassCards);

		jbStartHand = new JButton("Start Hand");
		// The constructor kicks off the passing phase automatically at its
		// very end (see the beginPassingPhase() call there) without needing
		// "Start Hand" clicked, so this starts disabled the same as before;
		// re-enabled by End_Of_Hand() once that hand actually finishes.
		jbStartHand.setEnabled(false);
		jbStartHand.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
//    start round out  2 of club or who won last hand
				
//			Start_Hand
				
			PLAYER a1;

			// Added 2026-08-09: this button doubles as "New Game" once some
			// seat has reached TARGET_SCORE (see checkForGameOver()). Zero the
			// running totals BEFORE the normal deal below, so the rest of this
			// handler stays a single unchanged code path.
			if (game_over) {
				startNewGame();
			}

			a1 = current_dealer.Next_Dealer();
			current_dealer = a1;
			
			  g_table_cards.play_position = 1;
			  g_table_cards.south_status = South_Hand_Status.first;
			
			System.out.println("****** Dealer = " + a1 + " *****");
			
			gameDeck.shuffle_now();
			all_hands = gameDeck.deal(a1, gameDeck);
			
//*********   Add hands to EnumSet Hands    *********************
		     Add_Hand2(all_hands);
		     
		     Display_Hand2();
			
			// Cards won accumulate across a whole hand's 13 tricks (see
			// Player.won_cards / Scoring.java) -- clear them here too,
			// same as the very first hand's setup, since these Player
			// objects persist across hands rather than being recreated.
			table_player_n.won_cards = new Hand();
			table_player_e.won_cards = new Hand();
			table_player_s.won_cards = new Hand();
			table_player_w.won_cards = new Hand();

	        // BUG FIX (2026-07-30, found via live playthrough): g_table_cards is
	        // one persistent object reused across hands, not recreated per
	        // hand -- without resetting these, the new hand would inherit the
	        // previous hand's trick_nbr/hearts_broken, silently defeating
	        // Rules.isFirstTrick() (breaking the forced-2-of-Clubs-lead rule)
	        // and letting hearts be led immediately instead of only after
	        // actually being broken this hand.
	        g_table_cards.trick_nbr = 0;
	        g_table_cards.hearts_broken = false;
	        firstHeartTaker = null;
	        firstHeartTrick = 0;
	        g_table_cards.void_suits = VoidTracker.freshVoidMap();

	        // BUG FIX (2026-08-16, found via live playthrough): the previous
	        // hand's final trick was still showing in the table-slot labels
	        // (lblNorth/East/South/West) here -- these are only ever cleared
	        // by jbClearTable's own handler, which the user has no reason to
	        // click after the very last trick of a hand (nothing left to
	        // clear FOR, since the hand is already over and this button is
	        // the natural next click). The stale icon then lingered until
	        // that seat's own next play overwrote it, which is exactly what
	        // was reported: a leftover card sitting in South's own table
	        // slot until South's real card replaced it.
	        lblNorth.setIcon(null);
	        lblWest.setIcon(null);
	        lblEast.setIcon(null);
	        lblSouth.setIcon(null);
	        lblNorth.setText(null);
	        lblWest.setText(null);
	        lblEast.setText(null);
	        lblSouth.setText(null);

	        PASS_DIRECTION current_direction;
	        current_direction = p_d.Next_Direction();
	        p_d = current_direction;
	        jbStartHand.setEnabled(false);

	        jl_a.get(0).setText(current_dealer.toString());
	        // The HAND cell (jl_a index 6, same label as jpInfoBoxes component
	        // 13). This is the only place it advances -- the session's very
	        // first hand doesn't come through here at all, which is why the
	        // label is built showing "1" and hand_cnt is seeded to 1. A new
	        // game re-seeds it to 0 in startNewGame() so this increment lands
	        // back on hand 1 rather than continuing the old game's count.
	        hand_cnt++;
	        jl_a.get(6).setText(Integer.toString(hand_cnt));
	        // jl_a.get(1) (the PASS direction display) is now synced inside
	        // beginPassingPhase() below instead -- that's the single place
	        // both hand-start paths (this one and the constructor's first
	        // hand) go through, so it's the one spot that can't miss updating it.

			i_cnt = 0;
			jbClearTable.setAction(null);

			// Added 2026-07-31: the passing phase, Find_Lead(), and starting
			// the hand's first trick all happen inside beginPassingPhase()
			// now -- it needs p_d already advanced to THIS hand's real
			// direction (just above), and it's what shows South's card
			// buttons in "select 3 to pass" mode instead of jumping straight
			// into play. See beginPassingPhase()/completePassingAndStartHand().
			beginPassingPhase();

			}
		});
		 
		jbStartHand.setBounds(763, 229, 200, 50);
		contentPane.add(jbStartHand);
		
//		JLabel lblInfoStatus = new JLabel("     INFO");
		lblInfoStatus.setHorizontalAlignment(SwingConstants.LEFT);
		lblInfoStatus.setBackground(new Color(102, 255, 153));
		lblInfoStatus.setBounds(546, 457, 600, 75);
//		lblInfoStatus.setBackground(bg);
		lblInfoStatus.setOpaque(true);
		contentPane.add(lblInfoStatus);
		
		// Added 2026-08-09: one-time keyboard binding setup -- needs
		// contentPane to already exist (it does, by this point in the
		// constructor), but nothing about jbA, which gets rebuilt fresh
		// every hand and is read live by the actions bound here rather
		// than captured now.
		setupKeyboardCardSelection();

		// Added 2026-07-31: this is now where the passing phase actually
		// kicks off for the very first hand of a session -- moved here (the
		// true end of the constructor) from the "Special Display Deck"
		// section above because beginPassingPhase() needs jbPlayCard/
		// jbClearTable/jbPassCards to already exist, and this is the first
		// point in the constructor where all of them do. p_d starts at LEFT
		// for hand 1; jbStartHand's handler advances it via Next_Direction()
		// for hand 2 onward.
		p_d = ForcedDeal.directionOr(PASS_DIRECTION.LEFT);
		beginPassingPhase();

	}
}
