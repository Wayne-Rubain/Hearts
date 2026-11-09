package hearts_1;

import java.util.EnumSet;

import javax.swing.ImageIcon;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.*;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_JFrame.South_Hand_Status;

public class Player {
  public int p_round_points;
  // Added 2026-07-30 for Scoring.java. p_round_points already existed
  // (see Reset_Player() below) but was clearly meant as "this hand's
  // score" -- it's never actually been reset anywhere (Reset_Player() has
  // no call sites in the codebase), so relying on that omission to make it
  // double as a running game total would be fragile. p_total_points is the
  // explicit, intentionally-never-reset-mid-game running total instead.
  public int p_total_points;
  // Cards this player has actually WON (captured in a trick they took),
  // accumulated across the current hand -- cleared at the start of each
  // new hand (see Hearts_JFrame's hand-start setup), fed into
  // Scoring.computeHandScores() once all 13 tricks are done.
  public Hand won_cards = new Hand();
  public PLAYER tbl_player;
  // Added 2026-07-31 for Deal_Hand.java/HandEvaluator.java -- set once at the
  // start of each hand (a one-time commitment, not re-evaluated every trick)
  // and read by AI.java's trick-play strategy via AI.isMoonShotAlive().
  //
  // CORRECTION (2026-08-09): this comment used to claim "Not reset explicitly
  // elsewhere since Deal_Hand.passCards() unconditionally overwrites it for
  // every seat at the start of every hand." That invariant was FALSE and the
  // gap was a real bug -- passCards() returns early on a KEEP pass direction
  // (and on KEEP hands Hearts_JFrame never called it at all), so on every 4th
  // hand no seat's flag was written and the previous hand's value survived.
  // South's was never written on ANY hand, the human's cards bypassing the
  // only writer. Both fixed by Deal_Hand.beginHand(), which now owns this
  // field's per-hand reset for all four seats and is called for every hand
  // regardless of pass direction. See TODO.md's 2026-08-09 rules-audit entry.
  //
  // REFINEMENT (2026-08-16): still a one-time commitment never re-opened
  // during trick play, but it is now decided in TWO stages rather than one.
  // beginHand() makes the first call from the dealt hand, and -- on any hand
  // that actually exchanges cards -- Deal_Hand.reevaluateMoonShotAfterExchange()
  // takes a second look once each seat holds the 13 cards it will really
  // play. That second pass can only turn the flag ON, never off; see its
  // doc for why the asymmetry matters. Before it existed, the commitment
  // was made on the strength of three cards the seat was about to pass
  // away, blind to the three arriving in return.
  public boolean attempting_moon_shot;
  // Added 2026-08-17 (TODO.md's "borderline moon-shot Spade caution" item,
  // from a deep-dive strategy review): which ROUTE this hand's commitment
  // took, per HandEvaluator.hasMoonHeartControl() -- the Ace of Hearts
  // itself, or the weaker King-plus-length route. Set alongside
  // attempting_moon_shot, from the SAME final hand (see Deal_Hand.
  // beginHand() and reevaluateMoonShotAfterExchange() -- the latter
  // overwrites this too, not just the commitment flag, since a hand that
  // changes via the exchange can change which route it took). Read by
  // AICardScorer's Spade-control-hold-back guard: expert Hearts consensus
  // is that a shooter on the WEAKER route should not spend its Ace/King
  // of Spades hunting the Queen early, since the Ace of Hearts might
  // still surface on its own and the Queen is usually free anyway. Not
  // meaningful when attempting_moon_shot is false.
  public boolean moonShotHasAceOfHearts;
  // Added 2026-08-07 for the tactic-weighting matrix (TacticWeights.java).
  // Records WHICH preset bundle this seat was set to, because the weight
  // table is keyed by type -- p_traits alone can't distinguish, e.g.,
  // AVOIDS_QUEEN from AGGRESSIVE_AVOIDS_QUEEN's Avoids_Queen half. Defaults
  // to NORMAL so anything that never goes through Set_Player(Player_Type)
  // (the human seat, the legacy Set_Player(int) path) gets full-strength
  // standard weights rather than an accidental zero row.
  public Player_Type p_type = Player_Type.NORMAL;
  // Added 2026-10-10: the full playing profile -- the template above plus
  // any per-player overrides (see Profile.java). TacticWeights reads this,
  // not p_type. Defaults to the plain NORMAL template, for the same reason
  // p_type defaults to NORMAL.
  public Profile profile = Profile.of(Player_Type.NORMAL);
  // Added 2026-10-10 (AI players step 2): the families this seat is NOT
  // using this hand, from its profile's reliability dice. TacticWeights.
  // weight() reads them as 0. Rolled with the mood styles in rollHand().
  public final EnumSet<TacticWeights.Tactic> skippedThisHand = EnumSet.noneOf(TacticWeights.Tactic.class);
  // Added 2026-10-10 for Queen payback (see Payback.java): who stuck this
  // player with the Queen, and how many hands ago (0 = this hand). Lives
  // for the whole game -- cleared by Hearts_JFrame.startNewGame().
  public final java.util.Map<PLAYER, Integer> grudges = new java.util.EnumMap<PLAYER, Integer>(PLAYER.class);
  // Who passed this player the Queen this hand, if anyone (Deal_Hand.
  // passCards). A passed Queen only counts if this player ends up taking it.
  public PLAYER queenPassedBy;
  // Added 2026-10-11 for the MOON_ALARM strategy (see MoonRead.java), all
  // public information, cleared every hand: the suit of the trick where this
  // player's first Heart was dumped on them, and whether they have since LED
  // a sure winner (in any suit / in that same suit) with the Queen still out.
  public Hearts_1.Suit heartDiscardSuit;
  public boolean ledSureWinner;
  public boolean ledSureWinnerSameSuit;
  // 2026-11-08 (Wayne): led the Queen of Spades this hand -- a moon warning.
  public boolean ledQueenOfSpades;
  // Added 2026-10-11 for wildness (see Profile.Wild): which hand of the
  // current game this is (1 = first; bumped in Deal_Hand.beginHand, reset by
  // a new game), and the strategy options this hand strayed to.
  public int handsThisGame;
  public final java.util.Map<Strategy, Enum<?>> strategyThisHand =
		  new java.util.EnumMap<Strategy, Enum<?>>(Strategy.class);

