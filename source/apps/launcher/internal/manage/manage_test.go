package manage

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRequestsWithoutTheTokenAreRefused(t *testing.T) {
	h := guarded("secret", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusOK) })
	for _, tc := range []struct {
		header, query string
		want          int
	}{
		{"", "", http.StatusForbidden},
		{"wrong", "", http.StatusForbidden},
		{"secret", "", http.StatusOK},
		{"", "token=secret", http.StatusOK},
	} {
		r := httptest.NewRequest(http.MethodGet, "/api/run?"+tc.query, nil)
		if tc.header != "" {
			r.Header.Set("X-Habnut-Token", tc.header)
		}
		w := httptest.NewRecorder()
		h(w, r)
		if w.Code != tc.want {
			t.Errorf("header=%q query=%q: got %d, want %d", tc.header, tc.query, w.Code, tc.want)
		}
	}
}

// Whatever the page sends, only known services reach the server's command line.
func TestServiceArgumentsAreChecked(t *testing.T) {
	for _, arg := range []string{"cms; rm -rf /", "../x", "$(id)", "cms nginx", "mysql"} {
		if _, err := actions["restart"].args(arg); err == nil {
			t.Errorf("restart accepted %q", arg)
		}
	}
	got, err := actions["logs"].args("emulator")
	if err != nil || got[len(got)-1] != "emulator" {
		t.Errorf("logs emulator: %v %v", got, err)
	}
	if got, _ := actions["verify"].args("anything"); len(got) != 1 || got[0] != "verify" {
		t.Errorf("verify passed its argument through: %v", got)
	}
}

func TestOnlyBackupNamesCanBeFetched(t *testing.T) {
	for name, ok := range map[string]bool{
		"habnut-20261007T222331Z.sql.gz":   true,
		"../habnut.env":                    false,
		"habnut-x.sql.gz; cat /etc/shadow": false,
	} {
		if backupName.MatchString(name) != ok {
			t.Errorf("%q: allowed=%v", name, !ok)
		}
	}
}
