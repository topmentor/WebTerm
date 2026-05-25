<#
.SYNOPSIS
    SSF 프레임워크 기반 신규 프로젝트 생성 스크립트

.DESCRIPTION
    대화형으로 프로젝트 이름·패키지·DB 설정 등을 입력받아
    SSF 프레임워크 기반 새 프로젝트를 지정 디렉토리에 생성합니다.

    처리 내용:
      1. 소스 파일 복사 (.git, 빌드 산출물, 런타임 디렉토리 제외)
      2. 플랫폼 설정값 치환 (경로, 도메인, DB URL, Python 경로 등)
      3. Java 패키지 일괄 치환 (com.ithows → 신규 패키지)
      4. 프로젝트명 일괄 치환 (SSF2026 → 신규 프로젝트명)
      5. 소스 패키지 디렉토리 구조 재편성 (src/com/ithows → src/새/패키지)
      6. 프로젝트명이 포함된 파일명 변경 (*.iml 등)
      7. Git 초기화 (선택)

    제외 항목:
      - .git, .claude (환경별 설정)
      - target, build, out, dist (빌드 산출물)
      - tomcat.* (내장 런타임)
      - node_modules

    포함 항목:
      - lib, servlet_lib, .idea, .vscode, .run, nbproject, docs 등 모두 포함

.PARAMETER SourceRoot
    원본 SSF 프로젝트 루트. 기본값: 이 스크립트가 위치한 폴더.

.PARAMETER ConfigFile
    JSON 설정 파일 경로. 지정하면 대화형 입력을 생략하고 파일 값으로 자동 실행.
    템플릿 생성: .\new-ssf-project.ps1 -GenerateConfig

.PARAMETER Force
    확인 프롬프트를 모두 생략하고 자동으로 진행 (덮어쓰기 포함).

.PARAMETER GenerateConfig
    JSON 설정 파일 템플릿(ssf-project-config.json)을 생성한 뒤 종료.

.EXAMPLE
    .\new-ssf-project.ps1
    대화형 모드로 새 프로젝트 생성.

.EXAMPLE
    .\new-ssf-project.ps1 -SourceRoot C:\other\SSF_2026
    다른 경로의 SSF 소스를 기반으로 생성.

.EXAMPLE
    .\new-ssf-project.ps1 -GenerateConfig
    ssf-project-config.json 템플릿 파일 생성.

.EXAMPLE
    .\new-ssf-project.ps1 -ConfigFile ssf-project-config.json
    JSON 설정 파일로 비대화형 프로젝트 생성.

.EXAMPLE
    .\new-ssf-project.ps1 -ConfigFile ssf-project-config.json -Force
    JSON 설정 파일로 확인 없이 자동 생성.
#>

