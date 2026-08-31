package imager

import (
	"container/list"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"image"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// The imager serves avatar and badge pictures over HTTP so the website, the
// forums and the staff tools can show a player's figure without any of them
// knowing how a figure is assembled — and without pointing at somebody else's
// server, which is the usual arrangement and breaks the day that server stops
// answering.

// Defaults chosen so a first request looks like the hotel rather than like an
// error page.
const (
	defaultDirection = 2
	maxScale         = 4
	// cacheEntries is roughly a few hundred megabytes of rendered PNGs at the
	// sizes a hotel actually serves, which covers an active hotel's players
	// many times over.
	cacheEntries = 4096
	// cacheMaxAge is long because every input that changes the picture is part
	// of the URL, so a stale answer is not possible.
	cacheMaxAge = 24 * time.Hour
)

// Server renders avatars and badges for one or more installed eras.
type Server struct {
	assetsRoot string
	// fallbackEra is used when a request does not name one.
	fallbackEra string

	mu         sync.Mutex
	renderers  map[string]*Renderer
	badgeParts map[string]*BadgeParts

	cache *pngCache
}

// NewServer prepares a server over the directory holding each era's artwork.
func NewServer(assetsRoot, fallbackEra string) *Server {
	return &Server{
		assetsRoot:  assetsRoot,
		fallbackEra: fallbackEra,
		renderers:   map[string]*Renderer{},
		badgeParts:  map[string]*BadgeParts{},
		cache:       newPNGCache(cacheEntries),
	}
}

// Handler builds the routes the imager answers on.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/avatar", s.handleAvatar)
	mux.HandleFunc("/avatar.png", s.handleAvatar)
	mux.HandleFunc("/badge/", s.handleBadge)
	return mux
}

// ─── era loading ────────────────────────────────────────────────────────────

// rendererFor loads an era's pack on first use and keeps it.
//
// Loading is deferred so the imager starts even when only one era is installed,
// and so installing the second era does not need a restart.
func (s *Server) rendererFor(era string) (*Renderer, *BadgeParts, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if renderer, ok := s.renderers[era]; ok {
		return renderer, s.badgeParts[era], nil
	}

	root := filepath.Join(s.assetsRoot, era)
	if _, err := os.Stat(root); err != nil {
		return nil, nil, fmt.Errorf("era %q is not installed", era)
	}

	pack, err := LoadPack(root)
	if err != nil {
		return nil, nil, err
	}
	badges, err := LoadBadgeParts(root)
	if err != nil {
		return nil, nil, err
	}

	renderer := NewRenderer(pack)
	s.renderers[era] = renderer
	s.badgeParts[era] = badges
	return renderer, badges, nil
}

// Reload drops the cached packs and rendered images so freshly installed
// artwork is served without restarting.
func (s *Server) Reload() {
	s.mu.Lock()
	s.renderers = map[string]*Renderer{}
	s.badgeParts = map[string]*BadgeParts{}
	s.mu.Unlock()
	s.cache.clear()
}

// eraOf picks the era a request is asking for.
func (s *Server) eraOf(r *http.Request) string {
	if era := r.URL.Query().Get("era"); era == "classic" || era == "modern" {
		return era
	}
	return s.fallbackEra
}

// ─── handlers ───────────────────────────────────────────────────────────────

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprint(w, `{"status":"ok"}`)
}

func (s *Server) handleAvatar(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()

	figure := query.Get("figure")
	if figure == "" {
		http.Error(w, "figure is required", http.StatusBadRequest)
		return
	}

	era := s.eraOf(r)
	key := era + "|avatar|" + r.URL.RawQuery
	if s.serveCached(w, r, key) {
		return
	}

	renderer, _, err := s.rendererFor(era)
	if err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}

	direction := intParam(query, "direction", defaultDirection)
	headDirection := intParam(query, "head_direction", direction)

	req := Request{
		Figure:        figure,
		Direction:     direction,
		HeadDirection: &headDirection,
		Action:        actionParam(query.Get("action")),
		Frame:         intParam(query, "frame", 0),
		Size:          sizeParam(query.Get("size")),
	}
	if boolParam(query.Get("headonly")) {
		req.Hide = bodyPartTypes()
	}

	png, err := renderer.RenderAvatar(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	if scale := scaleParam(query); scale > 1 {
		png, err = rescalePNG(png, scale)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	s.cache.put(key, png)
	writePNG(w, r, png)
}

func (s *Server) handleBadge(w http.ResponseWriter, r *http.Request) {
	name := strings.TrimSuffix(strings.TrimPrefix(r.URL.Path, "/badge/"), ".png")
	if name == "" {
		http.Error(w, "badge code is required", http.StatusBadRequest)
		return
	}

	era := s.eraOf(r)
	scale := scaleParam(r.URL.Query())
	key := era + "|badge|" + name + "|" + strconv.Itoa(scale)
	if s.serveCached(w, r, key) {
		return
	}

	renderer, badgeParts, err := s.rendererFor(era)
	if err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}

	png, err := renderBadgeOrNamed(renderer, badgeParts, name)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	if scale > 1 {
		png, err = rescalePNG(png, scale)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	s.cache.put(key, png)
	writePNG(w, r, png)
}

// renderBadgeOrNamed composes a group badge code, or serves a badge that ships
// with the pack under its own name.
//
// Both live at the same route because a caller holding a badge string from the
// database does not know which kind it has: achievement badges are named, group
// badges are codes, and both sit in the same column.
func renderBadgeOrNamed(renderer *Renderer, badgeParts *BadgeParts, name string) ([]byte, error) {
	if _, err := ParseBadgeCode(name); err == nil {
		return renderer.RenderBadge(name, badgeParts)
	}

	for _, candidate := range []string{name, "badge_" + name} {
		entry, flip, ok := renderer.Pack().Resolve(candidate)
		if !ok {
			continue
		}
		img, err := renderer.loadImage(entry.File)
		if err != nil {
			continue
		}
		return EncodePNG(prepare(Placement{Image: img, Flip: flip}))
	}
	return nil, fmt.Errorf("no badge %q in this pack", name)
}

// ─── response helpers ───────────────────────────────────────────────────────

// serveCached answers from the render cache, reporting whether it did.
func (s *Server) serveCached(w http.ResponseWriter, r *http.Request, key string) bool {
	png, ok := s.cache.get(key)
	if !ok {
		return false
	}
	writePNG(w, r, png)
	return true
}

// writePNG sends an image, letting a browser that already has it skip the body.
func writePNG(w http.ResponseWriter, r *http.Request, png []byte) {
	etag := etagOf(png)
	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(cacheMaxAge.Seconds())))
	w.Header().Set("Content-Type", "image/png")

	if match := r.Header.Get("If-None-Match"); match == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}

	w.Header().Set("Content-Length", strconv.Itoa(len(png)))
	if r.Method == http.MethodHead {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.Write(png)
}

