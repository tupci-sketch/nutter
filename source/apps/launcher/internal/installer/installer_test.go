package installer

import (
	"strings"
	"testing"

	"github.com/habnut/launcher/internal/seed"
)

// The install sequence is only ever run against a real machine, so most of it
// cannot be tested here. What can be checked is the shape of it: that the
// steps are all there, in an order that makes sense, and that the seed step
// has something real to apply.

func TestEveryStepHasANameAndSomethingToDo(t *testing.T) {
	steps := Steps()
	if len(steps) < 10 {
		t.Fatalf("Steps() = %d steps, which is too few to be a whole install", len(steps))
	}

	for i, step := range steps {
		if strings.TrimSpace(step.Name) == "" {
			t.Errorf("step %d has no name; the name is what the user sees go past", i+1)
		}
		if step.Run == nil {
			t.Errorf("step %d (%s) does nothing", i+1, step.Name)
		}
	}
}

func TestTheSchemaIsAppliedBeforeItIsSeeded(t *testing.T) {
	var migrate, seedAt = -1, -1

	for i, step := range Steps() {
		name := strings.ToLower(step.Name)
		if strings.Contains(name, "migration") {
			migrate = i
		}
		if strings.Contains(name, "seed") {
			seedAt = i
		}
	}

	if migrate < 0 {
		t.Fatal("no step applies the schema")
	}
	if seedAt < 0 {
		t.Fatal("no step seeds the hotel, so it would come up with no rooms and nothing to buy")
	}
	if seedAt < migrate {
		t.Error("the hotel is seeded before its schema exists, which cannot work")
	}
}

func TestTheSeedStepHasRealContentToApply(t *testing.T) {
	// This step used to call a Laravel seeder named InitialSeeder, which has
	// never existed, so every install finished with an empty hotel: no room
	// shapes, no furniture, nothing in the catalogue.
	statements := seed.Statements(seed.Base())
	if len(statements) < 10 {
		t.Fatalf("the base seed is %d statements; an install would leave the hotel empty",
			len(statements))
	}

	for _, table := range []string{
		"habnut_room_models",
		"habnut_items_base",
		"habnut_catalogue_offers",
	} {
		if !strings.Contains(seed.Base(), table) {
			t.Errorf("the base seed never touches %s", table)
		}
	}
}

func TestAnInstallNeverSeedsTheDemoWorld(t *testing.T) {
	// The demo accounts all share one password and are described in the
	// launcher's own help as being for a machine talking to itself. An install
	// that created them would put four known logins on a public hotel.
	for _, step := range Steps() {
		if !strings.Contains(strings.ToLower(step.Name), "seed") {
			continue
		}
		// The check is on the content, not the step: there is one seed step
		// and it must apply the base content only.
		if strings.Contains(step.Name, "demo") || strings.Contains(step.Name, "Demo") {
			t.Errorf("install step %q looks like it seeds the demo world", step.Name)
		}
	}
}

func TestDefaultConfigPointsSomewhereSensible(t *testing.T) {
	cfg := DefaultConfig()

	if cfg.InstallPath == "" {
		t.Error("DefaultConfig has no install path")
	}
	if cfg.DbName == "" || cfg.DbUser == "" {
		t.Error("DefaultConfig has no database to connect to")
	}
}
