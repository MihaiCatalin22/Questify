param(
    [string]$RuntimeBaseUrl = "http://127.0.0.1:11434",
    [string]$OutputRoot = "",
    [string[]]$Models = @("phi3:mini", "llama3.2:3b", "qwen2.5:3b", "smollm2:1.7b"),
    [double]$MinimumSuccessRate = 80,
    [string]$ScoresFile = "",
    [switch]$SkipPull,
    [switch]$GenerateReportOnly
)

$ErrorActionPreference = "Stop"

$script:ServiceRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$script:RepoRoot = Resolve-Path (Join-Path $script:ServiceRoot "..")
$script:GradleCommand = Join-Path $script:ServiceRoot "gradlew.bat"
$script:OllamaExe = $null

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $script:ServiceRoot "build\reports\coach-benchmark"
}

if ([string]::IsNullOrWhiteSpace($ScoresFile)) {
    $ScoresFile = Join-Path $OutputRoot "manual-review-scores.csv"
}

function Write-Section($text) {
    Write-Host ""
    Write-Host "== $text ==" -ForegroundColor Cyan
}

function Fail([string]$message) {
    throw $message
}

function Get-SafeModelName([string]$model) {
    return ($model -replace "[:/\\]", "-" -replace "[^A-Za-z0-9._-]", "-")
}

function Ensure-Directory([string]$path) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}

function Ensure-OllamaInstalled {
    $command = Get-Command ollama -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        $script:OllamaExe = $command.Source
        return
    }

    $candidates = @(
        "C:\Users\Administrator\AppData\Local\Programs\Ollama\ollama.exe",
        "C:\Program Files\Ollama\ollama.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $script:OllamaExe = $candidate
            return
        }
    }

    if (-not $script:OllamaExe) {
        Fail "Ollama is not installed or is not available on PATH. Install Ollama locally, confirm the 'ollama' command works, then rerun this script."
    }
}

function Test-OllamaRuntime {
    param([string]$BaseUrl)

    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/tags" -Method Get -TimeoutSec 3 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-ForOllamaRuntime {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds = 25
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-OllamaRuntime -BaseUrl $BaseUrl) {
            return
        }
        Start-Sleep -Milliseconds 750
    }

    Fail "Ollama runtime is not reachable at $BaseUrl."
}

function Ensure-OllamaRuntime {
    param([string]$BaseUrl)

    if (Test-OllamaRuntime -BaseUrl $BaseUrl) {
        return $null
    }

    $uri = [Uri]$BaseUrl
    if ($uri.Host -notin @("127.0.0.1", "localhost")) {
        Fail "Ollama runtime is not reachable at $BaseUrl, and auto-start is only supported for localhost URLs."
    }

    Write-Host "Starting local Ollama runtime..." -ForegroundColor Yellow
    $process = Start-Process -FilePath $script:OllamaExe -ArgumentList "serve" -PassThru -WindowStyle Hidden
    Wait-ForOllamaRuntime -BaseUrl $BaseUrl
    return $process
}

function Get-RuntimeModelNames {
    param([string]$BaseUrl)

    $tags = Invoke-RestMethod -Uri "$BaseUrl/api/tags" -Method Get -TimeoutSec 5
    $models = @()
    if ($null -ne $tags.models) {
        foreach ($item in $tags.models) {
            if ($null -ne $item.name) {
                $models += [string]$item.name
            }
        }
    }
    return $models
}

function Ensure-ModelAvailable {
    param(
        [string]$Model,
        [string]$BaseUrl,
        [bool]$PullModel
    )

    if ($PullModel) {
        Write-Host "Pulling $Model..." -ForegroundColor Yellow
        & $script:OllamaExe pull $Model
        if ($LASTEXITCODE -ne 0) {
            Fail "Failed to pull model $Model."
        }
    }

    $available = Get-RuntimeModelNames -BaseUrl $BaseUrl
    if ($available -notcontains $Model) {
        Fail "Model $Model is not available in the Ollama runtime at $BaseUrl."
    }
}