func etagOf(data []byte) string {
	sum := sha256.Sum256(data)
	return `"` + hex.EncodeToString(sum[:12]) + `"`
}

// ─── parameter parsing ──────────────────────────────────────────────────────

func intParam(query url.Values, name string, fallback int) int {
	value, err := strconv.Atoi(query.Get(name))
	if err != nil {
		return fallback
	}
	return value
}

// scaleParam reads the pixel multiplier, refusing one large enough to turn a
// single request into a very large image.
func scaleParam(query url.Values) int {
	scale := intParam(query, "scale", 1)
	if scale < 1 || scale > maxScale {
		return 1
	}
	return scale
}

func boolParam(value string) bool {
	return value == "1" || value == "true" || value == "yes"
}

// actionParam accepts only actions a pack ships sprites for, so an unknown one
// renders a standing avatar rather than nothing at all.
func actionParam(value string) string {
	switch value {
	case ActionWalk, ActionSit, ActionLay, ActionWave, ActionRespect, ActionBlow:
		return value
	default:
		return ActionStand
	}
}

func sizeParam(value string) Size {
	if value == "s" || value == string(SizeHead) {
		return SizeHead
	}
	return SizeBody
}

// bodyPartTypes is every slot below the neck, used to render a head on its own.
func bodyPartTypes() []string {
	var out []string
	for _, partType := range drawOrder {
		if !headParts[partType] {
			out = append(out, partType)
		}
	}
	return out
}

// rescalePNG enlarges an image by a whole number of pixels per pixel.
//
// Nearest neighbour is the point: these are pixel-art sprites, and smoothing
// them turns a crisp avatar into a blurred one.
func rescalePNG(data []byte, scale int) ([]byte, error) {
	src, err := decodePNG(data)
	if err != nil {
		return nil, err
	}

	b := src.Bounds()
	out := image.NewNRGBA(image.Rect(0, 0, b.Dx()*scale, b.Dy()*scale))
	for y := 0; y < b.Dy()*scale; y++ {
		for x := 0; x < b.Dx()*scale; x++ {
			out.Set(x, y, src.At(b.Min.X+x/scale, b.Min.Y+y/scale))
		}
	}
	return EncodePNG(out)
}

// ─── render cache ───────────────────────────────────────────────────────────

// pngCache keeps recently rendered images, evicting the least recently used.
//
// Without it a page showing a room full of avatars re-composes every one of
// them on every load, and profile pictures are the most repeated request a
// hotel's website serves.
type pngCache struct {
	mu      sync.Mutex
	limit   int
	entries map[string]*list.Element
	order   *list.List
}

type cacheEntry struct {
	key  string
	data []byte
}

func newPNGCache(limit int) *pngCache {
	return &pngCache{
		limit:   limit,
		entries: map[string]*list.Element{},
		order:   list.New(),
	}
}

func (c *pngCache) get(key string) ([]byte, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	element, ok := c.entries[key]
	if !ok {
		return nil, false
	}
	c.order.MoveToFront(element)
	return element.Value.(*cacheEntry).data, true
}

func (c *pngCache) put(key string, data []byte) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if element, ok := c.entries[key]; ok {
		element.Value.(*cacheEntry).data = data
		c.order.MoveToFront(element)
		return
	}

	c.entries[key] = c.order.PushFront(&cacheEntry{key: key, data: data})
	for c.order.Len() > c.limit {
		oldest := c.order.Back()
		if oldest == nil {
			break
		}
		c.order.Remove(oldest)
		delete(c.entries, oldest.Value.(*cacheEntry).key)
	}
}

func (c *pngCache) clear() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.entries = map[string]*list.Element{}
	c.order.Init()
}
