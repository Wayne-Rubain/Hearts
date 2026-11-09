package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PASS_DIRECTION;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;

/**
 * Test-only headless game driver, added 2026-08-15. Plays full, real,
 * AI-vs-AI Hearts games -- real Deck/Deal_Hand/Rules/AI/Scoring, nothing
 * stubbed -- without touching Hearts_JFrame or Play_Hand, both of which are
 * wired directly into Swing (Hearts_JFrame's constructor calls
 * setVisible(true) as its second line) and so aren't safe to instantiate
 * from an automated test. This class duplicates only the two
 * System.out.println FORMATS Play_Hand.resolveTrick() uses for its TRICK
 * lines (not its logic -- the actual trick-winner/legality logic is all
 * real Rules.java/Scoring.java calls), so its output is line-for-line
 * consistent with a real play session and can be fed straight into
 * LogReplayer. See LogReplayerRoundTripTest for that combination.
 *
 * Deal_Hand.beginHand()/passCards() print their own DEAL/PASS/MOON lines
 * already (real production code, not duplicated here).
 */
public final class HeadlessSimulator {

	private HeadlessSimulator() { }

	private static final PASS_DIRECTION[] CYCLE = {
			PASS_DIRECTION.LEFT, PASS_DIRECTION.RIGHT, PASS_DIRECTION.ACROSS, PASS_DIRECTION.KEEP };

	/** Plays `numHands` full hands (13 tricks each) of a 4-seat AI game,
	 * with each seat's Player_Type given by `typesBySeat` (indexed by
	 * PLAYER.values() order: NORTH, EAST, SOUTH, WEST). Every DEAL/PASS/
	 * MOON/TRICK line is printed to System.out, exactly matching the real
	 * game's log format. Returns the final Player objects (with
	 * accumulated p_total_points) for callers that want to inspect
	 * standings, but most callers just care about what got printed. */
	public static Map<PLAYER, Player> playHeadlessGame(int numHands, Player_Type[] typesBySeat) {
		return playHeadlessGame(numHands, typesBySeat, new int[] { 0, 0, 0, 0 });
	}

	/** Same, with each seat's counts-cards rate (0-100, same meaning as a
	 * roster player's countCardsPercent; Deal_Hand rolls it every hand).
	 * Added 2026-10-08: every COUNTED_CERTAINTIES tactic, the endgame solver
	 * included, is dead at the default of 0, so a simulation meant to
	 * exercise them has to set it. */
	public static Map<PLAYER, Player> playHeadlessGame(int numHands, Player_Type[] typesBySeat,
			int[] countCardsPercentBySeat) {
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		PLAYER[] seats = PLAYER.values();
		for (int i = 0; i < seats.length; i++) {
			Player p = new Player();
			p.tbl_player = seats[i];
			p.Set_Player(typesBySeat[i]);
			p.countCardsPercent = countCardsPercentBySeat[i];
			players.put(seats[i], p);
		}

		for (int handNum = 1; handNum <= numHands; handNum++) {
			for (PLAYER seat : seats) {
				players.get(seat).won_cards = new Hand();
			}

			Deck deck = new Deck();
			deck.shuffle_now();
			All_Hands allHands = deck.deal(PLAYER.NORTH, deck);

			// Fresh Table_Cards every hand -- same reasoning as the real
			// game's hand-start setup (see Hearts_JFrame's comment on
			// avoiding stale trick_nbr/hearts_broken/void_suits): the
			// constructor already zeroes everything that must not leak
			// across hands. `players` is the one thing carried over, since
			// p_total_points is a running game total.
			Table_Cards tc = new Table_Cards();
			tc.players = players;

			Deal_Hand.beginHand(allHands, tc, null);
			Deal_Hand.passCards(allHands, tc, CYCLE[(handNum - 1) % CYCLE.length], null, null);

			tc.who_leads = allHands.Find_Lead(allHands);
			tc.current_player = tc.who_leads;

			for (int trick = 1; trick <= 13; trick++) {
				tc.play_position = 1;
				for (int pos = 1; pos <= 4; pos++) {
					PLAYER seat = tc.current_player;
					Hand hand = handFor(allHands, seat);
					// 2026-11-05: the same GameEngine steps the real game uses.
					Card played = GameEngine.chooseCard(tc, pos, hand, players.get(seat));
					hand.pull_card(played);
					GameEngine.placeCard(tc, pos, seat, played);
					tc.current_player = tc.current_player.Next_Dealer();
					tc.play_position = pos + 1;
				}

				// 2026-11-05: GameEngine.resolveTrick() is what Play_Hand runs too,
				// TRICK lines included, so this log matches the real game's.
				GameEngine.resolveTrick(tc, true);
			}

			Map<PLAYER, Hand> wonThisHand = new EnumMap<PLAYER, Hand>(PLAYER.class);
			for (PLAYER seat : seats) {
				wonThisHand.put(seat, players.get(seat).won_cards);
			}
			Map<PLAYER, Integer> scores = Scoring.computeHandScores(wonThisHand);
			for (PLAYER seat : seats) {
				players.get(seat).p_total_points += scores.get(seat).intValue();
			}
			GameEventBus.publish(GameEvent.HAND_ENDED, GameEventBus.facts()
					.with("handNumber", Integer.valueOf(handNum)).with("scores", scores).build());
		}

		return players;
	}

