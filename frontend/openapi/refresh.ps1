<#
.SYNOPSIS
    Refreshes the committed OpenAPI snapshot from a running backend.

.DESCRIPTION
    The snapshot is the contract the Kotlin client is generated from, so it has to be
    re-exported whenever the REST API changes. /api/openapi is not public—it belongs to
    the management UI filter chain—so this logs in with an administrator account first
    and reuses that session.

    The `servers` block is removed on the way out. It would record whichever host the
    export happened to run on, while the TV asks the user for the server address at
    runtime.

.EXAMPLE
    ./refresh.ps1 -BaseUrl http://localhost:8085 -Username admin
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8085',
    [Parameter(Mandatory = $true)][string] $Username,
    [string] $Password
)

$ErrorActionPreference = 'Stop'

if (-not $Password) {
    $secure = Read-Host -AsSecureString "Password for $Username"
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$target = Join-Path $PSScriptRoot 'homecenter-openapi.json'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# The login form carries a CSRF token; the UI chain rejects the POST without it.
$form = Invoke-WebRequest -Uri "$BaseUrl/prihlasenie" -WebSession $session -UseBasicParsing
$csrf = [regex]::Match($form.Content, 'name="_csrf"\s+value="([^"]+)"').Groups[1].Value
if (-not $csrf) { throw "CSRF token not found on $BaseUrl/prihlasenie" }

try {
    Invoke-WebRequest -Uri "$BaseUrl/prihlasenie" -Method Post -WebSession $session -UseBasicParsing `
        -Body @{ username = $Username; password = $Password; _csrf = $csrf } -MaximumRedirection 0 | Out-Null
} catch {
    # A successful form login answers with a redirect, which -MaximumRedirection 0 reports
    # as an error. A failed one redirects to /prihlasenie?chyba, so check where it points.
    $location = $_.Exception.Response.Headers.Location
    if ("$location" -match 'chyba') { throw "Login failed for $Username" }
}

$response = Invoke-WebRequest -Uri "$BaseUrl/api/openapi" -WebSession $session -UseBasicParsing
$document = $response.Content | ConvertFrom-Json -Depth 60
$document.PSObject.Properties.Remove('servers')

$json = ($document | ConvertTo-Json -Depth 60).Replace("`r`n", "`n")
[IO.File]::WriteAllText($target, $json + "`n", (New-Object Text.UTF8Encoding($false)))

$paths = ($document.paths.PSObject.Properties.Name | Sort-Object) -join ', '
Write-Host "Saved $target"
Write-Host "Paths: $paths"
