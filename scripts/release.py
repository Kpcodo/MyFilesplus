import os
import subprocess
import re

def run_command(command, check=True):
    print(f"Running: {command}")
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"Error: {result.stderr}")
        exit(result.returncode)
    return result.stdout.strip()

def main():
    # 1. Get Version Info
    with open("app/build.gradle.kts", "r") as f:
        content = f.read()
    
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    version_code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    
    if not version_name_match or not version_code_match:
        print("Could not find version info in build.gradle.kts")
        exit(1)
        
    version_name = version_name_match.group(1)
    version_code = version_code_match.group(1)
    tag = f"v{version_name}"
    
    print(f"Detected Version: {version_name} ({version_code})")

    # 2. Commit changes
    status = run_command("git status --short")
    if status:
        print("Changes detected, committing...")
        run_command("git add .")
        # Check if there's a custom message or just use version name
        commit_msg = f"Release {tag}: Internal Build {version_code}\n\nChanges:\n{status}"
        run_command(f'git commit -m "{commit_msg}"')
    else:
        print("No changes to commit.")

    # 3. Push to master
    print("Pushing to master...")
    run_command("git push origin master")

    # 4. Build APK
    print("Building Release APK...")
    # Using gradlew.bat for Windows
    run_command("gradlew.bat assembleRelease")

    # 5. Rename APK
    apk_source = "app/build/outputs/apk/release/app-release.apk"
    apk_dest = "app/build/outputs/apk/release/MyFilesplus_x25.apk"
    
    if os.path.exists(apk_source):
        if os.path.exists(apk_dest):
            os.remove(apk_dest)
        os.rename(apk_source, apk_dest)
        print(f"Renamed APK to {apk_dest}")
    else:
        print(f"Could not find source APK at {apk_source}")
        exit(1)

    # 6. Generate Release Notes
    # Get changes since last tag
    try:
        last_tag = run_command("git describe --tags --abbrev=0 HEAD^", check=False)
    except:
        last_tag = ""
        
    if last_tag:
        git_log = run_command(f'git log {last_tag}..HEAD --oneline')
    else:
        git_log = run_command('git log --oneline -n 10')

    release_notes = f"### {tag} Release Notes\n\n**What's New**\n"
    for line in git_log.split('\n'):
        if line:
            # Strip hash
            branch_msg = ' '.join(line.split(' ')[1:])
            release_notes += f"* {branch_msg}\n"
    
    release_notes += f"\n**Version:** {version_name}\n**Build:** {version_code}"

    # 7. Create GitHub Release
    print(f"Creating GitHub Release {tag}...")
    # Double check if tag already exists on remote, if so, we might need --overwrite or just update
    # For simplicity, we'll try to create it.
    notes_file = "release_notes.tmp"
    with open(notes_file, "w") as f:
        f.write(release_notes)
        
    run_command(f'gh release create {tag} {apk_dest} --title "Release {tag}" --notes-file {notes_file}')
    os.remove(notes_file)
    
    print("Release successful!")

if __name__ == "__main__":
    main()
