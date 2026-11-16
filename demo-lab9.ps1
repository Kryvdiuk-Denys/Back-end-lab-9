$ErrorActionPreference = 'Stop'

function Invoke-ComposeQuietly {
    param([string[]]$Arguments)

    # PowerShell 5.1 promotes native stderr output to an error record. Docker
    # Compose uses stderr for normal build progress, so keep that non-terminating
    # and rely on the process exit code to detect an actual Compose failure.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & docker compose @Arguments *> $null
        $composeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($composeExitCode -ne 0) {
        throw "docker compose $($Arguments -join ' ') exited with code $composeExitCode. Run that command directly to see its error details."
    }
}

function Wait-ForApi {
    param(
        [string]$Url,
        [hashtable]$Headers,
        [string]$ServiceName
    )

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            Invoke-RestMethod -Uri $Url -Headers $Headers -TimeoutSec 10 | Out-Null
            return
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$ServiceName did not become ready at $Url. Check it with: docker compose logs $ServiceName"
}

function New-DemoBooking {
    param(
        [string]$Transport,
        [int]$Hour,
        [string]$Email,
        [long]$WorkspaceId,
        [hashtable]$Headers
    )

    $start = [datetime]::Today.AddDays(1).AddHours($Hour)
    $end = $start.AddHours(2)
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $payload = [ordered]@{
        workspaceId = $WorkspaceId
        customerName = "Lab 9 $Transport Demo"
        customerEmail = $Email
        startsAt = $start.ToString("yyyy-MM-dd'T'HH:mm:ss", $culture)
        endsAt = $end.ToString("yyyy-MM-dd'T'HH:mm:ss", $culture)
        status = 'CONFIRMED'
    }

    $response = Invoke-WebRequest `
        -Uri 'http://localhost:8080/api/bookings' `
        -Method Post `
        -Headers $Headers `
        -ContentType 'application/json' `
        -Body ($payload | ConvertTo-Json) `
        -UseBasicParsing `
        -TimeoutSec 20

    $booking = $response.Content | ConvertFrom-Json
    if ([int]$response.StatusCode -ne 201 -or [long]$booking.workspaceId -ne $WorkspaceId) {
        throw "$Transport booking did not return the expected HTTP 201 response and workspace ID."
    }

    [pscustomobject]@{
        Transport  = $Transport
        HttpStatus = [int]$response.StatusCode
        BookingId  = $booking.id
        Workspace  = $booking.workspaceId
        TotalPrice = $booking.totalPrice
    }
}

Push-Location $PSScriptRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Start Docker Desktop and make sure docker is available in PATH.'
    }

    $runId = Get-Date -Format 'yyyyMMddHHmmss'
    $env:WORKSPACE_CLIENT_TYPE = 'http'
    $env:WORKSPACE_GRPC_SERVER_ENABLED = 'true'

    Write-Host 'Starting the Compose stack (HTTP client; gRPC server enabled)...'
    Invoke-ComposeQuietly -Arguments @('up', '-d', '--build')

    $tokenResponse = $null
    for ($attempt = 1; $attempt -le 36; $attempt++) {
        try {
            $tokenResponse = Invoke-RestMethod `
                -Uri 'http://localhost:8081/realms/coworking/protocol/openid-connect/token' `
                -Method Post `
                -ContentType 'application/x-www-form-urlencoded' `
                -Body @{
                    grant_type = 'password'
                    client_id = 'coworking-api'
                    client_secret = 'api-secret'
                    username = 'manager'
                    password = 'manager'
                } `
                -TimeoutSec 10
            break
        }
        catch {
            Start-Sleep -Seconds 5
        }
    }

    if (-not $tokenResponse.access_token) {
        throw 'Keycloak did not issue a manager token. Check it with: docker compose logs keycloak'
    }

    $headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
    Wait-ForApi -Url 'http://localhost:8082/api/workspaces' -Headers $headers -ServiceName 'workspace-service'
    Wait-ForApi -Url 'http://localhost:8080/api/bookings' -Headers $headers -ServiceName 'booking-service'

    $workspaceName = "Lab 9 Demo $runId"
    $workspacePayload = @{
        name = $workspaceName
        type = 'ROOM'
        capacity = 4
        hourlyRate = 35.00
        active = $true
    }
    $workspaceResponse = Invoke-WebRequest `
        -Uri 'http://localhost:8082/api/workspaces' `
        -Method Post `
        -Headers $headers `
        -ContentType 'application/json' `
        -Body ($workspacePayload | ConvertTo-Json) `
        -UseBasicParsing `
        -TimeoutSec 20
    $workspace = $workspaceResponse.Content | ConvertFrom-Json
    if ([int]$workspaceResponse.StatusCode -ne 201 -or -not $workspace.id) {
        throw 'Workspace service did not return HTTP 201 with a workspace ID.'
    }

    Write-Host 'Creating a booking through the HTTP workspace client...'
    $results = @(
        New-DemoBooking -Transport 'HTTP' -Hour 10 -Email "lab9.http.$runId@example.com" -WorkspaceId ([long]$workspace.id) -Headers $headers
    )

    $env:WORKSPACE_CLIENT_TYPE = 'grpc'
    Write-Host 'Switching booking-service to gRPC and recreating it...'
    Invoke-ComposeQuietly -Arguments @('up', '-d', '--build', '--force-recreate', '--no-deps', 'booking-service')
    Wait-ForApi -Url 'http://localhost:8080/api/bookings' -Headers $headers -ServiceName 'booking-service'

    Write-Host 'Creating a booking through the gRPC workspace client...'
    $results += New-DemoBooking -Transport 'gRPC' -Hour 14 -Email "lab9.grpc.$runId@example.com" -WorkspaceId ([long]$workspace.id) -Headers $headers

    $grpcLog = & docker compose logs --no-color --tail=200 workspace-service 2>$null |
        Select-String -Pattern 'Workspace gRPC server started on port' |
        Select-Object -Last 1
    if (-not $grpcLog) {
        throw 'Bookings succeeded, but the gRPC server startup log was not found. Check: docker compose logs workspace-service'
    }

    Write-Host ''
    Write-Host 'LAB 9 - INTERSERVICE COMMUNICATION'
    Write-Host ('Workspace: {0} - {1}' -f $workspace.id, $workspace.name)
    $results | Format-Table Transport, HttpStatus, BookingId, Workspace, TotalPrice -AutoSize
    Write-Host $grpcLog.Line.Trim()
    Write-Host 'Both booking requests returned HTTP 201. Containers are still running.'
}
finally {
    Pop-Location
}
