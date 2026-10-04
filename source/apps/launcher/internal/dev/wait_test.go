package dev

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func serving(status int) *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(status)
	}))
}

// A short deadline: each check makes one attempt and then reports.
const oneTry = 10 * time.Millisecond

func TestReadinessAcceptsPageErrorsButNotGatewayErrors(t *testing.T) {
	cases := map[int]bool{
		http.StatusOK:                  true,
		http.StatusInternalServerError: true, // PHP answered; there is a page to read
		http.StatusNotFound:            true,
		http.StatusBadGateway:          false, // the web server could not reach PHP
		http.StatusServiceUnavailable:  false,
		http.StatusGatewayTimeout:      false,
	}
	for status, want := range cases {
		srv := serving(status)
		err := waitForHTTP(context.Background(), srv.URL, oneTry)
		srv.Close()
		if got := err == nil; got != want {
			t.Errorf("HTTP %d: ready=%v, want %v (err: %v)", status, got, want, err)
		}
	}
}

func TestPlayerPagesMustActuallyWork(t *testing.T) {
	cases := map[int]bool{
		http.StatusOK:                  true,
		http.StatusFound:               true,  // followed: a redirect to a page that works
		http.StatusForbidden:           false, // the game's directory listing
		http.StatusInternalServerError: false, // the website failing every page
		http.StatusBadGateway:          false,
	}
	for status, want := range cases {
		var srv *httptest.Server
		if status == http.StatusFound {
			target := serving(http.StatusOK)
			defer target.Close()
			srv = httptest.NewServer(http.RedirectHandler(target.URL, http.StatusFound))
		} else {
			srv = serving(status)
		}
		err := waitForOK(context.Background(), srv.URL, oneTry)
		srv.Close()
		if got := err == nil; got != want {
			t.Errorf("HTTP %d: ok=%v, want %v (err: %v)", status, got, want, err)
		}
	}
}
