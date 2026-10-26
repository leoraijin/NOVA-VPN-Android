package main

import (
	"runtime"
	"testing"
)

func TestAndroidCompilerCommandWindowsSpaces(t *testing.T) {
	if runtime.GOOS != "windows" {
		t.Skip("Windows NDK invocation")
	}
	for _, item := range []struct{ path, want string }{
		{`D:\SDK path\bin\x86_64-linux-android24-clang`, `"D:\SDK path\bin\clang.exe" --target=x86_64-linux-android24`},
		{`D:\SDK path\bin\armv7a-linux-androideabi24-clang++`, `"D:\SDK path\bin\clang++.exe" --target=armv7a-linux-androideabi24`},
		{`D:\sdk\bin\aarch64-linux-android24-clang.cmd`, `D:\sdk\bin\clang.exe --target=aarch64-linux-android24`},
	} {
		if got := androidCompilerCommand(item.path); got != item.want {
			t.Fatalf("compiler command %q, want %q", got, item.want)
		}
	}
}
