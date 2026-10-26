// SPDX-License-Identifier: GPL-3.0-or-later
// Bounded, loopback-only Android interoperability fixture. No production keys.
package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"flag"
	"fmt"
	"io"
	"log"
	"math/big"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"time"

	"github.com/sagernet/quic-go/http3"
)

type counters struct {
	Requests      atomic.Int64
	WrongLeg      atomic.Int64
	WrongProtocol atomic.Int64
	BackendH2     atomic.Int64
	Errors        atomic.Int64
}

func certificate(host string) (tls.Certificate, string, error) {
	rootKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	leafKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	serial := func() *big.Int {
		n, e := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 120))
		if e != nil {
			panic(e)
		}
		return n
	}
	now := time.Now()
	root := &x509.Certificate{SerialNumber: serial(), Subject: pkix.Name{CommonName: "Ephemeral NOVA " + host},
		NotBefore: now.Add(-30 * 24 * time.Hour), NotAfter: now.Add(30 * 24 * time.Hour), IsCA: true,
		BasicConstraintsValid: true, KeyUsage: x509.KeyUsageCertSign | x509.KeyUsageCRLSign}
	rootDER, err := x509.CreateCertificate(rand.Reader, root, root, &rootKey.PublicKey, rootKey)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	leaf := &x509.Certificate{SerialNumber: serial(), Subject: pkix.Name{CommonName: host}, DNSNames: []string{host},
		NotBefore: root.NotBefore, NotAfter: root.NotAfter, BasicConstraintsValid: true,
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	leafDER, err := x509.CreateCertificate(rand.Reader, leaf, root, &leafKey.PublicKey, rootKey)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	return tls.Certificate{Certificate: [][]byte{leafDER}, PrivateKey: leafKey}, string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: rootDER})), nil
}

