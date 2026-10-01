// Package seed carries the content a hotel needs before anybody can use it.
//
// A freshly migrated hotel has no room shapes, no furniture and an empty
// catalogue: no room can be created or loaded, nothing can be put down, and
// there is nothing to buy. These files fill that in. They are embedded in the
// binary so a hotel can be seeded on a machine that has never seen this source
// tree.
//
// Every statement in them is guarded so that applying a file twice changes
// nothing the second time: a hotel that has been running for a year can be
// brought up to a newer base seed without its staff's own changes being
// overwritten.
package seed

import (
	_ "embed"
	"fmt"
	"strings"
)

//go:embed sql/base.sql
var baseSQL string

//go:embed sql/demo.sql
var demoSQL string

// Base is the content every hotel needs: room shapes, furniture and a
// catalogue. Applied by `habnutctl install` and by `habnutctl dev up`.
func Base() string { return baseSQL }

// Demo is a hotel with people in it: accounts to sign in as, furnished rooms,
// friends, inventory and a forum post. Only ever applied to a local hotel
// started with `habnutctl dev` — never to an install.
func Demo() string { return demoSQL }

// DemoAccounts are the accounts Demo creates, for printing after a local hotel
// comes up. The password is the same for all of them.
var DemoAccounts = []struct {
	Username string
	Rank     string
	Note     string
}{
	{"tupci", "Administrator", "owns the public rooms and can open the staff pages"},
	{"Hal", "Moderator", "can see the moderation queue"},
	{"Marnie", "VIP", "owns a furnished room of her own"},
	{"Robbie", "Member", "an ordinary account, to see what a new player sees"},
}

// DemoPassword is the password on every demo account. It is fixed so the seed
// produces the same hotel every time; the accounts exist only on a hotel
// running on somebody's own machine.
const DemoPassword = "password"

// Statements splits a seed file into the statements it is made of.
//
// Splitting on the semicolon alone would cut the room heightmaps in half: they
// are multi-line string literals, and a literal may contain a semicolon. This
// tracks quoting and comments instead, so what comes out is what the database
// should be asked to run.
func Statements(sql string) []string {
	var (
		out       []string
		current   strings.Builder
		inString  bool
		inComment bool
	)

	runes := []rune(sql)
	for i := 0; i < len(runes); i++ {
		c := runes[i]

		switch {
		case inComment:
			if c == '\n' {
				inComment = false
				current.WriteRune(c)
			}
			continue

		case !inString && c == '-' && i+1 < len(runes) && runes[i+1] == '-':
			inComment = true
			continue

		case c == '\'':
			// Two quotes in a row are an escaped quote, not the end of the
			// literal: Marnie's Front Room is one string, not two.
			if inString && i+1 < len(runes) && runes[i+1] == '\'' {
				current.WriteString("''")
				i++
				continue
			}
			inString = !inString

		case c == ';' && !inString:
			if s := strings.TrimSpace(current.String()); s != "" {
				out = append(out, s)
			}
			current.Reset()
			continue
		}

		current.WriteRune(c)
	}

	if s := strings.TrimSpace(current.String()); s != "" {
		out = append(out, s)
	}
	return out
}

// Describe summarises a seed file, for the install log.
func Describe(name, sql string) string {
	return fmt.Sprintf("%s: %d statements", name, len(Statements(sql)))
}