  // Added 2026-08-16 for the per-roster-player "counts cards" ability
  // (user request): a static per-seat rate, set from the assigned
  // RosterPlayer whenever the seat dropdowns are applied (see
  // Hearts_JFrame.applySelectedPlayerTypes()), and a per-HAND boolean
  // rolled fresh against that rate at the start of every hand (see
  // Deal_Hand.beginHand()) -- a 50%-rate seat genuinely is a counter on
  // some hands and not others, not a permanently-half-strength counter.
  // 0 means "never" (the roster's explicit No). Deliberately NOT a
  // Player_Traits/TacticWeights row: this gates a specific per-INDIVIDUAL
  // roster identity's access to the COUNTED_CERTAINTIES tactic family at
  // all (see AICardScorer.pickLead/pickFollow), separate from
  // TacticWeights.COUNTED_CERTAINTIES itself, which keeps meaning "how
  // much does this seat's PERSONALITY want to act on a certainty once
  // available" -- the outer-gate/inner-weight split TODO.md's
  // Counts_cards research settled on.
  public int countCardsPercent = 0;
  public boolean countsCardsThisHand = false;

  // Added 2026-08-16 for the per-roster-player moon-offense gambling trait
  // (user request, from a live-hand review): same shape as
  // countCardsPercent above, but gates AICardScorer's "PULL THE ACE OF
  // HEARTS OUT" flush lead specifically. moonGamblePercent is the static
  // per-seat rate from the assigned RosterPlayer; takesMoonGambleThisHand
  // is the per-hand roll (Deal_Hand.beginHand()), taken against an
  // EFFECTIVE rate that Deal_Hand.effectiveMoonGamblePercent() boosts
  // upward the further this seat is trailing the field -- "when a person
  // is trailing far behind... they would take these type of chances,"
  // per the user's explicit instruction. That standings boost is why this
  // roll can't be taken once per game the way a personality trait would:
  // the same seat is a more willing gambler in hand 9 down by 40 than it
  // was in hand 1 level with the field.
  public int moonGamblePercent = 0;
  public boolean takesMoonGambleThisHand = false;

  // Added 2026-10-17 (user design: AIs must try the moon now and then, like
  // people -- low levels most, experts least, and more when trailing the
  // leader). moonInterestPercent is the static per-hand chance from the
  // RosterPlayer; moonInterestThisHand is the roll made in Deal_Hand.beginHand
  // against Deal_Hand.effectiveMoonInterestPercent(). When on, the player may
  // commit to a moon shot on a hand with real Heart control even if its
  // template, reliability or quality bar would normally say no.
  public int moonInterestPercent = 0;
  public boolean moonInterestThisHand = false;

