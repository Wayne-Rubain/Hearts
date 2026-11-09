package hearts_1;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Tracks which suits each seat is PROVABLY void in, added 2026-07-31 as the
 * first step toward smarter AI decision-making (see the project's TODO.md /
 * the "deep dive" discussion on game-playing AI architecture).
 *
 * This is a hard deduction, not a probability estimate: Rules.legalFollows()
 * already forces every player to follow the led suit whenever they hold it,
 * so if a play doesn't match the led suit, that seat is void in it, full
 * stop -- there's no uncertainty to model. Cheap, exact, and immediately
 * useful on its own (e.g. an AI can stop worrying a heart it's about to
 * dump might get beaten by an opponent it already knows is void in Hearts),
 * and it's also the natural prerequisite for a future Perfect Information
 * Monte Carlo (determinization) layer: knowing who's void in what sharply
 * narrows which hidden-hand layouts are even plausible to sample.
 *
 * Deliberately separate from Rules.java: Rules enforces the game's actual
 * rules (follow-suit, hearts-broken); this tracks a belief/strategy concern
 * derived from watching those rules play out, same relationship AI.java has
 * to Rules.java already.
 */
public final class VoidTracker {

	private VoidTracker() { }

	/** A fresh "nobody is known void in anything yet" map -- call this at the
	 * start of every hand (voids are only valid within the hand they were
	 * observed in; a new deal tells you nothing about the old one). */
	public static Map<PLAYER, EnumSet<Suit>> freshVoidMap() {
		Map<PLAYER, EnumSet<Suit>> map = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			map.put(p, EnumSet.noneOf(Suit.class));
		}
		return map;
	}

	/** Call once a card has actually been placed on the table -- same
	 * "call for every play, lead or follow, human or computer" contract as
	 * Rules.recordPlay(). Safe/idempotent: leads can't reveal a void (the
	 * leader sets the suit, they can't be void in the suit they just led),
	 * and re-marking an already-known void is a no-op (it's a Set). tc.p1_card
	 * must already reflect this trick's actual lead card by the time this is
	 * called (true at every existing call site -- Place_Card_on_Table() runs
	 * before this). */
	public static void recordPlay(Table_Cards tc, PLAYER player, Card played, int playPosition) {
		if (playPosition == 1) {
			return;
		}
		Suit leadSuit = tc.p1_card.suit;
		if (played.suit != leadSuit) {
			tc.void_suits.get(player).add(leadSuit);
		}
	}

	public static boolean isVoid(Table_Cards tc, PLAYER player, Suit suit) {
		return tc.void_suits.get(player).contains(suit);
	}
}
