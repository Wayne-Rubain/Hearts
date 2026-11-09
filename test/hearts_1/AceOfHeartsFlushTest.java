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
 * Committed regression coverage for the "PULL THE ACE OF HEARTS OUT"
 * moon-offense lead, added 2026-08-16 from a live-hand review: the user
 * proposed leading the King of Diamonds specifically hoping a void seat
 * would drop the Ace of Hearts onto it. Investigating showed that exact
 * play would NOT have worked in the hand that prompted it -- nobody was
 * void in Diamonds yet, and the King was not even a guaranteed winner
 * (South held the actual Ace of Diamonds) -- but the underlying idea is
 * sound and mirrors AICardScorer's existing QUEEN_FLUSH tactic, just for
 * the Ace of Hearts instead of the Queen of Spades and for the shooter's
 * OFFENSE instead of a defender's safety.
 *
 * Two structural differences from QUEEN_FLUSH, both load-bearing:
 *   - the lead must be a PROVABLE, GUARANTEED winner (QUEEN_FLUSH wants
 *     the opposite -- provably BEATABLE), because a dropped Ace has to
 *     land in the SHOOTER's own pile to do any good; if some other seat
 *     wins the trick, the Ace is just as lost to the attempt as if it had
 *     never been flushed at all;
 *   - it only fires when Player.takesMoonGambleThisHand is true -- an
 *     opt-in per-individual, per-hand gamble (see Deal_Hand's
 *     effectiveMoonGamblePercent(), covered in MoonGambleTest) rather
 *     than something every committed shooter tries automatically.
 *
 * These fixtures deliberately flush through CLUBS, not Diamonds -- an
 * earlier draft used a guaranteed-winning Ace of Diamonds and several
 * "stands down" cases failed for the wrong reason: AICardScorer already
 * has an unrelated, older JACK_HUNT tactic ("pull the Jack of Diamonds
 * out") that ALSO leads a guaranteed-winning boss Diamond, and it doesn't
 * care about moon mode at all, so it kept firing even when this scorer's
 * own gates correctly returned 0. Clubs have no such entanglement.
 */
public final class AceOfHeartsFlushTest {

	private AceOfHeartsFlushTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		flushesWhenGamblingIsOnAndAVoidTargetExists();
		standsDownWhenGamblingIsOff();
		standsDownWithNoConfirmedVoid();
		standsDownWhenNotAGuaranteedWinner();
		standsDownOnceIAlreadyHoldTheAce();
		standsDownOnceIHaveAlreadyCapturedTheAceMyself();
		leaderTargetOutscoresNonLeaderTarget();
		theKingOfDiamondsHandDoesNotFireWithoutAConfirmedVoid();
		return TestKit.summarize();
	}

	private static void flushesWhenGamblingIsOnAndAVoidTargetExists() {
		TestKit.suite("flushesWhenGamblingIsOnAndAVoidTargetExists");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		tc.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		Card chosen = AICardScorer.pickLead(shooterHand(), tc, tc.players.get(PLAYER.NORTH));
		TestKit.checkEquals("leads the guaranteed-winning Ace of Clubs to flush the target",
				Suit.CLUB, chosen.suit);
		TestKit.checkEquals("", CardValue.ACE, chosen.cardValue);
	}

	private static void standsDownWhenGamblingIsOff() {
		TestKit.suite("standsDownWhenGamblingIsOff");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = false;
		tc.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		Card chosen = AICardScorer.pickLead(shooterHand(), tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("without the per-hand gamble roll, falls back to running the long suit (Hearts)",
				chosen.suit == Suit.HEART);
	}

	private static void standsDownWithNoConfirmedVoid() {
		TestKit.suite("standsDownWithNoConfirmedVoid");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		// No void_suits entries added -- nobody is confirmed void in anything.
		Card chosen = AICardScorer.pickLead(shooterHand(), tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("with no confirmed void, never guesses -- falls back to running Hearts",
				chosen.suit == Suit.HEART);
	}

	private static void standsDownWhenNotAGuaranteedWinner() {
		TestKit.suite("standsDownWhenNotAGuaranteedWinner");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		tc.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		// King of Clubs instead of the Ace -- the real Ace of Clubs is still
		// unaccounted for (nobody's hand, nobody's won pile), so this is NOT
		// provably the boss. Mirrors the user's own original King-of-Diamonds
		// proposal, which had exactly this problem against South's real Ace.
		List<Card> hand = cards(
				card(CardValue.KING, Suit.HEART), card(CardValue.QUEEN, Suit.HEART),
				card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.NINE, Suit.HEART),
				card(CardValue.KING, Suit.CLUB), card(CardValue.TWO, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(hand, tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("an unguaranteed King of Clubs is not led for the flush -- falls back to Hearts",
				chosen.suit == Suit.HEART);
	}

	private static void standsDownOnceIAlreadyHoldTheAce() {
		TestKit.suite("standsDownOnceIAlreadyHoldTheAce");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		tc.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		List<Card> hand = cards(
				card(CardValue.ACE, Suit.HEART), card(CardValue.KING, Suit.HEART),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART),
				card(CardValue.TEN, Suit.HEART),
				card(CardValue.ACE, Suit.CLUB), card(CardValue.TWO, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(hand, tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("already holding the Ace myself -- nothing to flush, runs Hearts instead",
				chosen.suit == Suit.HEART);
	}

	/** "Already captured" has to mean captured by ME to stay a meaningful,
	 * reachable case: if some OTHER seat had captured a Heart, this seat's
	 * own moon shot would already be dead (AI.isMoonShotAlive) and mode
	 * would drop out of MOON_SHOT entirely before this scorer's own guard
	 * ever mattered -- that is correct, not a bug, and not what this test
	 * is for. The only way to reach "the Ace is gone AND I'm still
	 * shooting" is if I was the one who won it, on some earlier trick. */
	private static void standsDownOnceIHaveAlreadyCapturedTheAceMyself() {
		TestKit.suite("standsDownOnceIHaveAlreadyCapturedTheAceMyself");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		tc.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.ACE, Suit.HEART));
		Card chosen = AICardScorer.pickLead(shooterHand(), tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("the Ace is already in my own pile -- nothing left to flush, runs Hearts instead",
				chosen.suit == Suit.HEART);
	}

	/**
	 * Confirms MOON_ACE_FLUSH_LEADER_BONUS is not just wired but large
	 * enough to matter, using a hand shaped so the bare flush (no bonus)
	 * actually LOSES to running Hearts: 5 Hearts topped by the King scores
	 * 1011 raw on Tier 1's longest-suit rule (505.5 at NORMAL's 0.5
	 * MOON_OFFENSE weight); the bare flush of a guaranteed-winning Ace
	 * scores only 888 raw (444 weighted) -- Hearts wins. Add the leader
	 * bonus (target's total pushed strictly below every other seat, so
	 * isCurrentLeader is unambiguous) and the SAME flush scores 1138 raw
	 * (569 weighted), clearing Hearts. This proves the bonus both fires
	 * and is sized to actually change the decision, not just nudge a tie.
	 */
	private static void leaderTargetOutscoresNonLeaderTarget() {
		TestKit.suite("leaderTargetOutscoresNonLeaderTarget");
		List<Card> hand = cards(
				card(CardValue.KING, Suit.HEART), card(CardValue.QUEEN, Suit.HEART),
				card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.NINE, Suit.HEART),
				card(CardValue.ACE, Suit.CLUB), card(CardValue.TWO, Suit.DIAMOND));

		// EAST is void in Clubs but strictly NOT the leader (SOUTH is
		// clearly ahead of it) -- the bare flush must lose to Hearts.
		Table_Cards notLeader = tableOf();
		notLeader.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		notLeader.players.get(PLAYER.SOUTH).p_total_points = -10;
		notLeader.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		Card chosenA = AICardScorer.pickLead(hand, notLeader, notLeader.players.get(PLAYER.NORTH));
		TestKit.check("targeting a non-leader: the bare flush score loses to running Hearts",
				chosenA.suit == Suit.HEART);

		// Same shape, but EAST is now the sole, unambiguous leader.
		Table_Cards isLeader = tableOf();
		isLeader.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		isLeader.players.get(PLAYER.EAST).p_total_points = -10;
		isLeader.void_suits.get(PLAYER.EAST).add(Suit.CLUB);
		Card chosenB = AICardScorer.pickLead(hand, isLeader, isLeader.players.get(PLAYER.NORTH));
		TestKit.check("targeting the sole leader: the bonus is enough to flip the decision to the flush",
				chosenB.suit == Suit.CLUB);
	}

	/** The exact scenario that prompted this feature, reconstructed: no
	 * suit has any confirmed void yet (only the forced opening trick has
	 * been played), so even with gambling on, the flush has nothing to
	 * fire on and North correctly falls back to its forced legal Spade
	 * leads -- exactly what happened in the real hand. */
	private static void theKingOfDiamondsHandDoesNotFireWithoutAConfirmedVoid() {
		TestKit.suite("theKingOfDiamondsHandDoesNotFireWithoutAConfirmedVoid");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).takesMoonGambleThisHand = true;
		// No voids recorded anywhere -- matches trick 2/3 of the real hand.
		List<Card> legalAtTrick2 = cards(
				card(CardValue.JACK, Suit.SPADE), card(CardValue.ACE, Suit.SPADE),
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.KING, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(legalAtTrick2, tc, tc.players.get(PLAYER.NORTH));
		TestKit.checkEquals("no void proven yet -- the flush can't fire, so ordinary Tier 3 leads the Ace of Spades",
				Suit.SPADE, chosen.suit);
	}

	// ------------------------------------------------------------------

	private static List<Card> shooterHand() {
		return cards(
				card(CardValue.KING, Suit.HEART), card(CardValue.QUEEN, Suit.HEART),
				card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.NINE, Suit.HEART),
				card(CardValue.ACE, Suit.CLUB), card(CardValue.TWO, Suit.DIAMOND));
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static List<Card> cards(Card... cs) {
		List<Card> list = new ArrayList<Card>();
		for (Card c : cs) {
			list.add(c);
		}
		return list;
	}

	private static Table_Cards tableOf() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Player_Type.NORMAL);
			players.put(p, pl);
		}
		Player north = players.get(PLAYER.NORTH);
		north.attempting_moon_shot = true;
		tc.players = players;

		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = false;
		tc.trick_nbr = 3;
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.NORTH;
		return tc;
	}
}
