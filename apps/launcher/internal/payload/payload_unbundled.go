//go:build !bundled

package payload

// Development builds carry no server components.  Every component request
// fails with ErrNotBundled so the installer can report the cause precisely
// instead of failing part-way through a system change.

const (
	bundled        = false
	payloadVersion = "unbundled"
)

func component(_ string) ([]byte, error) { return nil, ErrNotBundled }
