import os
import subprocess
import re
import sys 

def run_command(command, check=True):
    print(f"Running: {command}")
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"Error: {result.stderr}")
        exit(result.returncode)
    return result.stdout.strip()

def determine_bump_type(log_output):
    """
    Analyzes git log content to determine SemVer bump type.
    """
    lines = log_output.lower().split('\n')
    is_major = False
    is_minor = False
    
    for line in lines:
        if "breaking change" in line or "feat!:" in line or "fix!:" in line:
            is_major = True
            break # Major overrides everything
        if "feat:" in line:
            is_minor = True
            
    if is_major:
        return "MAJOR"
    elif is_minor:
        return "MINOR"
    else:
        return "PATCH"

def bump_version_file(file_path, bump_type, dry_run=False):
    with open(file_path, "r") as f:
        content = f.read()
    
    # regex for versionName = "1.2.3"
    vn_match = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', content)
    # regex for versionCode = 9
    vc_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    
    if not vn_match or not vc_match:
        print("Error: Could not parse version info from build.gradle.kts")
        exit(1)
        
    major = int(vn_match.group(1))
    minor = int(vn_match.group(2))
    patch = int(vn_match.group(3))
    code = int(vc_match.group(1))
    
    old_version_name = f"{major}.{minor}.{patch}"
    
    # Calculate new values
    new_code = code + 1
    
    if bump_type == "MAJOR":
        major += 1
        minor = 0
        patch = 0
    elif bump_type == "MINOR":
        minor += 1
        patch = 0
    else: # PATCH
        patch += 1
        
    new_version_name = f"{major}.{minor}.{patch}"
    
    print(f"Bumping Version: {old_version_name} ({code}) -> {new_version_name} ({new_code}) [{bump_type}]")
    
    if dry_run:
        return new_version_name, str(new_code)

    # Perform replacement
    new_content = content
    # Replace Name
    new_content = re.sub(r'versionName\s*=\s*"\d+\.\d+\.\d+"', f'versionName = "{new_version_name}"', new_content)
    # Replace Code
    new_content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', new_content)
    
    with open(file_path, "w") as f:
        f.write(new_content)
        
    return new_version_name, str(new_code)

def generate_release_notes(log_output, version_name, version_code):
    categories = {
        "🚀 New Features": [],
        "🐛 Bug Fixes": [],
        "⚡ Performance & Improvements": [],
        "🛠️ Maintenance & Chore": [],
        "Other Changes": []
    }

    lines = log_output.split('\n')
    for line in lines:
        if not line: continue
        
        parts = line.split(' ', 1)
        if len(parts) < 2: continue
        msg = parts[1].strip()
        lower_msg = msg.lower()
        
        if re.search(r'^(feat|feature)!?:?', lower_msg):
            categories["🚀 New Features"].append(msg)
        elif re.search(r'^(fix|bug)!?:?', lower_msg) or "fix" in lower_msg:
             categories["🐛 Bug Fixes"].append(msg)
        elif re.search(r'^(perf|optimize):?', lower_msg) or "optim" in lower_msg:
            categories["⚡ Performance & Improvements"].append(msg)
        elif re.search(r'^(chore|refactor|docs|style|test):?', lower_msg):
            categories["🛠️ Maintenance & Chore"].append(msg)
        else:
            categories["Other Changes"].append(msg)

    output = []
    output.append(f"### v{version_name} Release Notes")
    output.append("")
    
    for title, items in categories.items():
        if items:
            output.append(f"**{title}**")
            for item in items:
                clean_item = re.sub(r'^(feat|fix|perf|chore|refactor|docs|style|test)!?(\(.*\))?:?\s*', '', item, flags=re.IGNORECASE)
                if clean_item:
                    clean_item = clean_item[0].upper() + clean_item[1:]
                output.append(f"* {clean_item}")
            output.append("")

    output.append(f"**Version:** {version_name}")
    output.append(f"**Build:** {version_code}")
    
    return "\n".join(output)

def main():
    # Force UTF-8 encoding for stdout/stderr to handle emojis on Windows
    if sys.stdout.encoding != 'utf-8':
        sys.stdout.reconfigure(encoding='utf-8')
    if sys.stderr.encoding != 'utf-8':
        sys.stderr.reconfigure(encoding='utf-8')

    dry_run = "--dry-run" in sys.argv
    build_gradle = "app/build.gradle.kts"
    
    if dry_run:
        print("🔧 DRY RUN MODE: No git push or release creation will happen.")

    # 1. Analyze Changes & Dependencies (Needs Git Log FIRST)
    # Check current status for dirty changes
    status = run_command("git status --short")
    
    # Get last tag
    try:
        last_tag = run_command("git describe --tags --abbrev=0 HEAD^", check=False)
    except:
        last_tag = ""
    
    git_log = ""
    if last_tag and "fatal" not in last_tag:
        git_log = run_command(f'git log {last_tag}..HEAD --oneline')
    else:
        git_log = run_command('git log --oneline -n 10')

    # If dirty, simulate adding it to log for Analysis
    if status:
        fake_msg = "feat: Uncommitted changes included in this release"
        git_log = f"0000000 {fake_msg}\n{git_log}"

    # 2. Determine Version Bump
    bump_type = determine_bump_type(git_log)
    print(f"Detected SemVer Bump Type: {bump_type}")

    # 3. Apply Bump
    version_name, version_code = bump_version_file(build_gradle, bump_type, dry_run=dry_run)
    tag = f"v{version_name}"

    # 4. Commit Changes (Including Version Bump)
    if status or not dry_run: # If dirty OR we bumped version (modified file), we need to commit
        if not dry_run:
            print(f"Committing version bump {tag}...")
            run_command("git add .")
            commit_msg = f"chore: Release {tag} (Build {version_code})"
            run_command(f'git commit -m "{commit_msg}"')
        else:
            print(f"Would commit: chore: Release {tag} (Build {version_code})")
    
    # 5. Push
    if not dry_run:
        print("Pushing to master...")
        run_command("git push origin master")
    else:
        print("Would push to master")
        
    # 6. Build
    if not dry_run:
        print("Building Release APK...")
        run_command("gradlew.bat assembleRelease")
        
        apk_source = "app/build/outputs/apk/release/app-release.apk"
        apk_dest = "app/build/outputs/apk/release/MyFilesplus_x25.apk"
        
        if os.path.exists(apk_source):
             if os.path.exists(apk_dest):
                os.remove(apk_dest)
             os.rename(apk_source, apk_dest)
             print(f"Renamed APK to {apk_dest}")
        else:
             print("Error: Build failed or APK not found.")
             exit(1)
    else:
        apk_dest = "DRY_RUN_APK"

    # 7. Generate Notes
    release_notes = generate_release_notes(git_log, version_name, version_code)
    
    if dry_run:
        print("\n" + "="*30)
        print(release_notes)
        print("="*30 + "\n")
        with open("dry_run_notes.md", "w", encoding="utf-8") as f:
            f.write(release_notes)

    # 8. Release
    if not dry_run:
        print(f"Creating GitHub Release {tag}...")
        notes_file = "release_notes.tmp"
        with open(notes_file, "w", encoding="utf-8") as f:
            f.write(release_notes)
        run_command(f'gh release create {tag} "{apk_dest}" --title "Release {tag}" --notes-file {notes_file}')
        os.remove(notes_file)
        print("Release Successful!")

if __name__ == "__main__":
    main()
