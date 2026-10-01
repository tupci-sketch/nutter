package dev

import (
	"context"
	"fmt"
	"net/http"
	"time"
)

// waitForHTTP waits until a URL answers with anything at all.
//
// Any status will do, including a 500: the question is whether the web server
// and PHP are talking to each other, not whether every page is right. A hotel
// that answers with an error is a hotel somebody can go and look at; one that
// refuses the connection is not.
func waitForHTTP(ctx context.Context, url string, timeout time.Duration) error {
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
			return nil
		}
		lastErr = err

		if time.Now().After(deadline) {
			return fmt.Errorf("%s did not answer within %s (%v); "+
				"run `habnutctl dev logs web` to see what the web server is doing",
				url, timeout, lastErr)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(2 * time.Second):
		}
	}
}