  // Added 2026-11-08 -- see Profile.moonLookPercent. Rolled each hand in
  // Deal_Hand.beginHand: this hand the player also reads its cards the way an
  // optimistic real player does (HandEvaluator.sideSuitMoonScore).
  public boolean moonLookThisHand = false;
  // Added 2026-11-08 -- see Profile.moonHeartLast. Rolled each hand in
  // Deal_Hand with the moon look.
  public boolean moonHeartLastThisHand = true;
  // True when this hand's moon commitment came from the look alone (not the
  // heart-length rules); AICardScorer's look-shooter leads read it.
  public boolean moonByLook = false;

  public  enum Player_Traits {
		Brain_Dead,
		Bad,
		Normal,
		Cautious,
		Aggressive,
		Avoids_Queen,
		Counts_cards,
		Prevents_Shooter,
		Vengeful,
		Last_Place,
		First_Place,
		Dave,
		Payback_p1,
		Payback_p2,
		Payback_p3,
		Need_to_Stop_Shooter,
		Need_to_Shoot,
		Shooting_the_Moon
		}

	/**
	 * Selectable computer-player types, added 2026-08-06 so each seat can be
	 * chosen at the start of a deal instead of being hardcoded (North was
	 * permanently Brain_Dead and East permanently Bad, which meant only West
	 * ever exercised the real strategy code -- see Hearts_JFrame's seat
	 * dropdowns).
	 *
	 * Each type is a named bundle of Player_Traits. Only the traits AI.java
	 * and HandEvaluator.java actually read are offered here; the rest of the
	 * Player_Traits vocabulary (Vengeful, Payback_p1/2/3, Last_Place,
	 * First_Place, Need_to_Stop_Shooter, ...) is still inert scaffolding
	 * waiting on the persistent-roster/cross-game-memory feature in TODO.md,
	 * so offering them in a dropdown would imply behaviour that doesn't
	 * exist yet.
	 *
	 * This is deliberately a flat list of preset bundles rather than a ranked
	 * trait mixture -- the ranked-mixture personality system is the LATER
	 * piece described in TODO.md, and it will build on these same traits.
	 */
	public enum Player_Type {
		NORMAL("Normal", EnumSet.of(Player_Traits.Normal)),
		/** Currently plays identically to Normal -- AI.java folds Cautious
		 * into the baseline path, since there's no well-justified "more
		 * cautious than safe" behaviour without deeper score awareness. */
		CAUTIOUS("Cautious (= Normal today)", EnumSet.of(Player_Traits.Cautious)),
		AGGRESSIVE("Aggressive", EnumSet.of(Player_Traits.Aggressive)),
		AVOIDS_QUEEN("Avoids Queen", EnumSet.of(Player_Traits.Normal, Player_Traits.Avoids_Queen)),
		AGGRESSIVE_AVOIDS_QUEEN("Aggressive + Avoids Queen",
				EnumSet.of(Player_Traits.Aggressive, Player_Traits.Avoids_Queen)),
		/** Lowers the hand-quality bar for committing to a moon shot during
		 * passing (see HandEvaluator.shouldAttemptMoonShot). */
		MOON_SHOOTER("Moon Shooter", EnumSet.of(Player_Traits.Normal, Player_Traits.Shooting_the_Moon)),
		BAD("Bad (1-in-3 random)", EnumSet.of(Player_Traits.Bad)),
		BRAIN_DEAD("Brain Dead (no strategy)", EnumSet.of(Player_Traits.Brain_Dead));

		private final String label;
		private final EnumSet<Player_Traits> traits;

		private Player_Type(String label, EnumSet<Player_Traits> traits) {
			this.label = label;
			this.traits = traits;
		}

		/** Fresh copy each call -- callers load these straight into a live
		 * player's p_traits, and handing out the shared backing set would let
		 * one seat's edits leak into every other seat of the same type. */
		public EnumSet<Player_Traits> traits() {
			return EnumSet.copyOf(this.traits);
		}

		/** Drives what a JComboBox renders for this value. */
		@Override
		public String toString() {
			return this.label;
		}
	}
  
	EnumSet<Player_Traits> p_traits 	= EnumSet.noneOf(Player_Traits.class);
	EnumSet<Player_Traits> round_traits = EnumSet.noneOf(Player_Traits.class);
	EnumSet<Player_Traits> h_traits 	= EnumSet.noneOf(Player_Traits.class);
	
	EnumSet<Player_Traits> comp_p1, comp_p2, comp_p3, comp_p4, comp_p5, comp_p6, comp_p7;
	EnumSet<Player_Traits> comp_p8, comp_p9, comp_p10;

	
	
