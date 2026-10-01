package seed

import (
	"strings"
	"testing"
)

// The seed files are the difference between a hotel that works and an empty
// lobby. These check the parts of them that are the launcher's business:
// that they are there, that they can be split into statements, and that they
// carry nothing that would mean one thing on MariaDB and another in a test.

func TestBaseSeedIsEmbedded(t *testing.T) {
	if len(Base()) == 0 {
		t.Fatal("the base seed is empty; a hotel seeded with it would have no rooms")
	}
	for _, table := range []string{
		"habnut_room_models",     // nothing can be created without a shape
		"habnut_items_base",      // nothing can be put down
		"habnut_catalogue_pages", // nothing can be bought
		"habnut_catalogue_offers",
	} {
		if !strings.Contains(Base(), table) {
			t.Errorf("the base seed never touches %s", table)
		}
	}
}

func TestDemoSeedIsEmbedded(t *testing.T) {
	if len(Demo()) == 0 {
		t.Fatal("the demo world is empty; a local hotel would come up with nobody in it")
	}
	for _, table := range []string{
		"habnut_users", "habnut_rooms", "habnut_floor_items", "habnut_friends",
	} {
		if !strings.Contains(Demo(), table) {
			t.Errorf("the demo world never touches %s", table)
		}
	}
}

func TestNoSeedContainsABackslash(t *testing.T) {
	// MariaDB unescapes backslash sequences in a string literal and H2 does
	// not, so the same file would seed two different things depending on where
	// it ran — which is exactly how a room shape ends up right in production
	// and wrong under test.
	for name, body := range map[string]string{"base.sql": Base(), "demo.sql": Demo()} {
		if strings.Contains(body, `\`) {
			t.Errorf("%s contains a backslash", name)
		}
	}
}

func TestEveryDemoAccountIsDescribed(t *testing.T) {
	if len(DemoAccounts) < 3 {
		t.Error("one account shows nothing about what other people see")
	}
	for _, acc := range DemoAccounts {
		if acc.Username == "" || acc.Rank == "" || acc.Note == "" {
			t.Errorf("account %+v is missing something; this is printed to the user", acc)
		}
		if !strings.Contains(Demo(), "'"+acc.Username+"'") {
			t.Errorf("%s is listed but the seed never creates it", acc.Username)
		}
	}
}

func TestStatementsSplitsOnSemicolonsOutsideLiterals(t *testing.T) {
	got := Statements("SELECT 1; SELECT 2;")
	if len(got) != 2 {
		t.Fatalf("Statements() = %d statements, want 2: %q", len(got), got)
	}
}

func TestStatementsKeepsASemicolonInsideALiteral(t *testing.T) {
	got := Statements("INSERT INTO t VALUES ('a;b');")
	if len(got) != 1 {
		t.Fatalf("Statements() = %d statements, want 1: %q", len(got), got)
	}
	if !strings.Contains(got[0], "'a;b'") {
		t.Errorf("the literal was cut in half: %q", got[0])
	}
}

func TestStatementsKeepsAnEscapedQuote(t *testing.T) {
	// Marnie's Front Room is one string, not two.
	got := Statements("INSERT INTO t VALUES ('Marnie''s Room');")
	if len(got) != 1 {
		t.Fatalf("Statements() = %d statements, want 1: %q", len(got), got)
	}
	if !strings.Contains(got[0], "Marnie''s Room") {
		t.Errorf("the escaped quote was lost: %q", got[0])
	}
}

func TestStatementsKeepsALiteralSpanningLines(t *testing.T) {
	// The room heightmaps are multi-line literals; losing the line breaks
	// would flatten every room into one row of tiles.
	sql := "INSERT INTO m VALUES ('xxx\nx00\nxxx');"
	got := Statements(sql)
	if len(got) != 1 {
		t.Fatalf("Statements() = %d statements, want 1", len(got))
	}
	if strings.Count(got[0], "\n") != 2 {
		t.Errorf("the line breaks inside the literal were lost: %q", got[0])
	}
}

func TestStatementsDropsComments(t *testing.T) {
	got := Statements("-- a note\nSELECT 1;\n-- another\nSELECT 2;")
	if len(got) != 2 {
		t.Fatalf("Statements() = %d statements, want 2: %q", len(got), got)
	}
	for _, s := range got {
		if strings.Contains(s, "--") {
			t.Errorf("a comment survived into a statement: %q", s)
		}
	}
}

func TestStatementsIgnoresASemicolonInAComment(t *testing.T) {
	got := Statements("-- this; is a note\nSELECT 1;")
	if len(got) != 1 {
		t.Fatalf("Statements() = %d statements, want 1: %q", len(got), got)
	}
}

func TestTheShippedSeedsSplitIntoStatements(t *testing.T) {
	for name, body := range map[string]string{"base.sql": Base(), "demo.sql": Demo()} {
		statements := Statements(body)
		if len(statements) < 10 {
			t.Errorf("%s split into only %d statements, which cannot be right", name, len(statements))
		}
		for _, s := range statements {
			if strings.Count(s, "'")%2 != 0 {
				t.Errorf("%s: a statement has an unclosed quote, so the split went wrong:\n%s", name, s)
			}
		}
	}
}