	/** One silent hand played entirely from seeds -- the deal from
	 * `seed`, AI.RNG reseeded from it too -- so the same call always plays
	 * the same hand card-for-card unless a tactic changes a decision. Game
	 * totals start at 0 and the pass direction cycles with the seed. Added
	 * 2026-10-09 for DuplicateTest. */
	static final class SeededHand {
		Map<PLAYER, Integer> scores;
		String plays;
	}

	static SeededHand playSeededHand(long seed, Player_Type[] typesBySeat, int[] countCardsPercentBySeat) {
		AI.RNG.setSeed(seed * 7919L + 13);
		AI.seedRolls(seed * 7919L + 13);
		Map<PLAYER, Player> players = newPlayers(typesBySeat, countCardsPercentBySeat);
		StringBuilder plays = new StringBuilder();
		SeededHand result = new SeededHand();
		result.scores = playOneHand(players, seed, CYCLE[(int) (seed % CYCLE.length)], plays);
		result.plays = plays.toString();
		return result;
	}

	/** A whole silent game played from one seed, added 2026-10-10 for
	 * GameDuplicateTest: every hand's deal and AI.RNG come from (seed, hand
	 * number), so the same call replays the same game card-for-card unless a
	 * tactic changes a decision -- and, unlike playSeededHand, the standings
	 * are REAL (totals carry from hand to hand until someone reaches 100), so
	 * standings-driven decisions actually get exercised. */
	static final class SeededGame {
		int[] finals = new int[4];
		String plays;
		int hands;
	}

	static final int MAX_HANDS_PER_GAME = 80;

	/** Measurement hook (2026-10-17, MoonCount): told about every hand. The flags
	 * are the state right after the pass; totals are the game totals going INTO
	 * the hand; handScores are the hand's scores. Seat order is PLAYER.values(). */
	interface HandObserver {
		void hand(boolean[] attempting, boolean[] gamble, int[] totals, int[] handScores);

		/** Which seats captured the Queen of Spades and the Jack of Diamonds this
		 * hand (2026-11-02, for Lineup's per-player counts). Called after hand(). */
		default void captures(boolean[] queen, boolean[] jack) { }
	}
	static HandObserver observer = null;

	static SeededGame playSeededGame(long seed, Player_Type[] typesBySeat, int[] countCardsPercentBySeat) {
		return playSeededGame(seed, profilesOf(typesBySeat), countCardsPercentBySeat);
	}