	EnumSet<CardValue> deduced_p1 = EnumSet.noneOf(CardValue.class);	
	EnumSet<CardValue> deduced_p2 = EnumSet.noneOf(CardValue.class);
	EnumSet<CardValue> deduced_p3 = EnumSet.noneOf(CardValue.class);
	
	
	EnumSet<CardValue2> deduced2_p1 = EnumSet.noneOf(CardValue2.class);
	EnumSet<CardValue2> deduced2_p2 = EnumSet.noneOf(CardValue2.class);
	EnumSet<CardValue2> deduced2_p3 = EnumSet.noneOf(CardValue2.class);


	 
	public Player ( ) {
		
		this.deduced_p1  		=  EnumSet.noneOf(CardValue.class);
		this.deduced_p2  		=  EnumSet.noneOf(CardValue.class);
		this.deduced_p3  		=  EnumSet.noneOf(CardValue.class);
	
		
//		this.discard_p1.clear();  this.discard_p2.clear();  this.discard_p3.clear();
//		this.outstand_p1.clear(); this.outstand_p2.clear(); this.outstand_p3.clear();
		
	
		this.p_round_points = 0;
		this.p_total_points = 0;
		this.round_traits.clear();

		comp_p1  =  EnumSet.of(Player_Traits.Brain_Dead);
		comp_p2  =  EnumSet.of(Player_Traits.Bad);
		comp_p3  =  EnumSet.of(Player_Traits.Normal);
		// BUG FIX (2026-08-06): comp_p4 was assigned twice in a row here, so
		// the Cautious bundle was silently overwritten by Aggressive before
		// anything could ever read it -- leaving comp_p4 a duplicate of
		// comp_p8 and no way to select a Cautious-only player through this
		// path at all. Restored to the evidently-intended Cautious (comp_p9
		// is also Cautious; comp_p8 already covers Aggressive). Note these
		// comp_pN sets are now legacy -- Player_Type above is what the seat
		// dropdowns use.
		comp_p4  =  EnumSet.of(Player_Traits.Cautious);
		comp_p5  =  EnumSet.of(Player_Traits.Bad, Player_Traits.Vengeful);
		comp_p6  =  EnumSet.of(Player_Traits.Bad, Player_Traits.Aggressive, Player_Traits.Avoids_Queen);
		comp_p7  =  EnumSet.of(Player_Traits.Normal, Player_Traits.Vengeful);
		comp_p8  =  EnumSet.of(Player_Traits.Aggressive);
		comp_p9  =  EnumSet.of(Player_Traits.Cautious);
		comp_p10 =  EnumSet.of(Player_Traits.Dave);
  	   }

	public void Reset_Player() {
		

		this.deduced_p1.clear();  this.deduced_p2.clear();  this.deduced_p3.clear();
		this.deduced2_p1.clear();  this.deduced2_p2.clear();  this.deduced2_p3.clear();

		this.p_round_points = 0;
		this.won_cards = new Hand();
		// Deliberately NOT resetting p_total_points here -- that's the
		// running game total across hands, not a per-hand value.

		comp_p1 = EnumSet.of(Player_Traits.Brain_Dead);
		comp_p2 = EnumSet.of(Player_Traits.Bad);
		comp_p3 = EnumSet.of(Player_Traits.Normal);
		comp_p4 = EnumSet.of(Player_Traits.Cautious); // see the same fix in the constructor above
		comp_p5 = EnumSet.of(Player_Traits.Bad, Player_Traits.Vengeful);
		comp_p6 = EnumSet.of(Player_Traits.Bad, Player_Traits.Aggressive, Player_Traits.Avoids_Queen);
		comp_p7 = EnumSet.of(Player_Traits.Normal, Player_Traits.Vengeful);
		comp_p8 = EnumSet.of(Player_Traits.Aggressive);
		comp_p9 = EnumSet.of(Player_Traits.Cautious);
		comp_p10 = EnumSet.of(Player_Traits.Dave);
		
	
		p_traits.removeAll(EnumSet.range(Player.Player_Traits.Payback_p1, Player.Player_Traits.Shooting_the_Moon));
		
//		h_traits = EnumSet.range(Player.Player_Traits.Payback_p1, Player.Player_Traits.Shooting_the_Moon);
//		p_traits.removeAll(h_traits);
//		
//		p_traits.remove(Player_Traits.Payback_p1);
//		p_traits.remove(Player_Traits.Payback_p2);
//		p_traits.remove(Player_Traits.Payback_p3);
//		p_traits.remove(Player_Traits.Shooting_the_Moon);
//		
//		round_traits = EnumSet.noneOf(Player_Traits.class);
		this.round_traits.clear();
	}
	
