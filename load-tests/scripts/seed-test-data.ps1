# ============================================================
# Movie Ticket System — Test Data Seeder
# ============================================================
# Seeds the database with test data for manual testing or
# before running Gatling load tests.
#
# Prerequisites: All services must be running.
# Usage: .\load-tests\scripts\seed-test-data.ps1
# ============================================================

$ErrorActionPreference = "Stop"

$USERS_URL   = "http://localhost:8081"
$THEATERS_URL = "http://localhost:8082"
$MOVIES_URL  = "http://localhost:8083"
$HEADERS = @{ "Content-Type" = "application/json" }

Write-Host "`n=== Movie Ticket System — Seeding Test Data ===" -ForegroundColor Cyan

# ── Step 1: Register a test admin user ──
Write-Host "`n[1/4] Registering test user..." -ForegroundColor Yellow
$registerBody = @{
    name     = "Test Admin"
    email    = "admin@movietickets.com"
    password = "admin123"
    phone    = "+91-9876543210"
} | ConvertTo-Json

try {
    $authResponse = Invoke-RestMethod -Uri "$USERS_URL/api/users/register" -Method POST -Body $registerBody -Headers $HEADERS
    $token = $authResponse.data.token
    $userId = $authResponse.data.user.id
    Write-Host "  User registered: $userId" -ForegroundColor Green
    Write-Host "  JWT Token: $($token.Substring(0, 30))..." -ForegroundColor DarkGray
} catch {
    Write-Host "  Registration failed (user may already exist), trying login..." -ForegroundColor DarkYellow
    $loginBody = @{
        email    = "admin@movietickets.com"
        password = "admin123"
    } | ConvertTo-Json
    $authResponse = Invoke-RestMethod -Uri "$USERS_URL/api/users/login" -Method POST -Body $loginBody -Headers $HEADERS
    $token = $authResponse.data.token
    $userId = $authResponse.data.user.id
    Write-Host "  User logged in: $userId" -ForegroundColor Green
}

# ── Step 2: Create a Theater with 2 screens ──
Write-Host "`n[2/4] Creating theater with 2 screens..." -ForegroundColor Yellow
$theaterBody = @{
    name    = "PVR Cinemas - Test"
    city    = "Mumbai"
    address = "Phoenix Mall, Lower Parel"
    screens = @(
        @{
            name = "IMAX Screen 1"
            seatLayouts = @(
                @{
                    category   = "REGULAR"
                    rowPrefix  = "A"
                    rowCount   = 8
                    seatsPerRow = 12
                    price      = 250.00
                },
                @{
                    category   = "PREMIUM"
                    rowPrefix  = "I"
                    rowCount   = 4
                    seatsPerRow = 12
                    price      = 450.00
                },
                @{
                    category   = "VIP"
                    rowPrefix  = "M"
                    rowCount   = 2
                    seatsPerRow = 8
                    price      = 750.00
                }
            )
        },
        @{
            name = "Screen 2"
            seatLayouts = @(
                @{
                    category   = "REGULAR"
                    rowPrefix  = "A"
                    rowCount   = 10
                    seatsPerRow = 10
                    price      = 200.00
                }
            )
        }
    )
} | ConvertTo-Json -Depth 5

$theaterResponse = Invoke-RestMethod -Uri "$THEATERS_URL/api/theaters" -Method POST -Body $theaterBody -Headers $HEADERS
$theaterId = $theaterResponse.data.id
$screen1Id = $theaterResponse.data.screens[0].id
$screen2Id = $theaterResponse.data.screens[1].id
Write-Host "  Theater: $theaterId" -ForegroundColor Green
Write-Host "  Screen 1 (IMAX): $screen1Id" -ForegroundColor Green
Write-Host "  Screen 2: $screen2Id" -ForegroundColor Green

# ── Step 3: Create 2 Movies ──
Write-Host "`n[3/4] Creating movies..." -ForegroundColor Yellow

$movie1Body = @{
    title           = "Interstellar"
    description     = "A team of explorers travel through a wormhole in space"
    genre           = "Sci-Fi"
    durationMinutes = 169
    language        = "English"
    releaseDate     = (Get-Date).ToString("yyyy-MM-dd")
} | ConvertTo-Json

$movie1Response = Invoke-RestMethod -Uri "$MOVIES_URL/api/movies" -Method POST -Body $movie1Body -Headers $HEADERS
$movie1Id = $movie1Response.data.id
Write-Host "  Movie 1: $movie1Id (Interstellar)" -ForegroundColor Green

$movie2Body = @{
    title           = "The Dark Knight"
    description     = "Batman faces the Joker"
    genre           = "Action"
    durationMinutes = 152
    language        = "English"
    releaseDate     = (Get-Date).ToString("yyyy-MM-dd")
} | ConvertTo-Json

$movie2Response = Invoke-RestMethod -Uri "$MOVIES_URL/api/movies" -Method POST -Body $movie2Body -Headers $HEADERS
$movie2Id = $movie2Response.data.id
Write-Host "  Movie 2: $movie2Id (The Dark Knight)" -ForegroundColor Green

# ── Step 4: Create Schedules ──
Write-Host "`n[4/4] Creating schedules..." -ForegroundColor Yellow
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")

$schedule1Body = @{
    movieId  = $movie1Id
    screenId = $screen1Id
    showDate = $tomorrow
    showTime = "14:00"
} | ConvertTo-Json

$schedule1Response = Invoke-RestMethod -Uri "$MOVIES_URL/api/movies/schedules" -Method POST -Body $schedule1Body -Headers $HEADERS
$schedule1Id = $schedule1Response.data.id
Write-Host "  Schedule 1: $schedule1Id (Interstellar `@ IMAX, 2:00 PM)" -ForegroundColor Green

$schedule2Body = @{
    movieId  = $movie2Id
    screenId = $screen2Id
    showDate = $tomorrow
    showTime = "18:00"
} | ConvertTo-Json

$schedule2Response = Invoke-RestMethod -Uri "$MOVIES_URL/api/movies/schedules" -Method POST -Body $schedule2Body -Headers $HEADERS
$schedule2Id = $schedule2Response.data.id
Write-Host "  Schedule 2: $schedule2Id (Dark Knight `@ Screen 2, 6:00 PM)" -ForegroundColor Green

# ── Summary ──
Write-Host "`n=== Seed Complete ===" -ForegroundColor Cyan
Write-Host "Seats are being seeded via Kafka (wait ~5 seconds)" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Key IDs for testing:" -ForegroundColor White
Write-Host "  User ID:      $userId"
Write-Host "  Theater ID:   $theaterId"
Write-Host "  Screen 1 ID:  $screen1Id"
Write-Host "  Screen 2 ID:  $screen2Id"
Write-Host "  Movie 1 ID:   $movie1Id"
Write-Host "  Movie 2 ID:   $movie2Id"
Write-Host "  Schedule 1:   $schedule1Id"
Write-Host "  Schedule 2:   $schedule2Id"
Write-Host "  JWT Token:    $($token.Substring(0, 50))..."
Write-Host ""
Write-Host 'Test a booking:' -ForegroundColor Yellow
Write-Host ('  curl -X POST http://localhost:8080/api/bookings `')
Write-Host ('    -H "Authorization: Bearer ' + $token.Substring(0, 20) + '..." `')
Write-Host ('    -H "Content-Type: application/json" `')
Write-Host ('    -d ''{"userId":"' + $userId + '","scheduleId":"' + $schedule1Id + '","seatNumbers":["A1","A2"]}''')
Write-Host ''
