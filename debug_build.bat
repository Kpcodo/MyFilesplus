@echo off
echo Running Java Version... > debug_out.txt
java -version >> debug_out.txt 2>&1
echo. >> debug_out.txt
echo Running Gradle Version... >> debug_out.txt
call gradlew.bat --version >> debug_out.txt 2>&1
echo Done. >> debug_out.txt
type debug_out.txt
