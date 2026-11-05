param(
    [string]$h,
    [int]$p,
    [string]$U,
    [string]$d,
    [switch]$w,
    [switch]$t,
    [switch]$A,
    [string]$c
)

$behavior = $env:TEST_MOCK_PSQL_BEHAVIOR
if (-not $behavior) { $behavior = "OK" }
$mockDb = if ($env:MOCK_DB) { $env:MOCK_DB } else { "lysandri_clone_db" }

if ($behavior -eq "FAIL_APP" -and $U -eq "lysandri_app") {
    [Console]::Error.WriteLine("FATAL: password authentication failed for user lysandri_app")
    exit 1
}

if ($behavior -eq "FAIL_FLYWAY" -and $U -eq "lysandri_admin") {
    [Console]::Error.WriteLine("FATAL: password authentication failed for user lysandri_admin")
    exit 1
}

if ($behavior -eq "DIFFERENT_SERVER") {
    Write-Output "other_server_db|other_user"
    exit 0
}

if ($behavior -eq "WRONG_DB") {
    Write-Output "wrong_db|$U"
    exit 0
}

# Default OK behavior
Write-Output "$mockDb|$U"
exit 0
