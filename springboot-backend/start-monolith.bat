@echo off
cd /d "%~dp0"
echo Starting legacy monolith (single JAR on port 8080)...
echo Do NOT run this together with api-gateway / microservices.
call mvnw.cmd -q -pl monolith -Plegacy-monolith spring-boot:run
