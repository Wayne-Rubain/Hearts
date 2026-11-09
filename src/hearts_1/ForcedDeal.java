package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PASS_DIRECTION;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;

/**
 * Replays a specific deal (user request 2026-10-17: restart the game with the exact
 * same hand for everyone after reviewing a hand in the log). If
 * {@code %USERPROFILE%\.hearts2\forced_deal.txt} exists at startup, the session's
 * FIRST hand is dealt from it instead of the shuffled deck, then the file is renamed
 * to {@code forced_deal.used.txt} so it applies once.
 *
 * File format: the four "DEAL <SEAT> hand = <cards>" lines exactly as the game log
 * prints them (the "****** " markers are optional), plus an optional line
 * "DIRECTION KEEP" (or LEFT/RIGHT/ACROSS) for the pass direction of that hand;
 * without it the first hand passes LEFT as usual.
 */
public final class ForcedDeal {

	private static PASS_DIRECTION direction = null;

	private ForcedDeal() { }

	public static File defaultFile() {
		return new File(new File(System.getProperty("user.home"), ".hearts2"), "forced_deal.txt");
	}

	public static All_Hands applyIfPending(All_Hands dealt, Deck deck) {
		return applyIfPending(dealt, deck, defaultFile());
	}

	static All_Hands applyIfPending(All_Hands dealt, Deck deck, File file) {
		if (!file.isFile()) {
			return dealt;
		}
		try {
			List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
			Map<PLAYER, Hand> hands = new EnumMap<PLAYER, Hand>(PLAYER.class);
			for (String raw : lines) {
				String line = raw.replace("******", "").trim();
				if (line.startsWith("DIRECTION ")) {
					direction = PASS_DIRECTION.valueOf(line.substring("DIRECTION ".length()).trim());
				} else if (line.startsWith("DEAL ")) {
					int split = line.indexOf(" hand = ");
					PLAYER seat = PLAYER.valueOf(line.substring("DEAL ".length(), split).trim());
					hands.put(seat, build(line.substring(split + " hand = ".length()).trim(), deck));
				}
			}
			if (hands.size() != 4) {
				System.out.println("****** FORCED DEAL ignored: needs all four seats (found " + hands.size() + ") ******");
				direction = null;
				return dealt;
			}
			all(hands);
			dealt.n_hand(Hand.sortem2(hands.get(PLAYER.NORTH)));
			dealt.e_hand(Hand.sortem2(hands.get(PLAYER.EAST)));
			dealt.s_hand(Hand.sortem2(hands.get(PLAYER.SOUTH)));
			dealt.w_hand(Hand.sortem2(hands.get(PLAYER.WEST)));
			File used = new File(file.getParentFile(), "forced_deal.used.txt");
			used.delete();
			file.renameTo(used);
			System.out.println("****** FORCED DEAL applied (direction " + (direction == null ? "default" : direction) + ") ******");
			return dealt;
		} catch (IOException | RuntimeException e) {
			System.out.println("****** FORCED DEAL ignored: " + e + " ******");
			direction = null;
			return dealt;
		}
	}

	/** Pass direction asked for by the forced deal, else `fallback`. */
	public static PASS_DIRECTION directionOr(PASS_DIRECTION fallback) {
		return direction == null ? fallback : direction;
	}

	private static Hand build(String csv, Deck deck) {
		Hand h = new Hand();
		for (String part : csv.split(", ")) {
			int sp = part.trim().indexOf(' ');
			CardValue v = CardValue.valueOf(part.trim().substring(0, sp));
			Suit s = Suit.valueOf(part.trim().substring(sp + 1));
			Card found = null;
			for (int i = 0; i < 52 && found == null; i++) {
				Card c = deck.get_card(i);
				if (c.cardValue == v && c.suit == s) {
					found = c;
				}
			}
			if (found == null) {
				throw new IllegalArgumentException("card not in deck: " + part);
			}
			h.addX(found);
		}
		return h;
	}

	private static void all(Map<PLAYER, Hand> hands) {
		int total = 0;
		java.util.Set<String> seen = new java.util.HashSet<String>();
		for (Hand h : hands.values()) {
			for (Card c : h.hand) {
				total++;
				seen.add(c.cardValue + " " + c.suit);
			}
		}
		if (total != 52 || seen.size() != 52) {
			throw new IllegalArgumentException("forced deal must be 52 distinct cards (got " + total + "/" + seen.size() + ")");
		}
	}
}
