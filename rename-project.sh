#!/usr/bin/env bash
#
# SSF project rename script for macOS/Linux.
#
# Examples:
#   ./rename-project.sh MyApp --dry-run
#   ./rename-project.sh MyApp
#   ./rename-project.sh MyApp --old-name WebTerm
#   ./rename-project.sh --new-name MyApp --root /path/to/project

set -euo pipefail

OLD_NAME="WebTerm"
NEW_NAME=""
ROOT=""
DRY_RUN=0

SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd -P)"

usage() {
    cat <<'EOF'
Usage:
  ./rename-project.sh NEW_NAME [options]
  ./rename-project.sh --new-name NEW_NAME [options]

Options:
  --old-name NAME     Existing project name to replace. Default: WebTerm
  --root PATH         Project root. Default: directory containing this script
  --dry-run           Print planned changes without writing files
  -h, --help          Show this help

Examples:
  ./rename-project.sh MyApp --dry-run
  ./rename-project.sh MyApp
  ./rename-project.sh --new-name MyApp --old-name WebTerm
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --new-name)
            [ "$#" -ge 2 ] || { echo "Missing value for --new-name" >&2; exit 1; }
            NEW_NAME="$2"
            shift 2
            ;;
        --old-name)
            [ "$#" -ge 2 ] || { echo "Missing value for --old-name" >&2; exit 1; }
            OLD_NAME="$2"
            shift 2
            ;;
        --root)
            [ "$#" -ge 2 ] || { echo "Missing value for --root" >&2; exit 1; }
            ROOT="$2"
            shift 2
            ;;
        --dry-run|-n)
            DRY_RUN=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [ -z "$NEW_NAME" ]; then
                NEW_NAME="$1"
                shift
            else
                echo "Unexpected argument: $1" >&2
                usage >&2
                exit 1
            fi
            ;;
    esac
done

if [ -z "$NEW_NAME" ]; then
    echo "New project name is required." >&2
    usage >&2
    exit 1
fi

if [ -z "$ROOT" ]; then
    ROOT="$SCRIPT_DIR"
fi

if [ ! -d "$ROOT" ]; then
    echo "Root path does not exist: $ROOT" >&2
    exit 1
fi

ROOT="$(cd "$ROOT" && pwd -P)"
SELF_REALPATH="$(cd "$(dirname "$SCRIPT_PATH")" && pwd -P)/$(basename "$SCRIPT_PATH")"

if [ "$OLD_NAME" = "$NEW_NAME" ]; then
    echo "OldName and NewName are identical. Nothing to do."
    exit 0
fi

case "$NEW_NAME" in
    *[!A-Za-z0-9_.-]*)
        echo "NewName may contain only letters, numbers, underscore, hyphen, and dot: '$NEW_NAME'" >&2
        exit 1
        ;;
esac

mode="APPLY"
if [ "$DRY_RUN" -eq 1 ]; then
    mode="DRY-RUN (no changes)"
fi

cat <<EOF

=====================================
 SSF Project Rename
=====================================
 Root     : $ROOT
 Old name : $OLD_NAME
 New name : $NEW_NAME
 Mode     : $mode
=====================================
EOF

is_text_extension() {
    local ext
    ext="$(printf '%s' "${1##*.}" | tr '[:upper:]' '[:lower:]')"
    case "$ext" in
        java|jsp|jspf|tag|tld|xml|properties|yml|yaml|json|sh|bat|cmd|ps1|md|txt|html|htm|css|js|iml|launch|cfg|conf)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

should_skip() {
    local full_path="$1"
    local rel="${full_path#$ROOT/}"
    local base
    local part

    [ "$full_path" = "$SELF_REALPATH" ] && return 0
    base="$(basename "$full_path")"
    case "$base" in
        rename-project.ps1|rename-project.sh)
            return 0
            ;;
    esac
    [ "${full_path%"~"}" != "$full_path" ] && return 0

    IFS='/' read -r -a parts <<< "$rel"
    for part in "${parts[@]}"; do
        case "$part" in
            target|build|out|dist|.git|.idea|.claude|.run|lib|servlet_lib|node_modules)
                return 0
                ;;
            tomcat.*)
                return 0
                ;;
        esac
    done

    return 1
}

relative_path() {
    local full_path="$1"
    printf '%s' "${full_path#$ROOT/}"
}

count_occurrences() {
    OLD_NAME="$OLD_NAME" perl -0ne '
        BEGIN { $old = $ENV{"OLD_NAME"}; }
        $count = () = /\Q$old\E/g;
        print $count;
    ' "$1"
}

replace_file() {
    OLD_NAME="$OLD_NAME" NEW_NAME="$NEW_NAME" perl -0pi -e '
        BEGIN {
            $old = $ENV{"OLD_NAME"};
            $new = $ENV{"NEW_NAME"};
        }
        s/\Q$old\E/$new/g;
    ' "$1"
}

ALL_FILES="$(mktemp)"
trap 'rm -f "$ALL_FILES"' EXIT

find "$ROOT" -type f -print0 > "$ALL_FILES"

echo
echo "[1/2] Replace file contents"
echo "-------------------------------------"

files_changed=0
total_occurrences=0

while IFS= read -r -d '' file; do
    if should_skip "$file"; then
        continue
    fi
    if ! is_text_extension "$file"; then
        continue
    fi
    if ! grep -qF "$OLD_NAME" "$file" 2>/dev/null; then
        continue
    fi

    count="$(count_occurrences "$file")"
    printf '  %s  (%s occurrences)\n' "$(relative_path "$file")" "$count"

    if [ "$DRY_RUN" -eq 0 ]; then
        replace_file "$file"
    fi

    files_changed=$((files_changed + 1))
    total_occurrences=$((total_occurrences + count))
done < "$ALL_FILES"

if [ "$files_changed" -eq 0 ]; then
    echo "  (no files to replace)"
fi

echo
echo "[2/2] Rename files"
echo "-------------------------------------"

files_renamed=0

while IFS= read -r -d '' file; do
    if should_skip "$file"; then
        continue
    fi

    dir="$(dirname "$file")"
    base="$(basename "$file")"

    case "$base" in
        *"$OLD_NAME"*) ;;
        *) continue ;;
    esac

    new_base="${base//$OLD_NAME/$NEW_NAME}"
    new_path="$dir/$new_base"

    if [ -e "$new_path" ]; then
        printf '  %s  -> %s  (already exists - skipped)\n' "$(relative_path "$file")" "$new_base"
        continue
    fi

    printf '  %s  -> %s\n' "$(relative_path "$file")" "$new_base"

    if [ "$DRY_RUN" -eq 0 ]; then
        mv "$file" "$new_path"
    fi

    files_renamed=$((files_renamed + 1))
done < "$ALL_FILES"

if [ "$files_renamed" -eq 0 ]; then
    echo "  (no files to rename)"
fi

cat <<EOF

=====================================
 Summary
=====================================
 Content replacement : $files_changed files, $total_occurrences occurrences
 File rename         : $files_renamed files
EOF

if [ "$DRY_RUN" -eq 1 ]; then
    cat <<'EOF'

 [DRY-RUN] No files were changed.
 Run again without --dry-run to apply changes.
EOF
else
    cat <<EOF

 Done. Items that may still need manual review:
  - Project root directory name (current: $(basename "$ROOT"))
  - IDE reload: .idea/ or nbproject/ cache
  - Old build outputs: target/, build/, out/, dist/  =>  mvn clean recommended
  - Absolute paths in context.xml / configplatform.xml
EOF
fi

echo