[CmdletBinding()]
param(
    [string]$SourceRoot    = $PSScriptRoot,
    [string]$ConfigFile    = '',      # JSON 설정 파일 경로 (지정 시 대화형 생략)
    [switch]$Force,                   # 확인 프롬프트 생략 (덮어쓰기 자동 수락)
    [switch]$GenerateConfig           # 설정 파일 템플릿 생성 후 종료
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ─────────────────────────────────────────────────────────────
# 원본 식별자 상수 (치환 대상 기준값)
# ─────────────────────────────────────────────────────────────
$SRC_PROJECT   = 'SSF2026'
$SRC_PACKAGE   = 'com.ithows'          # Java 패키지 (점 구분)
$SRC_PKG_URL   = 'com/ithows'          # JSP·XML 경로 스타일 (슬래시 구분)
$SRC_PKG_DIR   = 'com\ithows'          # 소스 디렉토리 경로 (Windows 역슬래시)

# configplatform.xml 원본값 (XML 내 리터럴 문자열과 정확히 일치해야 함)
$SRC_WIN_DIR   = 'C:\\03_work\\SSF_2026\\build\\web\\'
$SRC_LINUX_DIR = '/locationService/tomcat/tomcatweb/webapps/SSF2026/'
$SRC_SITE_DOM  = '[SSF2026 Location]'
$SRC_PYTHON    = 'C:\\Python310\\python.exe'
$SRC_API_KEY   = 'sox_api_key_2018'

# connpool.xml 원본 DB URL (XML 내 &amp; 포함)
$SRC_DB_URL  = 'jdbc:mysql://127.0.0.1:3307/collectdata?characterEncoding=utf8mb4&amp;useUnicode=true&amp;useSSL=false'
$SRC_DB2_URL = 'jdbc:mysql://127.0.0.1:3306/collectdata?characterEncoding=utf8&amp;useSSL=false'

# 복사 제외 목록 — .scaffold-ignore 파일에서 런타임에 로드됩니다
$SCAFFOLD_IGNORE_FILE = '.scaffold-ignore'

# 텍스트 치환 대상 확장자
$TEXT_EXTS = @(
    '.java', '.jsp', '.jspf', '.tag', '.tld',
    '.xml', '.properties', '.yml', '.yaml', '.json',
    '.sh', '.bat', '.cmd', '.ps1',
    '.md', '.txt', '.html', '.htm', '.css', '.js',
    '.iml', '.launch', '.cfg', '.conf', '.gradle'
)

# ─────────────────────────────────────────────────────────────
# UI 헬퍼
# ─────────────────────────────────────────────────────────────
function Write-Banner {
    param([string]$Text)
    $line = '=' * 62
    Write-Host ''
    Write-Host $line -ForegroundColor Cyan
    Write-Host ("  {0}" -f $Text) -ForegroundColor Cyan
    Write-Host $line -ForegroundColor Cyan
}

function Write-Phase {
    param([string]$Label, [string]$Msg)
    Write-Host ''
    Write-Host ("[{0}] " -f $Label) -ForegroundColor Green -NoNewline
    Write-Host $Msg
    Write-Host ('-' * 50)
}

function Write-Info  { param([string]$M) Write-Host ("  {0}" -f $M) -ForegroundColor Gray }
function Write-Ok    { param([string]$M) Write-Host ("  {0}" -f $M) -ForegroundColor Green }
function Write-Warn  { param([string]$M) Write-Host ("  [!] {0}" -f $M) -ForegroundColor DarkYellow }
function Write-Err   { param([string]$M) Write-Host ("  [X] {0}" -f $M) -ForegroundColor Red }

function Ask {
    param(
        [string]$Prompt,
        [string]$Default  = '',
        [switch]$Mandatory
    )
    $hint = if ($Default -ne '') { " [기본값: $Default]" } else { '' }
    while ($true) {
        Write-Host ("  {0}{1}: " -f $Prompt, $hint) -ForegroundColor Yellow -NoNewline
        $val = (Read-Host).Trim()
        if ($val -eq '') {
            if ($Default -ne '') { return $Default }
            if ($Mandatory)      { Write-Err '필수 항목입니다.'; continue }
            return ''
        }
        return $val
    }
}

function Confirm-Yes {
    param([string]$Q)
    Write-Host ("  {0} (y/N): " -f $Q) -ForegroundColor Yellow -NoNewline
    return ((Read-Host).Trim() -match '^[yY]')
}

# ─────────────────────────────────────────────────────────────
# .scaffold-ignore 파서
# 반환: @{ Dirs=[string[]]; Prefixes=[string[]]; Files=[string[]] }
# ─────────────────────────────────────────────────────────────
function Read-ScaffoldIgnore {
    param([string]$Path)

    $dirs     = [System.Collections.Generic.List[string]]::new()
    $prefixes = [System.Collections.Generic.List[string]]::new()
    $files    = [System.Collections.Generic.List[string]]::new()

    if (-not (Test-Path $Path)) {
        Write-Warn (".scaffold-ignore 파일 없음: {0} — 기본 제외 목록을 사용합니다." -f $Path)
        # 파일 없을 때 안전한 기본값
        $dirs.AddRange([string[]]@('.git','.github','.claude','target','build','out','dist','node_modules'))
        $prefixes.Add('tomcat.')
        return @{ Dirs = $dirs.ToArray(); Prefixes = $prefixes.ToArray(); Files = $files.ToArray() }
    }

    foreach ($raw in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }

        if ($line.EndsWith('/')) {
            $pattern = $line.TrimEnd('/')
            if ($pattern.EndsWith('*')) {
                # 접두어 패턴: tomcat.*/ → "tomcat."
                $prefixes.Add($pattern.TrimEnd('*'))
            } else {
                # 이름 완전 일치: .git/ → ".git"
                $dirs.Add($pattern)
            }
        } else {
            # 파일 패턴: *.class, Thumbs.db 등
            $files.Add($line)
        }
    }

    return @{ Dirs = $dirs.ToArray(); Prefixes = $prefixes.ToArray(); Files = $files.ToArray() }
}

