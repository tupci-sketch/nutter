package swf

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/habnut/launcher/internal/furni"
	"github.com/habnut/launcher/internal/swfextract"
)

// hofAssetsDir holds furniture fetched item by item. Kept apart from what a
// build installs, so installing a newer build does not throw it away.
func hofAssetsDir(era string) string { return filepath.Join(eraRoot(era), "hof") }

// DefaultFurniCDN is where furniture artwork is fetched from: one SWF per
// item, at <revision>/<classname>.swf, mirrored from the live client.
const DefaultFurniCDN = "https://cdn.habboassets.com/images.habbo.com/dcr/hof_furni"

// FurniOptions say which furniture to fetch.
type FurniOptions struct {
	CDN   string
	All   bool            // every item in furnidata, not only what is on sale
	Names map[string]bool // classnames wanted besides, e.g. what rooms hold
	Jobs  int
}

// FetchFurni downloads and extracts the artwork for an era's furniture.
//
// Full client builds carry clothing, effects and pets but no furniture: the
// live client fetches each piece's SWF on its own. This does the same, from
// the era's installed furnidata, and is incremental — an item already fetched
// at its current revision is skipped, so running it again after a newer build
// brings in only what is new or changed.
func FetchFurni(era string, opt FurniOptions) error {
	if !ValidEra(era) {
		return fmt.Errorf("unknown era %q", era)
	}
	if opt.CDN == "" {
		opt.CDN = DefaultFurniCDN
	}
	if opt.Jobs <= 0 {
		opt.Jobs = 8
	}

	f, err := os.Open(filepath.Join(eraRoot(era), "furnidata.xml"))
	if err != nil {
		return fmt.Errorf("no furnidata installed for %s: install a build first", era)
	}
	items, err := furni.Parse(f)
	f.Close()
	if err != nil {
		return err
	}

	// One file per artwork name, at the newest revision any variant names.
	want := map[string]int{}
	for _, it := range items {
		if !(opt.All || (it.OnSale && !it.Rare) || opt.Names[it.Classname] || opt.Names[it.Artwork()]) {
			continue
		}
		if it.Revision > want[it.Artwork()] {
			want[it.Artwork()] = it.Revision
		}
	}

	statePath := filepath.Join(hofAssetsDir(era), "fetched.json")
	fetched := map[string]int{}
	if data, err := os.ReadFile(statePath); err == nil {
		_ = json.Unmarshal(data, &fetched)
	}

	var todo []string
	for name, rev := range want {
		if fetched[name] != rev {
			todo = append(todo, name)
		}
	}
	sort.Strings(todo)
	fmt.Printf("→ %d pieces of furniture wanted, %d already up to date, %d to fetch\n",
		len(want), len(want)-len(todo), len(todo))
	if len(todo) == 0 {
		return nil
	}

	if err := os.MkdirAll(hofAssetsDir(era), 0o755); err != nil {
		return err
	}
	manifestPath := filepath.Join(hofAssetsDir(era), "manifest.json")
	manifest := &swfextract.Manifest{Sprites: map[string]swfextract.SpriteEntry{}}
	if data, err := os.ReadFile(manifestPath); err == nil {
		_ = json.Unmarshal(data, manifest)
		if manifest.Sprites == nil {
			manifest.Sprites = map[string]swfextract.SpriteEntry{}
		}
	}

	swfDir := filepath.Join(packRoot, era, "hof_furni")
	if err := os.MkdirAll(swfDir, 0o755); err != nil {
		return err
	}

	client := &http.Client{Timeout: 60 * time.Second}
	jobs := make(chan string)
	var mu sync.Mutex
	var done, failed, sprites atomic.Int64
	var wg sync.WaitGroup
	for w := 0; w < opt.Jobs; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for name := range jobs {
				rev := want[name]
				path := filepath.Join(swfDir, name+".swf")
				url := fmt.Sprintf("%s/%d/%s.swf", opt.CDN, rev, name)
				if err := download(client, url, path); err != nil {
					failed.Add(1)
					fmt.Fprintf(os.Stderr, "warn: %s: %v\n", name, err)
					continue
				}
				m, err := swfextract.Extract(path, hofAssetsDir(era))
				if err != nil {
					failed.Add(1)
					fmt.Fprintf(os.Stderr, "warn: %s: %v\n", name, err)
					continue
				}
				mu.Lock()
				for k, v := range m.Sprites {
					manifest.Sprites[k] = v
				}
				fetched[name] = rev
				mu.Unlock()
				sprites.Add(int64(len(m.Sprites)))
				if n := done.Add(1); n%250 == 0 {
					fmt.Printf("→ %d of %d fetched\n", n, len(todo))
				}
			}
		}()
	}
	for _, name := range todo {
		jobs <- name
	}
	close(jobs)
	wg.Wait()

	fmt.Printf("→ fetched %d, failed %d, %d sprites\n", done.Load(), failed.Load(), sprites.Load())
	if err := writeJSONAtomic(manifestPath, manifest); err != nil {
		return err
	}
	if err := writeJSONAtomic(statePath, fetched); err != nil {
		return err
	}
	if err := mergeManifests(era); err != nil {
		return err
	}
	if done.Load() == 0 {
		return fmt.Errorf("no furniture could be fetched from %s", opt.CDN)
	}
	return nil
}

func download(client *http.Client, url, path string) error {
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt*2) * time.Second)
		}
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		req, _ := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		req.Header.Set("User-Agent", "habnutctl")
		resp, err := client.Do(req)
		if err != nil {
			cancel()
			lastErr = err
			continue
		}
		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			cancel()
			lastErr = fmt.Errorf("HTTP %d", resp.StatusCode)
			if resp.StatusCode == http.StatusNotFound {
				return lastErr
			}
			continue
		}
		tmp := path + ".part"
		out, err := os.Create(tmp)
		if err == nil {
			_, err = io.Copy(out, io.LimitReader(resp.Body, 64<<20))
			if cerr := out.Close(); err == nil {
				err = cerr
			}
		}
		resp.Body.Close()
		cancel()
		if err != nil {
			os.Remove(tmp)
			lastErr = err
			continue
		}
		return os.Rename(tmp, path)
	}
	return lastErr
}

// writeJSONAtomic replaces a file in one step, so the client never reads a
// half-written sprite list while furniture is being added.
func writeJSONAtomic(path string, v any) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	tmp := path + ".tmp-" + strconv.Itoa(os.Getpid())
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
