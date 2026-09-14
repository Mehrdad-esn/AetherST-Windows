// Command psiphon-helper runs Psiphon (psiphon-tunnel-core) as a standalone
// local proxy for the AetherST Windows port.
//
// It mirrors the Android app's Psiphon configuration (same propagation
// channel, sponsor, keys, timeouts) and exposes the tunnel on a local SOCKS
// port (plus optional HTTP port). An upstream proxy (e.g. the Aether core
// SOCKS) can be set so Psiphon dials through it.
//
// Status is reported on stdout as lines:
//
//	PSIPHON_EVENT noticeType=<type> [port=<n>] [count=<n>] [regions=<csv>]
//
// License: this helper links psiphon-tunnel-core (GPL-3.0) and is
// distributed under GPL-3.0. See README.md in this directory.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"sort"
	"strings"
	"syscall"

	"github.com/Psiphon-Labs/psiphon-tunnel-core/psiphon"
)

type noticeEnvelope struct {
	NoticeType string          `json:"noticeType"`
	Data       json.RawMessage `json:"data"`
}

type noticeWriter struct{}

func (noticeWriter) Write(p []byte) (int, error) {
	var env noticeEnvelope
	if err := json.Unmarshal(p, &env); err != nil {
		return len(p), nil
	}
	switch env.NoticeType {
	case "Tunnels":
		var data struct {
			Count int `json:"count"`
		}
		_ = json.Unmarshal(env.Data, &data)
		fmt.Printf("PSIPHON_EVENT noticeType=Tunnels count=%d\n", data.Count)
	case "ListeningSocksProxyPort":
		var data struct {
			Port int `json:"port"`
		}
		_ = json.Unmarshal(env.Data, &data)
		fmt.Printf("PSIPHON_EVENT noticeType=ListeningSocksProxyPort port=%d\n", data.Port)
	case "ListeningHttpProxyPort":
		var data struct {
			Port int `json:"port"`
		}
		_ = json.Unmarshal(env.Data, &data)
		fmt.Printf("PSIPHON_EVENT noticeType=ListeningHttpProxyPort port=%d\n", data.Port)
	case "AvailableEgressRegions":
		var data struct {
			Regions []string `json:"regions"`
		}
		_ = json.Unmarshal(env.Data, &data)
		regions := append([]string{}, data.Regions...)
		sort.Strings(regions)
		fmt.Printf("PSIPHON_EVENT noticeType=AvailableEgressRegions regions=%s\n", strings.Join(regions, ","))
	}
	return len(p), nil
}

func main() {
	socksPort := flag.Int("socks-port", 3080, "local SOCKS proxy listen port")
	httpPort := flag.Int("http-port", 0, "local HTTP proxy listen port (0 = disabled)")
	dataDir := flag.String("data-dir", "", "writable data directory (required)")
	serverEntries := flag.String("server-entries", "", "path to embedded server entries file (required)")
	egressRegion := flag.String("egress-region", "", "ISO 3166-1 alpha-2 egress country (empty = automatic)")
	upstream := flag.String("upstream", "", "upstream proxy URL Psiphon dials through, e.g. socks5://127.0.0.1:1819")
	propagationChannel := flag.String("propagation-channel", "FFFFFFFFFFFFFFFF", "Psiphon propagation channel ID")
	sponsorID := flag.String("sponsor-id", "1111111111111111", "Psiphon sponsor ID")
	deviceRegion := flag.String("device-region", "IR", "device region code reported to Psiphon")
	flag.Parse()

	if *dataDir == "" || *serverEntries == "" {
		fmt.Fprintln(os.Stderr, "psiphon-helper: --data-dir and --server-entries are required")
		os.Exit(2)
	}
	if err := os.MkdirAll(*dataDir, 0700); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot create data dir:", err)
		os.Exit(2)
	}
	// BoltDB does not create parent directories for the datastore file.
	if err := os.MkdirAll(*dataDir+"/ca.psiphon.PsiphonTunnel.tunnel-core/datastore", 0700); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot create datastore dir:", err)
		os.Exit(2)
	}

	configMap := map[string]interface{}{
		"PropagationChannelId":            *propagationChannel,
		"SponsorId":                       *sponsorID,
		"EgressRegion":                    *egressRegion,
		"EstablishTunnelTimeoutSeconds":   120,
		"DataRootDirectory":               *dataDir,
		"ClientVersion":                   "1",
		"ClientPlatform":                  "Windows",
		"TunnelProtocol":                  "",
		"RemoteServerListURL":             "",
		"LocalSocksProxyPort":             *socksPort,
		"RemoteServerListSignaturePublicKey": "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM=",
		"ServerEntrySignaturePublicKey": "sHuUVTWaRyh5pZwy4UguSgkwmBe0EHtJJkoF5WrxmvA=",
		"ExchangeObfuscationKey":        "DpXzloJk1Hw6aSzmKKky0xcahsEHubch81Mi6K0XMlU=",
		"EmitBytesTransferred":          true,
		"DeviceRegion":                  *deviceRegion,
		"ConnectionWorkerPoolSize":      12,
	}
	if *httpPort > 0 {
		configMap["LocalHttpProxyPort"] = *httpPort
	}
	if *upstream != "" {
		configMap["UpstreamProxyURL"] = *upstream
	}

	configJSON, err := json.Marshal(configMap)
	if err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot encode config:", err)
		os.Exit(2)
	}
	config, err := psiphon.LoadConfig(configJSON)
	if err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: invalid config:", err)
		os.Exit(2)
	}
	if err := config.Commit(false); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot commit config:", err)
		os.Exit(2)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := psiphon.OpenDataStore(config); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot open data store:", err)
		os.Exit(1)
	}
	defer psiphon.CloseDataStore()

	if err := psiphon.ImportEmbeddedServerEntries(ctx, config, *serverEntries, ""); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot import server entries:", err)
		os.Exit(1)
	}

	if err := psiphon.SetNoticeWriter(noticeWriter{}); err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot set notice writer:", err)
		os.Exit(2)
	}

	controller, err := psiphon.NewController(config)
	if err != nil {
		fmt.Fprintln(os.Stderr, "psiphon-helper: cannot create controller:", err)
		os.Exit(1)
	}

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-signals
		fmt.Fprintln(os.Stderr, "psiphon-helper: stopping")
		cancel()
	}()

	fmt.Fprintln(os.Stderr, "psiphon-helper: starting")
	controller.Run(ctx)
	fmt.Fprintln(os.Stderr, "psiphon-helper: stopped")
}
