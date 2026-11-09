package hearts_1.events;

/**
 * The fixed vocabulary of notable moments this game can emit, added
 * 2026-08-15. Exists because three separate planned features --
 * trashTalk, the persistent AI roster's tendency counters, and the
 * player-stats screen -- all need to react to the same handful of
 * moments (see DESIGN_roster_and_trashtalk.md section 4.1 and TODO.md's
 * player-stats entry). Building one small event layer now, before any of
 * the three consumers exist, means those moments get instrumented in
 * Play_Hand/Scoring/Deal_Hand exactly once instead of three times.
 *
 * Deliberately just an enum, not a class hierarchy -- see GameEventBus
 * for how the accompanying facts are carried (a plain fact bag, matching
 * the Valve response-rule design the roster/trashTalk study already
 * settled on, rather than a typed payload per event).
 */
public enum GameEvent {
	/** A trick resolved. Facts: trick, winner, leadSuit, leader (who led
	 * it), cards (Map<PLAYER,Card> of all four plays) -- leader/cards added
	 * 2026-10-10 for the passing-outcome and lead-success stats. */
	TRICK_WON,
	/** The Queen of Spades was captured this trick. Facts: trick, winner
	 * (who now holds her), droppedBySeat (who played her, may equal
	 * winner if they took their own Queen -- see SELF_ATE_QUEEN). */
	QUEEN_DROPPED_ON,
	/** The Jack of Diamonds was captured this trick. Facts: trick,
	 * winner, playedBySeat (who played the Jack into the trick -- equal to
	 * winner when they won it themselves, added 2026-08-16 so the stats
	 * store can attribute "Jack given away" per pair). */
	JACK_CAPTURED,
	/** An AI seat committed to attempting a moon shot at hand start
	 * (HandEvaluator.shouldAttemptMoonShot returned true). Facts: seat.
	 * Added 2026-08-16 for the stats store: MOON_SUSPECTED is useless as
	 * an "attempts" signal, because the FIRST point capture of every hand
	 * always creates a lone point-holder -- it fires nearly every hand.
	 * Never fires for the human seat, whose intent is unknowable. */
	MOON_COMMITTED,
	/** A single seat now holds every point card (Heart or the Queen)
	 * captured so far this hand, with no rival claim yet -- see
	 * Scoring.inProgressMoonShooter(). Facts: trick, shooter. Fires once,
	 * at the trick where the threat first becomes visible. */
	MOON_SUSPECTED,
	/** A previously-suspected moon shot is no longer a lone threat --
	 * another seat captured a point card. Facts: trick, formerShooter,
	 * breaker (the seat whose point capture ended the lone claim -- this
	 * trick's winner), shooterHearts (how many Hearts the former shooter
	 * had banked when it broke; lets a consumer separate a real stopped
	 * moon from the routine first-vs-second point capture of an ordinary
	 * hand -- breaker/shooterHearts added 2026-08-16). */
	MOON_BROKEN,
	/** A hand ended with a successful moon shot. Facts: shooter,
	 * capturedJackToo. */
	MOON_SUCCEEDED,
	/** A seat won a trick containing the Queen of Spades that they
	 * themselves played (took their own Queen). Facts: trick, seat. */
	SELF_ATE_QUEEN,
	/** One seat received their passed-in cards. Facts: recipient, from,
	 * cards (formatted string, matching the existing PASS log line),
	 * passedCards (List<Card>, the actual cards -- added 2026-10-10). */
	PASS_RECEIVED,
	/** A hand finished (13 tricks played) and scores were tallied. Facts:
	 * handNumber, scores (Map<PLAYER,Integer>), totals (Map<PLAYER,Integer>
	 * of running game totals after this hand -- added 2026-10-10 for the
	 * comeback stat's midpoint snapshot). */
	HAND_ENDED,
	/** The game ended (some seat reached TARGET_SCORE). Facts: winners
	 * (comma-joined seat names), lowestTotal, totals
	 * (Map<PLAYER,Integer> of every seat's final running total -- added
	 * 2026-08-16 so the stats store can compute placements and pairwise
	 * finished-ahead-of results, which winners/lowestTotal alone can't). */
	GAME_ENDED
}
