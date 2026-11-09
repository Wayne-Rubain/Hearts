package hearts_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed tool, added 2026-08-15, formalizing the method the 2026-08-09
 * rules audit used by hand (see CHANGELOG.md): reconstruct every hand from
 * a session's console log (the DEAL/PASS/MOON/TRICK lines Deal_Hand.java
 * and Play_Hand.java already print) and replay every trick against
 * Rules/Scoring, independently of whatever the live game concluded. That
 * audit found 5 real defects by hand; this tool exists so the same check
 * can be run on any future session log in seconds instead of by hand.
 *
 * In package hearts_1 (not a "tools" subpackage) deliberately --
 * Table_Cards.n_card/e_card/s_card/w_card are package-private (see
 * Hearts_JFrame.java), and this replayer needs to set them directly to
 * reuse Scoring.determineTrickWinner() unchanged rather than duplicating
 * its logic.
 *
 * Deliberately reads plain console output, not a purpose-built log format
 * -- the game already prints everything this needs (see Deal_Hand.beginHand/
 * passCards and Play_Hand.resolveTrick for the exact line formats this
 * parses), so there is nothing extra to keep in sync except those formats
 * themselves, and this class's javadoc says so at each parse site.
 *
 * Usage: {@code java hearts_1.LogReplayer <path-to-log.txt>}, or call
 * {@link #replay(List)} directly (e.g. from a test capturing System.out).
 */
public final class LogReplayer {

	private LogReplayer() { }

	public static final class Result {
		public int handsProcessed = 0;
		public int tricksVerified = 0;
		public final List<String> violations = new ArrayList<String>();

		public boolean isClean() {
			return violations.isEmpty();
		}

		public String report() {
			StringBuilder sb = new StringBuilder();
			sb.append("LogReplayer: ").append(handsProcessed).append(" hand(s), ")
					.append(tricksVerified).append(" trick(s) verified, ")
					.append(violations.size()).append(" violation(s)\n");
			for (String v : violations) {
				sb.append("  VIOLATION: ").append(v).append('\n');
			}
			return sb.toString();
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("Usage: java hearts_1.LogReplayer <path-to-log.txt>");
			System.exit(2);
			return;
		}
		List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
		Result result = replay(lines);
		System.out.print(result.report());
		System.exit(result.isClean() ? 0 : 1);
	}

	// ------------------------------------------------------------------
	// Replay state -- reset every time a new hand's "DEAL NORTH" line is
	// seen (see replay() below for why NORTH specifically marks a new
	// hand: Deal_Hand.beginHand() prints DEAL for PLAYER.values() in
	// enum order, NORTH/EAST/SOUTH/WEST, so NORTH is always first).
	// ------------------------------------------------------------------

	public static Result replay(List<String> lines) {
		Result result = new Result();
		Map<PLAYER, List<Card>> hands = null;
		Map<PLAYER, Hand> wonThisHand = null;
		boolean heartsBroken = false;
		int trickInHand = 0;

		Card leadCard = null;
		Map<PLAYER, Card> pendingTrick = null;
		int pendingTrickNbr = -1;

		for (String rawLine : lines) {
			String inner = stripMarkers(rawLine);
			if (inner == null) {
				continue; // not one of our lines -- ignore (South-turn/status/etc. noise)
			}

			if (inner.startsWith("DEAL ")) {
				String rest = inner.substring("DEAL ".length());
				int handSplit = rest.indexOf(" hand = ");
				if (handSplit < 0) {
					continue;
				}
				PLAYER seat = PLAYER.valueOf(rest.substring(0, handSplit));
				List<Card> cards = parseCards(rest.substring(handSplit + " hand = ".length()));
				if (seat == PLAYER.NORTH) {
					// New hand starting -- reset all per-hand state.
					if (hands != null && trickInHand > 0 && trickInHand < 13) {
						result.violations.add("hand " + result.handsProcessed
								+ " ended mid-hand in the log (stopped after trick " + trickInHand + " of 13)");
					}
					hands = new EnumMap<PLAYER, List<Card>>(PLAYER.class);
					wonThisHand = new EnumMap<PLAYER, Hand>(PLAYER.class);
					for (PLAYER p : PLAYER.values()) {
						wonThisHand.put(p, new Hand());
					}
					heartsBroken = false;
					trickInHand = 0;
					result.handsProcessed++;
				}
				hands.put(seat, cards);
				continue;
			}

			if (inner.startsWith("PASS ")) {
				String rest = inner.substring("PASS ".length());
				int arrow = rest.indexOf(" -> ");
				int eq = rest.indexOf(" = ");
				if (arrow < 0 || eq < 0) {
					continue;
				}
				PLAYER from = PLAYER.valueOf(rest.substring(0, arrow));
				PLAYER to = PLAYER.valueOf(rest.substring(arrow + " -> ".length(), eq));
				List<Card> passed = parseCards(rest.substring(eq + " = ".length()));
				for (Card c : passed) {
					if (!removeMatching(hands.get(from), c)) {
						result.violations.add("hand " + result.handsProcessed + ": PASS " + from
								+ " -> " + to + " named " + c.cardValue + " " + c.suit
								+ " but " + from + " did not hold it");
					} else {
						hands.get(to).add(c);
					}
				}
				continue;
			}

			if (inner.startsWith("MOON ")) {
				continue; // informational only -- no rule to verify here
			}

			if (inner.startsWith("TRICK ")) {
				String rest = inner.substring("TRICK ".length());
				int sp = rest.indexOf(' ');
				if (sp < 0) {
					continue;
				}
				int trickNbr = Integer.parseInt(rest.substring(0, sp));
				String remainder = rest.substring(sp + 1);

				if (remainder.startsWith("lead=")) {
					pendingTrickNbr = trickNbr;
					pendingTrick = new EnumMap<PLAYER, Card>(PLAYER.class);
					int bar = remainder.indexOf(" | ");
					String leadPart = remainder.substring("lead=".length(), bar);
					leadCard = parseCard(leadPart);

					String nesw = remainder.substring(bar + " | ".length());
					int eIdx = nesw.indexOf(" E=");
					int sIdx = nesw.indexOf(" S=");
					int wIdx = nesw.indexOf(" W=");
					pendingTrick.put(PLAYER.NORTH, parseCard(nesw.substring("N=".length(), eIdx)));
					pendingTrick.put(PLAYER.EAST, parseCard(nesw.substring(eIdx + " E=".length(), sIdx)));
					pendingTrick.put(PLAYER.SOUTH, parseCard(nesw.substring(sIdx + " S=".length(), wIdx)));
					pendingTrick.put(PLAYER.WEST, parseCard(nesw.substring(wIdx + " W=".length())));
					continue;
				}

				if (remainder.startsWith("winner=")) {
					PLAYER loggedWinner = PLAYER.valueOf(remainder.substring("winner=".length()));
					if (pendingTrick == null || pendingTrickNbr != trickNbr) {
						result.violations.add("TRICK " + trickNbr + " winner line with no matching lead line");
						continue;
					}
					verifyTrick(result, hands, wonThisHand, pendingTrick, leadCard, loggedWinner,
							trickInHand == 0, heartsBroken);
					// hearts-broken tracking: any of the 4 cards being a Heart
					// breaks it for every subsequent trick, regardless of who
					// led/won -- matches Rules.recordPlay()'s per-card check.
					for (Card c : pendingTrick.values()) {
						if (c.suit == Suit.HEART) {
							heartsBroken = true;
						}
					}
					trickInHand++;
					result.tricksVerified++;

					if (trickInHand == 13) {
						checkHandConservation(result, wonThisHand);
					}
					pendingTrick = null;
				}
			}
		}

		return result;
	}

	/** Checks the trick's plays against Rules (follow-suit, hearts-broken
	 * lead restriction, first-trick restriction all fall out of Rules.
	 * legalLeads()/legalFollows() -- see Rules.java) and independently
	 * recomputes the winner via Scoring.determineTrickWinner(), comparing
	 * against what the log claims. Mutates `hands` (removing each played
	 * card) and `wonThisHand` (accumulating the winner's 4 cards) as a
	 * side effect, same as the real game does. */
	private static void verifyTrick(Result result, Map<PLAYER, List<Card>> hands,
			Map<PLAYER, Hand> wonThisHand, Map<PLAYER, Card> trick, Card leadCard,
			PLAYER loggedWinner, boolean isFirstTrick, boolean heartsBrokenBeforeTrick) {

		PLAYER leader = null;
		for (Map.Entry<PLAYER, Card> e : trick.entrySet()) {
			if (sameCard(e.getValue(), leadCard)) {
				leader = e.getKey();
				break;
			}
		}
		if (leader == null) {
			result.violations.add("trick with lead=" + leadCard.cardValue + " " + leadCard.suit
					+ " but no seat's played card matches it");
			return;
		}

		PLAYER seat = leader;
		for (int i = 0; i < 4; i++) {
			Card played = trick.get(seat);
			List<Card> handBefore = hands.get(seat);
			Hand h = new Hand();
			h.hand.addAll(handBefore);

			List<Card> legal = (seat == leader)
					? Rules.legalLeads(h, heartsBrokenBeforeTrick, isFirstTrick)
					: Rules.legalFollows(h, leadCard.suit, isFirstTrick);

			if (!containsMatching(legal, played)) {
				result.violations.add(seat + " played " + played.cardValue + " " + played.suit
						+ (seat == leader ? " as an illegal LEAD" : " as an illegal FOLLOW")
						+ " (heartsBroken=" + heartsBrokenBeforeTrick + ", firstTrick=" + isFirstTrick + ")");
			}
			if (!removeMatching(handBefore, played)) {
				result.violations.add(seat + " played " + played.cardValue + " " + played.suit
						+ " but did not hold it (phantom card)");
			}
			seat = seat.Next_Dealer();
		}

		Table_Cards tc = new Table_Cards();
		tc.p1_card = leadCard;
		tc.n_card = trick.get(PLAYER.NORTH);
		tc.e_card = trick.get(PLAYER.EAST);
		tc.s_card = trick.get(PLAYER.SOUTH);
		tc.w_card = trick.get(PLAYER.WEST);
		PLAYER actualWinner = Scoring.determineTrickWinner(tc);
		if (actualWinner != loggedWinner) {
			result.violations.add("trick lead=" + leadCard.cardValue + " " + leadCard.suit
					+ " logged winner=" + loggedWinner + " but recomputed winner=" + actualWinner);
		}

		Hand winnerPile = wonThisHand.get(loggedWinner);
		winnerPile.addX(tc.n_card);
		winnerPile.addX(tc.e_card);
		winnerPile.addX(tc.s_card);
		winnerPile.addX(tc.w_card);
	}

	/** Point conservation: a non-moon hand's 4 scores must sum to exactly
	 * 16 (26 for Hearts+Queen, minus 10 for the Jack of Diamonds always
	 * landing somewhere) -- see Scoring.java's Omnibus ruleset. A moon-shot
	 * hand sums to -36 (-26 for the shoot, minus 10 for the Jack) instead.
	 * Matches the check the 2026-08-09 audit ran by hand (CHANGELOG.md:
	 * "9/9 hands sum to exactly 16"). */
	private static void checkHandConservation(Result result, Map<PLAYER, Hand> wonThisHand) {
		Map<PLAYER, Integer> scores = Scoring.computeHandScores(wonThisHand);
		int sum = 0;
		for (int s : scores.values()) {
			sum += s;
		}
		boolean anyMoon = false;
		for (PLAYER p : PLAYER.values()) {
			if (Scoring.isShootingTheMoon(wonThisHand.get(p))) {
				anyMoon = true;
			}
		}
		int expected = anyMoon ? -36 : 16;
		if (sum != expected) {
			result.violations.add("hand score sum=" + sum + ", expected " + expected
					+ " (moon=" + anyMoon + ") -- scores=" + scores);
		}
	}

	// ------------------------------------------------------------------
	// Parsing helpers -- tied to the exact formats Deal_Hand.formatCards()
	// and Play_Hand.resolveTrick() print. If those change, update here.
	// ------------------------------------------------------------------

	private static final String PREFIX = "****** ";
	private static final String SUFFIX = " ******";

	private static String stripMarkers(String rawLine) {
		String line = rawLine.trim();
		if (!line.startsWith(PREFIX) || !line.endsWith(SUFFIX) || line.length() < PREFIX.length() + SUFFIX.length()) {
			return null;
		}
		return line.substring(PREFIX.length(), line.length() - SUFFIX.length());
	}

	/** "TEN HEART, JACK HEART, QUEEN HEART" -> 3 Cards. Matches
	 * Deal_Hand.formatCards(). Empty string -> empty list. */
	private static List<Card> parseCards(String csv) {
		List<Card> cards = new ArrayList<Card>();
		if (csv == null || csv.isEmpty()) {
			return cards;
		}
		for (String part : csv.split(", ")) {
			cards.add(parseCard(part));
		}
		return cards;
	}

	/** "TEN HEART" -> Card(TEN, HEART). */
	private static Card parseCard(String valueAndSuit) {
		int sp = valueAndSuit.indexOf(' ');
		CardValue value = CardValue.valueOf(valueAndSuit.substring(0, sp));
		Suit suit = Suit.valueOf(valueAndSuit.substring(sp + 1));
		return new Card(value, suit, null);
	}

	private static boolean sameCard(Card a, Card b) {
		return a.cardValue == b.cardValue && a.suit == b.suit;
	}

	private static boolean containsMatching(List<Card> cards, Card target) {
		for (Card c : cards) {
			if (sameCard(c, target)) {
				return true;
			}
		}
		return false;
	}

	/** Removes the first card matching `target` by value+suit (not by
	 * reference -- every parsed Card is its own instance). Returns false,
	 * without modifying the list, if no match was found. */
	private static boolean removeMatching(List<Card> cards, Card target) {
		for (int i = 0; i < cards.size(); i++) {
			if (sameCard(cards.get(i), target)) {
				cards.remove(i);
				return true;
			}
		}
		return false;
	}
}
