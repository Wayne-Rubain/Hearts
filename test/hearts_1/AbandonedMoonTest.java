package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * 2026-10-17, live hand 4 (Shark, Aggressive, committed to the moon): the King of
 * Hearts went to South on trick 3 and North took four more Hearts on trick 4, but
 * AI.isMoonShotAlive() read "points split between two seats" as "nobody has
 * captured anything" and kept Shark in moon mode, cashing the Q/J of Hearts into
 * the Queen of Spades. User rule: a dead moon means minimize the damage -- lead
 * the low Hearts and get out of the lead.
 */
public final class AbandonedMoonTest {

	private AbandonedMoonTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		splitPointsKillTheMoon();
		deadMoonLeadsLowHeartsNotBossHearts();
		breakingTheShotUsesTheCheapestCardThatBeatsThem();
		return TestKit.summarize();
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static Table_Cards table() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.WEST).Set_Player(Player_Type.AGGRESSIVE);
		players.get(PLAYER.WEST).attempting_moon_shot = true;
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voids = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voids.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		tc.hearts_broken = true;
		return tc;
	}

	/** Live hand 4 replay: South (lone Heart-taker) led the 3 of Hearts and West,
	 * second to play, must break the shot. The 4 beats South's 3; the Ace costs
	 * the same trick plus nothing extra it could have avoided. */
	private static Table_Cards southLedThreeOfHearts(PLAYER leader) {
		Table_Cards tc = table();
		tc.players.get(PLAYER.SOUTH).won_cards.addX(card(CardValue.KING, Suit.HEART));
		tc.players.get(PLAYER.WEST).strategyThisHand.put(Strategy.MOON_ALARM, Strategy.MoonAlarm.FIRST_HEART);
		tc.who_leads = leader;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.THREE, Suit.HEART);
		return tc;
	}

	private static Hand westHearts(List<Card> out) {
		Hand h = new Hand();
		for (CardValue v : new CardValue[] { CardValue.FOUR, CardValue.FIVE, CardValue.SIX, CardValue.JACK,
				CardValue.QUEEN, CardValue.ACE }) {
			Card c = card(v, Suit.HEART);
			out.add(c);
			h.addX(c);
		}
		return h;
	}

	private static void breakingTheShotUsesTheCheapestCardThatBeatsThem() {
		TestKit.suite("breakingTheShotUsesTheCheapestCardThatBeatsThem");
		Table_Cards tc = southLedThreeOfHearts(PLAYER.SOUTH);
		Player west = tc.players.get(PLAYER.WEST);
		List<Card> legal = new ArrayList<Card>();
		Hand h = westHearts(legal);
		Card pick = AI.chooseFollow(legal, h, tc, 2, west);
		TestKit.checkEquals("South already led the 3: West takes with the 4, not the Ace",
				CardValue.FOUR, pick.cardValue);

		// Shooter still to play (North led, South acts third): a small card could be
		// overtaken, so the highest is still the way to break it.
		Table_Cards later = southLedThreeOfHearts(PLAYER.NORTH);
		Player west2 = later.players.get(PLAYER.WEST);
		List<Card> legal2 = new ArrayList<Card>();
		Hand h2 = westHearts(legal2);
		Card pick2 = AI.chooseFollow(legal2, h2, later, 2, west2);
		TestKit.checkEquals("shooter yet to play: the highest card still breaks it",
				CardValue.ACE, pick2.cardValue);
	}

	private static void splitPointsKillTheMoon() {
		TestKit.suite("splitPointsKillTheMoon");
		Table_Cards tc = table();
		Player west = tc.players.get(PLAYER.WEST);
		TestKit.check("nothing captured yet: alive", AI.isMoonShotAlive(tc, west));
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.ACE, Suit.HEART));
		TestKit.check("only the shooter holds points: alive", AI.isMoonShotAlive(tc, west));
		tc.players.get(PLAYER.SOUTH).won_cards.addX(card(CardValue.KING, Suit.HEART));
		TestKit.check("one other seat holds a Heart: dead", !AI.isMoonShotAlive(tc, west));
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.EIGHT, Suit.HEART));
		TestKit.check("points split across other seats: STILL dead (was misread as alive)",
				!AI.isMoonShotAlive(tc, west));
		TestKit.check("and the attempt counts as abandoned", AI.moonAttemptAbandoned(tc, west));
		Table_Cards q = table();
		q.players.get(PLAYER.EAST).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		TestKit.check("another seat holding the Queen kills it too",
				!AI.isMoonShotAlive(q, q.players.get(PLAYER.WEST)));
	}

	private static void deadMoonLeadsLowHeartsNotBossHearts() {
		TestKit.suite("deadMoonLeadsLowHeartsNotBossHearts");
		// Live hand 4, trick 7: West leads holding Q J 6 5 of Hearts, 9D, 4C, 10C.
		// South holds the King of Hearts, North four Hearts; the Ace of Hearts is
		// West's own and gone. The Queen of Hearts is now the boss Heart.
		Table_Cards tc = table();
		Player west = tc.players.get(PLAYER.WEST);
		tc.players.get(PLAYER.SOUTH).won_cards.addX(card(CardValue.KING, Suit.HEART));
		for (CardValue v : new CardValue[] { CardValue.EIGHT, CardValue.SEVEN, CardValue.THREE, CardValue.FOUR }) {
			tc.players.get(PLAYER.NORTH).won_cards.addX(card(v, Suit.HEART));
		}
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.NINE, CardValue.ACE }) {
			west.won_cards.addX(card(v, Suit.HEART));
		}
		List<Card> hand = new ArrayList<Card>();
		hand.add(card(CardValue.QUEEN, Suit.HEART));
		hand.add(card(CardValue.JACK, Suit.HEART));
		hand.add(card(CardValue.SIX, Suit.HEART));
		hand.add(card(CardValue.FIVE, Suit.HEART));
		hand.add(card(CardValue.NINE, Suit.DIAMOND));
		hand.add(card(CardValue.FOUR, Suit.CLUB));
		hand.add(card(CardValue.TEN, Suit.CLUB));
		Hand h = new Hand();
		for (Card c : hand) {
			h.addX(c);
		}
		tc.who_leads = PLAYER.WEST;
		tc.current_player = PLAYER.WEST;
		Card pick = AI.chooseLead(hand, h, tc, west);
		System.out.println("[AbandonedMoonTest] lead = " + pick.cardValue + " " + pick.suit);
		TestKit.check("lead is a LOW Heart (6 or 5), not the boss Queen/Jack of Hearts",
				pick.suit == Suit.HEART && (pick.cardValue == CardValue.FIVE || pick.cardValue == CardValue.SIX));
	}
}
