package main

import (
	"path/filepath"
	"runtime"
	"strings"
)

// Cgo parses CC/CXX as command lines, unlike exec.Command's argv paths. Quote
// spaces, and on Windows invoke the native Clang executable with the exact NDK
// wrapper target instead of depending on batch/shell script execution.
func androidCompilerCommand(compiler string) string {
	if runtime.GOOS == "windows" {
		name := strings.TrimSuffix(filepath.Base(compiler), ".cmd")
		for _, suffix := range []string{"-clang++", "-clang"} {
			if strings.HasSuffix(name, suffix) {
				executable := strings.TrimPrefix(suffix, "-") + ".exe"
				return quoteCompilerPath(filepath.Join(filepath.Dir(compiler), executable)) + " --target=" + strings.TrimSuffix(name, suffix)
			}
		}
	}
	return quoteCompilerPath(compiler)
}

func quoteCompilerPath(path string) string {
	if strings.ContainsAny(path, " \t") {
		return `"` + path + `"`
	}
	return path
}
