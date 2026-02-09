import subprocess
import sys
import os
import datetime
import re

class AntigravityToolbox:
    def __init__(self):
        self.workspace_root = os.getcwd()
        if sys.platform == 'win32':
             sys.stdout.reconfigure(encoding='utf-8')

    def run_command(self, command, check=True):
        print(f"Executing: {command}")
        try:
            result = subprocess.run(command, shell=True, capture_output=True, text=True, encoding='utf-8', errors='replace')
            if check and result.returncode != 0:
                print(f"Error: {result.stderr}")
                return None
            return result.stdout.strip()
        except Exception as e:
            print(f"Execution failed: {e}")
            return None

    def get_git_diff(self):
        """Returns staged and unstaged changes."""
        unstaged = self.run_command("git diff")
        staged = self.run_command("git diff --cached")
        return f"--- UNSTAGED ---\n{unstaged}\n--- STAGED ---\n{staged}"

    def get_latest_tag(self):
        """Fetches the latest git tag."""
        tag = self.run_command("git describe --tags --abbrev=0", check=False)
        if not tag:
            return "v1.0.0"
        return tag

    def increment_tag(self, tag):
        """Increments version tag serially (v1.2.0 -> v1.2.1)."""
        match = re.search(r'v?(\d+)\.(\d+)\.(\d+)', tag)
        if match:
            major, minor, patch = map(int, match.groups())
            new_tag = f"v{major}.{minor}.{patch + 1}"
            return new_tag
        return "v1.0.1"

    def commit_all(self, message):
        """Stages all changes, commits, and pushes."""
        self.run_command("git add .")
        self.run_command(f'git commit -m "{message}"')
        self.run_command("git push")
        print("Successfully committed and pushed changes.")

    def release_finalize(self, tag, notes_file):
        """Runs final release steps: tag, push tags, and gh release create."""
        # Read notes from file if provided, else use default
        notes = ""
        if os.path.exists(notes_file):
            with open(notes_file, 'r', encoding='utf-8') as f:
                notes = f.read()
        
        # Create tag
        self.run_command(f"git tag {tag}")
        self.run_command("git push --tags")
        
        # Create GitHub release
        # Note: Using heredoc or temp file for notes to avoid shell escaping issues
        with open("release_notes_tmp.md", "w", encoding='utf-8') as f:
            f.write(notes)
            
        self.run_command(f'gh release create {tag} -F release_notes_tmp.md')
        os.remove("release_notes_tmp.md")
        print(f"Successfully released {tag} to GitHub.")

    def build_and_upload(self, name, tag):
        """Builds APK, renames, and uploads to existing release."""
        print("Starting Gradle build...")
        
        # Use gradlew.bat on Windows
        gradle_cmd = "gradlew.bat assembleRelease" if os.name == 'nt' else "./gradlew assembleRelease"
        build_res = self.run_command(gradle_cmd)
        
        if build_res is None:
            print("Build failed!")
            return False
        
        # Search for the APK in build outputs
        apk_found = None
        search_dir = os.path.join("app", "build", "outputs", "apk", "release")
        
        if os.path.exists(search_dir):
            for file in os.listdir(search_dir):
                if file.endswith(".apk"):
                    apk_found = os.path.join(search_dir, file)
                    break
        
        if not apk_found:
            # Fallback search
            for root, dirs, files in os.walk(os.path.join("app", "build", "outputs")):
                for file in files:
                    if file.endswith(".apk") and "release" in root.lower():
                        apk_found = os.path.join(root, file)
                        break
                if apk_found: break

        if not name.lower().endswith(".apk"):
            target_apk = f"{name}.apk"
        else:
            target_apk = name

        if apk_found and os.path.exists(apk_found):
            import shutil
            shutil.copy(apk_found, target_apk)
            print(f"Renamed {apk_found} to {target_apk}")
            
            # Upload using gh
            self.run_command(f'gh release upload {tag} "{target_apk}" --clobber')
            print(f"Uploaded {target_apk} to release {tag}")
            return True
        else:
            print(f"Could not find any APK in app/build/outputs/")
            return False

def main():
    if len(sys.argv) < 2:
        print("Antigravity Toolbox - Usage: python antigravity_tools.py <command> [args]")
        return

    toolbox = AntigravityToolbox()
    cmd = sys.argv[1].lower()

    if cmd == "get_diff":
        print(toolbox.get_git_diff())
    elif cmd == "get_tag":
        print(toolbox.get_latest_tag())
    elif cmd == "next_tag":
        tag = toolbox.get_latest_tag()
        print(toolbox.increment_tag(tag))
    elif cmd == "commit":
        msg = sys.argv[2] if len(sys.argv) > 2 else "Automatic commit"
        toolbox.commit_all(msg)
    elif cmd == "finalize_release":
        tag = sys.argv[2]
        notes_path = sys.argv[3]
        toolbox.release_finalize(tag, notes_path)
    elif cmd == "build_upload":
        name = sys.argv[2]
        tag = sys.argv[3] if len(sys.argv) > 3 else toolbox.get_latest_tag()
        toolbox.build_and_upload(name, tag)
    else:
        print(f"Unknown command: {cmd}")

if __name__ == "__main__":
    main()