# ─────────────────────────────────────────────────────────────
# 핵심 기능: robocopy 기반 파일 복사 (빠른 복사 + 진행 표시)
# ─────────────────────────────────────────────────────────────
function Copy-Tree {
    param(
        [string]   $Src,
        [string]   $Dst,
        [string[]] $ExclDirs,
        [string[]] $ExclPfx,
        [string[]] $ExclFiles = @()
    )

    # /XD 제외 목록: 이름 완전 일치 + 접두어 와일드카드 (예: tomcat.*)
    $xdList = $ExclDirs + ($ExclPfx | ForEach-Object { "${_}*" })

    # 대상이 원본 내부에 있을 때 무한 재귀 복사 방지
    # robocopy가 목적지를 먼저 생성하므로, 목적지의 최상위 세그먼트가
    # 소스에 폴더로 나타납니다. 전체 경로(/XD fullpath)는 robocopy에서
    # 신뢰하기 어렵고, 이름 기반 제외(/XD name)가 가장 확실합니다.
    if ($Dst.StartsWith($Src + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        $relPath    = $Dst.Substring($Src.Length + 1)               # 예: "make\test"
        $topSegName = ($relPath -split '[\\/]', 2)[0]               # 예: "make"
        Write-Warn ("대상 경로가 원본 내부입니다. '{0}' 폴더를 복사 제외합니다." -f $topSegName)
        $xdList += $topSegName    # 이름 기반 제외 → 트리 어디서나 매칭
    }

    # robocopy 옵션
    # /E   : 빈 디렉토리 포함 전체 복사
    # /NP  : 진행률(%) 숫자 표시 않음 (로그 가독성)
    # /NJH : 작업 헤더 생략
    # /NJS : 작업 요약 생략
    # /NFL : 파일 목록 생략 (디렉토리만 표시 → 진행 상황 파악 가능)
    $rcArgs = @($Src, $Dst, '/E', '/NP', '/NJH', '/NJS', '/NFL') + @('/XD') + $xdList

    # 파일 패턴 제외 (/XF): *.class, Thumbs.db 등
    if ($ExclFiles.Count -gt 0) {
        $rcArgs += @('/XF') + $ExclFiles
    }

    & robocopy @rcArgs | ForEach-Object {
        $line = $_.TrimEnd()
        if ($line -match '\S') {
            Write-Host ("    {0}" -f $line.Trim()) -ForegroundColor DarkGray
        }
    }

    # robocopy 종료 코드: 0=변경 없음, 1=정상 복사, 2~7=경고, 8+=오류
    if ($LASTEXITCODE -ge 8) {
        throw ("robocopy 오류 (exit code: {0}). 경로·권한을 확인하세요." -f $LASTEXITCODE)
    }
}

# ─────────────────────────────────────────────────────────────
# 핵심 기능: 텍스트 다중 치환 (multi-pass)
# ─────────────────────────────────────────────────────────────
function Invoke-TextReplace {
    param(
        [string]      $Root,
        [hashtable[]] $Pairs   # @{ Old='...'; New='...' } 배열
    )

    $enc   = New-Object System.Text.UTF8Encoding $false
    $files = 0
    $hits  = 0

    Get-ChildItem -Path $Root -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $script:TEXT_EXTS -contains $_.Extension.ToLower() } |
        ForEach-Object {
            $file = $_
            try {
                $txt = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
            } catch {
                Write-Warn ("읽기 실패 (스킵): {0}" -f $file.Name)
                return
            }

            if (-not $txt) { return }

            $changed = $false
            $new     = $txt

            foreach ($p in $Pairs) {
                if ($new.Contains($p.Old)) {
                    $cnt  = ([regex]::Matches($new, [regex]::Escape($p.Old))).Count
                    $new  = $new.Replace($p.Old, $p.New)
                    $hits += $cnt
                    $changed = $true
                }
            }

            if ($changed) {
                [System.IO.File]::WriteAllText($file.FullName, $new, $enc)
                $files++
                Write-Host ('    {0}' -f $file.FullName.Substring($Root.Length).TrimStart('\', '/')) `
                    -ForegroundColor DarkGray
            }
        }

    return [PSCustomObject]@{ Files = $files; Hits = $hits }
}

# ─────────────────────────────────────────────────────────────
# 핵심 기능: 소스 패키지 디렉토리 이동
# ─────────────────────────────────────────────────────────────
function Move-PackageDir {
    param(
        [string] $Root,
        [string] $OldPkg,   # 예: com\ithows
        [string] $NewPkg    # 예: com\mycompany
    )

    $srcBase = Join-Path $Root 'src'
    $oldDir  = Join-Path $srcBase $OldPkg
    $newDir  = Join-Path $srcBase $NewPkg

    if (-not (Test-Path $oldDir)) {
        Write-Warn ("소스 패키지 디렉토리를 찾을 수 없음: src\{0}" -f $OldPkg)
        return
    }

    if ($oldDir -ieq $newDir) {
        Write-Info '패키지 경로 동일 — 디렉토리 이동 생략'
        return
    }

    # 대상 부모 디렉토리 생성
    New-Item -ItemType Directory -Force -Path (Split-Path $newDir) | Out-Null

    if (Test-Path $newDir) {
        # 대상 디렉토리가 이미 있으면 파일 단위 병합 이동
        Write-Warn ("대상 디렉토리 존재 → 병합: src\{0}" -f $NewPkg)
        Get-ChildItem $oldDir -Recurse -File | ForEach-Object {
            $rel  = $_.FullName.Substring($oldDir.Length).TrimStart('\')
            $dest = Join-Path $newDir $rel
            New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
            Move-Item $_.FullName $dest -Force
        }
    } else {
        Move-Item $oldDir $newDir
    }

    Write-Info ("src\{0}  ->  src\{1}" -f $OldPkg, $NewPkg)

    # 비어있는 상위 디렉토리 정리
    $dir = Split-Path $oldDir
    while ($dir.Length -gt $srcBase.Length -and (Test-Path $dir)) {
        if ((Get-ChildItem $dir -ErrorAction SilentlyContinue).Count -gt 0) { break }
        Remove-Item $dir -Force
        $dir = Split-Path $dir
    }
}

# ─────────────────────────────────────────────────────────────
# 핵심 기능: 프로젝트명 포함 파일명 변경
# ─────────────────────────────────────────────────────────────
function Rename-ProjectFiles {
    param([string]$Root, [string]$OldName, [string]$NewName)

    $count = 0

    Get-ChildItem $Root -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name.Contains($OldName) } |
        Sort-Object { $_.FullName.Length } -Descending |    # 깊은 경로부터
        ForEach-Object {
            $newName = $_.Name.Replace($OldName, $NewName)
            $dest    = Join-Path $_.DirectoryName $newName

            if (Test-Path -LiteralPath $dest) {
                Write-Warn ("이미 존재 (스킵): {0}" -f $newName)
                return
            }

            try { Rename-Item -LiteralPath $_.FullName -NewName $newName -ErrorAction Stop }
            catch { Write-Warn ("이름 변경 실패: {0} — {1}" -f $_.Name, $_.Exception.Message); return }

            Write-Host ('    {0}  ->  {1}' -f
                $_.FullName.Substring($Root.Length).TrimStart('\', '/'), $newName) `
                -ForegroundColor DarkGray
            $count++
        }

    return $count
}

# ─────────────────────────────────────────────────────────────
# XML용 역슬래시 이중화 (Java Properties XML 형식)
# C:\foo\bar  →  C:\\foo\\bar
# ─────────────────────────────────────────────────────────────
function To-XmlBackslash {
    param([string]$Path)
    # 이미 이중화된 경우도 정규화: \\  →  \  →  \\
    return $Path.Replace('\\', '\').Replace('\', '\\')
}

# ─────────────────────────────────────────────────────────────
# JSON 설정 파일 읽기 및 필수 항목 검증
# 반환: PSCustomObject (projectName, groupId, siteDesc, targetParent,
#        dbHost, dbPort, dbName, winContextDir, linuxContextDir,
#        pythonExe, apiKey, gitInit)
# ─────────────────────────────────────────────────────────────
function Read-ProjectConfig {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw ("설정 파일을 찾을 수 없습니다: {0}" -f $Path)
    }

    $json = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json

    foreach ($key in @('projectName', 'groupId')) {
        if (-not $json.PSObject.Properties[$key] -or
            [string]::IsNullOrWhiteSpace($json.$key)) {
            throw ("설정 파일에 필수 항목 '{0}' 이(가) 없거나 비어 있습니다: {1}" -f $key, $Path)
        }
    }

    return $json
}

# ─────────────────────────────────────────────────────────────
# JSON 설정 파일 템플릿 생성
# ─────────────────────────────────────────────────────────────
function New-SampleConfig {
    param([string]$OutPath, [string]$SrcRoot)

    $sample = [ordered]@{
        '_comment'        = 'new-ssf-project.ps1 설정 파일. 빈 값("")은 자동 계산됩니다.'
        'projectName'     = 'MyNewProject'
        'groupId'         = 'com.example'
        'siteDesc'        = 'My New Project Service'
        'targetParent'    = (Join-Path $SrcRoot 'make_project')
        'dbHost'          = '127.0.0.1'
        'dbPort'          = '3307'
        'dbName'          = 'collectdata'
        'winContextDir'   = ''
        'linuxContextDir' = ''
        'pythonExe'       = 'C:\Python310\python.exe'
        'apiKey'          = 'sox_api_key_2018'
        'gitInit'         = $false
    }

    $json = $sample | ConvertTo-Json -Depth 3
    [System.IO.File]::WriteAllText($OutPath, $json, [System.Text.Encoding]::UTF8)
    Write-Ok ("설정 파일 템플릿 생성: {0}" -f $OutPath)
    Write-Info '항목을 편집한 뒤  -ConfigFile 옵션으로 전달하세요.'
    Write-Info '  gitInit: true  으로 설정하면 프로젝트 생성 후 git init이 자동 실행됩니다.'
}


# ═══════════════════════════════════════════════════════════════
# 메인 시작
# ═══════════════════════════════════════════════════════════════
Write-Banner 'SSF 신규 프로젝트 생성기  v1.0'
Write-Host ''
Write-Host '  SSF 프레임워크 소스를 복제하여 새 프로젝트를 구성합니다.'
Write-Host ("  원본: {0}" -f $SourceRoot) -ForegroundColor DarkCyan
Write-Host ''

# ── GenerateConfig: 템플릿 생성 후 종료 ──────────────────────
if ($GenerateConfig) {
    $outPath = Join-Path $SourceRoot 'ssf-project-config.json'
    New-SampleConfig -OutPath $outPath -SrcRoot $SourceRoot
    Write-Host ''
    Write-Host ("  출력: {0}" -f $outPath) -ForegroundColor Cyan
    Write-Host '  편집 후:  .\new-ssf-project.ps1 -ConfigFile ssf-project-config.json' -ForegroundColor DarkCyan
    Write-Host ''
    exit 0
}

# ─────────────────────────────────────────────────────────────
# 입력: JSON 설정 파일 모드 또는 대화형 모드
# ─────────────────────────────────────────────────────────────
$defaultParent = Join-Path $SourceRoot 'make_project'
$cfg = $null   # JSON 모드일 때 로드됨 (git 단계에서도 참조)

if ($ConfigFile -ne '') {
    # ══════════════════════════════════════════════════════════
    # JSON 설정 파일 모드 (비대화형)
    # ══════════════════════════════════════════════════════════
    Write-Phase 'CONFIG' ("JSON 설정 파일 로드: {0}" -f $ConfigFile)

    $cfg = Read-ProjectConfig -Path $ConfigFile

    # 필수 항목
    $projName = $cfg.projectName.Trim()
    $groupId  = $cfg.groupId.Trim()

    if ($projName -notmatch '^[A-Za-z][A-Za-z0-9_\-]{0,49}$') {
        throw ("projectName 형식 오류: '{0}' — 영문 시작, 영숫자·밑줄·하이픈, 1~50자" -f $projName)
    }
    if ($groupId -notmatch '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$') {
        throw ("groupId 형식 오류: '{0}' — 소문자, 점 구분, 2단계 이상" -f $groupId)
    }

    # 선택 항목 — 미설정 시 기본값 적용
    $siteDesc     = if ($cfg.PSObject.Properties['siteDesc']      -and $cfg.siteDesc)      { $cfg.siteDesc }      else { "$projName Service" }
    $targetParent = if ($cfg.PSObject.Properties['targetParent']  -and $cfg.targetParent)  { $cfg.targetParent }  else { $defaultParent }
    $dbHost       = if ($cfg.PSObject.Properties['dbHost']        -and $cfg.dbHost)        { $cfg.dbHost }        else { '127.0.0.1' }
    $dbPort       = if ($cfg.PSObject.Properties['dbPort']        -and $cfg.dbPort)        { $cfg.dbPort }        else { '3307' }
    $dbName       = if ($cfg.PSObject.Properties['dbName']        -and $cfg.dbName)        { $cfg.dbName }        else { 'collectdata' }
    $pythonExe    = if ($cfg.PSObject.Properties['pythonExe']     -and $cfg.pythonExe)     { $cfg.pythonExe }     else { 'C:\Python310\python.exe' }
    $apiKey       = if ($cfg.PSObject.Properties['apiKey']        -and $cfg.apiKey)        { $cfg.apiKey }        else { 'sox_api_key_2018' }

    $targetParent = $targetParent.TrimEnd('\', '/')
    if (-not (Test-Path $targetParent)) {
        New-Item -ItemType Directory -Force -Path $targetParent | Out-Null
        Write-Ok ("디렉토리 생성: {0}" -f $targetParent)
    }

    $destRoot = Join-Path $targetParent $projName

    # winContextDir / linuxContextDir — 빈 값이면 destRoot 기반 자동 설정
    $winDir   = if ($cfg.PSObject.Properties['winContextDir']   -and $cfg.winContextDir)   { $cfg.winContextDir }   else { "${destRoot}\build\web\" }
    $linuxDir = if ($cfg.PSObject.Properties['linuxContextDir'] -and $cfg.linuxContextDir) { $cfg.linuxContextDir } else { "/opt/tomcat/webapps/${projName}/" }

    # 대상이 원본 내부인 경우 — 비대화형: 잔류물 자동 삭제 후 계속
    if ($destRoot.StartsWith($SourceRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        $relPath    = $destRoot.Substring($SourceRoot.Length + 1)
        $topSegName = ($relPath -split '[\\/]', 2)[0]
        $topSegPath = Join-Path $SourceRoot $topSegName

        Write-Host ''
        Write-Warn "생성 위치가 원본 프로젝트 내부입니다. (권장: 원본 외부 경로 사용)"
        Write-Warn ("복사 시 '{0}' 폴더 전체가 제외됩니다." -f $topSegName)

        if (Test-Path $topSegPath) {
            Write-Warn ("잔류 디렉토리 자동 삭제: {0}" -f $topSegPath)
            $emptyTmp = Join-Path $env:TEMP ('ssf_empty_' + [System.IO.Path]::GetRandomFileName())
            New-Item -ItemType Directory -Path $emptyTmp | Out-Null
            & robocopy $emptyTmp $topSegPath /MIR /NFL /NDL /NJH /NJS | Out-Null
            Remove-Item $emptyTmp  -Force
            Remove-Item $topSegPath -Force -ErrorAction SilentlyContinue
            Write-Ok ("삭제 완료: {0}" -f $topSegPath)
        }
    }

    Write-Ok ('설정 로드 완료 — 프로젝트: {0}  /  패키지: {1}' -f $projName, $groupId)

} else {
    # ══════════════════════════════════════════════════════════
    # 대화형 입력 모드
    # ══════════════════════════════════════════════════════════
    Write-Host '  [Enter] = 기본값 사용'
    Write-Host ''

    # ── 입력 섹션 1: 프로젝트 기본 정보 ──────────────────────
    Write-Phase '입력 1/3' '프로젝트 기본 정보'

    $projName = ''
    while ($true) {
        $projName = Ask '프로젝트명 (영문 시작, 영숫자 / _ / - 허용)' -Mandatory
        if ($projName -match '^[A-Za-z][A-Za-z0-9_\-]{0,49}$') { break }
        Write-Err '영문으로 시작, 영숫자·밑줄·하이픈만, 1~50자'
    }

    $groupId = ''
    while ($true) {
        $groupId = Ask 'Java 패키지 (Group ID, 소문자·점만)' 'com.example'
        if ($groupId -match '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$') { break }
        Write-Err '예: com.mycompany  /  org.example.service  (소문자, 점으로 구분, 2단계 이상)'
    }

    $siteDesc = Ask '서비스 설명 (site_domain)' "$projName Service"

    # ── 입력 섹션 2: 생성 위치 ────────────────────────────────
    Write-Phase '입력 2/3' '프로젝트 생성 위치'

    $targetParent = ''
    while ($true) {
        $targetParent = Ask '상위 디렉토리 (프로젝트 폴더가 여기에 생성됨)' $defaultParent
        $targetParent = $targetParent.TrimEnd('\', '/')
        if (Test-Path $targetParent) { break }
        Write-Warn ("디렉토리 없음: {0}" -f $targetParent)
        if (Confirm-Yes '생성하시겠습니까?') {
            New-Item -ItemType Directory -Force -Path $targetParent | Out-Null
            break
        }
    }

    $destRoot = Join-Path $targetParent $projName

    # 대상이 원본 내부인 경우: 이전 실패 잔류물 정리 + 경고
    if ($destRoot.StartsWith($SourceRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        $relPath    = $destRoot.Substring($SourceRoot.Length + 1)
        $topSegName = ($relPath -split '[\\/]', 2)[0]
        $topSegPath = Join-Path $SourceRoot $topSegName

        Write-Host ''
        Write-Warn "생성 위치가 원본 프로젝트 내부입니다. (권장: 원본 외부 경로 사용)"
        Write-Warn ("복사 시 '{0}' 폴더 전체가 제외됩니다." -f $topSegName)

        if (Test-Path $topSegPath) {
            Write-Host ''
            Write-Warn ("이전 실행 잔류 디렉토리 감지: {0}" -f $topSegPath)
            if (Confirm-Yes '잔류 디렉토리를 삭제하고 계속하시겠습니까?') {
                Write-Info "삭제 중..."
                $emptyTmp = Join-Path $env:TEMP ('ssf_empty_' + [System.IO.Path]::GetRandomFileName())
                New-Item -ItemType Directory -Path $emptyTmp | Out-Null
                & robocopy $emptyTmp $topSegPath /MIR /NFL /NDL /NJH /NJS | Out-Null
                Remove-Item $emptyTmp  -Force
                Remove-Item $topSegPath -Force -ErrorAction SilentlyContinue
                Write-Ok ("삭제 완료: {0}" -f $topSegPath)
            } else {
                Write-Host '  취소되었습니다.' -ForegroundColor Yellow; exit 0
            }
        } elseif (-not (Confirm-Yes '계속 진행하시겠습니까?')) {
            Write-Host '  취소되었습니다.' -ForegroundColor Yellow; exit 0
        }
    }

    # ── 입력 섹션 3: DB · 경로 · 기타 ────────────────────────
    Write-Phase '입력 3/3' '데이터베이스 및 경로 설정  (Enter → 기본값 사용)'
    Write-Host ''

    $dbHost = Ask '  DB 호스트' '127.0.0.1'
    $dbPort = Ask '  DB 포트'   '3307'
    $dbName = Ask '  DB 이름'   'collectdata'

    Write-Host ''
    $suggestWin   = "${destRoot}\build\web\"
    $suggestLinux = "/opt/tomcat/webapps/${projName}/"
    $winDir       = Ask '  Windows context_win_dir (빌드 결과물 경로)' $suggestWin
    $linuxDir     = Ask '  Linux 배포 경로 (context_dir)'              $suggestLinux
    $pythonExe    = Ask '  Python 실행 파일 경로'                       'C:\Python310\python.exe'
    $apiKey       = Ask '  공통 API Key (common_api_key)'              'sox_api_key_2018'
}

# ─────────────────────────────────────────────────────────────
# 파생값 계산
# ─────────────────────────────────────────────────────────────
$newPkgUrl  = $groupId.Replace('.', '/')    # com/example
$newPkgDir  = $groupId.Replace('.', '\')    # com\example  (Windows 디렉토리용)
$winDirXml  = To-XmlBackslash $winDir       # XML 이중 역슬래시
$pythonXml  = To-XmlBackslash $pythonExe    # XML 이중 역슬래시
$contextPath = "${projName}/"
$newDbUrl   = "jdbc:mysql://${dbHost}:${dbPort}/${dbName}?characterEncoding=utf8mb4&amp;useUnicode=true&amp;useSSL=false"
$newDb2Url  = "jdbc:mysql://${dbHost}:${dbPort}/${dbName}?characterEncoding=utf8&amp;useSSL=false"

# ─────────────────────────────────────────────────────────────
# 설정 미리보기 + 확인
# ─────────────────────────────────────────────────────────────
Write-Banner '설정 확인'
Write-Host ''
Write-Host ("  생성 위치      : {0}" -f $destRoot)         -ForegroundColor White
Write-Host ("  프로젝트명     : {0,-25} ← {1}" -f $projName,  $SRC_PROJECT) -ForegroundColor White
Write-Host ("  Java 패키지    : {0,-25} ← {1}" -f $groupId,   $SRC_PACKAGE) -ForegroundColor White
Write-Host ("  서비스 설명    : {0}" -f $siteDesc)          -ForegroundColor White
Write-Host ''
Write-Host ("  DB             : jdbc:mysql://{0}:{1}/{2}" -f $dbHost, $dbPort, $dbName) -ForegroundColor White
Write-Host ("  context_path   : {0}" -f $contextPath)      -ForegroundColor White
Write-Host ("  Windows 경로   : {0}" -f $winDir)            -ForegroundColor White
Write-Host ("  Linux 경로     : {0}" -f $linuxDir)          -ForegroundColor White
Write-Host ("  Python         : {0}" -f $pythonExe)         -ForegroundColor White
Write-Host ("  API Key        : {0}" -f $apiKey)            -ForegroundColor White
Write-Host ''

if (Test-Path $destRoot) {
    Write-Warn ("대상 디렉토리가 이미 존재합니다: {0}" -f $destRoot)
    if (-not $Force -and -not (Confirm-Yes '기존 내용 위에 덮어씁니까?')) {
        Write-Host '  취소되었습니다.' -ForegroundColor Yellow; exit 0
    }
} elseif (-not $Force -and -not (Confirm-Yes '위 설정으로 프로젝트를 생성합니까?')) {
    Write-Host '  취소되었습니다.' -ForegroundColor Yellow; exit 0
}


# ═══════════════════════════════════════════════════════════════
# 실행 단계
# ═══════════════════════════════════════════════════════════════
$sw = [System.Diagnostics.Stopwatch]::StartNew()

# ── .scaffold-ignore 로드 ────────────────────────────────────
$ignoreFile = Join-Path $SourceRoot $SCAFFOLD_IGNORE_FILE
$ignore     = Read-ScaffoldIgnore -Path $ignoreFile

# ── 단계 1/5: 파일 복사 ──────────────────────────────────────
Write-Phase '단계 1/5' '소스 파일 복사'
Write-Info ("원본: {0}" -f $SourceRoot)
Write-Info ("대상: {0}" -f $destRoot)
Write-Info ("제외 설정: {0}  (디렉토리 {1}개, 접두어 {2}개, 파일패턴 {3}개)" -f
    $SCAFFOLD_IGNORE_FILE, $ignore.Dirs.Count, $ignore.Prefixes.Count, $ignore.Files.Count)
Write-Warn "lib/, servlet_lib/ 등 대용량 디렉토리 포함 시 수 분 소요될 수 있습니다."
Write-Host ''

Copy-Tree -Src $SourceRoot -Dst $destRoot `
          -ExclDirs  $ignore.Dirs `
          -ExclPfx   $ignore.Prefixes `
          -ExclFiles $ignore.Files

Write-Ok '복사 완료'

# ── 단계 2/5: 텍스트 치환 ────────────────────────────────────
Write-Phase '단계 2/5' '텍스트 치환'
Write-Host ''

# 치환 순서가 중요
#   1) 구체적·긴 패턴 먼저 (configplatform.xml 특정값, DB URL)
#   2) 패키지 URI 스타일 (com/ithows)
#   3) 패키지 dot 스타일 (com.ithows)
#   4) 프로젝트명 (SSF2026) — 마지막
#      ∵ 앞 항목들이 먼저 바뀐 뒤 SSF2026 잔존분만 처리
$pairs = @(
    # configplatform.xml 특정 값
    @{ Old = $SRC_SITE_DOM;  New = $siteDesc   }
    @{ Old = $SRC_WIN_DIR;   New = $winDirXml  }
    @{ Old = $SRC_LINUX_DIR; New = $linuxDir   }
    @{ Old = $SRC_PYTHON;    New = $pythonXml  }
    @{ Old = $SRC_API_KEY;   New = $apiKey     }

    # connpool.xml DB URL
    @{ Old = $SRC_DB_URL;    New = $newDbUrl   }
    @{ Old = $SRC_DB2_URL;   New = $newDb2Url  }

    # Java 패키지 (URI 스타일 → dot 스타일 순)
    @{ Old = $SRC_PKG_URL;   New = $newPkgUrl  }
    @{ Old = $SRC_PACKAGE;   New = $groupId    }

    # 프로젝트명 (마지막)
    @{ Old = $SRC_PROJECT;   New = $projName   }
)

$r = Invoke-TextReplace -Root $destRoot -Pairs $pairs
Write-Ok ("{0}개 파일, {1}건 치환 완료" -f $r.Files, $r.Hits)

# ── 단계 3/5: 소스 패키지 디렉토리 재편성 ───────────────────
Write-Phase '단계 3/5' '소스 패키지 디렉토리 재편성'

Move-PackageDir -Root $destRoot -OldPkg $SRC_PKG_DIR -NewPkg $newPkgDir

Write-Ok '완료'

# ── 단계 4/5: 파일명 변경 ────────────────────────────────────
Write-Phase '단계 4/5' '파일명 변경'

$renamed = Rename-ProjectFiles -Root $destRoot -OldName $SRC_PROJECT -NewName $projName
Write-Ok ("{0}개 파일 이름 변경 완료" -f $renamed)

# ── 단계 5/5: Git 초기화 (선택) ─────────────────────────────
Write-Phase '단계 5/5' 'Git 저장소 초기화'

$runGitInit = if ($null -ne $cfg -and $cfg.PSObject.Properties['gitInit']) {
    $cfg.gitInit -eq $true
} else {
    Confirm-Yes '새 프로젝트에 git init을 실행할까요?'
}

if ($runGitInit) {
    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($git) {
        Push-Location $destRoot
        try {
            & git init -q
            & git add .
            & git commit -q -m "chore: init ${projName} (scaffolded from SSF2026 framework)"
            Write-Ok 'git init + 초기 커밋 완료'
        } catch {
            Write-Warn ("Git 오류: {0}" -f $_.Exception.Message)
        } finally {
            Pop-Location
        }
    } else {
        Write-Warn 'git 명령을 찾을 수 없습니다. 수동으로 git init을 실행하세요.'
    }
} else {
    Write-Info '스킵 (수동으로 git init 실행 필요)'
}

$sw.Stop()


# ═══════════════════════════════════════════════════════════════
# 완료 요약
# ═══════════════════════════════════════════════════════════════
Write-Banner ("생성 완료!   소요 시간: {0:N1}초" -f $sw.Elapsed.TotalSeconds)
Write-Host ''
Write-Host ("  프로젝트 위치  : {0}" -f $destRoot) -ForegroundColor Cyan
Write-Host ("  프로젝트명     : {0}" -f $projName) -ForegroundColor Cyan
Write-Host ("  Java 패키지    : {0}" -f $groupId)  -ForegroundColor Cyan
Write-Host ''

Write-Host '  다음 단계:' -ForegroundColor Yellow
Write-Host '    1. IDE(IntelliJ / Eclipse / NetBeans)에서 프로젝트 열기 → Maven 재임포트'
Write-Host ("    2. web\WEB-INF\classes\connpool.xml  — DB username / password 추가")
Write-Host ("    3. web\WEB-INF\classes\configplatform.xml  — 경로·도메인 최종 확인")
Write-Host '    4. mvn clean package 후 Tomcat 배포  또는  embedded-run.bat 실행'
Write-Host ''

Write-Host '  수동 확인 필요:' -ForegroundColor DarkYellow
Write-Host '    - pom.xml <systemPath> 로컬 JAR 절대경로 (ojdbc6.jar 등)'
Write-Host '    - .idea / nbproject 내 절대경로 캐시 → IDE 재로드로 자동 갱신'
Write-Host '    - connpool.xml username / password (미설정 시 추가)'
Write-Host '    - tomcat.* 런타임 디렉토리는 복사 제외됨 → 별도 구성 필요'
Write-Host ''