	/** Same, seating full profiles (2026-10-10, AI players step 2) so
	 * reliability, moods and blunder rates can be measured in real games. */
	static SeededGame playSeededGame(long seed, Profile[] profilesBySeat, int[] countCardsPercentBySeat) {
		return playSeededGame(seed, profilesBySeat, countCardsPercentBySeat, NO_GAMBLE);
	}

	/** Same, with each seat's moon-gamble rate (0-100, a roster player's
	 * moonGamblePercent; Deal_Hand rolls it every hand, with the trailing-seat
	 * boost). Added 2026-10-14: the simulator used to leave it at 0, so the
	 * Ace-of-Hearts flush and every other takesMoonGambleThisHand tactic was
	 * unmeasurable. */
	static SeededGame playSeededGame(long seed, Profile[] profilesBySeat, int[] countCardsPercentBySeat,
			int[] moonGamblePercentBySeat) {
		return playSeededGame(seed, profilesBySeat, countCardsPercentBySeat, moonGamblePercentBySeat, NO_GAMBLE);
	}

	/** Same, with each seat's moon-interest rate too (2026-10-17). */
	static SeededGame playSeededGame(long seed, Profile[] profilesBySeat, int[] countCardsPercentBySeat,
			int[] moonGamblePercentBySeat, int[] moonInterestPercentBySeat) {
		Map<PLAYER, Player> players = newPlayers(profilesBySeat, countCardsPercentBySeat, moonGamblePercentBySeat,
				moonInterestPercentBySeat);
		StringBuilder plays = new StringBuilder();
		SeededGame game = new SeededGame();
		for (int hand = 0; hand < MAX_HANDS_PER_GAME; hand++) {
			int max = Integer.MIN_VALUE;
			for (Player p : players.values()) {
				max = Math.max(max, p.p_total_points);
			}
			if (max >= Hearts_JFrame.TARGET_SCORE) {
				break;
			}
			AI.RNG.setSeed(seed * 1_000_003L + hand * 7919L + 13);
			AI.seedRolls(seed * 1_000_003L + hand * 7919L + 13);
			Map<PLAYER, Integer> scores = playOneHand(players, seed * 1000L + hand, CYCLE[hand % CYCLE.length], plays);
			for (PLAYER p : PLAYER.values()) {
				players.get(p).p_total_points += scores.get(p).intValue();
			}
			plays.append("| ");
			game.hands++;
		}
		for (PLAYER p : PLAYER.values()) {
			game.finals[p.ordinal()] = players.get(p).p_total_points;
		}
		game.plays = plays.toString();
		return game;
	}

	private static Map<PLAYER, Player> newPlayers(Player_Type[] typesBySeat, int[] countCardsPercentBySeat) {
		return newPlayers(profilesOf(typesBySeat), countCardsPercentBySeat);
	}

	static Profile[] profilesOf(Player_Type[] types) {
		Profile[] out = new Profile[types.length];
		for (int i = 0; i < types.length; i++) {
			out[i] = Profile.of(types[i]);
		}
		return out;
	}

	private static final int[] NO_GAMBLE = { 0, 0, 0, 0 };

	private static Map<PLAYER, Player> newPlayers(Profile[] profilesBySeat, int[] countCardsPercentBySeat) {
		return newPlayers(profilesBySeat, countCardsPercentBySeat, NO_GAMBLE);
	}

	static Map<PLAYER, Player> newPlayers(Profile[] profilesBySeat, int[] countCardsPercentBySeat,
			int[] moonGamblePercentBySeat) {
		return newPlayers(profilesBySeat, countCardsPercentBySeat, moonGamblePercentBySeat, NO_GAMBLE);
	}

