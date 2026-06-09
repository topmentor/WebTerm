param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateRange(1, 65535)]
    [int]$Port,

    [switch]$Force
)

$connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue

if (-not $connections) {
    Write-Host "No listening process found on port $Port."
    exit 0
}

$processIds = $connections |
    Select-Object -ExpandProperty OwningProcess -Unique |
    Where-Object { $_ -and $_ -gt 0 }

foreach ($processId in $processIds) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) {
        Write-Host "Process $processId is no longer running."
        continue
    }

    $name = $process.ProcessName
    $path = $process.Path
    if (-not $path) {
        $path = "(path unavailable)"
    }

    if (-not $Force) {
        $answer = Read-Host "Kill process $processId ($name) listening on port $Port? $path [y/N]"
        if ($answer -notin @("y", "Y", "yes", "YES")) {
            Write-Host "Skipped process $processId."
            continue
        }
    }

    Stop-Process -Id $processId -Force
    Write-Host "Killed process $processId ($name) on port $Port."
}
