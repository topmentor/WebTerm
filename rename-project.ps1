<#
.SYNOPSIS
    SSF 프로젝트 이름 일괄 변경 스크립트.

.DESCRIPTION
    소스/설정 파일 내에 하드코딩된 프로젝트 이름(기본값: "WebTerm")을
    지정한 새 이름으로 일괄 치환하고, 파일명에 포함된 이름도 함께 변경한다.

    처리 대상:
      - 텍스트 파일 내용 치환 (.java, .jsp, .xml, .properties, .sh, .bat, .md, .iml 등)
      - 파일 이름 변경 (예: WebTerm.iml -> MyApp.iml)

    제외 대상:
      - 빌드 산출물   : target/, build/, out/, dist/
      - 런타임/캐시   : tomcat.*/
      - IDE / VCS     : .git/, .idea/, .claude/, .run/
      - 외부 라이브러리: lib/, servlet_lib/
      - 백업 파일     : *~

    디렉토리 이름은 변경하지 않는다 (프로젝트 루트 폴더명 등은 수동 변경).

.PARAMETER NewName
    새 프로젝트 이름 (필수). 예: "MyApp"

.PARAMETER OldName
    바꿀 기존 이름. 기본값 "WebTerm".

.PARAMETER Root
    프로젝트 루트 경로. 기본값: 이 스크립트가 위치한 폴더.

.PARAMETER DryRun
    실제 변경 없이 변경 예정 항목만 출력한다.

.EXAMPLE
    .\rename-project.ps1 -NewName MyApp -DryRun
    변경 미리보기.

.EXAMPLE
    .\rename-project.ps1 -NewName MyApp
    실제 적용.

.EXAMPLE
    .\rename-project.ps1 -NewName MyApp -OldName WebTerm
    OldName 을 명시적으로 지정.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$NewName,

    [string]$OldName = "WebTerm",

    [string]$Root = $PSScriptRoot,

    [switch]$DryRun
)

# ---- 초기 검증 ----
if ([string]::IsNullOrWhiteSpace($Root)) { $Root = (Get-Location).Path }
$Root = (Resolve-Path -LiteralPath $Root).Path

if ($OldName -eq $NewName) {
    Write-Host "OldName 과 NewName 이 동일합니다. 종료합니다." -ForegroundColor Yellow
    return
}

if ($NewName -notmatch '^[A-Za-z0-9_.\-]+$') {
    Write-Host "NewName 에는 영문/숫자/밑줄/하이픈/점만 사용하세요: '$NewName'" -ForegroundColor Red
    return
}

# ---- 설정 ----
# 이름이 정확히 일치하면 제외할 디렉토리
$excludeDirs = @(
    'target', 'build', 'out', 'dist',
    '.git', '.idea', '.claude', '.run',
    'lib', 'servlet_lib',
    'node_modules'
)

# 이 접두어로 시작하는 디렉토리 이름은 제외 (예: tomcat.8088, tomcat.9090)
$excludePrefixes = @('tomcat.')

# 치환 대상 확장자 (텍스트)
$includeExtensions = @(
    '.java', '.jsp', '.jspf', '.tag', '.tld',
    '.xml', '.properties', '.yml', '.yaml', '.json',
    '.sh', '.bat', '.cmd', '.ps1',
    '.md', '.txt',
    '.html', '.htm', '.css', '.js',
    '.iml', '.launch', '.cfg', '.conf'
)