	/** Load a selected type's traits into this player. Added 2026-08-06
	 * alongside Player_Type -- the seat dropdowns call this at the start of
	 * every deal, replacing the one-time hardcoded Set_Player(1/2/3) calls
	 * that used to fix North as Brain_Dead and East as Bad for the whole
	 * session. */
	public void Set_Player(Player_Type type) {
		Set_Player(Profile.of(type));
	}

	/** Load a full profile (template + overrides) into this player. Added
	 * 2026-10-10 -- the roster seats call this; Set_Player(Player_Type) is
	 * the no-override case. */
	public void Set_Player(Profile profile) {
		this.profile = profile;
		this.p_type = profile.template;
		this.p_traits.clear();
		this.p_traits.addAll(profile.traits());
		this.skippedThisHand.clear();
		this.strategyThisHand.clear();
	}

	/** Start-of-hand dice for the profile's per-hand settings (2026-10-10,
	 * AI players step 2): which skills sit out this hand and which mood
	 * styles are on. Called from Deal_Hand.beginHand() for every AI seat. */
	public void rollHand(java.util.Random rng) {
		rollHand(rng, null);
	}

	/** As rollHand(rng), plus this hand's strategy strays (2026-10-11,
	 * wildness -- see Profile.Wild). `tc` supplies the standings; null means
	 * "no standings adjustment". A player with no wildness draws nothing
	 * extra from `rng`, so seeded replays are unchanged. */
	public void rollHand(java.util.Random rng, Hearts_JFrame.Table_Cards tc) {
		profile.rollHand(rng, skippedThisHand, p_traits);
		strategyThisHand.clear();
		int wildness = wildnessThisHand(tc);
		if (wildness <= 0) {
			return;
		}
		for (Strategy component : Strategy.values()) {
			if (component.signature) {
				continue; // never strayed, and no random number drawn (2026-11-05)
			}
			if (rng.nextInt(100) < wildness) {
				Enum<?> stray = profile.strayOption(component, rng);
				// Experts never stray into a measured leak (2026-10-17, user rule).
				if (!(profile.isExpert() && Strategy.isWeak(stray))) {
					strategyThisHand.put(component, stray);
				}
			}
		}
	}

	/** This hand's chance (0-100) of straying on each component: the
	 * profile's base, plus its opening wildness in the game's first
	 * openingHands hands, times its standings percent (user: losing = more
	 * wildcard, winning = less). All three are per-AI profile settings. */
	int wildnessThisHand(Hearts_JFrame.Table_Cards tc) {
		if (profile.wild.isOff()) {
			return 0;
		}
		double w = profile.wildness();
		if (handsThisGame <= profile.openingHands()) {
			w += profile.openingWildness();
		}
		if (tc != null && tc.players != null) {
			int best = Integer.MAX_VALUE, worst = Integer.MIN_VALUE;
			for (Player p : tc.players.values()) {
				best = Math.min(best, p.p_total_points);
				worst = Math.max(worst, p.p_total_points);
			}
			if (best != worst) { // scores have spread out
				if (p_total_points == best) {
					w = w * profile.leadingWildnessPct() / 100.0;
				} else if (p_total_points == worst) {
					w = w * profile.losingWildnessPct() / 100.0;
				}
			}
		}
		return (int) Math.min(100, Math.round(w));
	}

	/** Does this player play `option` THIS hand -- its profile's choice,
	 * unless wildness strayed it elsewhere (2026-10-11). Every strategy
	 * decision asks here, not the profile directly. */
	public boolean uses(Enum<?> option) {
		return strategy(Strategy.of(option)) == option;
	}

	/** This hand's option for a strategy component. */
	public Enum<?> strategy(Strategy component) {
		Enum<?> strayed = strategyThisHand.get(component);
		return strayed != null ? strayed : profile.strategy(component);
	}

	public void Set_Player(int ii) {

		switch (ii) {
			case 1:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p1);
				break;
			case 2:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p2);
				break;
			case 3:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p3);
				break;
			case 4:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p4);
				break;
			case 5:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p5);
				break;
			case 6:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p6);
				break;
			case 7:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p7);
				break;
			case 8:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p8);
				break;
			case 9:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p9);
				break;
			case 10:
				this.p_traits.clear();
				this.p_traits.addAll(comp_p10);
				break;
			default:
				break;
		}
	}



}