	static Map<PLAYER, Player> newPlayers(Profile[] profilesBySeat, int[] countCardsPercentBySeat,
			int[] moonGamblePercentBySeat, int[] moonInterestPercentBySeat) {
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		PLAYER[] seats = PLAYER.values();
		for (int i = 0; i < seats.length; i++) {
			Player p = new Player();
			p.tbl_player = seats[i];
			p.Set_Player(profilesBySeat[i]);
			p.countCardsPercent = countCardsPercentBySeat[i];
			p.moonGamblePercent = moonGamblePercentBySeat[i];
			p.moonInterestPercent = moonInterestPercentBySeat[i];
			p.won_cards = new Hand();
			players.put(seats[i], p);
		}
		return players;
	}

	/** One hand on `players` (game totals carried in, not updated here),
	 * deck shuffled from `deckSeed`. Appends every card played to `plays`
	 * and returns the hand's scores. */
	private static Map<PLAYER, Integer> playOneHand(Map<PLAYER, Player> players, long deckSeed,
			PASS_DIRECTION direction, StringBuilder plays) {
		PLAYER[] seats = PLAYER.values();
		for (PLAYER seat : seats) {
			players.get(seat).won_cards = new Hand();
		}
		Deck deck = new Deck();
		deck.shuffle_now(new java.util.Random(deckSeed));
		All_Hands allHands = deck.deal(PLAYER.NORTH, deck);
		Table_Cards tc = new Table_Cards();
		tc.players = players;
		Deal_Hand.beginHand(allHands, tc, null);
		Deal_Hand.passCards(allHands, tc, direction, null, null);
		boolean[] obsAttempt = new boolean[4], obsGamble = new boolean[4];
		int[] obsTotals = new int[4];
		for (PLAYER seat : seats) {
			Player op = players.get(seat);
			obsAttempt[seat.ordinal()] = op.attempting_moon_shot;
			obsGamble[seat.ordinal()] = op.takesMoonGambleThisHand;
			obsTotals[seat.ordinal()] = op.p_total_points;
		}
		tc.who_leads = allHands.Find_Lead(allHands);
		tc.current_player = tc.who_leads;

		for (int trick = 1; trick <= 13; trick++) {
			tc.play_position = 1;
			for (int pos = 1; pos <= 4; pos++) {
				PLAYER seat = tc.current_player;
				Hand hand = handFor(allHands, seat);
				Card played = GameEngine.chooseCard(tc, pos, hand, players.get(seat));
				plays.append(played.cardValue).append(played.suit).append(' ');
				hand.pull_card(played);
				GameEngine.placeCard(tc, pos, seat, played);
				tc.current_player = tc.current_player.Next_Dealer();
				tc.play_position = pos + 1;
			}
			GameEngine.resolveTrick(tc, false);
		}

		Map<PLAYER, Hand> won = new EnumMap<PLAYER, Hand>(PLAYER.class);
		for (PLAYER seat : seats) {
			won.put(seat, players.get(seat).won_cards);
		}
		Map<PLAYER, Integer> handScores = Scoring.computeHandScores(won);
		if (observer != null) {
			int[] hs = new int[4];
			for (PLAYER seat : seats) {
				hs[seat.ordinal()] = handScores.get(seat).intValue();
			}
			observer.hand(obsAttempt, obsGamble, obsTotals, hs);
			boolean[] queen = new boolean[4], jack = new boolean[4];
			for (PLAYER seat : seats) {
				List<Card> pile = won.get(seat).hand;
				queen[seat.ordinal()] = CardCounter.holds(pile, CardValue.QUEEN, Suit.SPADE);
				jack[seat.ordinal()] = CardCounter.holds(pile, CardValue.JACK, Suit.DIAMOND);
			}
			observer.captures(queen, jack);
		}
		return handScores;
	}

	private static Hand handFor(All_Hands allHands, PLAYER seat) {
		switch (seat) {
			case NORTH: return allHands.n_hand;
			case EAST: return allHands.e_hand;
			case SOUTH: return allHands.s_hand;
			case WEST: return allHands.w_hand;
			default: throw new IllegalArgumentException("Unknown seat: " + seat);
		}
	}
}
