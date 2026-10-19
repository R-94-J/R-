param(
    [string]$DocumentPath = ''
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($DocumentPath)) {
    # Code points keep this script ASCII-compatible with Windows PowerShell 5.1.
    $reportName = [string][char]0x5F00 + [char]0x9898 + [char]0x62A5 + [char]0x544A
    $DocumentPath = "$PSScriptRoot\..\docs\proposal\${reportName}.docx"
}
$inputFile = (Resolve-Path -LiteralPath $DocumentPath).Path
$outputFile = [System.IO.Path]::ChangeExtension($inputFile, '.pdf')
$word = $null
$document = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($inputFile, $false, $true)
    $document.Repaginate()
    $document.ExportAsFixedFormat($outputFile, 17)
    "PDF: $outputFile"
    "Pages: $($document.ComputeStatistics(2))"
} finally {
    if ($null -ne $document) {
        $document.Close(0)
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($document)
    }
    if ($null -ne $word) {
        try { $word.Quit() } catch [System.Runtime.InteropServices.COMException] { }
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($word)
    }
}
