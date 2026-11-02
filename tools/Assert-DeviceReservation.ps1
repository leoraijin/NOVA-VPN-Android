param(
    [Parameter(Mandatory=$true)][string] $ReservationFile,
    [Parameter(Mandatory=$true)][string] $DeviceSerial
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ReservationFile -PathType Leaf)) { throw 'Device reservation file is missing; availability is unconfirmed.' }
try { $reservation = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $ReservationFile)) | ConvertFrom-Json }
catch { throw 'Device reservation is unreadable; availability is unconfirmed.' }
if ($reservation.device -ne $DeviceSerial) { throw 'Reservation belongs to a different device.' }
# Never infer release from a timestamp or a stale process list.
if ($reservation.status -ne 'released' -or -not $reservation.releasedUtc) {
    throw 'Device has not been explicitly released by its current owner.'
}
$released = [DateTimeOffset]::MinValue
if (-not [DateTimeOffset]::TryParse([string]$reservation.releasedUtc, [ref]$released) -or $released -gt [DateTimeOffset]::UtcNow) {
    throw 'Device release evidence has an invalid timestamp.'
}
'Explicit device release confirmed.'