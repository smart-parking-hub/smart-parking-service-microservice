param(
    [Parameter(Mandatory=$true)][long[]]$BookingIds,
    [string]$BookingServiceUrl = 'http://localhost:8083',
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
if (-not $env:SPH_INTERNAL_SERVICE_TOKEN) { throw 'Set SPH_INTERNAL_SERVICE_TOKEN before running this tool.' }
$migrationHeaders = @{ 'X-Internal-Service-Token' = $env:SPH_INTERNAL_SERVICE_TOKEN }
foreach ($bookingId in $BookingIds) {
    if ($bookingId -le 0) { throw 'Booking IDs must be positive.' }
    $migrationBase = $BookingServiceUrl.TrimEnd('/') + '/api/v1/internal/bookings/' + $bookingId
    $status = Invoke-RestMethod -Method Get -Uri ($migrationBase + '/hold-status') -Headers $migrationHeaders
    if ($Apply) {
        $result = Invoke-RestMethod -Method Post -Uri ($migrationBase + '/adopt-schedule') -Headers $migrationHeaders
        [pscustomobject]@{ BookingId=$bookingId; PreviousStatus=$status.data; AdoptionCommitted=$result.data }
    } else {
        [pscustomobject]@{ BookingId=$bookingId; Status=$status.data; Mode='DRY_RUN' }
    }
}