function Start-MemorySampler {
    $stopFile = Join-Path $env:TEMP ("coach-benchmark-stop-" + [guid]::NewGuid().ToString("N") + ".tmp")
    $job = Start-Job -ArgumentList $stopFile -ScriptBlock {
        param($stopFilePath)

        $maxBytes = 0L
        while (-not (Test-Path $stopFilePath)) {
            $processes = Get-Process -Name "ollama" -ErrorAction SilentlyContinue
            foreach ($process in $processes) {
                if ($process.WorkingSet64 -gt $maxBytes) {
                    $maxBytes = $process.WorkingSet64
                }
            }
            Start-Sleep -Milliseconds 500
        }
        return $maxBytes
    }

    return [pscustomobject]@{
        StopFile = $stopFile
        Job = $job
    }
}

function Stop-MemorySampler($sampler) {
    if ($null -eq $sampler) {
        return $null
    }

    New-Item -ItemType File -Force -Path $sampler.StopFile | Out-Null
    try {
        $bytes = Receive-Job -Job $sampler.Job -Wait -AutoRemoveJob
    } finally {
        Remove-Item -Path $sampler.StopFile -Force -ErrorAction SilentlyContinue
    }

    if ($null -eq $bytes) {
        return $null
    }

    return [math]::Round(($bytes / 1MB), 2)
}

function Read-Json([string]$path) {
    return Get-Content -Path $path -Raw | ConvertFrom-Json -Depth 50
}

function Write-Json([string]$path, $value) {
    $value | ConvertTo-Json -Depth 50 | Set-Content -Path $path -Encoding UTF8
}

