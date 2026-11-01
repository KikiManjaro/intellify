<#
    Intellify - Windows media session bridge.

    Emits exactly one JSON line on stdout (nothing else), describing the current System Media
    Transport Controls session, or {"status":"None"} when there is no session.

    Usage:
        powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File nowplaying.ps1 status|playpause|next|prev

    Requires Windows PowerShell 5.1 (the WinRT projections are not available in PowerShell 7).
#>
param(
    [ValidateSet('status', 'playpause', 'next', 'prev')]
    [string]$Command = 'status'
)

$ErrorActionPreference = 'Stop'

# --- WinRT plumbing: turn an IAsyncOperation<T> into something we can wait for --------------------
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await($operation, $resultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($resultType)
    $netTask = $asTask.Invoke($null, @($operation))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]

function Get-CurrentSession {
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    if ($null -eq $manager) { return $null }
    return $manager.GetCurrentSession()
}

function Write-Line($payload) {
    # ConvertTo-Json -Compress always produces a single line for a flat object.
    [Console]::Out.WriteLine(($payload | ConvertTo-Json -Compress -Depth 4))
}

$session = Get-CurrentSession
if ($null -eq $session) {
    Write-Line @{ status = 'None' }
    exit 0
}

if ($Command -ne 'status') {
    $accepted = switch ($Command) {
        'playpause' { Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
        'next' { Await ($session.TrySkipNextAsync()) ([bool]) }
        'prev' { Await ($session.TrySkipPreviousAsync()) ([bool]) }
    }
    Write-Line @{ status = 'Ok'; accepted = $accepted }
    exit 0
}

$playback = $session.GetPlaybackInfo()
$status = if ($null -eq $playback) { 'Unknown' } else { $playback.PlaybackStatus.ToString() }

$properties = $null
try {
    $properties = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
}
catch {
    $properties = $null
}

$timeline = $null
try {
    $timeline = $session.GetTimelineProperties()
}
catch {
    $timeline = $null
}

$payload = [ordered]@{
    status     = $status
    title      = if ($null -ne $properties) { $properties.Title } else { $null }
    artist     = if ($null -ne $properties) { $properties.Artist } else { $null }
    album      = if ($null -ne $properties) { $properties.AlbumTitle } else { $null }
    durationMs = if ($null -ne $timeline) { [long][math]::Round($timeline.EndTime.TotalMilliseconds) } else { 0 }
    positionMs = if ($null -ne $timeline) { [long][math]::Round($timeline.Position.TotalMilliseconds) } else { 0 }
}

Write-Line $payload
exit 0
