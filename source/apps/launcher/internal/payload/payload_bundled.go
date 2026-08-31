//go:build bundled

package payload

import (
	_ "embed"
	"fmt"
)

// Release binaries embed the server components produced by
// scripts/build-launcher.sh.  The files live in data/ and are generated at
// build time rather than committed, so the source tree stays small.

//go:embed data/habnut-emulator.jar
var emulatorJAR []byte

//go:embed data/client.tar.gz
var clientArchive []byte

//go:embed data/cms.tar.gz
var cmsArchive []byte

//go:embed data/VERSION
var payloadVersion string

const bundled = true

func component(name string) ([]byte, error) {
	switch name {
	case ComponentEmulator:
		return emulatorJAR, nil
	case ComponentClient:
		return clientArchive, nil
	case ComponentCMS:
		return cmsArchive, nil
	default:
		return nil, fmt.Errorf("unknown component: %s", name)
	}
}
