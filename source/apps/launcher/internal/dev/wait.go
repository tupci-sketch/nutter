package dev

import (
	"context"
	"fmt"
	"net/http"
	"time"
)

// waitForHTTP waits until the web server answers and has reached what is
// behind it.
//
// A page error will do, including a 500: the question is whether the web
// server and PHP are talking to each other, not whether every page is right. A
// hotel that answers with an error is a hotel somebody can go and look at; one
// that refuses the connection is not.
//
// A gateway error will not. 502, 503 and 504 are the web server saying it
// could not reach PHP or the hotel at all, which is the one thing this wait is
// for; they used to count as up, so a website whose PHP had died passed.
func waitForHTTP(ctx context.Context, url string, timeout time.Duration) error {
	return waitFor(ctx, url, timeout, func(status int) bool {
		return status != http.StatusBadGateway &&
			status != http.StatusServiceUnavailable &&
			status != http.StatusGatewayTimeout
	})
}

// waitForOK waits until url answers with a status below 400, following
// redirects.
//
// For the pages a player actually goes through, where an error page is not a
// hotel anybody can use: the website's front page and the game the website
// sends a signed-in player to.
func waitForOK(ctx context.Context, url string, timeout time.Duration) error {
	return waitFor(ctx, url, timeout, func(status int) bool { return status < 400 })
}

func waitFor(ctx context.Context, url string, timeout time.Duration, good func(status int) bool) error {
	deadline := time.Now().Add(timeout)
	client := &http.Client{Timeout: 5 * time.Second}

	var lastErr error
	for {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err != nil {
			return err
		}

		resp, err := client.Do(req)
		if err == nil {
			resp.Body.Close()
			if good(resp.StatusCode) {
				return nil
			}
			lastErr = fmt.Errorf("HTTP %d", resp.StatusCode)
		} else {
			lastErr = err
		}

		if time.Now().After(deadline) {
			return fmt.Errorf("%s did not answer within %s (last: %v)", url, timeout, lastErr)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(2 * time.Second):
		}
	}
}