func main() {
	backend := flag.String("backend", "https://127.0.0.1:38123", "official Xray loopback backend")
	backendCA := flag.String("backend-ca", "", "ephemeral backend CA PEM")
	work := flag.String("work", "", "dedicated output directory")
	upVersion := flag.Int("up-version", 2, "upload HTTP version: 1, 2 or 3")
	downVersion := flag.Int("down-version", 2, "download HTTP version: 1, 2 or 3")
	androidAddress := flag.String("android-address", "127.0.0.1", "Android dial address: loopback reverse or emulator host-loopback alias")
	seconds := flag.Int("seconds", 45, "bounded lifetime, 1..300 seconds")
	flag.Parse()
	if *work == "" || *backendCA == "" || *upVersion < 1 || *upVersion > 3 || *downVersion < 1 || *downVersion > 3 || *seconds < 1 || *seconds > 300 || (*androidAddress != "127.0.0.1" && *androidAddress != "10.0.2.2") {
		log.Fatal("invalid fixture options")
	}
	target, err := url.Parse(*backend)
	if err != nil || target.Scheme != "https" || net.ParseIP(target.Hostname()) == nil || !net.ParseIP(target.Hostname()).IsLoopback() {
		log.Fatal("backend must be loopback HTTPS")
	}
	if err := os.MkdirAll(*work, 0700); err != nil {
		log.Fatal(err)
	}
	rootPEM, err := os.ReadFile(*backendCA)
	if err != nil {
		log.Fatal(err)
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(rootPEM) {
		log.Fatal("invalid backend CA")
	}
	transport := &http.Transport{ForceAttemptHTTP2: true, DisableCompression: true,
		TLSClientConfig: &tls.Config{RootCAs: roots, ServerName: "localhost", MinVersion: tls.VersionTLS12}}
	defer transport.CloseIdleConnections()
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(*seconds)*time.Second)
	defer cancel()
	manifest := map[string]any{}
	stats := map[string]*counters{"upload": {}, "download": {}}
	servers := []*http.Server{}
	defer func() {
		for _, server := range servers {
			server.Close()
		}
	}()
	for index, leg := range []string{"upload", "download"} {
		leg, index := leg, index
		host := leg + ".localhost"
		prefix := "/nova-" + leg + "/"
		port, version := 38125, *upVersion
		if index == 1 {
			port, version = 38126, *downVersion
		}
		cert, ca, err := certificate(host)
		if err != nil {
			log.Fatal(err)
		}
		manifest[leg] = map[string]any{"port": port, "server_name": host, "path": prefix, "ca": ca, "version": version, "address": *androidAddress}
		proxy := httputil.NewSingleHostReverseProxy(target)
		original := proxy.Director
		proxy.Director = func(r *http.Request) {
			original(r)
			r.URL.Path = "/nova-fixture/" + strings.TrimPrefix(r.URL.Path, prefix)
			r.URL.RawPath = ""
		}
		proxy.Transport = transport
		proxy.FlushInterval = -1
		proxy.ModifyResponse = func(response *http.Response) error {
			log.Printf("response leg=%s method=%s path=%s status=%d protocol=%s", leg, response.Request.Method, response.Request.URL.Path, response.StatusCode, response.Proto)
			if response.ProtoMajor != 2 {
				stats[leg].Errors.Add(1)
				return fmt.Errorf("backend did not negotiate HTTP/2")
			}
			stats[leg].BackendH2.Add(1)
			return nil
		}
		proxy.ErrorHandler = func(w http.ResponseWriter, request *http.Request, err error) {
			log.Printf("proxy-error leg=%s method=%s path=%s error=%v", leg, request.Method, request.URL.Path, err)
			stats[leg].Errors.Add(1)
			http.Error(w, "fixture backend error", 502)
		}
		server := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			stats[leg].Requests.Add(1)
			if r.ProtoMajor != version {
				stats[leg].WrongProtocol.Add(1)
				http.Error(w, "wrong HTTP version", 400)
				return
			}
			hostname := strings.Split(r.Host, ":")[0]
			if !strings.HasPrefix(r.URL.Path, prefix) || hostname != host || (index == 0 && r.Method == http.MethodGet) || (index == 1 && r.Method != http.MethodGet) {
				stats[leg].WrongLeg.Add(1)
				http.Error(w, "wrong XHTTP leg", 400)
				return
			}
			started := time.Now()
			log.Printf("request-start leg=%s method=%s path=%s protocol=%s", leg, r.Method, r.URL.Path, r.Proto)
			defer func() {
				log.Printf("request-end leg=%s method=%s path=%s elapsed=%s context=%v", leg, r.Method, r.URL.Path, time.Since(started), r.Context().Err())
			}()
			proxy.ServeHTTP(w, r)
		}), TLSConfig: &tls.Config{Certificates: []tls.Certificate{cert}, MinVersion: tls.VersionTLS12}, ErrorLog: log.New(io.Discard, "", 0)}
		if version == 3 {
			packet, err := net.ListenPacket("udp", fmt.Sprintf("127.0.0.1:%d", port))
			if err != nil {
				log.Fatal(err)
			}
			quicServer := &http3.Server{Handler: server.Handler, TLSConfig: http3.ConfigureTLSConfig(server.TLSConfig)}
			defer quicServer.Close()
			defer packet.Close()
			go func() {
				if err := quicServer.Serve(packet); err != nil && ctx.Err() == nil {
					log.Print("fixture QUIC listener failed")
					cancel()
				}
			}()
			continue
		}
		if version == 1 {
			server.TLSConfig.NextProtos = []string{"http/1.1"}
			server.TLSNextProto = map[string]func(*http.Server, *tls.Conn, http.Handler){}
		} else {
			server.TLSConfig.NextProtos = []string{"h2"}
		}
		listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
		if err != nil {
			log.Fatal(err)
		}
		servers = append(servers, server)
		go func() {
			if err := server.ServeTLS(listener, "", ""); err != nil && err != http.ErrServerClosed {
				log.Print("fixture listener failed")
				cancel()
			}
		}()
	}
	encoded, err := json.Marshal(manifest)
	if err != nil {
		log.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(*work, "manifest.json"), encoded, 0600); err != nil {
		log.Fatal(err)
	}
	fmt.Println("ANDROID_DISTINCT_TLS_FRONTENDS_READY")
	<-ctx.Done()
	for _, server := range servers {
		server.Close()
	}
	report := map[string]any{}
	for leg, c := range stats {
		report[leg] = map[string]int64{"requests": c.Requests.Load(), "wrong_leg": c.WrongLeg.Load(), "wrong_protocol": c.WrongProtocol.Load(), "backend_h2": c.BackendH2.Load(), "errors": c.Errors.Load()}
	}
	encoded, err = json.MarshalIndent(report, "", "  ")
	if err != nil {
		log.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(*work, "requests.json"), encoded, 0600); err != nil {
		log.Fatal(err)
	}
	if ctx.Err() != context.DeadlineExceeded {
		os.Exit(1)
	}
}