# ---- 헤더 출력 ----
Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " SSF Project Rename" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host (" Root     : {0}" -f $Root)
Write-Host (" Old name : {0}" -f $OldName)
Write-Host (" New name : {0}" -f $NewName)
Write-Host (" Mode     : {0}" -f $(if ($DryRun) { 'DRY-RUN (변경 미적용)' } else { 'APPLY (실제 변경)' })) `
    -ForegroundColor $(if ($DryRun) { 'Yellow' } else { 'Green' })
Write-Host "=====================================" -ForegroundColor Cyan

# ---- 공통 함수 ----
$selfPath = $MyInvocation.MyCommand.Path

function Test-ShouldSkip {
    param([string]$FullPath)

    # 루트 밖이면 스킵
    if (-not $FullPath.StartsWith($Root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }

    # 스크립트 자기 자신은 스킵 (치환되면 재사용 불가)
    if ($selfPath -and ($FullPath -eq $selfPath)) { return $true }

    $rel = $FullPath.Substring($Root.Length).TrimStart('\', '/')
    if ([string]::IsNullOrEmpty($rel)) { return $false }

    $parts = $rel -split '[\\/]'
    foreach ($part in $parts) {
        if ($excludeDirs -contains $part) { return $true }
        foreach ($pfx in $excludePrefixes) {
            if ($part.StartsWith($pfx, [System.StringComparison]::OrdinalIgnoreCase)) {
                return $true
            }
        }
    }

    # 백업 파일
    if ($FullPath.EndsWith('~')) { return $true }

    return $false
}

function Write-RelativePath {
    param([string]$FullPath, [string]$Suffix = "", [string]$Color = "Gray")
    $rel = $FullPath.Substring($Root.Length).TrimStart('\', '/')
    if ($Suffix) {
        Write-Host ("  {0}  {1}" -f $rel, $Suffix) -ForegroundColor $Color
    } else {
        Write-Host ("  {0}" -f $rel) -ForegroundColor $Color
    }
}

# ---- 1단계: 파일 내용 치환 ----
Write-Host ""
Write-Host "[1/2] 파일 내용 치환" -ForegroundColor Green
Write-Host "-------------------------------------"

$filesChanged = 0
$totalOccurrences = 0

$allFiles = Get-ChildItem -Path $Root -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { -not (Test-ShouldSkip $_.FullName) }

$targetFiles = $allFiles | Where-Object {
    $includeExtensions -contains $_.Extension.ToLower()
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false

foreach ($file in $targetFiles) {
    try {
        $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    } catch {
        Write-RelativePath $file.FullName "(읽기 실패 - 스킵)" "DarkYellow"
        continue
    }

    if ([string]::IsNullOrEmpty($content)) { continue }
    if (-not $content.Contains($OldName)) { continue }

    # 리터럴 치환 + occurrence 카운트
    $count = ([regex]::Matches($content, [regex]::Escape($OldName))).Count
    $newContent = $content.Replace($OldName, $NewName)

    Write-RelativePath $file.FullName ("({0}건)" -f $count) "Gray"

    if (-not $DryRun) {
        [System.IO.File]::WriteAllText($file.FullName, $newContent, $utf8NoBom)
    }

    $filesChanged++
    $totalOccurrences += $count
}

if ($filesChanged -eq 0) {
    Write-Host "  (치환 대상 파일 없음)" -ForegroundColor DarkGray
}

# ---- 2단계: 파일 이름 변경 ----
Write-Host ""
Write-Host "[2/2] 파일 이름 변경" -ForegroundColor Green
Write-Host "-------------------------------------"

$filesRenamed = 0

# 파일명 변경은 깊은 경로부터 처리 (부모 디렉토리명과 겹치는 경우 대비)
$renameTargets = $allFiles |
    Where-Object { $_.Name.Contains($OldName) } |
    Sort-Object -Property { $_.FullName.Length } -Descending

foreach ($file in $renameTargets) {
    $newFileName = $file.Name.Replace($OldName, $NewName)
    $newFullPath = Join-Path $file.DirectoryName $newFileName

    if (Test-Path -LiteralPath $newFullPath) {
        Write-RelativePath $file.FullName ("-> {0}  (이미 존재 - 스킵)" -f $newFileName) "DarkYellow"
        continue
    }

    Write-RelativePath $file.FullName ("-> {0}" -f $newFileName) "Gray"

    if (-not $DryRun) {
        try {
            Rename-Item -LiteralPath $file.FullName -NewName $newFileName -ErrorAction Stop
        } catch {
            Write-Host ("    [오류] {0}" -f $_.Exception.Message) -ForegroundColor Red
            continue
        }
    }

    $filesRenamed++
}

if ($filesRenamed -eq 0) {
    Write-Host "  (이름 변경 대상 파일 없음)" -ForegroundColor DarkGray
}

# ---- 결과 요약 ----
Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " 결과 요약" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host (" 내용 치환 : {0} 파일, {1}건" -f $filesChanged, $totalOccurrences)
Write-Host (" 이름 변경 : {0} 파일" -f $filesRenamed)

if ($DryRun) {
    Write-Host ""
    Write-Host " [DRY-RUN] 실제 변경은 이루어지지 않았습니다." -ForegroundColor Yellow
    Write-Host " 적용하려면 -DryRun 없이 다시 실행하세요." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host " 완료. 추가로 수동 확인이 필요할 수 있는 항목:" -ForegroundColor Green
    Write-Host ("  - 프로젝트 루트 디렉토리명 (현재: {0})" -f (Split-Path -Leaf $Root))
    Write-Host "  - IDE 재로드: .idea/ 또는 nbproject/ 캐시"
    Write-Host "  - 기존 빌드 산출물: target/, build/, out/, dist/  =>  mvn clean 권장"
    Write-Host "  - context.xml / configplatform.xml 의 절대경로(C:\..., /usr/share/...) 확인"
}
Write-Host ""
