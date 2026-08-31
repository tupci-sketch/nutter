package state

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"
)

const statePath = "/var/lib/habnut/state.json"

type State struct {
	InstalledVersion string     `json:"installed_version"`
	InstallPath      string     `json:"install_path"`
	InstalledAt      time.Time  `json:"installed_at"`
	LastUpdatedAt    *time.Time `json:"last_updated_at,omitempty"`
	Services         []string   `json:"services"`
	DbMigrationLevel int        `json:"db_migration_level"`
	SwfVersion       string     `json:"swf_version,omitempty"`
	SwfInstalledAt   *time.Time `json:"swf_installed_at,omitempty"`
}

func Load() (*State, error) {
	data, err := os.ReadFile(statePath)
	if err != nil {
		return nil, err
	}
	var s State
	if err := json.Unmarshal(data, &s); err != nil {
		return nil, err
	}
	return &s, nil
}

func Save(s *State) error {
	if err := os.MkdirAll(filepath.Dir(statePath), 0755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(statePath, data, 0644)
}

func Exists() bool {
	_, err := os.Stat(statePath)
	return err == nil
}