function Invoke-BenchmarkBatch {
    param(
        [string]$Model,
        [string]$BaseUrl,
        [string]$BatchRoot,
        [double]$MinimumSuccessRate
    )

    $safeModel = Get-SafeModelName $Model
    $modelOutputRoot = Join-Path $BatchRoot $safeModel
    Ensure-Directory $modelOutputRoot

    $memorySampler = Start-MemorySampler
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $peakWorkingSetMb = $null

    try {
        try {
            Push-Location $script:ServiceRoot
            & $script:GradleCommand --no-daemon coachBenchmark `
                "-Dcoach.benchmark.enabled=true" `
                "-Dcoach.benchmark.model=$Model" `
                "-Dcoach.benchmark.runtime-base-url=$BaseUrl" `
                "-Dcoach.benchmark.output-dir=$modelOutputRoot" `
                "-Dcoach.benchmark.minimum-success-rate=$MinimumSuccessRate" `
                "-Dcoach.benchmark.warmup-runs=1" `
                "-Dcoach.benchmark.measured-runs=5"

            if ($LASTEXITCODE -ne 0) {
                Fail "Benchmark task failed for $Model."
            }
        } finally {
            Pop-Location
        }
    } finally {
        $stopwatch.Stop()
        $peakWorkingSetMb = Stop-MemorySampler $memorySampler
    }

    $summaryPath = Join-Path $modelOutputRoot "summary.json"
    if (-not (Test-Path $summaryPath)) {
        Fail "Benchmark summary was not written for $Model."
    }

    $summary = Read-Json $summaryPath
    $summary.automaticMetrics.peakWorkingSetMb = $peakWorkingSetMb
    $summary.automaticMetrics.totalBatchDurationMs = $stopwatch.ElapsedMilliseconds
    Write-Json $summaryPath $summary

    return $summary
}

function New-AutomaticComparisonRow($summary) {
    return [pscustomobject]@{
        model = [string]$summary.model
        measuredRequestCount = [int]$summary.measuredRequestCount
        successRate = [double]$summary.automaticMetrics.successRate
        fallbackRate = [double]$summary.automaticMetrics.fallbackRate
        retryRate = [double]$summary.automaticMetrics.retryRate
        timeoutRate = [double]$summary.automaticMetrics.timeoutRate
        validationFailureRate = [double]$summary.automaticMetrics.validationFailureRate
        p50LatencyMs = [double]$summary.automaticMetrics.p50LatencyMs
        p95LatencyMs = [double]$summary.automaticMetrics.p95LatencyMs
        coldStartLatencyMs = [double]$summary.automaticMetrics.coldStartLatencyMs
        peakWorkingSetMb = [double]$summary.automaticMetrics.peakWorkingSetMb
        totalBatchDurationMs = [int64]$summary.automaticMetrics.totalBatchDurationMs
        coachRequests = [double]$summary.actuatorMetrics.coachRequests
        coachSuccess = [double]$summary.actuatorMetrics.coachSuccess
        coachRetry = [double]$summary.actuatorMetrics.coachRetry
        coachTimeouts = [double]$summary.actuatorMetrics.coachTimeouts
        coachFallback = [double]$summary.actuatorMetrics.coachFallback
        coachValidationFailures = [double]$summary.actuatorMetrics.coachValidationFailures
    }
}

function Write-AutomaticComparisonArtifacts {
    param(
        [object[]]$Summaries,
        [string]$BatchRoot
    )

    $comparisonRows = foreach ($summary in $Summaries) {
        New-AutomaticComparisonRow $summary
    }

    $comparisonCsv = Join-Path $BatchRoot "comparison-automatic.csv"
    $comparisonRows | Sort-Object model | Export-Csv -Path $comparisonCsv -NoTypeInformation -Encoding UTF8

    return $comparisonRows
}

function Write-ManualReviewTemplate {
    param(
        [object[]]$Summaries,
        [string]$TemplatePath
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($summary in $Summaries) {
        foreach ($sample in $summary.representativeSamples) {
            $hasSuccess = [bool]$sample.hasSuccessSample
            $rows.Add([pscustomobject]@{
                model = [string]$summary.model
                scenarioId = [string]$sample.scenarioId
                scenarioTitle = [string]$sample.scenarioTitle
                hasSuccessSample = $hasSuccess
                responseStatus = [string]$sample.responseStatus
                sampleFile = [string]$sample.sampleFile
                goalAlignment = if ($hasSuccess) { "" } else { "0" }
                actionability = if ($hasSuccess) { "" } else { "0" }
                personalization = if ($hasSuccess) { "" } else { "0" }
                tone = if ($hasSuccess) { "" } else { "0" }
                variety = if ($hasSuccess) { "" } else { "0" }
                redFlagCount = if ($hasSuccess) { "" } else { "0" }
                redFlags = if ($hasSuccess) { "" } else { "No SUCCESS sample produced." }
                notes = if ($hasSuccess) { "" } else { "Automatic zero by benchmark rule." }
            })
        }
    }

    Ensure-Directory (Split-Path -Parent $TemplatePath)
    $rows | Sort-Object model, scenarioId | Export-Csv -Path $TemplatePath -NoTypeInformation -Encoding UTF8
}

function Convert-ToDoubleOrNull($value) {
    if ($null -eq $value) {
        return $null
    }
    $text = [string]$value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return [double]$text
}

function Load-ManualScoreRows {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return @()
    }

    return Import-Csv -Path $Path
}

function Get-ManualSummaryByModel {
    param([object[]]$Rows)

    $byModel = @{}
    foreach ($group in ($Rows | Group-Object model)) {
        $totals = @()
        $redFlaggedSamples = 0

        foreach ($row in $group.Group) {
            $goal = Convert-ToDoubleOrNull $row.goalAlignment
            $actionability = Convert-ToDoubleOrNull $row.actionability
            $personalization = Convert-ToDoubleOrNull $row.personalization
            $tone = Convert-ToDoubleOrNull $row.tone
            $variety = Convert-ToDoubleOrNull $row.variety
            $redFlagCount = Convert-ToDoubleOrNull $row.redFlagCount

            if ($null -eq $goal -or $null -eq $actionability -or $null -eq $personalization -or $null -eq $tone -or $null -eq $variety) {
                continue
            }

            $totals += ($goal + $actionability + $personalization + $tone + $variety)
            if ($null -ne $redFlagCount -and $redFlagCount -gt 0) {
                $redFlaggedSamples++
            }
        }

        if ($totals.Count -eq 0) {
            continue
        }

        $average = ($totals | Measure-Object -Average).Average
        $byModel[$group.Name] = [pscustomobject]@{
            model = $group.Name
            manualAverage = [math]::Round($average, 2)
            manualQualityScore = [math]::Round(($average / 25.0) * 100.0, 2)
            redFlaggedSamples = $redFlaggedSamples
            completedRows = $totals.Count
        }
    }

    return $byModel
}

function Get-NormalizedLowerIsBetterScores {
    param(
        [object[]]$Items,
        [string]$PropertyName
    )

    $values = @($Items | ForEach-Object { [double]($_.$PropertyName) })
    $min = ($values | Measure-Object -Minimum).Minimum
    $max = ($values | Measure-Object -Maximum).Maximum
    $scores = @{}

    foreach ($item in $Items) {
        $value = [double]($item.$PropertyName)
        $score = if ($max -eq $min) { 100.0 } else { 100.0 * (($max - $value) / ($max - $min)) }
        $scores[$item.model] = [math]::Round($score, 2)
    }

    return $scores
}

function Get-FinalScoredRows {
    param(
        [object[]]$ComparisonRows,
        $ManualByModel
    )

    $p50Scores = Get-NormalizedLowerIsBetterScores -Items $ComparisonRows -PropertyName "p50LatencyMs"
    $p95Scores = Get-NormalizedLowerIsBetterScores -Items $ComparisonRows -PropertyName "p95LatencyMs"
    $memoryScores = Get-NormalizedLowerIsBetterScores -Items $ComparisonRows -PropertyName "peakWorkingSetMb"

    $finalRows = New-Object System.Collections.Generic.List[object]
    foreach ($row in $ComparisonRows) {
        $manual = $ManualByModel[[string]$row.model]
        if ($null -eq $manual -or $manual.completedRows -lt 10) {
            continue
        }

        $hardGateFailures = New-Object System.Collections.Generic.List[string]
        if ($row.successRate -lt $MinimumSuccessRate) { $hardGateFailures.Add("success rate < $MinimumSuccessRate%") }
        if ($row.fallbackRate -gt 10) { $hardGateFailures.Add("fallback rate > 10%") }
        if ($row.timeoutRate -gt 5) { $hardGateFailures.Add("timeout rate > 5%") }
        if ($row.p95LatencyMs -gt 45000) { $hardGateFailures.Add("p95 latency > 45000 ms") }
        if ($manual.manualAverage -lt 18) { $hardGateFailures.Add("manual average < 18/25") }
        if ($manual.redFlaggedSamples -gt 2) { $hardGateFailures.Add("more than 2 red-flagged samples") }

        $successComponent = [double]$row.successRate
        $fallbackComponent = [math]::Max(0.0, 100.0 - [double]$row.fallbackRate)
        $retryComponent = [math]::Max(0.0, 100.0 - [double]$row.retryRate)
        $validationComponent = [math]::Max(0.0, 100.0 - [double]$row.validationFailureRate)
        $manualComponent = [double]$manual.manualQualityScore
        $p50Component = [double]$p50Scores[[string]$row.model]
        $p95Component = [double]$p95Scores[[string]$row.model]
        $memoryComponent = [double]$memoryScores[[string]$row.model]

        $finalScore =
            (0.20 * $successComponent) +
            (0.10 * $fallbackComponent) +
            (0.05 * $retryComponent) +
            (0.05 * $validationComponent) +
            (0.40 * $manualComponent) +
            (0.10 * $p50Component) +
            (0.05 * $p95Component) +
            (0.05 * $memoryComponent)

        $finalRows.Add([pscustomobject]@{
            model = [string]$row.model
            eligible = ($hardGateFailures.Count -eq 0)
            hardGateFailures = ($hardGateFailures -join "; ")
            successRate = [double]$row.successRate
            fallbackRate = [double]$row.fallbackRate
            retryRate = [double]$row.retryRate
            timeoutRate = [double]$row.timeoutRate
            validationFailureRate = [double]$row.validationFailureRate
            p50LatencyMs = [double]$row.p50LatencyMs
            p95LatencyMs = [double]$row.p95LatencyMs
            peakWorkingSetMb = [double]$row.peakWorkingSetMb
            manualAverage = [double]$manual.manualAverage
            manualQualityScore = [double]$manual.manualQualityScore
            redFlaggedSamples = [int]$manual.redFlaggedSamples
            finalScore = [math]::Round($finalScore, 2)
        })
    }

    return $finalRows
}

function Get-LowResourceFallback($finalRows) {
    $candidates = $finalRows | Where-Object {
        $_.successRate -ge $MinimumSuccessRate -and $_.timeoutRate -le 10 -and $_.manualAverage -ge 16
    } | Sort-Object `
        @{ Expression = "peakWorkingSetMb"; Descending = $false }, `
        @{ Expression = "successRate"; Descending = $true }, `
        @{ Expression = "fallbackRate"; Descending = $false }, `
        @{ Expression = "p95LatencyMs"; Descending = $false }

    if ($candidates.Count -eq 0) {
        return $null
    }

    return $candidates[0]
}

function ConvertTo-MarkdownTable {
    param(
        [string[]]$Headers,
        [object[]]$Rows
    )

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.AppendLine(("| " + ($Headers -join " | ") + " |"))
    [void]$builder.AppendLine(("| " + (($Headers | ForEach-Object { "---" }) -join " | ") + " |"))

    foreach ($row in $Rows) {
        $cells = foreach ($header in $Headers) {
            [string]$row.$header
        }
        [void]$builder.AppendLine(("| " + ($cells -join " | ") + " |"))
    }

    return $builder.ToString().TrimEnd()
}

function Write-BenchmarkReport {
    param(
        [object[]]$ComparisonRows,
        [string]$ReportPath,
        [string]$ManualTemplatePath,
        [string]$ScoresPath
    )

    $manualRows = Load-ManualScoreRows -Path $ScoresPath
    $manualByModel = Get-ManualSummaryByModel -Rows $manualRows
    $finalRows = Get-FinalScoredRows -ComparisonRows $ComparisonRows -ManualByModel $manualByModel

    $automaticTableRows = foreach ($row in ($ComparisonRows | Sort-Object `
            @{ Expression = "successRate"; Descending = $true }, `
            @{ Expression = "p95LatencyMs"; Descending = $false })) {
        [pscustomobject]@{
            model = $row.model
            successRate = "{0:N2}%" -f $row.successRate
            fallbackRate = "{0:N2}%" -f $row.fallbackRate
            retryRate = "{0:N2}%" -f $row.retryRate
            timeoutRate = "{0:N2}%" -f $row.timeoutRate
            validationFailureRate = "{0:N2}%" -f $row.validationFailureRate
            p50LatencyMs = "{0:N2}" -f $row.p50LatencyMs
            p95LatencyMs = "{0:N2}" -f $row.p95LatencyMs
            peakWorkingSetMb = "{0:N2}" -f $row.peakWorkingSetMb
        }
    }

    $report = New-Object System.Text.StringBuilder
    [void]$report.AppendLine("# Questify Coach Model Viability Report")
    [void]$report.AppendLine("")
    [void]$report.AppendLine('This report was generated by `coach-service/scripts/Run-CoachModelBenchmark.ps1`.')
    [void]$report.AppendLine("")
    [void]$report.AppendLine("## Setup")
    [void]$report.AppendLine("")
    [void]$report.AppendLine("- Runtime: Ollama")
    [void]$report.AppendLine("- Models: " + (($ComparisonRows | ForEach-Object { $_.model }) -join ", "))
    [void]$report.AppendLine('- Coach runtime settings: `timeout-ms=45000`, `max-output-tokens=160`, `temperature=0.15`, `retry-enabled=true`, `max-retries=1`')
    [void]$report.AppendLine("- Benchmark corpus: 10 fixed Questify coach scenarios, 1 warm-up request per model, 5 measured runs per scenario")
    [void]$report.AppendLine("- Minimum AI success gate: $MinimumSuccessRate%")
    [void]$report.AppendLine("")
    [void]$report.AppendLine("## Automatic Metrics")
    [void]$report.AppendLine("")
    [void]$report.AppendLine((ConvertTo-MarkdownTable -Headers @("model", "successRate", "fallbackRate", "retryRate", "timeoutRate", "validationFailureRate", "p50LatencyMs", "p95LatencyMs", "peakWorkingSetMb") -Rows $automaticTableRows))
    [void]$report.AppendLine("")

    if ($finalRows.Count -eq 0) {
        [void]$report.AppendLine("## Manual Review")
        [void]$report.AppendLine("")
        [void]$report.AppendLine("Manual scoring is still required before a winner can be recommended.")
        [void]$report.AppendLine("")
        [void]$report.AppendLine(('- Review template: `' + $ManualTemplatePath + '`'))
        [void]$report.AppendLine(('- Expected filled scores file: `' + $ScoresPath + '`'))
        [void]$report.AppendLine("- After filling the scores file, rerun:")
        [void]$report.AppendLine(('  `.\coach-service\scripts\Run-CoachModelBenchmark.ps1 -GenerateReportOnly -ScoresFile "' + $ScoresPath + '"`'))
    } else {
        $ordered = $finalRows | Sort-Object `
            @{ Expression = "eligible"; Descending = $true }, `
            @{ Expression = "finalScore"; Descending = $true }, `
            @{ Expression = "successRate"; Descending = $true }, `
            @{ Expression = "fallbackRate"; Descending = $false }, `
            @{ Expression = "p95LatencyMs"; Descending = $false }, `
            @{ Expression = "peakWorkingSetMb"; Descending = $false }
        $winner = $ordered | Where-Object { $_.eligible } | Select-Object -First 1
        $lowResource = Get-LowResourceFallback -finalRows $ordered
        $rejected = $ordered | Where-Object { -not $_.eligible }

        [void]$report.AppendLine("## Manual Review and Recommendation")
        [void]$report.AppendLine("")
        $scoreRows = foreach ($row in $ordered) {
            [pscustomobject]@{
                model = $row.model
                eligible = if ($row.eligible) { "yes" } else { "no" }
                manualAverage = "{0:N2}/25" -f $row.manualAverage
                redFlaggedSamples = [string]$row.redFlaggedSamples
                finalScore = "{0:N2}" -f $row.finalScore
                hardGateFailures = if ([string]::IsNullOrWhiteSpace($row.hardGateFailures)) { "-" } else { $row.hardGateFailures }
            }
        }
        [void]$report.AppendLine((ConvertTo-MarkdownTable -Headers @("model", "eligible", "manualAverage", "redFlaggedSamples", "finalScore", "hardGateFailures") -Rows $scoreRows))
        [void]$report.AppendLine("")

        [void]$report.AppendLine("### Recommendation")
        [void]$report.AppendLine("")
        if ($null -ne $winner) {
            [void]$report.AppendLine(('- Best balanced Questify Coach model: `' + $winner.model + '`'))
            [void]$report.AppendLine("  - Reason: it finished as the highest-scoring eligible model after applying the reliability, manual quality, and performance weighting.")
        } else {
            [void]$report.AppendLine("- Best balanced Questify Coach model: none of the tested models cleared the hard gates.")
        }

        if ($null -ne $lowResource) {
            [void]$report.AppendLine(('- Best low-resource fallback: `' + $lowResource.model + '`'))
            [void]$report.AppendLine("  - Reason: it delivered the lowest memory footprint among models that still met the fallback-quality floor.")
        } else {
            [void]$report.AppendLine("- Best low-resource fallback: no model met the fallback floor.")
        }

        if ($rejected.Count -gt 0) {
            [void]$report.AppendLine("- Models to reject outright: " + (($rejected | ForEach-Object { "$($_.model) ($($_.hardGateFailures))" }) -join "; "))
        } else {
            [void]$report.AppendLine("- Models to reject outright: none.")
        }
    }

    Set-Content -Path $ReportPath -Value $report.ToString() -Encoding UTF8
}

function Load-ExistingSummaries {
    param([string]$BatchRoot)

    $summaryFiles = Get-ChildItem -Path $BatchRoot -Recurse -Filter "summary.json" -File | Sort-Object FullName
    if ($summaryFiles.Count -eq 0) {
        Fail "No benchmark summaries were found under $BatchRoot."
    }

    return @($summaryFiles | ForEach-Object { Read-Json $_.FullName })
}

Ensure-Directory $OutputRoot
$manualTemplatePath = Join-Path $OutputRoot "manual-review-template.csv"
$reportPath = Join-Path $script:RepoRoot "COACH_MODEL_VIABILITY_REPORT.md"

if ($GenerateReportOnly) {
    Write-Section "Generating report from existing benchmark outputs"
    $summaries = Load-ExistingSummaries -BatchRoot $OutputRoot
    $comparisonRows = Write-AutomaticComparisonArtifacts -Summaries $summaries -BatchRoot $OutputRoot
    Write-ManualReviewTemplate -Summaries $summaries -TemplatePath $manualTemplatePath
    Write-BenchmarkReport -ComparisonRows $comparisonRows -ReportPath $reportPath -ManualTemplatePath $manualTemplatePath -ScoresPath $ScoresFile
    Write-Host "Report written to $reportPath" -ForegroundColor Green
    exit 0
}

$startedOllamaProcess = $null
try {
    Write-Section "Preflight"
    Ensure-OllamaInstalled
    $startedOllamaProcess = Ensure-OllamaRuntime -BaseUrl $RuntimeBaseUrl

    foreach ($model in $Models) {
        Ensure-ModelAvailable -Model $model -BaseUrl $RuntimeBaseUrl -PullModel (-not $SkipPull)
    }

    $summaries = New-Object System.Collections.Generic.List[object]
    foreach ($model in $Models) {
        Write-Section "Benchmarking $model"
        $summary = Invoke-BenchmarkBatch -Model $model -BaseUrl $RuntimeBaseUrl -BatchRoot $OutputRoot -MinimumSuccessRate $MinimumSuccessRate
        $summaries.Add($summary) | Out-Null
    }

    Write-Section "Writing comparison artifacts"
    $comparisonRows = Write-AutomaticComparisonArtifacts -Summaries $summaries -BatchRoot $OutputRoot
    Write-ManualReviewTemplate -Summaries $summaries -TemplatePath $manualTemplatePath
    Write-BenchmarkReport -ComparisonRows $comparisonRows -ReportPath $reportPath -ManualTemplatePath $manualTemplatePath -ScoresPath $ScoresFile

    Write-Host "Automatic comparison CSV: $(Join-Path $OutputRoot 'comparison-automatic.csv')" -ForegroundColor Green
    Write-Host "Manual review template: $manualTemplatePath" -ForegroundColor Green
    Write-Host "Report path: $reportPath" -ForegroundColor Green
} finally {
    if ($null -ne $startedOllamaProcess -and -not $startedOllamaProcess.HasExited) {
        Write-Host "Stopping Ollama runtime started by this script..." -ForegroundColor Yellow
        Stop-Process -Id $startedOllamaProcess.Id -Force
    }
}
