# Count Lines of Code
$extensions = @('*.java', '*.yml', '*.yaml', '*.xml', '*.properties', '*.md', '*.sh', '*.ps1', '*.sql', '*.json')
$excludePaths = @('target', 'node_modules', '.git', '.idea', '.vscode')

$files = Get-ChildItem -Path . -Include $extensions -Recurse -File | Where-Object {
    $path = $_.FullName
    $exclude = $false
    foreach ($excludePath in $excludePaths) {
        if ($path -match [regex]::Escape("\$excludePath\")) {
            $exclude = $true
            break
        }
    }
    -not $exclude
}

$totalLines = 0
$filesByType = @{}

foreach ($file in $files) {
    $lines = (Get-Content $file.FullName -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
    $totalLines += $lines
    
    $ext = $file.Extension
    if (-not $filesByType.ContainsKey($ext)) {
        $filesByType[$ext] = @{Count = 0; Lines = 0}
    }
    $filesByType[$ext].Count++
    $filesByType[$ext].Lines += $lines
}

Write-Host "`n=== PROJECT LINE COUNT ===" -ForegroundColor Cyan
Write-Host "Total Files: $($files.Count)" -ForegroundColor Green
Write-Host "Total Lines: $totalLines" -ForegroundColor Green

Write-Host "`n=== BREAKDOWN BY FILE TYPE ===" -ForegroundColor Cyan
$filesByType.GetEnumerator() | Sort-Object {$_.Value.Lines} -Descending | ForEach-Object {
    Write-Host "$($_.Key): $($_.Value.Count) files, $($_.Value.Lines) lines"
}
