# Automated Moderation

The hotel is for adults and talks like it. Swearing and frank conversation
between grown-ups are not moderated, and a filter that quietly creeps into
policing ordinary talk is a worse outcome than no filter at all.

What *is* moderated is the small set of things that are harmful whoever is
reading:

| Category | What it covers |
|----------|----------------|
| `hate` | Hatred aimed at who somebody is |
| `threat` | Threats of violence against a person |
| `minor_safety` | Sexualising a child, or moving one off the hotel |
| `doxxing` | Publishing somebody's private details |
| `self_harm` | Pushing a person toward hurting themselves |
| `scam` | Phishing for accounts |

## The three questions

Every spoken message — say, shout and whisper alike — goes through
`ChatModerator`, which asks three questions in this order:

1. **Is this player allowed to speak?** A mute is read from the database on
   every message, so a moderator lifting one takes effect on the player's next
   line rather than whenever a cache happens to expire.
2. **Is what they said harmful?** `ContentPolicy` checks the message against the
   rules in `habnut_content_rules`.
3. **Does the word list want anything changed?** `WordFilter` makes its
   substitutions last, on a message that has already been allowed.

Asking them in any other order means a muted player still talks, which is the
whole point of a mute. Before this existed, chat bypassed both the filter and
mutes entirely.

A whisper is screened like anything else. Harm delivered quietly is still harm.

## Muting first, reviewing after

An automatic judgement is a guess. It has to act immediately — a threat left
standing while somebody waits for a moderator has already done its damage — but
it must not be the last word.

So a trip does three things at once:

1. Mutes the player.
2. Opens a case in `habnut_auto_mutes` holding **the message as sent**, so a
   reviewer judges what actually happened rather than the rule's opinion of it.
3. Tells the player what happened and offers them a way to have a person look.

The mute also carries a fallback expiry, longest for the most serious
categories. A hotel with nobody on duty overnight should not silence somebody
indefinitely because a rule misfired at 3am.

Staff settle the case in the DCC under **Automatic Mutes**. Overturning lifts
the mute with it: a decision in the player's favour that leaves them silenced has
not decided anything. The queue is ordered by category rather than arrival, so
the urgent cases are worked first.

## Asking for help

Somebody muted by a machine needs a way to say so. An automatically muted player
may send one help request every **15 minutes** — long enough that it cannot
become a second way of shouting at the room, short enough that a player who was
wrongly caught is not left with no recourse.

The interval is enforced on the server. A client is not a place to keep a rule
that matters.

Somebody a *moderator* muted has already had a person look at them, so they get
no help route; they appeal on the website instead.

## Writing a rule

Rules live in the database, so a hotel tunes them without a new build. Two things
about how a message reaches a rule shape how rules must be written.

**No backslashes.** A backslash inside a SQL string means different things to
different databases: MariaDB reads it as an escape, H2 does not. A pattern
written with one works in one place and silently matches nothing in the other —
which is exactly how this table first shipped, with rules that caught nobody.
Java regular expressions can say everything these rules need without one:

| Instead of | Write |
|------------|-------|
| `\d` | `[0-9]` |
| `\s` | `[ ]` |
| `\b` (start) | `(?<![a-z])` |
| `\b` (end) | `(?![a-z])` |

The DCC refuses a pattern containing a backslash, and a test asserts that none
of the shipped rules has one.

**Long runs collapse.** Before a rule sees a message, runs of three or more of
the same character become one, so `kiiiilll` cannot walk past a rule about
`kill`. A word with a doubled letter therefore has to allow for the doubling
being gone: write `kil+`, not `kill`.

Messages are also matched twice: as typed, and with digits and symbols decoded
back to the letters they stand in for. Decoding alone would turn a phone number
into gibberish and make every rule about digits useless; not decoding at all lets
`k1ll` through. A rule can be about numbers or about words without saying which.

`match_mode` chooses what the pattern is matched against:

- `words` — the message with its spacing intact, so word boundaries work.
- `condensed` — every space and mark removed, which catches a term spelt out one
  letter at a time. Word boundaries are gone here, so use it only where a false
  positive is unlikely.
- `both` — try each.

`exempt_pattern` is the answer to the town whose name contains a rude word: when
it also matches, the rule stands down. It is always checked against the message
as written, because that is where the context which makes it innocent lives.

A rule that will not compile is skipped at load with an error logged, rather than
taking the whole policy down. The DCC compiles a pattern before storing it, so
that a typo is refused at the point somebody makes it rather than silently
leaving the hotel with one fewer protection.

## Why the shipped rules describe shapes, not words

A hotel opens with rules already in force. They match *shapes* of harmful
messages — a group named followed by a dehumanising statement about it, a stated
age near sexual language, a violence verb aimed at "you" — rather than listing
terms.

That is deliberate. A vocabulary of slurs does not belong sitting in a public
repository, every hotel's list is different, and any such list needs constant
upkeep. Hotels add their own terms through the DCC, where they belong.

## Forums

A mute applies to the forums too. Leaving the loudest place on the site open to
whatever the mute was for would defeat it.
